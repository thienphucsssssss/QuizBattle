package vn.edu.hutech.nqb.client;

import vn.edu.hutech.nqb.shared.WifiSurvey;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Explicit upload only. Retrying the same immutable run is safe even after a lost ACK. */
public final class SurveySync {
    private SurveySync() { }
    private static CompletableFuture<JsonNode> request(QuizGateway gateway,String type,ObjectNode payload){
        // Large offline batches must leave room under the server's 60 requests/second limit.
        return CompletableFuture.runAsync(()->{},CompletableFuture.delayedExecutor(75,TimeUnit.MILLISECONDS)).thenCompose(v->gateway.request(type,payload));
    }
    public static CompletableFuture<Integer> upload(QuizGateway gateway,Bundle bundle,Consumer<String> progress){
        validate(bundle);CompletableFuture<Integer> chain=CompletableFuture.completedFuture(0);
        for(var area:bundle.areas())chain=chain.thenCompose(n->request(gateway,"SURVEY_PUT_AREA",QuizGateway.object().set("area",JSON.valueToTree(area))).thenApply(v->n));
        for(var run:bundle.runs())chain=chain.thenCompose(n->{
            if(run.state()==State.REVIEW)return CompletableFuture.completedFuture(n); // Require operator confirmation or a rejection before sharing.
            if(progress!=null)progress.accept("Đồng bộ lượt "+(n+1)+"…");
            String hash=digest(run);var header=run.withScans(List.of()).finish(State.REVIEW,run.note(),run.endedAtMillis());
            var begin=QuizGateway.object().put("scan_count",run.scans().size()).put("digest",hash).put("final_state",run.state().name());begin.set("run",JSON.valueToTree(header));
            return request(gateway,"SURVEY_UPLOAD_BEGIN",begin).thenCompose(reply->{
                if(reply.path("committed").asBoolean())return CompletableFuture.completedFuture(n+1);
                CompletableFuture<?> scans=CompletableFuture.completedFuture(null);
                for(int i=0;i<run.scans().size();i++){var payload=QuizGateway.object().put("run_id",run.id()).put("sequence",i);payload.set("scan",JSON.valueToTree(run.scans().get(i)));scans=scans.thenCompose(v->request(gateway,"SURVEY_UPLOAD_SCAN",payload));}
                return scans.thenCompose(v->request(gateway,"SURVEY_UPLOAD_COMMIT",QuizGateway.object().put("run_id",run.id()).put("digest",hash))).thenApply(v->n+1);
            });
        });return chain;
    }
}
