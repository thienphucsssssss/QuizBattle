package vn.edu.hutech.nqb.client;

import vn.edu.hutech.nqb.shared.DiscoveryProtocol;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/** Only Server-key-pinned TLS ROOM_BROWSE results reach the interface. */
public final class LanDirectory {
    private LanDirectory() { }
    public record Room(String serverId,Endpoint endpoint,String code,String name,String teacher,String phase,int players,int maxPlayers,boolean joinable) { }
    public record Server(String id,String name,Endpoint endpoint,List<Room> rooms) {
        @Override public String toString(){return name;}
    }
    public static List<Server> scan(ClientConnectionProfile profile) throws Exception {
        if(!java.nio.file.Files.isRegularFile(profile.truststore()))throw new IllegalStateException("Bộ cài chưa có chứng chỉ máy chủ. Nhận bộ cài Client từ Admin.");
        var endpoints=new LinkedHashSet<Endpoint>();
        // Explicit legacy destinations are hints only. Automatic profiles carry no hotspot IP.
        if(!profile.automaticLan())try { if(DiscoveryProtocol.localAddress(InetAddress.getByName(profile.host())))endpoints.add(new Endpoint(profile.host(),profile.port())); } catch(Exception ignored) { }
        try { endpoints.addAll(LanDiscovery.find(profile,650)); } catch(Exception ignored) { /* Directed TLS fallback remains available. */ }
        var pool=Executors.newFixedThreadPool(4,Thread.ofPlatform().daemon().name("lan-probe-",0).factory());
        var completion=new ExecutorCompletionService<Server>(pool);int submitted=0;
        try {
            for(var endpoint:endpoints){if(submitted>=8)break;completion.submit(()->probe(profile,endpoint));submitted++;}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);var servers=new LinkedHashMap<String,Server>();
            for(int i=0;i<submitted;i++) {
                long left=deadline-System.nanoTime();if(left<=0)break;var completed=completion.poll(left,TimeUnit.NANOSECONDS);if(completed==null)break;
                try {var server=completed.get();if(server!=null)servers.putIfAbsent(server.id(),server);}catch(ExecutionException rejected) { }
            }
            return servers.values().stream().sorted(Comparator.comparing(Server::name)).toList();
        } finally {pool.shutdownNow();}
    }
    private static Server probe(ClientConnectionProfile profile,Endpoint endpoint)throws Exception {
        char[] secret=profile.truststorePassword().toCharArray();
        try(var gateway=TlsQuizGateway.connect(endpoint,profile.truststore(),secret,1500)) {
            var response=gateway.request("ROOM_BROWSE",QuizGateway.object()).get(1500,TimeUnit.MILLISECONDS);
            String id=response.path("server_id").asText(),name=response.path("server_name").asText();
            if(!id.matches("[a-f0-9-]{36}")||name.isBlank()||name.length()>80||!response.path("rooms").isArray()||response.path("rooms").size()>1)return null;
            var rooms=new ArrayList<Room>();
            for(var row:response.path("rooms")) {
                String code=row.path("room_code").asText(),roomName=row.path("room_name").asText(),teacher=row.path("teacher_name").asText(),phase=row.path("phase").asText();
                int players=row.path("player_count").asInt(-1),max=row.path("max_players").asInt(-1);
                if(!code.matches("[A-Z2-9]{6}")||roomName.isBlank()||roomName.length()>80||teacher.length()>80||players<0||max<2||max>10||players>max||!Set.of("WAITING","COUNTDOWN","QUESTION_ACTIVE","SHOW_RESULT","SHOW_RANKING","FINISHED").contains(phase))return null;
                rooms.add(new Room(id,endpoint,code,roomName,teacher,phase,players,max,row.path("joinable").asBoolean()&&phase.equals("WAITING")&&players<max));
            }
            return new Server(id,name,endpoint,List.copyOf(rooms));
        } finally {Arrays.fill(secret,'\0');}
    }
}
