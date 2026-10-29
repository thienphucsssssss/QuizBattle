package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;
import vn.edu.hutech.nqb.shared.JsonMessages;
import vn.edu.hutech.nqb.shared.Protocol;

class ProximityStoreTest {
    @TempDir Path temp;
    @Test void largeAlertHistoryIsOrderedPaginatedAndFitsProtocolFrame() throws Exception {
        Path file=temp.resolve("quiz.db");
        try(var main=new QuizStore(file)) {
            int owner=main.bootstrap("admin","Admin","Test-password-1".toCharArray());
            try(var history=new ProximityStore(file,8,warning->fail(warning))) {
                String now=Instant.now().toString();
                var events=new ArrayList<ProximityStore.AlertEventRow>();
                for(int i=0;i<205;i++)events.add(new ProximityStore.AlertEventRow("s","p","Sinh viên","DATA_LOST",null,null,"Chi tiết "+i+" x".repeat(150),now));
                history.enqueueSnapshot(new ProximityStore.SessionSnapshot("s",owner,"Room","CODE",true,"AP","aa:bb:cc:dd:ee:01","5 GHz",10,"COMPLETED",now,now,now,List.of(),events));
                int seen=0,lastId=0;
                for(int page=1;page<=11;page++) {
                    var detail=history.getHistoryDetailAsync(owner,false,"s",page).get(3,TimeUnit.SECONDS);
                    assertEquals(205,detail.path("alert_total").asInt());
                    assertEquals(page<11,detail.path("alerts_has_more").asBoolean());
                    assertTrue(JsonMessages.encode(envelope("PROXIMITY_HISTORY_GET_OK","id",detail)).length<=Protocol.MAX_FRAME_BYTES);
                    for(var event:detail.path("alerts")) { assertTrue(event.path("id").asInt()>lastId);lastId=event.path("id").asInt();seen++; }
                }
                assertEquals(205,seen);
            }
        }
    }
}
