package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.nqb.client.*;
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
    QuizServer server; int port; final List<Client> clients=new ArrayList<>();
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
        Client()throws Exception{gateway=TlsQuizGateway.connect(new Endpoint("127.0.0.1",port),trust,KEY_PASSWORD);gateway.onEvent(events::add);clients.add(this);}
        JsonNode request(String type,ObjectNode payload)throws Exception{return gateway.request(type,payload).get(8,TimeUnit.SECONDS);}
        JsonNode event(String type)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(9);while(System.nanoTime()<until){var e=events.poll(100,TimeUnit.MILLISECONDS);if(e!=null&&e.path("type").asText().equals(type))return e.path("payload");}throw new AssertionError("Missing event "+type);}
        @Override public void close(){gateway.close();}
    }
    Client admin()throws Exception{var c=new Client();assertEquals("ADMIN",c.request("LOGIN",obj().put("username","admin").put("password","Admin-test-only-1")).path("role").asText());return c;}
    String room(Client host,int count)throws Exception{return host.request("ROOM_CONFIGURE",obj().put("room_name","Lớp LAN").put("question_count",count).put("duration_seconds",5)).path("room_code").asText();}
    Client guest(String code,String name)throws Exception{var c=new Client();c.request("JOIN",obj().put("room_code",code).put("nickname",name));return c;}
    void error(Client c,String code,String type,ObjectNode p){var ex=assertThrows(ExecutionException.class,()->c.request(type,p));assertEquals(code,assertInstanceOf(GatewayException.class,ex.getCause()).code());}
    private ObjectNode importRow(String content){return obj().put("content",content).put("option_a","TCP").put("option_b","UDP").put("option_c","IP").put("option_d","ARP").put("correct_answer","A").put("category","Mạng LAN");}
    private ObjectNode preview(String token,int offset,boolean complete,ObjectNode... rows){var p=obj().put("offset",offset).put("complete",complete);if(token!=null)p.put("import_id",token);var data=p.putArray("rows");for(var row:rows)data.add(row);return p;}
    private ObjectNode commit(String token,boolean allow,int... indexes){var p=obj().put("import_id",token).put("allow_duplicates",allow);var selected=p.putArray("selected_indexes");for(int index:indexes)selected.add(index);return p;}
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
        var extra=new Client();error(extra,"ROOM_FULL","JOIN",obj().put("room_code",code).put("nickname","Thứ 11"));
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
        assertEquals(100,a.event("QUESTION_RESULT").path("score").asInt());assertFalse(b.request("ANSWER",payload).path("accepted").asBoolean());
        assertEquals(0,b.event("GAME_OVER").path("score").asInt());assertEquals(100,a.event("GAME_OVER").path("score").asInt());
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
