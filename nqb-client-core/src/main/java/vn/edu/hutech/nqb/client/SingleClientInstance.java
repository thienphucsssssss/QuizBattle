package vn.edu.hutech.nqb.client;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One launcher per Windows profile, shared by all installs/roles; local IPC only activates UI. */
public final class SingleClientInstance implements AutoCloseable {
    private final Path metadata;
    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket listener;
    private final String token;
    private final AtomicBoolean closed=new AtomicBoolean();
    private Runnable activation;
    private boolean pendingActivation;
    private SingleClientInstance(Path metadata,FileChannel channel,FileLock lock,ServerSocket listener,String token){this.metadata=metadata;this.channel=channel;this.lock=lock;this.listener=listener;this.token=token;}
    public static Path defaultDirectory(){String local=System.getenv("LOCALAPPDATA");return (local==null||local.isBlank()?Path.of(System.getProperty("user.home"),".quizbattle"):Path.of(local,"QuizBattle")).resolve("run");}
    public static SingleClientInstance acquire(Path directory)throws IOException{return acquire(directory,5000);}
    static SingleClientInstance acquire(Path directory,long timeoutMillis)throws IOException {
        directory=directory.toAbsolutePath().normalize();Files.createDirectories(directory);
        Path metadata=directory.resolve("client.instance");long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        do {
            var channel=FileChannel.open(directory.resolve("client.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            FileLock lock=null;try{lock=channel.tryLock();}catch(OverlappingFileLockException alreadyRunning){}catch(IOException failure){channel.close();throw failure;}
            if(lock!=null){
                ServerSocket listener=null;
                try {
                    listener=new ServerSocket();listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),8);
                    String token=UUID.randomUUID().toString()+UUID.randomUUID();
                    var instance=new SingleClientInstance(metadata,channel,lock,listener,token);
                    var values=new Properties();values.setProperty("port",Integer.toString(listener.getLocalPort()));values.setProperty("pid",Long.toString(ProcessHandle.current().pid()));values.setProperty("token",token);
                    Path temp=Files.createTempFile(directory,"client-instance-",".tmp");
                    try{try(var out=Files.newOutputStream(temp)){values.store(out,"QuizBattle local activation");}try{Files.move(temp,metadata,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException noAtomicMove){Files.move(temp,metadata,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
                    Thread.ofPlatform().daemon().name("nqb-instance-activation").start(instance::listen);
                    return instance;
                }catch(IOException|RuntimeException failure){if(listener!=null)try{listener.close();}catch(IOException ignored){}lock.release();channel.close();throw failure;}
            }
            channel.close();
            if(activateExisting(metadata))return new SingleClientInstance(metadata,null,null,null,"");
            try{Thread.sleep(100);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Đã hủy mở QuizBattle.",interrupted);}
        }while(System.nanoTime()<deadline);
        throw new IOException("QuizBattle đang khởi động hoặc chưa phản hồi. Hãy chờ rồi mở lại.");
    }
    public boolean isPrimary(){return lock!=null;}
    public synchronized void onActivation(Runnable action){activation=Objects.requireNonNull(action);if(pendingActivation){pendingActivation=false;activation.run();}}
    private synchronized void activate(){if(activation==null)pendingActivation=true;else activation.run();}
    private void listen(){
        while(!closed.get())try(var socket=listener.accept()){
            socket.setSoTimeout(750);
            if(!("HELLO "+token).equals(readLine(socket.getInputStream())))continue;
            writeLine(socket.getOutputStream(),"PID "+ProcessHandle.current().pid());
            if(!"ACTIVATE".equals(readLine(socket.getInputStream())))continue;
            activate();writeLine(socket.getOutputStream(),"OK");
        }catch(IOException ignored){/* Local activation failures never change the running session. */}
    }
    private static boolean activateExisting(Path metadata){
        try {
            var values=new Properties();try(var in=Files.newInputStream(metadata)){values.load(in);}
            int port=Integer.parseInt(values.getProperty("port"));long pid=Long.parseLong(values.getProperty("pid"));String token=values.getProperty("token","");
            if(port<1||port>65535||token.length()>100||token.isBlank()||ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isEmpty())return false;
            try(var socket=new Socket()){
                socket.connect(new InetSocketAddress("127.0.0.1",port),500);socket.setSoTimeout(1000);
                writeLine(socket.getOutputStream(),"HELLO "+token);
                if(!("PID "+pid).equals(readLine(socket.getInputStream())))return false;
                allowForeground(pid);writeLine(socket.getOutputStream(),"ACTIVATE");return "OK".equals(readLine(socket.getInputStream()));
            }
        }catch(IOException|RuntimeException unavailable){return false;}
    }
    private interface User32 extends StdCallLibrary {boolean AllowSetForegroundWindow(int processId);}
    private static void allowForeground(long pid){if(!System.getProperty("os.name","").startsWith("Windows"))return;try{Native.load("user32",User32.class).AllowSetForegroundWindow((int)pid);}catch(LinkageError|RuntimeException unavailable){/* JavaFX still restores the existing window. */}}
    private static String readLine(InputStream stream)throws IOException{var text=new StringBuilder();for(int i=0;i<128;i++){int value=stream.read();if(value<0)throw new EOFException();if(value=='\n')return text.toString();if(value!='\r')text.append((char)value);}throw new IOException("Invalid activation message");}
    private static void writeLine(OutputStream stream,String line)throws IOException{stream.write((line+"\n").getBytes(StandardCharsets.US_ASCII));stream.flush();}
    @Override public void close(){
        if(!closed.compareAndSet(false,true)||!isPrimary())return;
        try{listener.close();}catch(IOException ignored){}
        try{Files.deleteIfExists(metadata);}catch(IOException ignored){}
        try{lock.release();}catch(IOException ignored){}
        try{channel.close();}catch(IOException ignored){}
    }
}
