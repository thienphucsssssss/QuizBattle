package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SingleClientInstanceTest {
    @TempDir Path temp;
    private static void await(java.util.function.BooleanSupplier check)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(!check.getAsBoolean()){if(System.nanoTime()>until)fail("Activation did not arrive");Thread.sleep(10);}}
    @Test void secondLaunchActivatesFirstAndNeverOwnsItsLock()throws Exception {
        var calls=new AtomicInteger();try(var first=SingleClientInstance.acquire(temp)){
            first.onActivation(calls::incrementAndGet);
            try(var second=SingleClientInstance.acquire(temp)){assertFalse(second.isPrimary());}await(()->calls.get()==1);
            assertTrue(first.isPrimary());assertTrue(Files.exists(temp.resolve("client.instance")));
            try(var third=SingleClientInstance.acquire(temp)){assertFalse(third.isPrimary());}await(()->calls.get()==2);
        }
    }
    @Test void doubleClickDuringStartupIsBufferedUntilWindowExists()throws Exception {
        var calls=new AtomicInteger();try(var first=SingleClientInstance.acquire(temp)){
            try(var second=SingleClientInstance.acquire(temp)){assertFalse(second.isPrimary());}assertEquals(0,calls.get());
            first.onActivation(calls::incrementAndGet);assertEquals(1,calls.get());
        }
    }
    @Test void closingFirstReleasesLockAndStaleMetadataCannotPreventRestart()throws Exception {
        try(var first=SingleClientInstance.acquire(temp)){assertTrue(first.isPrimary());}
        Files.writeString(temp.resolve("client.instance"),"port=1\npid=99999999\ntoken=stale\n");
        try(var reopened=SingleClientInstance.acquire(temp)){assertTrue(reopened.isPrimary());}
        assertFalse(Files.exists(temp.resolve("client.instance")));
    }
    @Test void invalidLocalActivationTokenCannotRaiseTheWindow()throws Exception {
        var calls=new AtomicInteger();try(var first=SingleClientInstance.acquire(temp)){
            first.onActivation(calls::incrementAndGet);var values=new Properties();try(var in=Files.newInputStream(temp.resolve("client.instance"))){values.load(in);}
            try(var socket=new Socket("127.0.0.1",Integer.parseInt(values.getProperty("port")))){
                socket.setSoTimeout(1500);socket.getOutputStream().write("HELLO wrong-token\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();assertEquals(-1,socket.getInputStream().read());
            }
            assertEquals(0,calls.get());try(var valid=SingleClientInstance.acquire(temp)){assertFalse(valid.isPrimary());}await(()->calls.get()==1);
        }
    }
    @Test void busyStartupWithoutMetadataFailsInsteadOfCreatingAnotherClient()throws Exception {
        try(var channel=FileChannel.open(temp.resolve("client.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
            assertThrows(IOException.class,()->SingleClientInstance.acquire(temp,150));
        }
    }
    @Test void crashReleasesTheOperatingSystemLock()throws Exception {
        Path ready=temp.resolve("ready");
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),SingleInstanceProcessFixture.class.getName(),temp.toString(),ready.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{await(()->Files.exists(ready));assertTrue(child.isAlive());child.destroyForcibly();assertTrue(child.waitFor(4,TimeUnit.SECONDS));try(var next=SingleClientInstance.acquire(temp)){assertTrue(next.isPrimary());}}
        finally{if(child.isAlive())child.destroyForcibly().waitFor(4,TimeUnit.SECONDS);}
    }
    @Test void simultaneousProcessesElectExactlyOneClient()throws Exception {
        var children=new java.util.ArrayList<Process>();var results=new java.util.ArrayList<Path>();
        try {
            for(int i=0;i<6;i++){
                Path result=temp.resolve("launch-"+i);results.add(result);
                children.add(new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),SingleInstanceProcessFixture.class.getName(),temp.toString(),result.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start());
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
            while(results.stream().anyMatch(path->!Files.exists(path)||read(path).isEmpty())){
                if(System.nanoTime()>deadline)fail("Concurrent launches did not finish startup");Thread.sleep(20);
            }
            assertEquals(1,results.stream().filter(path->read(path).equals("PRIMARY")).count());
            assertEquals(5,results.stream().filter(path->read(path).equals("SECONDARY")).count());
            for(int i=0;i<children.size();i++)if(read(results.get(i)).equals("SECONDARY")){
                assertTrue(children.get(i).waitFor(4,TimeUnit.SECONDS));assertEquals(0,children.get(i).exitValue());
            }
        } finally {for(var child:children)if(child.isAlive())child.destroyForcibly().waitFor(4,TimeUnit.SECONDS);}
        try(var reopened=SingleClientInstance.acquire(temp)){assertTrue(reopened.isPrimary());}
    }
    private static String read(Path file){try{return Files.readString(file);}catch(IOException unavailable){return "";}}
}
