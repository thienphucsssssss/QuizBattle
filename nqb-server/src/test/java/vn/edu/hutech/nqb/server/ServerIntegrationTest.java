package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hutech.nqb.client.*;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

/** Production server + production TLS gateway + real temporary SQLite, not Preview. */
class ServerIntegrationTest {
    @TempDir static Path certs;
    @TempDir Path data;
    static final char[] KEY_PASSWORD="ephemeral-test-only".toCharArray();
    static Path key,trust;
    QuizServer server; int port,nextStudentIp=1; final List<Client> clients=new ArrayList<>();
    @BeforeAll static void certificates() throws Exception {
        key=certs.resolve("server.p12");trust=certs.resolve("trust.p12");Path certificate=certs.resolve("server.cer");
        keytool("-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=ip:127.0.0.1,dns:localhost","-storetype","PKCS12","-keystore",key.toString(),"-storepass",new String(KEY_PASSWORD));
        keytool("-exportcert","-alias","test","-keystore",key.toString(),"-storepass",new String(KEY_PASSWORD),"-file",certificate.toString());
        keytool("-importcert","-noprompt","-alias","test","-storetype","PKCS12","-keystore",trust.toString(),"-storepass",new String(KEY_PASSWORD),"-file",certificate.toString());
    }
    static void keytool(String...args)throws Exception{var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString());command.addAll(List.of(args));var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue());}
    @BeforeEach void start()throws Exception {
        var store=new QuizStore(data.resolve("quiz.db"));int owner=store.bootstrap("admin","Quản trị","Admin-test-only-1".toCharArray());ServerTestData.seedQuestions(store,owner);
        var config=new ServerConfig("127.0.0.1",0,data.resolve("quiz.db"),key,10,32,100,120,180);
        server=new QuizServer(config,config.tls(KEY_PASSWORD),store);server.start();port=server.port();
    }
    @AfterEach void close(){clients.forEach(Client::close);if(server!=null)server.close();}
    final class Client implements AutoCloseable {
        final TlsQuizGateway gateway;final BlockingQueue<JsonNode> events=new LinkedBlockingQueue<>();
        Client()throws Exception{this("127.0.0.1");}
        Client(String sourceIp)throws Exception{gateway=connectFrom(sourceIp);gateway.onEvent(events::add);clients.add(this);}
        JsonNode request(String type,ObjectNode payload)throws Exception{return gateway.request(type,payload).get(8,TimeUnit.SECONDS);}
        JsonNode event(String type)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(9);while(System.nanoTime()<until){var e=events.poll(100,TimeUnit.MILLISECONDS);if(e!=null&&e.path("type").asText().equals(type))return e.path("payload");}throw new AssertionError("Missing event "+type);}
        @Override public void close(){gateway.close();}
    }
    // Forward encrypted bytes unchanged; the real gateway still performs TLS and hostname verification.
    // Only the upstream socket's source address changes to simulate separate student machines.
    private TlsQuizGateway connectFrom(String sourceIp)throws Exception {
        if(sourceIp.equals("127.0.0.1"))return TlsQuizGateway.connect(new Endpoint("127.0.0.1",port),trust,KEY_PASSWORD);
        try(var forwarder=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            int targetPort=port;
            Thread.ofVirtual().start(()->{
                try(var upstream=new Socket();var downstream=forwarder.accept()){
                    upstream.bind(new InetSocketAddress(sourceIp,0));upstream.connect(new InetSocketAddress("127.0.0.1",targetPort),5000);
                    upstream.setTcpNoDelay(true);downstream.setTcpNoDelay(true);
                    Thread.ofVirtual().start(()->relay(downstream,upstream));relay(upstream,downstream);
                }catch(IOException ignored){/* The real gateway reports transport failures. */}
            });
            return TlsQuizGateway.connect(new Endpoint("127.0.0.1",forwarder.getLocalPort()),trust,KEY_PASSWORD);
        }
    }
    private static void relay(Socket from,Socket to){
        try{from.getInputStream().transferTo(to.getOutputStream());}catch(IOException ignored){}
        finally{try{from.close();}catch(IOException ignored){}try{to.close();}catch(IOException ignored){}}
    }
    Client admin()throws Exception{var c=new Client();assertEquals("ADMIN",c.request("LOGIN",obj().put("username","admin").put("password","Admin-test-only-1")).path("role").asText());return c;}
    String room(Client host,int count)throws Exception{return host.request("ROOM_CONFIGURE",obj().put("room_name","Lớp LAN").put("question_count",count).put("duration_seconds",5)).path("room_code").asText();}
    Client guest(String code,String name)throws Exception{var c=new Client("127.0.0."+nextStudentIp++);c.request("JOIN",obj().put("room_code",code).put("nickname",name));return c;}
    void error(Client c,String code,String type,ObjectNode p){var ex=assertThrows(ExecutionException.class,()->c.request(type,p));assertEquals(code,assertInstanceOf(GatewayException.class,ex.getCause()).code());}
    private ObjectNode importRow(String content){return obj().put("content",content).put("option_a","TCP").put("option_b","UDP").put("option_c","IP").put("option_d","ARP").put("correct_answer","A").put("category","Mạng LAN");}
    private ObjectNode rangeOptions(){
        return obj().put("survey_id",UUID.randomUUID().toString()).put("survey_name","Đo thật").put("route","Tầng 2")
            .put("topology","DEDICATED_AP").put("distance_basis","CORRIDOR_ROUTE").put("distance_m",0).put("duration_seconds",30);
    }
    @Test void remoteRangeTargetsOnlyTheSelectedStudentAndEnforcesRoomOwnership()throws Exception {
        var host=admin();String code=room(host,1);var chosen=guest(code,"Được chọn");var other=guest(code,"Không chọn");
        String playerId=chosen.request("ROOM_STATE",obj()).path("connection_info").path("player_id").asText();
        String control=UUID.randomUUID().toString();
        error(other,"FORBIDDEN","LAN_RANGE_REMOTE_START",rangeOptions().put("player_id",playerId).put("control_id",control));
        error(host,"CLIENT_OFFLINE","LAN_RANGE_REMOTE_START",rangeOptions().put("player_id","missing").put("control_id",control));
        var requested=host.request("LAN_RANGE_REMOTE_START",rangeOptions().put("player_id",playerId).put("control_id",control));
        assertEquals(playerId,requested.path("player_id").asText());assertEquals("Được chọn",requested.path("nickname").asText());
        var command=chosen.event("LAN_RANGE_REMOTE_REQUEST");assertEquals(control,command.path("control_id").asText());
        error(host,"LAN_RANGE_BUSY","LAN_RANGE_REMOTE_START",rangeOptions().put("player_id",playerId).put("control_id",UUID.randomUUID().toString()));
        error(other,"FORBIDDEN","LAN_RANGE_REMOTE_REPORT",obj().put("control_id",control).set("snapshot",obj()));
        var started=chosen.request("LAN_RANGE_START",((ObjectNode)command).deepCopy());String run=started.path("run_id").asText();
        assertEquals(playerId,started.path("player_id").asText());assertEquals(control,started.path("control_id").asText());
        chosen.request("LAN_RANGE_READY",obj().put("run_id",run));chosen.request("LAN_RANGE_PROBE",obj().put("run_id",run).put("sequence",1));
        chosen.event("LAN_RANGE_SERVER_SAMPLE");
        var reported=started.deepCopy();((ObjectNode)reported).put("active",true).put("saved",false).put("phase","MEASURING").put("server_ip","fake");
        ((ObjectNode)reported).putArray("samples").add(obj().put("direction","CLIENT_TO_SERVER").put("sequence",1).put("status","ON_TIME").put("rtt_ms",1));
        ((ObjectNode)reported).set("server_summary",obj().put("on_time",999));
        chosen.request("LAN_RANGE_REMOTE_REPORT",obj().put("control_id",control).set("snapshot",reported));
        var progress=host.event("LAN_RANGE_REMOTE_STATE").path("snapshot");
        assertEquals("127.0.0.1",progress.path("server_ip").asText());assertEquals(playerId,progress.path("player_id").asText());
        assertEquals(1,progress.path("server_summary").path("on_time").asInt());assertFalse(progress.path("passed").asBoolean());
        host.request("LAN_RANGE_REMOTE_STOP",obj().put("control_id",control));
        assertEquals(control,chosen.event("LAN_RANGE_REMOTE_CANCEL").path("control_id").asText());
        assertEquals("STOPPED",chosen.event("LAN_RANGE_FINISHED").path("end_reason").asText());
    }
    @Test void roomTopologyUsesActualPortAndConfiguredCapacityWithoutInventingDistances()throws Exception {
        var host=admin();String code=room(host,1);var player=new Client();
        player.request("JOIN",obj().put("room_code",code).put("nickname","Client topology"));
        var snapshot=host.request("ROOM_STATE",obj());
        assertEquals(port,snapshot.path("server_port").asInt());
        assertEquals(10,snapshot.path("max_players").asInt());
        var node=snapshot.path("players").get(0);
        assertFalse(node.has("wifi_rtt_measurement"));assertFalse(node.has("distance_m"));
        assertEquals("127.0.0.1",node.path("ip_address").asText());
    }
    @Test void hostControlsThirtySecondMeasurementOnChosenClientAndKeepsOwnReport()throws Exception {
        var host=admin();String code=room(host,1);var chosen=new Client("127.0.0.2");var other=new Client("127.0.0.3");
        String player=chosen.request("JOIN",obj().put("room_code",code).put("nickname","Client mục tiêu")).path("player_id").asText();
        other.request("JOIN",obj().put("room_code",code).put("nickname","Client khác"));var done=new CountDownLatch(1);
        Path hostReports=data.resolve("host-reports");
        try(var local=new LanRangeClient(chosen.gateway,data.resolve("chosen-reports"),s->{});
            var idle=new LanRangeClient(other.gateway,data.resolve("idle-reports"),s->{});
            var monitor=new LanRangeClient(host.gateway,hostReports,s->{if(s.path("saved").asBoolean()&&!s.path("active").asBoolean())done.countDown();})){
            chosen.gateway.onEvent(local::onEvent);other.gateway.onEvent(idle::onEvent);host.gateway.onEvent(monitor::onEvent);
            monitor.startFor(player,vn.edu.hutech.nqb.shared.LanRangeProtocol.parseOptions(rangeOptions().put("distance_m",30))).get(5,TimeUnit.SECONDS);
            assertTrue(done.await(40,TimeUnit.SECONDS),"Remote measurement did not finish");var result=monitor.snapshot();
            assertEquals("COMPLETED",result.path("end_reason").asText(),result.toPrettyString());assertTrue(result.path("passed").asBoolean(),result.toPrettyString());
            assertEquals(player,result.path("player_id").asText());assertEquals("127.0.0.2",result.path("client_ip_observed").asText());
            assertEquals(30,result.path("client_summary").path("confirmed").asInt());assertEquals(30,result.path("server_summary").path("confirmed").asInt());
            assertFalse(idle.snapshot().has("run_id"));assertTrue(result.path("loopback").asBoolean());
            var restored=LanRangeReport.load(hostReports);assertEquals(1,restored.size());assertEquals(30,restored.getFirst().path("client_summary").path("on_time").asInt());
            assertEquals(player,restored.getFirst().path("player_id").asText());
        }
    }
    @Test void remoteClientDisconnectUnblocksHostAndKeepsPartialEvidence()throws Exception {
        var host=admin();String code=room(host,1);var chosen=guest(code,"Sẽ mất kết nối");String player=chosen.request("ROOM_STATE",obj()).path("connection_info").path("player_id").asText();
        var samples=new CountDownLatch(1);var done=new CountDownLatch(1);
        try(var local=new LanRangeClient(chosen.gateway,data.resolve("client"),s->{});
            var monitor=new LanRangeClient(host.gateway,data.resolve("host"),s->{if(s.path("client_summary").path("confirmed").asInt()>=2)samples.countDown();if(s.has("run_id")&&!s.path("active").asBoolean()&&s.path("saved").asBoolean())done.countDown();})){
            chosen.gateway.onEvent(local::onEvent);host.gateway.onEvent(monitor::onEvent);
            monitor.startFor(player,vn.edu.hutech.nqb.shared.LanRangeProtocol.parseOptions(rangeOptions())).get(5,TimeUnit.SECONDS);
            assertTrue(samples.await(5,TimeUnit.SECONDS));chosen.close();assertTrue(done.await(7,TimeUnit.SECONDS));
            assertEquals("DISCONNECTED",monitor.snapshot().path("end_reason").asText());assertFalse(monitor.snapshot().path("passed").asBoolean());
            assertTrue(monitor.snapshot().path("client_summary").path("confirmed").asInt()>=2);assertEquals(1,LanRangeReport.load(data.resolve("host")).size());
        }
    }
    @Test void remoteRangeStopsForOwnerDisconnectAndRejectsOldControlOnReconnect()throws Exception {
        var host=admin();String code=room(host,1);var chosen=guest(code,"Client cũ");String player=chosen.request("ROOM_STATE",obj()).path("connection_info").path("player_id").asText();
        String control=UUID.randomUUID().toString();host.request("LAN_RANGE_REMOTE_START",rangeOptions().put("player_id",player).put("control_id",control));
        var command=chosen.event("LAN_RANGE_REMOTE_REQUEST");String run=chosen.request("LAN_RANGE_START",(ObjectNode)command).path("run_id").asText();
        chosen.request("LAN_RANGE_READY",obj().put("run_id",run));host.close();
        assertEquals("DISCONNECTED",chosen.event("LAN_RANGE_REMOTE_CANCEL").path("end_reason").asText());
        assertEquals("DISCONNECTED",chosen.event("LAN_RANGE_FINISHED").path("end_reason").asText());
        chosen.close();var rejoined=guest(code,"Client kết nối mới");
        error(rejoined,"FORBIDDEN","LAN_RANGE_START",rangeOptions().put("control_id",control));
    }
    @Test void remoteRangeRejectsUnrelatedTeacherAndPreservesTargetWhenLocalStorageFails()throws Exception {
        var host=admin();String code=room(host,1);var chosen=guest(code,"Client lưu lỗi");String player=chosen.request("ROOM_STATE",obj()).path("connection_info").path("player_id").asText();
        var stranger=new Client();stranger.request("REGISTER",obj().put("username","other-teacher").put("display_name","Giảng viên khác").put("password","Teacher-test-only-1"));
        stranger.request("LOGIN",obj().put("username","other-teacher").put("password","Teacher-test-only-1"));
        error(stranger,"FORBIDDEN","LAN_RANGE_REMOTE_START",rangeOptions().put("player_id",player).put("control_id",UUID.randomUUID().toString()));
        Path hostDir=data.resolve("host-write-error");Files.createDirectories(hostDir);Files.writeString(hostDir.resolve("controlled"),"not a directory");var done=new CountDownLatch(1);
        try(var local=new LanRangeClient(chosen.gateway,data.resolve("student-good"),s->{});
            var monitor=new LanRangeClient(host.gateway,hostDir,s->{if(!s.path("active").asBoolean()&&s.path("end_reason").asText().equals("STORAGE_ERROR"))done.countDown();})){
            chosen.gateway.onEvent(local::onEvent);host.gateway.onEvent(monitor::onEvent);
            monitor.startFor(player,vn.edu.hutech.nqb.shared.LanRangeProtocol.parseOptions(rangeOptions())).get(5,TimeUnit.SECONDS);
            assertTrue(done.await(7,TimeUnit.SECONDS),"Host storage failure did not end the remote measurement");assertFalse(monitor.snapshot().path("passed").asBoolean());
        }
    }
    @Test void roomStateExposesOwnSocketEndpointsBeforeMeasurementWithoutLeakingOtherClientIps()throws Exception {
        var host=admin();
        var initial=host.request("ROOM_STATE",obj());
        assertEquals("127.0.0.1",initial.path("connection_info").path("server_ip").asText());
        assertEquals(port,initial.path("connection_info").path("server_port").asInt());
        assertEquals("127.0.0.1",initial.path("connection_info").path("client_ip_observed").asText());
        String code=room(host,1);var student=new Client("127.0.0.2");
        var joined=student.request("JOIN",obj().put("room_code",code).put("nickname","Client IP thật"));
        var studentState=student.request("ROOM_STATE",obj());
        assertEquals("127.0.0.2",studentState.path("connection_info").path("client_ip_observed").asText());
        assertEquals(joined.path("player_id").asText(),studentState.path("connection_info").path("player_id").asText());
        assertFalse(studentState.path("players").get(0).has("ip_address"));
        assertEquals("127.0.0.2",host.request("ROOM_STATE",obj()).path("players").get(0).path("ip_address").asText());
        assertFalse(initial.has("run_id"),"Socket identity must not require starting a measurement");
    }
    @Test void lanRangeClientCompletesThirtyRealTlsSamplesOnOneIp()throws Exception {
        var manager=admin();var done=new CountDownLatch(1);
        Path report=data.resolve("client-reports");
        try(var range=new LanRangeClient(manager.gateway,report,s->{if(s.path("saved").asBoolean()&&!s.path("active").asBoolean())done.countDown();})){
            manager.gateway.onEvent(range::onEvent);
            range.start(vn.edu.hutech.nqb.shared.LanRangeProtocol.parseOptions(rangeOptions())).get(5,TimeUnit.SECONDS);
            assertTrue(done.await(40,TimeUnit.SECONDS),"Real 30-second TLS run did not finish");
            var result=range.snapshot();
            assertEquals("COMPLETED",result.path("end_reason").asText(),result.toPrettyString());
            assertEquals(30,result.path("client_summary").path("confirmed").asInt());
            assertEquals(30,result.path("server_summary").path("confirmed").asInt());
            assertTrue(result.path("server_summary_received").asBoolean());
            assertTrue(result.path("loopback").asBoolean());assertTrue(result.path("saved").asBoolean());
            assertEquals(1,LanRangeReport.load(report).size());
            assertTrue(LanRangeReport.farthestPassing(LanRangeReport.load(report),result.path("survey_id").asText()).isEmpty());
        }
    }
    @Test void lanRangeAuthenticatesUsesRealSocketAndKeepsServerJournal()throws Exception {
        var anonymous=new Client();error(anonymous,"AUTH_REQUIRED","LAN_RANGE_START",rangeOptions());
        var manager=admin();var started=manager.request("LAN_RANGE_START",rangeOptions().put("client_ip","fake").put("server_ip","fake"));
        String run=started.path("run_id").asText();
        assertEquals("127.0.0.1",started.path("client_ip_observed").asText());
        assertEquals("127.0.0.1",started.path("server_ip").asText());assertTrue(started.path("loopback").asBoolean());
        error(manager,"LAN_RANGE_BUSY","LAN_RANGE_START",rangeOptions());
        manager.request("LAN_RANGE_READY",obj().put("run_id",run));
        assertEquals(1,manager.request("LAN_RANGE_PROBE",obj().put("run_id",run).put("sequence",1)).path("sequence").asInt());
        assertEquals("ON_TIME",manager.event("LAN_RANGE_SERVER_SAMPLE").path("status").asText());
        manager.request("LAN_RANGE_STOP",obj().put("run_id",run));
        var ended=manager.event("LAN_RANGE_FINISHED");
        assertEquals("STOPPED",ended.path("end_reason").asText());assertTrue(ended.path("server_journal_healthy").asBoolean());
        assertTrue(Files.readString(data.resolve("lan-range/server-"+run+".jsonl")).contains("RUN_FINISHED"));
        manager.request("LAN_RANGE_STOP",obj().put("run_id",run));
    }
    @Test void lanRangeStudentIsInterruptedWhenTheGameStarts()throws Exception {
        var host=admin();String code=room(host,1);var player=new Client();
        player.request("JOIN",obj().put("room_code",code).put("nickname","Đo trên một máy"));
        player.request("READY_SET",obj().put("ready",true));
        String run=player.request("LAN_RANGE_START",rangeOptions()).path("run_id").asText();
        player.request("LAN_RANGE_READY",obj().put("run_id",run));
        host.request("START_GAME",obj());
        assertEquals("GAME_STARTED",player.event("LAN_RANGE_FINISHED").path("end_reason").asText());
        error(host,"INVALID_STATE","LAN_RANGE_START",rangeOptions());
    }
    @Test void lanRangeServerStorageFailureDoesNotPretendToStart()throws Exception {
        Files.writeString(data.resolve("lan-range"),"This path is a file, not a directory");
        var manager=admin();error(manager,"STORAGE_ERROR","LAN_RANGE_START",rangeOptions());
        manager.request("PING",obj());
    }
    @Test void lanRangeIsBoundedAndCannotBeStoppedByAnotherConnection()throws Exception {
        var managers=new ArrayList<Client>();var runs=new ArrayList<String>();
        for(int i=0;i<5;i++){
            var c=new Client();c.request("REGISTER",obj().put("username","range.teacher"+i).put("display_name","Giảng viên "+i).put("password","Range-test-only-1"));
            c.request("LOGIN",obj().put("username","range.teacher"+i).put("password","Range-test-only-1"));managers.add(c);
            if(i<4)runs.add(c.request("LAN_RANGE_START",rangeOptions()).path("run_id").asText());
        }
        error(managers.get(4),"LAN_RANGE_BUSY","LAN_RANGE_START",rangeOptions());
        error(managers.get(1),"LAN_RANGE_NOT_FOUND","LAN_RANGE_STOP",obj().put("run_id",runs.getFirst()));
    }
    @Test void sameIpCannotJoinFromAnotherApplicationOrSpoofedIdentity()throws Exception {
        var host=admin();String code=room(host,1);var first=new Client();
        var joined=first.request("JOIN",obj().put("room_code",code).put("nickname","Học sinh đầu"));
        var extra=new Client();
        error(extra,"IP_ALREADY_IN_USE","JOIN",obj().put("room_code",code).put("nickname","Học sinh khác")
            .put("ip_address","203.0.113.99").put("player_id","another-student").put("username","another-account"));
        error(extra,"IP_ALREADY_IN_USE","JOIN",obj().put("room_code",code).put("nickname","Học sinh đầu"));
        error(extra,"AUTH_REQUIRED","READY_SET",obj().put("ready",true));
        first.request("READY_SET",obj().put("ready",true));
        var state=host.request("ROOM_STATE",obj());assertEquals(1,state.path("players").size());
        assertEquals(joined.path("player_id"),state.path("players").get(0).path("player_id"));
        assertEquals(joined.path("avatar_id"),state.path("players").get(0).path("avatar_id"));
        assertEquals(1,state.path("ready_count").asInt());
    }
    @Test void simultaneousStudentJoinsFromSameIpHaveExactlyOneWinner()throws Exception {
        var host=admin();String code=room(host,1);var first=new Client();var second=new Client();
        var one=first.gateway.request("JOIN",obj().put("room_code",code).put("nickname","Một"));
        var two=second.gateway.request("JOIN",obj().put("room_code",code).put("nickname","Hai"));
        int winners=0,rejected=0;
        for(var response:List.of(one,two))try{response.get(8,TimeUnit.SECONDS);winners++;}
        catch(ExecutionException failure){assertEquals("IP_ALREADY_IN_USE",assertInstanceOf(GatewayException.class,failure.getCause()).code());rejected++;}
        assertEquals(1,winners);assertEquals(1,rejected);
        assertEquals(1,host.request("ROOM_STATE",obj()).path("connected_player_count").asInt());
    }
    @ParameterizedTest @ValueSource(strings={"LEAVE","LOGOUT"})
    void studentDepartureImmediatelyReleasesIp(String departure)throws Exception {
        var host=admin();String code=room(host,1);var first=new Client();var replacement=new Client();
        first.request("JOIN",obj().put("room_code",code).put("nickname","Trước"));
        var attempt=obj().put("room_code",code).put("nickname","Sau");
        error(replacement,"IP_ALREADY_IN_USE","JOIN",attempt);
        first.request(departure,obj());
        assertEquals("Sau",replacement.request("JOIN",attempt).path("nickname").asText());
        assertEquals(1,host.request("ROOM_STATE",obj()).path("players").size());
    }
    @Test void resetKeepsActiveIpReservedAndRoomCloseReleasesIt()throws Exception {
        var host=admin();String code=room(host,1);var first=new Client();var extra=new Client();
        first.request("JOIN",obj().put("room_code",code).put("nickname","Giữ chỗ"));
        String resetCode=host.request("ROOM_RESET",obj()).path("room_code").asText();
        error(extra,"IP_ALREADY_IN_USE","JOIN",obj().put("room_code",resetCode).put("nickname","Mở thêm"));
        host.request("ROOM_CLOSE",obj());first.event("ROOM_CLOSED");
        String newCode=room(host,1);
        assertEquals("Mở thêm",extra.request("JOIN",obj().put("room_code",newCode).put("nickname","Mở thêm")).path("nickname").asText());
        assertEquals(1,host.request("ROOM_STATE",obj()).path("players").size());
    }
    @Test void detectedDisconnectReleasesIpForAnotherStudent()throws Exception {
        var host=admin();String code=room(host,1);var first=new Client();var replacement=new Client();
        first.request("JOIN",obj().put("room_code",code).put("nickname","Mất mạng"));
        var attempt=obj().put("room_code",code).put("nickname","Kết nối mới");
        error(replacement,"IP_ALREADY_IN_USE","JOIN",attempt);first.close();
        JsonNode state;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        do{state=host.request("ROOM_STATE",obj());if(state.path("players").isEmpty())break;Thread.sleep(30);}while(System.nanoTime()<until);
        assertTrue(state.path("players").isEmpty());
        assertEquals("Kết nối mới",replacement.request("JOIN",attempt).path("nickname").asText());
    }
    @Test void invalidJoinDoesNotReserveIp()throws Exception {
        var host=admin();String code=room(host,1);var invalid=new Client();var valid=new Client();
        error(invalid,"WRONG_ROOM","JOIN",obj().put("room_code","INVALID").put("nickname","Sai phòng"));
        valid.request("JOIN",obj().put("room_code",code).put("nickname","Đúng phòng"));
        error(invalid,"IP_ALREADY_IN_USE","JOIN",obj().put("room_code",code).put("nickname","Thử lại"));
        assertEquals(1,host.request("ROOM_STATE",obj()).path("players").size());
    }
    @Test void roomTrackingUsesSocketIpAndRestrictsItToOwnerAndAdmin()throws Exception {
        var admin=admin();var owner=new Client();
        owner.request("REGISTER",obj().put("username","tracking.owner").put("display_name","Chủ phòng").put("password","Tracking-test-only-1"));
        owner.request("LOGIN",obj().put("username","tracking.owner").put("password","Tracking-test-only-1"));
        String code=room(owner,1);var student=new Client();
        student.request("JOIN",obj().put("room_code",code).put("nickname","Minh IP").put("ip_address","203.0.113.99"));
        for(var manager:List.of(owner,admin)){
            var state=manager.request("ROOM_STATE",obj());
            assertEquals(code,state.path("room_code").asText());assertEquals("Lớp LAN",state.path("room_name").asText());
            assertEquals("127.0.0.1",state.path("players").get(0).path("ip_address").asText());
            assertTrue(state.path("players").get(0).path("connected").asBoolean());
        }
        var unrelated=new Client();
        unrelated.request("REGISTER",obj().put("username","tracking.observer").put("display_name","Giảng viên khác").put("password","Tracking-test-only-2"));
        unrelated.request("LOGIN",obj().put("username","tracking.observer").put("password","Tracking-test-only-2"));
        assertFalse(unrelated.request("ROOM_STATE",obj()).path("players").get(0).has("ip_address"));
        assertFalse(student.request("ROOM_STATE",obj()).path("players").get(0).has("ip_address"));
        assertFalse(student.event("ROOM_STATE").path("players").get(0).has("ip_address"));
        owner.request("ROOM_CONFIGURE",obj().put("room_name","Phòng đổi tên"));
        JsonNode updated;
        do{updated=owner.event("ROOM_STATE");}while(!updated.path("room_name").asText().equals("Phòng đổi tên"));
        assertEquals("127.0.0.1",updated.path("players").get(0).path("ip_address").asText());
        assertFalse(unrelated.event("ROOM_STATE").path("players").get(0).has("ip_address"));
        assertFalse(new Client().request("ROOM_BROWSE",obj()).toString().contains("ip_address"));
    }
    @Test void roomTrackingRetainsLastIpAfterDisconnectAndRemovesItOnReset()throws Exception {
        var host=admin();String code=room(host,1);var a=guest(code,"Mất mạng");var b=guest(code,"Còn mạng");
        a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());a.event("QUESTION");a.close();
        JsonNode state;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        do{state=host.request("ROOM_STATE",obj());if(!state.path("players").get(0).path("connected").asBoolean())break;Thread.sleep(30);}while(System.nanoTime()<until);
        assertEquals(2,state.path("players").size());assertFalse(state.path("players").get(0).path("connected").asBoolean());
        assertEquals("127.0.0.1",state.path("players").get(0).path("ip_address").asText());
        assertEquals(code,state.path("room_code").asText());
        host.request("ABORT_GAME",obj());var reset=host.request("ROOM_RESET",obj());
        assertNotEquals(code,reset.path("room_code").asText());assertEquals(1,reset.path("players").size());
        assertEquals("Còn mạng",reset.path("players").get(0).path("nickname").asText());
        assertEquals("127.0.0.2",reset.path("players").get(0).path("ip_address").asText());
        b.request("LEAVE",obj());
        until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        do{state=host.request("ROOM_STATE",obj());if(state.path("players").isEmpty())break;Thread.sleep(30);}while(System.nanoTime()<until);
        assertTrue(state.path("players").isEmpty());
    }
    @Test void trackingLogsMembershipAndRoomChangesWithAuthoritativeIp()throws Exception {
        try(var logs=new TrackingLogs()){
            var host=admin();String code=room(host,1);var a=new Client();
            var joined=a.request("JOIN",obj().put("room_code",code).put("nickname","Minh \"IP\"").put("ip_address","203.0.113.99"));
            String playerId=joined.path("player_id").asText();var event=logs.next("JOINED");
            assertEquals(code,event.path("room_code").asText());assertEquals("Lớp LAN",event.path("room_name").asText());
            assertEquals(playerId,event.path("player_id").asText());assertEquals("Minh \"IP\"",event.path("nickname").asText());
            assertEquals("127.0.0.1",event.path("ip_address").asText());assertTrue(event.path("connected").asBoolean());
            java.time.Instant.parse(event.path("timestamp_utc").asText());
            host.request("ROOM_CONFIGURE",obj().put("room_name","Phòng mới"));
            assertEquals("Phòng mới",logs.next("ROOM_UPDATED").path("room_name").asText());
            String newCode=host.request("ROOM_RESET",obj()).path("room_code").asText();
            assertEquals(newCode,logs.next("ROOM_RESET").path("room_code").asText());
            a.request("LEAVE",obj());event=logs.next("LEFT");assertFalse(event.path("connected").asBoolean());assertEquals(newCode,event.path("room_code").asText());
            var b=guest(newCode,"Lan");logs.next("JOINED");b.close();
            assertFalse(logs.next("DISCONNECTED").path("connected").asBoolean());
            guest(newCode,"Còn trong phòng");logs.next("JOINED");host.request("ROOM_CLOSE",obj());
            event=logs.next("ROOM_CLOSED");assertEquals(newCode,event.path("room_code").asText());assertFalse(event.path("connected").asBoolean());
            assertTrue(logs.events.isEmpty(),"Each departure must be logged once");
        }
    }
    private static final class TrackingLogs extends java.util.logging.Handler implements AutoCloseable {
        final com.fasterxml.jackson.databind.ObjectMapper mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        final BlockingQueue<JsonNode> events=new LinkedBlockingQueue<>();
        final java.util.logging.Logger logger=java.util.logging.Logger.getLogger(QuizServer.class.getName());
        TrackingLogs(){logger.addHandler(this);}
        @Override public void publish(java.util.logging.LogRecord record){
            String prefix="NQB_STUDENT_TRACKING ";
            if(record.getMessage().startsWith(prefix))try{events.add(mapper.readTree(record.getMessage().substring(prefix.length())));}catch(Exception e){throw new AssertionError(e);}
        }
        JsonNode next(String expected)throws Exception{var event=events.poll(3,TimeUnit.SECONDS);assertNotNull(event,"Missing tracking event "+expected);assertEquals(expected,event.path("event").asText());return event;}
        @Override public void flush(){}
        @Override public void close(){logger.removeHandler(this);}
    }
    @Test void managerCanMonitorSubmissionsWithoutExposingAnswersToStudents()throws Exception {
        var host=admin();String code=room(host,1);var a=guest(code,"Minh");var b=guest(code,"Lan");
        a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());
        var question=a.event("QUESTION");
        a.request("ANSWER",obj().put("session_id",question.path("session_id").asText()).put("question_id",question.path("question_id").asInt()).put("answer","A"));
        var managed=host.request("ROOM_STATE",obj());
        assertEquals(1,managed.path("question_number").asInt());
        assertEquals(1,managed.path("answered_count").asInt());
        var minh=java.util.stream.StreamSupport.stream(managed.path("players").spliterator(),false).filter(p->p.path("nickname").asText().equals("Minh")).findFirst().orElseThrow();
        assertTrue(minh.path("answered").asBoolean());assertTrue(minh.has("score"));assertFalse(minh.has("answer"));
        var student=b.request("ROOM_STATE",obj());
        for(var player:student.path("players")){assertFalse(player.has("answered"));assertFalse(player.has("score"));assertFalse(player.has("answer"));}
        assertFalse(student.has("ranking"));assertFalse(student.path("question").has("correct_answer"));
        host.request("ABORT_GAME",obj());host.request("ROOM_RESET",obj());
        var reset=host.request("ROOM_STATE",obj());assertEquals(0,reset.path("question_number").asInt());assertEquals(0,reset.path("ranking").size());
        for(var player:reset.path("players")){assertFalse(player.path("answered").asBoolean());assertEquals(0,player.path("score").asInt());}
    }
    private ObjectNode preview(String token,int offset,boolean complete,ObjectNode... rows){var p=obj().put("offset",offset).put("complete",complete);if(token!=null)p.put("import_id",token);var data=p.putArray("rows");for(var row:rows)data.add(row);return p;}
    private ObjectNode commit(String token,boolean allow,int... indexes){var p=obj().put("import_id",token).put("allow_duplicates",allow);var selected=p.putArray("selected_indexes");for(int index:indexes)selected.add(index);return p;}
    private ObjectNode setPayload(String name,int... ids){var p=obj().put("name",name);var items=p.putArray("question_ids");for(int id:ids)items.add(id);return p;}
    @Test void questionSetsArePrivateAndRoomsUseOnlyTheirSelectedActiveQuestions()throws Exception {
        var host=admin();var created=host.request("QUESTION_SET_CREATE",setPayload("Bộ mạng",1,2));int setId=created.path("question_set_id").asInt();
        assertTrue(setId>0);assertEquals(2,created.path("question_count").asInt());
        assertEquals(2,host.request("QUESTION_LIST",obj().put("question_set_id",setId)).path("total").asInt());
        var teacher=new Client();teacher.request("REGISTER",obj().put("username","set.teacher").put("display_name","Teacher").put("password","Set-test-only-1"));teacher.request("LOGIN",obj().put("username","set.teacher").put("password","Set-test-only-1"));
        assertEquals(0,teacher.request("QUESTION_SET_LIST",obj()).path("total").asInt());
        error(teacher,"NOT_FOUND","QUESTION_SET_DELETE",obj().put("question_set_id",setId));
        error(teacher,"NOT_FOUND","QUESTION_SET_CREATE",setPayload("Không được lấy câu người khác",1));
        error(teacher,"NOT_FOUND","ROOM_CONFIGURE",obj().put("room_name","Foreign").put("question_count",1).put("question_set_id",setId));
        host.request("QUESTION_SET_ACTIVE",obj().put("question_id",2).put("is_active",false));
        error(host,"NOT_ENOUGH_QUESTIONS","ROOM_CONFIGURE",obj().put("room_name","Chưa đủ").put("question_count",2).put("question_set_id",setId));
        assertTrue(host.request("ROOM_STATE",obj()).path("room_code").asText().isEmpty());
        var configured=host.request("ROOM_CONFIGURE",obj().put("room_name","Chọn bộ mạng").put("question_count",1).put("duration_seconds",5).put("question_set_id",setId));
        assertEquals(setId,configured.path("question_set_id").asInt());assertEquals("Bộ mạng",configured.path("question_set_name").asText());
        assertEquals(setId,host.request("ROOM_CONFIGURE",obj().put("room_name","Giữ bộ đang chọn").put("question_count",1).put("duration_seconds",5)).path("question_set_id").asInt());
        var a=guest(configured.path("room_code").asText(),"Set A");var b=guest(configured.path("room_code").asText(),"Set B");
        for(int attempt=0;attempt<2;attempt++){
            a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());
            assertEquals(1,a.event("QUESTION").path("question_id").asInt());host.request("ABORT_GAME",obj());a.event("GAME_ABORTED");
            assertEquals(setId,host.request("ROOM_RESET",obj()).path("question_set_id").asInt());
        }
        error(host,"QUESTION_SET_IN_USE","QUESTION_SET_DELETE",obj().put("question_set_id",setId));
        host.request("ROOM_CLOSE",obj());host.request("QUESTION_SET_DELETE",obj().put("question_set_id",setId));
        assertEquals(0,host.request("QUESTION_SET_LIST",obj()).path("total").asInt());assertEquals(20,host.request("QUESTION_LIST",obj()).path("total").asInt());
    }
    @Test void importCreatesOrAppendsAnOwnedQuestionSetAtomically()throws Exception {
        var host=admin();var preview=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Bộ nhập TCP một"),importRow("Bộ nhập TCP hai")));
        var saved=host.request("QUESTION_IMPORT_COMMIT",commit(preview.path("import_id").asText(),false,0,1).put("question_set_name","Ôn TCP"));
        int setId=saved.path("question_set_id").asInt();assertTrue(setId>0);
        assertEquals(2,host.request("QUESTION_LIST",obj().put("question_set_id",setId)).path("total").asInt());
        var next=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Bộ nhập TCP ba")));
        assertEquals(setId,host.request("QUESTION_IMPORT_COMMIT",commit(next.path("import_id").asText(),false,0).put("question_set_name","Ôn TCP")).path("question_set_id").asInt());
        assertEquals(3,host.request("QUESTION_LIST",obj().put("question_set_id",setId)).path("total").asInt());
        var duplicate=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Không được lưu khi rollback"),importRow("Bộ nhập TCP một")));
        error(host,"DUPLICATE_QUESTION","QUESTION_IMPORT_COMMIT",commit(duplicate.path("import_id").asText(),false,0,1).put("question_set_name","Không tạo bộ khi lỗi"));
        assertEquals(1,host.request("QUESTION_SET_LIST",obj()).path("total").asInt());assertEquals(23,host.request("QUESTION_LIST",obj()).path("total").asInt());
        var filtered=obj().put("name","Bộ từ bộ lọc");filtered.putObject("filters").put("keyword","Bộ nhập TCP");
        assertEquals(3,host.request("QUESTION_SET_CREATE",filtered).path("question_count").asInt());
    }
    @Test void importPreviewReadOnlyThenCommitSelectedValidRows()throws Exception {
        var host=admin();var first=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,false,importRow("Câu nhập mới"),importRow("CÂU  NHẬP MỚI")));
        String token=first.path("import_id").asText();assertEquals(0,first.path("items").get(1).path("duplicate_in_file").asInt(-1));assertEquals(20,host.request("QUESTION_LIST",obj()).path("total").asInt());
        var last=host.request("QUESTION_IMPORT_PREVIEW",preview(token,2,true,importRow("Câu chưa đủ").put("option_b",""),importRow("Câu nhập khác")));
        assertFalse(last.path("items").get(0).path("valid").asBoolean());
        error(host,"INVALID_INPUT","QUESTION_IMPORT_COMMIT",commit(token,false,0,2));assertEquals(20,host.request("QUESTION_LIST",obj()).path("total").asInt());
        var saved=host.request("QUESTION_IMPORT_COMMIT",commit(token,false,0,3));assertEquals(2,saved.path("imported").asInt());assertEquals(2,saved.path("skipped").asInt());assertEquals(22,host.request("QUESTION_LIST",obj()).path("total").asInt());
        error(host,"IMPORT_INVALID_STATE","QUESTION_IMPORT_COMMIT",commit(token,false,0));
    }
    @Test void importOwnerBoundTokensAndAtomicDuplicateRace()throws Exception {
        var host=admin();var teacher=new Client();teacher.request("REGISTER",obj().put("username","import.teacher").put("display_name","Teacher").put("password","Import-test-only-1"));teacher.request("LOGIN",obj().put("username","import.teacher").put("password","Import-test-only-1"));
        var first=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("First atomic import"),importRow("Second atomic import")));
        String token=first.path("import_id").asText();error(teacher,"IMPORT_INVALID_STATE","QUESTION_IMPORT_COMMIT",commit(token,false,0));
        host.request("QUESTION_CREATE",importRow("Second atomic import"));error(host,"DUPLICATE_QUESTION","QUESTION_IMPORT_COMMIT",commit(token,false,0,1));
        assertEquals(21,host.request("QUESTION_LIST",obj()).path("total").asInt());assertFalse(host.request("QUESTION_CHECK_DUPLICATE",obj().put("content","First atomic import")).path("has_duplicate").asBoolean());
        var second=teacher.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Second atomic import").put("owner_user_id",1)));
        assertFalse(second.path("items").get(0).path("duplicate_in_bank").asBoolean());teacher.request("QUESTION_IMPORT_COMMIT",commit(second.path("import_id").asText(),false,0));assertEquals(1,teacher.request("QUESTION_LIST",obj()).path("total").asInt());assertEquals(21,host.request("QUESTION_LIST",obj()).path("total").asInt());
    }
    @Test void importRejectsDuplicatesUnlessExplicitAndRejectsInvalidChunks()throws Exception {
        var host=admin();var q=importRow("Duplicate import fixture");host.request("QUESTION_CREATE",q);
        var first=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,q,q));String token=first.path("import_id").asText();assertTrue(first.path("items").get(0).path("duplicate_in_bank").asBoolean());
        error(host,"DUPLICATE_QUESTION","QUESTION_IMPORT_COMMIT",commit(token,false,0));error(host,"INVALID_INPUT","QUESTION_IMPORT_COMMIT",commit(token,true,0,0));error(host,"INVALID_INPUT","QUESTION_IMPORT_COMMIT",commit(token,true,100));
        assertEquals(2,host.request("QUESTION_IMPORT_COMMIT",commit(token,true,0,1)).path("imported").asInt());assertEquals(23,host.request("QUESTION_LIST",obj()).path("total").asInt());
        var partial=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,false,importRow("Partial")));String partialId=partial.path("import_id").asText();error(host,"IMPORT_INVALID_STATE","QUESTION_IMPORT_PREVIEW",preview(partialId,0,true,q));error(host,"IMPORT_INVALID_STATE","QUESTION_IMPORT_COMMIT",commit(partialId,false,0));
        var oversized=preview(null,0,true);for(int i=0;i<21;i++)((com.fasterxml.jackson.databind.node.ArrayNode)oversized.path("rows")).add(importRow("Row "+i));error(host,"INVALID_INPUT","QUESTION_IMPORT_PREVIEW",oversized);
    }
    @Test void importRequiresManagerAndWaitingRoom()throws Exception {
        var host=admin();var unauth=new Client();error(unauth,"AUTH_REQUIRED","QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Not allowed")));
        String code=room(host,1);var a=guest(code,"Import guest 1");var b=guest(code,"Import guest 2");error(a,"FORBIDDEN","QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Not allowed")));
        String token=host.request("QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Waiting only"))).path("import_id").asText();a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());error(host,"INVALID_STATE","QUESTION_IMPORT_COMMIT",commit(token,false,0));error(host,"INVALID_STATE","QUESTION_IMPORT_PREVIEW",preview(null,0,true,importRow("Not while playing")));
    }
    @Test void oneReadyStudentCanFinishGameAndSaveHistory()throws Exception {
        var host=admin();String code=room(host,1);
        error(host,"NOT_READY","START_GAME",obj());
        var student=guest(code,"Thi một mình");
        error(host,"NOT_READY","START_GAME",obj());
        student.request("READY_SET",obj().put("ready",true));
        assertDoesNotThrow(()->host.request("START_GAME",obj()),"One ready student must be able to start");
        assertFalse(student.event("GAME_START").path("session_id").asText().isBlank());
        var question=student.event("QUESTION");
        var correct=host.request("QUESTION_GET",obj().put("question_id",question.path("question_id").asInt())).path("correct_answer").asText();
        assertTrue(student.request("ANSWER",obj().put("session_id",question.path("session_id").asText()).put("question_id",question.path("question_id").asInt()).put("answer",correct)).path("accepted").asBoolean());
        assertTrue(student.event("QUESTION_RESULT").path("correct").asBoolean());
        var ranking=student.event("ROUND_RANKING").path("ranking");
        assertEquals(1,ranking.size());assertEquals(1,ranking.get(0).path("rank").asInt());
        var end=student.event("GAME_OVER");assertTrue(end.path("result_saved").asBoolean());assertEquals(1,end.path("correct_count").asInt());
        assertEquals(end.path("ranking"),host.request("HISTORY_GET",obj().put("session_id",question.path("session_id").asText())).path("ranking"));
    }
    @Test void startStillRequiresEveryJoinedStudentToBeReady()throws Exception {
        var host=admin();String code=room(host,1);var ready=guest(code,"Sẵn sàng");guest(code,"Đang chờ");
        ready.request("READY_SET",obj().put("ready",true));
        error(host,"NOT_READY","START_GAME",obj());
        assertEquals("WAITING",host.request("ROOM_STATE",obj()).path("phase").asText());
    }
    @Test void threeRealPlayersFinishRankingAndPersistAfterRestart()throws Exception {
        var host=admin();String code=room(host,2);Client a=guest(code,"Minh Ánh"),b=guest(code,"Quốc Bảo"),c=guest(code,"Thu Hà");
        for(var player:List.of(a,b,c))player.request("READY_SET",obj().put("ready",true));
        host.request("START_GAME",obj());String sessionId="";
        for(int round=0;round<2;round++){
            var q=a.event("QUESTION");sessionId=q.path("session_id").asText();assertFalse(q.has("correct_answer"));
            var detail=host.request("QUESTION_GET",obj().put("question_id",q.path("question_id").asInt()));
            var answer=obj().put("session_id",sessionId).put("question_id",q.path("question_id").asInt()).put("answer",detail.path("correct_answer").asText());
            error(a,"FORBIDDEN","QUESTION_GET",obj().put("question_id",q.path("question_id").asInt()));
            var ack=a.request("ANSWER",answer);assertTrue(ack.path("accepted").asBoolean());assertFalse(ack.has("score"));assertFalse(ack.has("correct"));
            assertFalse(a.request("ANSWER",answer).path("accepted").asBoolean());b.request("ANSWER",answer);
            error(host,"INVALID_STATE","QUESTION_DELETE",obj().put("question_id",q.path("question_id").asInt()));
            assertEquals((round+1)*100,a.event("QUESTION_RESULT").path("score").asInt());
            var ranking=a.event("ROUND_RANKING").path("ranking");assertEquals(3,ranking.size());assertEquals(1,ranking.get(0).path("rank").asInt());assertEquals(1,ranking.get(1).path("rank").asInt());assertEquals(3,ranking.get(2).path("rank").asInt());
            assertEquals(round+1,b.event("ROUND_RANKING").path("number").asInt());assertEquals(round+1,c.event("ROUND_RANKING").path("number").asInt());
        }
        var end=a.event("GAME_OVER");assertTrue(end.path("result_saved").asBoolean());assertEquals(200,end.path("score").asInt());
        assertEquals(1,host.request("HISTORY_LIST",obj()).path("total").asInt());
        String newCode=host.request("ROOM_RESET",obj()).path("room_code").asText();assertNotEquals(code,newCode);
        clients.forEach(Client::close);server.close();server=null;
        try(var store=new QuizStore(data.resolve("quiz.db"))){assertEquals(20,store.questions(1,obj()).path("total").asInt());assertEquals(200,store.historyDetail(1,sessionId).path("ranking").get(0).path("score").asInt());}
    }
    @Test void avatarRaceAcrossSocketsHasOneWinnerAndDisconnectReleases()throws Exception {
        var host=admin();String code=room(host,1);Client a=guest(code,"Một"),b=guest(code,"Hai");
        var one=a.gateway.request("AVATAR_SET",obj().put("avatar_id","quizling-24"));var two=b.gateway.request("AVATAR_SET",obj().put("avatar_id","quizling-24"));int wins=0;
        for(var f:List.of(one,two))try{f.get(4,TimeUnit.SECONDS);wins++;}catch(ExecutionException e){assertEquals("AVATAR_TAKEN",assertInstanceOf(GatewayException.class,e.getCause()).code());}assertEquals(1,wins);
        var state=host.request("ROOM_STATE",obj());assertEquals(2,state.path("players").size());assertNotEquals(state.path("players").get(0).path("avatar_id").asText(),state.path("players").get(1).path("avatar_id").asText());
        a.close();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);do{state=host.request("ROOM_STATE",obj());if(state.path("players").size()==1)break;Thread.sleep(60);}while(System.nanoTime()<until);assertEquals(1,state.path("players").size());
        host.request("ROOM_CLOSE",obj());b.event("ROOM_CLOSED");assertEquals(0,host.request("ROOM_STATE",obj()).path("players").size());
    }
    @Test void accountsRequirePasswordChangeEnforceOwnershipAndRevokeSessions()throws Exception {
        var host=admin();var created=host.request("USER_CREATE",obj().put("username","teacher.a").put("display_name","Giảng viên A").put("temporary_password","Temporary-only-1"));assertFalse(created.toString().contains("Temporary-only"));int id=created.path("user_id").asInt();
        var teacher=new Client();assertTrue(teacher.request("LOGIN",obj().put("username","teacher.a").put("password","Temporary-only-1")).path("must_change_password").asBoolean());
        error(teacher,"PASSWORD_CHANGE_REQUIRED","QUESTION_LIST",obj());teacher.request("PASSWORD_CHANGE",obj().put("current_password","Temporary-only-1").put("new_password","Changed-only-2"));teacher.event("DISCONNECTED");
        var next=new Client();next.request("LOGIN",obj().put("username","teacher.a").put("password","Changed-only-2"));
        error(next,"FORBIDDEN","USER_LIST",obj());error(next,"NOT_FOUND","QUESTION_GET",obj().put("question_id",1));assertEquals(0,next.request("QUESTION_LIST",obj()).path("total").asInt());
        host.request("USER_SET_ACTIVE",obj().put("user_id",id).put("is_active",false));next.event("DISCONNECTED");
        var locked=new Client();error(locked,"AUTH_FAILED","LOGIN",obj().put("username","teacher.a").put("password","Changed-only-2"));
        host.request("USER_RESET_PASSWORD",obj().put("user_id",id).put("temporary_password","Reset-only-3"));host.request("USER_SET_ACTIVE",obj().put("user_id",id).put("is_active",true));
        var reset=new Client();assertTrue(reset.request("LOGIN",obj().put("username","teacher.a").put("password","Reset-only-3")).path("must_change_password").asBoolean());
    }
    @Test void duplicateFilterAndCrossOwnerRulesUseRealDatabase()throws Exception {
        var host=admin();var q=(ObjectNode)host.request("QUESTION_GET",obj().put("question_id",1));q.remove("question_id");q.put("content","  "+q.path("content").asText().toUpperCase(Locale.ROOT)+"  ");
        error(host,"DUPLICATE_QUESTION","QUESTION_CREATE",q);assertEquals(20,host.request("QUESTION_LIST",obj()).path("total").asInt());
        var saved=host.request("QUESTION_CREATE",q.put("allow_duplicate",true));assertEquals(21,saved.path("question_id").asInt());
        assertEquals(0,host.request("QUESTION_LIST",obj().put("keyword","%' OR 1=1 --")).path("total").asInt());
        host.request("QUESTION_SET_ACTIVE",obj().put("question_id",21).put("is_active",false));
        var filtered=host.request("QUESTION_LIST",obj().put("keyword","truyền dữ liệu tin cậy").put("is_active",false).put("category","Lập trình mạng"));assertEquals(1,filtered.path("total").asInt());assertFalse(filtered.path("items").get(0).has("correct_answer"));
    }
    @Test void ownerReconnectGetsAuthoritativeSnapshotDuringQuestion()throws Exception {
        var host=admin();String code=room(host,1);Client a=guest(code,"A"),b=guest(code,"B");a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());a.event("QUESTION");host.close();
        var restored=admin();var state=restored.request("ROOM_STATE",obj());assertTrue(state.path("is_owner").asBoolean());assertTrue(state.path("question").has("content"));assertFalse(state.path("question").has("correct_answer"));assertEquals("QUESTION_ACTIVE",state.path("phase").asText());restored.request("ABORT_GAME",obj());a.event("GAME_ABORTED");
    }
    @Test void tenPlayersCompleteOneRealTlsRound()throws Exception {
        var host=admin();String code=room(host,1);var guests=new ArrayList<Client>();
        for(int i=0;i<10;i++){var c=guest(code,"Player "+i);c.request("READY_SET",obj().put("ready",true));guests.add(c);}
        var extra=new Client("127.0.0.11");error(extra,"ROOM_FULL","JOIN",obj().put("room_code",code).put("nickname","Thứ 11"));
        host.request("START_GAME",obj());for(var c:guests){var q=c.event("QUESTION");assertTrue(c.request("ANSWER",obj().put("session_id",q.path("session_id").asText()).put("question_id",q.path("question_id").asInt()).put("answer","A")).path("accepted").asBoolean());}
        for(var c:guests){var end=c.event("GAME_OVER");assertEquals(10,end.path("ranking").size());assertTrue(end.path("result_saved").asBoolean());}
    }
    @Test void invalidPayloadDoesNotMutateRoomOrGrantRole()throws Exception {
        var host=admin();String code=room(host,1);var guest=guest(code,"Guest");error(guest,"FORBIDDEN","ROOM_CONFIGURE",obj().put("role","ADMIN"));
        error(host,"INVALID_INPUT","ROOM_CONFIGURE",obj().put("room_name","Bad").put("question_count","20"));assertEquals(code,host.request("ROOM_STATE",obj()).path("room_code").asText());
        error(guest,"INVALID_INPUT","READY_SET",obj().put("ready","true"));assertEquals(0,host.request("ROOM_STATE",obj()).path("ready_count").asInt());
    }
    @Test void wrongStaleDuplicateAndLateAnswersCannotAddPoints()throws Exception {
        var host=admin();String code=room(host,1);Client a=guest(code,"A"),b=guest(code,"B");
        a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());var q=a.event("QUESTION");
        var correct=host.request("QUESTION_GET",obj().put("question_id",q.path("question_id").asInt())).path("correct_answer").asText();
        var payload=obj().put("session_id",q.path("session_id").asText()).put("question_id",q.path("question_id").asInt()).put("answer",correct);
        assertEquals("WRONG_SESSION",a.request("ANSWER",payload.deepCopy().put("session_id","stale")).path("reason").asText());
        assertEquals("WRONG_QUESTION",a.request("ANSWER",payload.deepCopy().put("question_id",999)).path("reason").asText());
        assertEquals("INVALID_ANSWER",a.request("ANSWER",payload.deepCopy().put("answer","Z")).path("reason").asText());
        assertTrue(a.request("ANSWER",payload).path("accepted").asBoolean());assertEquals("DUPLICATE_ANSWER",a.request("ANSWER",payload).path("reason").asText());
        var result=a.event("QUESTION_RESULT");int awarded=result.path("points").asInt();assertTrue(awarded>=50&&awarded<=100);assertEquals(awarded,result.path("score").asInt());assertFalse(b.request("ANSWER",payload).path("accepted").asBoolean());
        assertEquals(0,b.event("GAME_OVER").path("score").asInt());assertEquals(awarded,a.event("GAME_OVER").path("score").asInt());
    }
    @Test void fasterCorrectAnswersEarnMoreAndPersistWithoutTrustingClientTiming()throws Exception {
        var host=admin();String code=room(host,2);
        var fast=guest(code,"Nhanh");var slow=guest(code,"Chậm");var wrong=guest(code,"Sai");var missing=guest(code,"Bỏ trống");
        for(var c:List.of(fast,slow,wrong,missing))c.request("READY_SET",obj().put("ready",true));
        host.request("START_GAME",obj());var q=fast.event("QUESTION");
        String correct=host.request("QUESTION_GET",obj().put("question_id",q.path("question_id").asInt())).path("correct_answer").asText();
        var answer=obj().put("session_id",q.path("session_id").asText()).put("question_id",q.path("question_id").asInt()).put("answer",correct);
        assertTrue(fast.request("ANSWER",answer.deepCopy().put("elapsed_ms",4999).put("response_time_ms",4999).put("points",0)).path("accepted").asBoolean());
        Thread.sleep(1100);
        assertTrue(slow.request("ANSWER",answer.deepCopy().put("elapsed_ms",0).put("response_time_ms",0).put("timestamp",Long.MAX_VALUE).put("points",999999)).path("accepted").asBoolean());
        assertEquals("DUPLICATE_ANSWER",fast.request("ANSWER",answer).path("reason").asText());
        String incorrect=correct.equals("A")?"B":"A";
        assertTrue(wrong.request("ANSWER",answer.deepCopy().put("answer",incorrect)).path("accepted").asBoolean());
        assertEquals("DUPLICATE_ANSWER",wrong.request("ANSWER",answer).path("reason").asText());
        var fastRound=fast.event("QUESTION_RESULT");var slowRound=slow.event("QUESTION_RESULT");
        int fastPoints=fastRound.path("points").asInt(),slowPoints=slowRound.path("points").asInt();
        assertTrue(fastPoints>slowPoints,"Faster correct answers must earn more than slower correct answers");
        assertTrue(slowPoints>=50&&fastPoints<=100);assertEquals(fastPoints,fastRound.path("score").asInt());assertEquals(slowPoints,slowRound.path("score").asInt());
        assertEquals(0,wrong.event("QUESTION_RESULT").path("points").asInt());assertEquals(0,missing.event("QUESTION_RESULT").path("points").asInt());
        var q2=slow.event("QUESTION");
        String correct2=host.request("QUESTION_GET",obj().put("question_id",q2.path("question_id").asInt())).path("correct_answer").asText();
        assertTrue(slow.request("ANSWER",obj().put("session_id",q2.path("session_id").asText()).put("question_id",q2.path("question_id").asInt()).put("answer",correct2)).path("accepted").asBoolean());
        var noAnswer=fast.event("QUESTION_RESULT");assertEquals(0,noAnswer.path("points").asInt());assertFalse(noAnswer.path("correct").asBoolean());assertEquals(fastPoints,noAnswer.path("score").asInt());
        int nextPoints=slow.event("QUESTION_RESULT").path("points").asInt();assertTrue(nextPoints>=50&&nextPoints<=100);
        var end=fast.event("GAME_OVER");var slowEnd=slow.event("GAME_OVER");
        assertEquals(fastPoints,end.path("score").asInt());assertEquals(1,end.path("correct_count").asInt());assertEquals(slowPoints+nextPoints,slowEnd.path("score").asInt());assertEquals(2,slowEnd.path("correct_count").asInt());
        assertEquals(0,wrong.event("GAME_OVER").path("score").asInt());assertEquals(0,missing.event("GAME_OVER").path("score").asInt());
        assertEquals(end.path("ranking"),host.request("HISTORY_GET",obj().put("session_id",q.path("session_id").asText())).path("ranking"));
        host.request("ROOM_RESET",obj());for(var player:host.request("ROOM_STATE",obj()).path("players")){assertEquals(0,player.path("score").asInt());assertEquals(0,player.path("correct_count").asInt());}
    }
    @Test void historyQuestionsCanBeReusedAfterTheBankQuestionIsDeleted()throws Exception {
        var host=admin();String originalCode=room(host,1);var player=guest(originalCode,"Thi lại");player.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());
        var question=player.event("QUESTION");var original=host.request("QUESTION_GET",obj().put("question_id",question.path("question_id").asInt()));
        assertTrue(player.event("GAME_OVER").path("result_saved").asBoolean());String savedSession=question.path("session_id").asText();
        var saved=host.request("HISTORY_GET",obj().put("session_id",savedSession));
        assertTrue(saved.path("reuse_available").asBoolean(),"A completed match must retain its question source");
        assertEquals(1,saved.path("saved_question_count").asInt());assertEquals(5,saved.path("duration_seconds").asInt());
        assertFalse(saved.has("questions"));assertFalse(saved.toString().contains("correct_answer"));
        host.request("ROOM_CLOSE",obj());host.request("QUESTION_DELETE",obj().put("question_id",question.path("question_id").asInt()));
        var other=new Client();other.request("REGISTER",obj().put("username","history.other").put("display_name","Giảng viên khác").put("password","History-other-only-1"));other.request("LOGIN",obj().put("username","history.other").put("password","History-other-only-1"));
        error(other,"NOT_FOUND","ROOM_CONFIGURE",obj().put("room_name","Không thuộc quyền").put("question_count",1).put("history_session_id",savedSession));
        error(host,"NOT_ENOUGH_QUESTIONS","ROOM_CONFIGURE",obj().put("room_name","Quá số câu").put("question_count",2).put("history_session_id",savedSession));
        error(host,"INVALID_INPUT","ROOM_CONFIGURE",obj().put("room_name","Hai nguồn").put("question_count",1).put("history_session_id",savedSession).put("question_set_id",1));
        assertEquals("",host.request("ROOM_STATE",obj()).path("room_code").asText());
        var next=host.request("ROOM_CONFIGURE",obj().put("room_name","Thi lại từ lịch sử").put("question_count",1).put("duration_seconds",5).put("history_session_id",savedSession).put("question_set_id",0));
        assertEquals(savedSession,next.path("history_session_id").asText());assertEquals("WAITING",next.path("phase").asText());
        assertEquals(savedSession,host.request("ROOM_CONFIGURE",obj().put("room_name","Thi lại từ lịch sử").put("question_count",1).put("duration_seconds",5)).path("history_session_id").asText());
        var replay=guest(next.path("room_code").asText(),"Ván mới");replay.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());var repeated=replay.event("QUESTION");
        error(host,"INVALID_STATE","ROOM_CONFIGURE",obj().put("room_name","Không đổi khi thi").put("question_count",1).put("history_session_id",savedSession));
        error(replay,"FORBIDDEN","ROOM_CONFIGURE",obj().put("room_name","Không cấp quyền").put("question_count",1).put("history_session_id",savedSession));
        for(String key:List.of("question_id","content","option_a","option_b","option_c","option_d"))assertEquals(original.path(key),repeated.path(key),"Changed historical question: "+key);
        assertFalse(repeated.has("correct_answer"));
        replay.request("ANSWER",obj().put("session_id",repeated.path("session_id").asText()).put("question_id",repeated.path("question_id").asInt()).put("answer",original.path("correct_answer").asText()));
        assertTrue(replay.event("QUESTION_RESULT").path("correct").asBoolean());assertTrue(replay.event("GAME_OVER").path("result_saved").asBoolean());
        error(host,"NOT_ENOUGH_QUESTIONS","ROOM_CONFIGURE",obj().put("room_name","Không reset nếu lỗi").put("question_count",2).put("history_session_id",savedSession));
        assertEquals("FINISHED",host.request("ROOM_STATE",obj()).path("phase").asText());
        var fresh=host.request("ROOM_CONFIGURE",obj().put("room_name","Tiếp tục ôn tập").put("question_count",1).put("duration_seconds",5).put("history_session_id",savedSession).put("question_set_id",0));
        assertEquals("WAITING",fresh.path("phase").asText());assertNotEquals(next.path("room_code").asText(),fresh.path("room_code").asText());
        assertEquals(1,fresh.path("players").size());assertFalse(fresh.path("players").get(0).path("ready").asBoolean());assertEquals(0,fresh.path("players").get(0).path("score").asInt());
        assertEquals(savedSession,host.request("ROOM_RESET",obj()).path("history_session_id").asText());
        assertEquals("",host.request("ROOM_CONFIGURE",obj().put("room_name","Chuyển về ngân hàng").put("question_count",1).put("question_set_id",0).put("history_session_id","")).path("history_session_id").asText());
    }
    @Test void disconnectedPlayerRemainsInFinalRankingAndSavedHistory()throws Exception {
        var host=admin();String code=room(host,1);Client a=guest(code,"Rời mạng"),b=guest(code,"Còn mạng");a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());
        var q=a.event("QUESTION");a.close();var end=b.event("GAME_OVER");assertEquals(2,end.path("ranking").size());
        var gone=java.util.stream.StreamSupport.stream(end.path("ranking").spliterator(),false).filter(v->v.path("nickname").asText().equals("Rời mạng")).findFirst().orElseThrow();assertFalse(gone.path("connected").asBoolean());
        assertEquals(2,host.request("HISTORY_GET",obj().put("session_id",q.path("session_id").asText())).path("ranking").size());
    }
    @Test void udpDiscoveryFindsServerWithoutCorrectConfiguredTcpPortAndTlsDirectoryIsPublicOnly()throws Exception {
        var profile=new ClientConnectionProfile("127.0.0.1",1,trust,new String(KEY_PASSWORD),server.discoveryPort());
        assertTrue(LanDiscovery.find(profile,300).contains(new Endpoint("127.0.0.1",port)));
        var directory=LanDirectory.scan(profile);assertEquals(1,directory.size());assertTrue(directory.getFirst().rooms().isEmpty());
        var host=admin();String code=room(host,1);var anon=new Client();var browse=anon.request("ROOM_BROWSE",obj());
        var room=browse.path("rooms").get(0);assertEquals(code,room.path("room_code").asText());assertTrue(room.path("joinable").asBoolean());
        for(String secret:List.of("password","hash","salt","username","players","questions","correct_answer"))assertFalse(browse.toString().contains("\""+secret+"\""));
        error(anon,"AUTH_REQUIRED","QUESTION_LIST",obj());error(anon,"AUTH_REQUIRED","START_GAME",obj());
        var found=LanDirectory.scan(profile).getFirst().rooms().getFirst();assertEquals(code,found.code());assertEquals("Lớp LAN",found.name());
        Client a=guest(code,"List player"),b=guest(code,"Code player");a.request("READY_SET",obj().put("ready",true));b.request("READY_SET",obj().put("ready",true));host.request("START_GAME",obj());
        assertFalse(anon.request("ROOM_BROWSE",obj()).path("rooms").get(0).path("joinable").asBoolean());
        host.request("ABORT_GAME",obj());host.request("ROOM_RESET",obj());String newCode=host.request("ROOM_STATE",obj()).path("room_code").asText();assertNotEquals(code,newCode);
        error(anon,"WRONG_ROOM","JOIN",obj().put("nickname","Stale").put("room_code",code));
        host.request("ROOM_CLOSE",obj());assertTrue(anon.request("ROOM_BROWSE",obj()).path("rooms").isEmpty());
    }
    @Test void directedTlsFallbackWorksWithNoUdpAndUntrustedAdvertisedServersStayHidden()throws Exception {
        var known=new ClientConnectionProfile("127.0.0.1",port,trust,new String(KEY_PASSWORD),1);
        assertEquals(1,LanDirectory.scan(known).size());
        Path empty=certs.resolve("empty-directory-trust.p12");var store=java.security.KeyStore.getInstance("PKCS12");store.load(null,KEY_PASSWORD);try(var out=Files.newOutputStream(empty)){store.store(out,KEY_PASSWORD);}
        assertTrue(LanDirectory.scan(new ClientConnectionProfile("127.0.0.1",port,empty,new String(KEY_PASSWORD),server.discoveryPort())).isEmpty());
    }
    @Test void selfRegistrationCreatesOnlyTeacherWithPrivateEmptyBankAndHashedPassword()throws Exception {
        var c=new Client();
        var registered=c.request("REGISTER",obj().put("username","Teacher.New").put("display_name","Giảng viên mới").put("password","Signup-test-only-1"));
        assertEquals("TEACHER",registered.path("role").asText());assertFalse(registered.has("hash"));
        error(c,"AUTH_REQUIRED","QUESTION_LIST",obj());
        var login=c.request("LOGIN",obj().put("username","teacher.new").put("password","Signup-test-only-1"));
        assertFalse(login.path("must_change_password").asBoolean());assertEquals("TEACHER",login.path("role").asText());
        assertEquals(0,c.request("QUESTION_LIST",obj()).path("total").asInt());error(c,"FORBIDDEN","USER_LIST",obj());
        error(c,"ALREADY_JOINED","REGISTER",obj());
        var other=new Client();error(other,"USERNAME_TAKEN","REGISTER",obj().put("username","TEACHER.NEW").put("display_name","Khác").put("password","Signup-test-only-1"));
        error(other,"FORBIDDEN","REGISTER",obj().put("username","admin.new").put("display_name","Admin").put("role","ADMIN").put("password","Signup-test-only-1"));
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+data.resolve("quiz.db"));var query=db.createStatement();var rows=query.executeQuery("SELECT salt,hash,iterations,role FROM users WHERE username='teacher.new'")) {
            assertTrue(rows.next());assertNotEquals("Signup-test-only-1",rows.getString("hash"));assertFalse(rows.getString("salt").isEmpty());assertEquals(600000,rows.getInt("iterations"));assertEquals("TEACHER",rows.getString("role"));
        }
    }
    @Test void concurrentRegistrationsCannotReuseUsernameAndAttemptsAreBounded()throws Exception {
        var a=new Client();var b=new Client();var body=obj().put("username","teacher.race").put("display_name","Race").put("password","Signup-test-only-1");
        var first=a.gateway.request("REGISTER",body);var second=b.gateway.request("REGISTER",body);
        int successful=0;
        for(var response:List.of(first,second))try{assertEquals("TEACHER",response.get(8,TimeUnit.SECONDS).path("role").asText());successful++;}catch(ExecutionException error){assertEquals("USERNAME_TAKEN",assertInstanceOf(GatewayException.class,error.getCause()).code());}
        assertEquals(1,successful);
        var c=new Client();for(int i=0;i<3;i++)error(c,"INVALID_INPUT","REGISTER",body.deepCopy().put("username","!"));
        error(c,"RATE_LIMITED","REGISTER",body.deepCopy().put("username","teacher.rate"));
    }
    @Test void registrationCanBeDisabledAndPrivateKeystoreIsNotClientTrust()throws Exception {
        server.close();
        var store=new QuizStore(data.resolve("quiz.db"));var config=new ServerConfig("127.0.0.1",0,data.resolve("quiz.db"),key,10,32,100,120,180,false);
        server=new QuizServer(config,config.tls(KEY_PASSWORD),store);server.start();port=server.port();
        var c=new Client();error(c,"REGISTRATION_DISABLED","REGISTER",obj().put("username","teacher.new").put("display_name","New").put("password","Signup-test-only-1"));
        assertThrows(IllegalArgumentException.class,()->TlsQuizGateway.connect(new Endpoint("127.0.0.1",port),key,KEY_PASSWORD));
    }
    @Test void duplicateMutationRequestIdWritesExactlyOnceOverFragmentedTlsFrames()throws Exception {
        var keys=java.security.KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(trust)){keys.load(input,KEY_PASSWORD);}var managers=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());managers.init(keys);var context=javax.net.ssl.SSLContext.getInstance("TLS");context.init(null,managers.getTrustManagers(),null);
        try(var socket=(javax.net.ssl.SSLSocket)context.getSocketFactory().createSocket("127.0.0.1",port)) {
            var parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);socket.setSoTimeout(5000);socket.startHandshake();
            var reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));
            java.util.function.Function<ObjectNode,JsonNode> exchange=request->{try{byte[] bytes=vn.edu.hutech.nqb.shared.JsonMessages.encode(request);socket.getOutputStream().write(bytes,0,17);socket.getOutputStream().flush();socket.getOutputStream().write(bytes,17,bytes.length-17);socket.getOutputStream().flush();for(int i=0;i<10;i++){var response=JSON.readTree(reader.readLine());if(response.path("request_id").asText().equals(request.path("request_id").asText()))return response;}throw new AssertionError("No response");}catch(Exception e){throw new RuntimeException(e);}};
            assertEquals("LOGIN_OK",exchange.apply(envelope("LOGIN","auth",obj().put("username","admin").put("password","Admin-test-only-1"))).path("type").asText());
            var body=obj().put("content","Một yêu cầu, một bản ghi").put("category","Kiểm thử").put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A");
            var frame=envelope("QUESTION_CREATE","repeat",body);var first=exchange.apply(frame);assertEquals("QUESTION_CREATE_OK",first.path("type").asText());assertEquals(first,exchange.apply(frame));
            assertEquals("REQUEST_ID_REUSED",exchange.apply(envelope("QUESTION_CREATE","repeat",body.deepCopy().put("content","Khác nội dung"))).path("payload").path("code").asText());
            assertEquals(21,exchange.apply(envelope("QUESTION_LIST","list",obj())).path("payload").path("total").asInt());
            var staged=exchange.apply(envelope("QUESTION_IMPORT_PREVIEW","import-preview",preview(null,0,true,importRow("Import exactly once"))));assertEquals("QUESTION_IMPORT_PREVIEW_OK",staged.path("type").asText());
            var importFrame=envelope("QUESTION_IMPORT_COMMIT","import-repeat",commit(staged.path("payload").path("import_id").asText(),false,0));
            var imported=exchange.apply(importFrame);assertEquals("QUESTION_IMPORT_COMMIT_OK",imported.path("type").asText());assertEquals(imported,exchange.apply(importFrame));assertEquals(22,exchange.apply(envelope("QUESTION_LIST","import-list",obj())).path("payload").path("total").asInt());
        }
    }
}
