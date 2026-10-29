package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.nqb.shared.ResumeRules;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

class ResumingQuizGatewayTest {
    static final class Session {
        String secret=ResumeRules.token();int resumeCount,answerCount;boolean loseAck,loseBeforeCommit;
        ObjectNode credential(){return object().put("token",secret).put("player_id","same-player").put("room_generation","generation").put("room_code","ABC234").put("grace_seconds",5);}
    }
    static final class Fake implements QuizGateway {
        final Session session;Consumer<JsonNode> listener=e->{};boolean closed;boolean leave;
        Fake(Session session){this.session=session;}
        void drop(){listener.accept(object().put("type","DISCONNECTED").set("payload",object()));}
        public CompletableFuture<JsonNode> request(String type,ObjectNode payload){
            if(closed)return CompletableFuture.failedFuture(new IllegalStateException("closed"));
            switch(type){
                case "JOIN":return CompletableFuture.completedFuture(object().set("resume",session.credential()));
                case "ANSWER":session.answerCount++;return CompletableFuture.failedFuture(new GatewayException("DISCONNECTED","lost",object()));
                case "LEAVE":leave=true;return CompletableFuture.completedFuture(object());
                case "RESUME":
                    session.resumeCount++;
                    if(!session.secret.equals(payload.path("token").asText()))return CompletableFuture.failedFuture(new GatewayException("RESUME_REJECTED","invalid",object()));
                    if(session.loseBeforeCommit){session.loseBeforeCommit=false;return CompletableFuture.failedFuture(new GatewayException("DISCONNECTED","lost before commit",object()));}
                    session.secret=payload.path("next_token").asText();
                    if(session.loseAck){session.loseAck=false;return CompletableFuture.failedFuture(new GatewayException("DISCONNECTED","lost ack",object()));}
                    return CompletableFuture.completedFuture(object().put("player_id","same-player").set("self_answer",object().put("recorded",true).put("answer","B")));
                default:return CompletableFuture.completedFuture(object());
            }
        }
        public void onEvent(Consumer<JsonNode> listener){this.listener=listener;}
        public boolean isPreview(){return false;}
        public void close(){closed=true;}
    }
    @Test void lostResumeAckUsesSuccessorWithoutReplayingAnswer()throws Exception {
        var session=new Session();session.loseAck=true;var first=new Fake(session);var restored=new CompletableFuture<JsonNode>();
        try(var gateway=new ResumingQuizGateway(first,()->new Fake(session))){
            gateway.onEvent(e->{if(e.path("type").asText().equals("RECONNECTED"))restored.complete(e.path("payload"));});
            gateway.request("JOIN",object()).get(1,TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,()->gateway.request("ANSWER",object()).get(1,TimeUnit.SECONDS));first.drop();
            assertEquals("same-player",restored.get(5,TimeUnit.SECONDS).path("player_id").asText());
            assertEquals(2,session.resumeCount);assertEquals(1,session.answerCount);
        }
    }
    @Test void lostRequestFallsBackToCurrentTokenAndExplicitLeaveStopsRetry()throws Exception {
        var session=new Session();session.loseBeforeCommit=true;var first=new Fake(session);var restored=new CompletableFuture<JsonNode>();var next=new AtomicReference<Fake>();var connections=new AtomicInteger();
        try(var gateway=new ResumingQuizGateway(first,()->{connections.incrementAndGet();var value=new Fake(session);next.set(value);return value;})){
            gateway.onEvent(e->{if(e.path("type").asText().equals("RECONNECTED"))restored.complete(e.path("payload"));});
            gateway.request("JOIN",object()).get(1,TimeUnit.SECONDS);first.drop();restored.get(5,TimeUnit.SECONDS);
            assertEquals(3,session.resumeCount);gateway.request("LEAVE",object()).get(1,TimeUnit.SECONDS);next.get().drop();
            Thread.sleep(650);assertEquals(2,connections.get());
        }
    }
    @Test void closeDuringConnectNeverReportsSuccessAndClosesReturnedSocket()throws Exception {
        var session=new Session();var first=new Fake(session);var reached=new CountDownLatch(1);var release=new CountDownLatch(1);var next=new Fake(session);var events=new LinkedBlockingQueue<String>();
        var gateway=new ResumingQuizGateway(first,()->{reached.countDown();release.await(3,TimeUnit.SECONDS);return next;});
        gateway.onEvent(e->events.add(e.path("type").asText()));gateway.request("JOIN",object()).get(1,TimeUnit.SECONDS);first.drop();
        assertTrue(reached.await(2,TimeUnit.SECONDS));gateway.close();release.countDown();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!next.closed&&System.nanoTime()<until)Thread.sleep(10);
        assertTrue(next.closed);assertFalse(events.contains("RECONNECTED"));assertEquals(0,session.resumeCount);
    }
    @Test void requestsMadeDuringRetryAreRejectedImmediatelyNotQueuedForNewSocket()throws Exception {
        var session=new Session();var first=new Fake(session);var reached=new CountDownLatch(1);var release=new CountDownLatch(1);var next=new Fake(session);
        try(var gateway=new ResumingQuizGateway(first,()->{reached.countDown();release.await(3,TimeUnit.SECONDS);return next;})){
            gateway.request("JOIN",object()).get(1,TimeUnit.SECONDS);first.drop();assertTrue(reached.await(2,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->gateway.request("ANSWER",object()).get(100,TimeUnit.MILLISECONDS));
            release.countDown();Thread.sleep(100);assertEquals(0,session.answerCount);
        }finally{release.countDown();}
    }
}
