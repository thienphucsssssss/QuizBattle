package vn.edu.hutech.nqb.server;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Console entrypoint; secrets are read from console or process environment, never arguments. */
public final class ServerMain {
    private ServerMain() { }

    public static void main(String[] args) throws Exception {
        // Windows may select Cp1252 for stdout even when file.encoding is UTF-8.
        // Encode log bytes explicitly; this also covers Maven exec:java launches.
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
        Map<String,String> options=new HashMap<>();
        for(String arg:args)if(arg.startsWith("--")){String[] pair=arg.substring(2).split("=",2);options.put(pair[0],pair.length==2?pair[1]:"true");}
        if(options.containsKey("seed"))throw new IllegalArgumentException("Demo data has been removed. Create your questions in the Client.");
        var config=ServerConfig.load(Path.of(options.getOrDefault("config","config/server.properties")));
        // A failed first bootstrap may have created an empty DB. Inspect the account,
        // rather than treating existence of the DB file as successful initialization.
        if(options.containsKey("check-admin")&&!java.nio.file.Files.exists(config.database())) {
            System.out.println("NQB_ADMIN_MISSING");return;
        }
        var store=new QuizStore(config.database());
        if(options.containsKey("check-admin")) {
            try(store){System.out.println(store.hasAdmin()?"NQB_ADMIN_PRESENT":"NQB_ADMIN_MISSING");}return;
        }
        if(options.containsKey("init-admin")) {
            try(store) {
                if(store.hasAdmin())throw new IllegalStateException("Admin đã tồn tại. Không thay đổi mật khẩu hoặc dữ liệu.");
                char[] password=secret("NQB_BOOTSTRAP_PASSWORD","Mật khẩu Admin mới: ");
                try{store.bootstrap(options.getOrDefault("username","admin"),"Admin",password);System.out.println("Đã khởi tạo Admin; chưa mở cổng mạng.");}finally{Arrays.fill(password,'\0');}
            }return;
        }
        if(!store.hasAdmin()){store.close();throw new IllegalStateException("Chưa có Admin. Chạy scripts/Start-Server.ps1 -InitAdmin trước.");}
        char[] password=secret("NQB_KEYSTORE_PASSWORD","Mật khẩu khóa TLS Server: ");QuizServer server;
        try{server=new QuizServer(config,config.tls(password),store);}catch(Exception e){store.close();throw e;}finally{Arrays.fill(password,'\0');}
        var done=new CountDownLatch(1);Runtime.getRuntime().addShutdownHook(new Thread(()->{server.close();done.countDown();},"server-shutdown"));
        server.start();System.out.println("NQB_SERVER_READY TLS "+config.bind()+":"+server.port()+" | SQLite: "+config.database());
        System.out.println("LAN Discovery UDP "+server.discoveryPort());
        try {for(var link:vn.edu.hutech.nqb.shared.LanNetworks.current()) {
            System.out.println("LAN_INTERFACE "+link.name()+" | "+link.address().getHostAddress()+"/"+link.prefixLength());
        }} catch(java.net.SocketException unavailable){System.out.println("LAN_INTERFACE: Khong doc duoc danh sach card mang; Server van dang chay.");}
        if(config.bind().equals("0.0.0.0"))System.out.println("LAN_AUTO: Client tu tim Server; khong can doi IP khi chuyen diem phat.");
        System.out.println("Chỉ dùng trong LAN; không forward cổng Internet. Ctrl+C để dừng.");done.await();
    }
    private static char[] secret(String environment,String prompt){String value=System.getenv(environment);if(value!=null&&!value.isEmpty())return value.toCharArray();var console=System.console();if(console!=null){char[] chars=console.readPassword(prompt);if(chars!=null&&chars.length>0)return chars;}throw new IllegalStateException("Thiếu secret "+environment+". Dùng script khởi chạy để nhập an toàn.");}
}
