package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.Test;
import vn.edu.hutech.nqb.shared.AutomaticApDistance;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApDistanceViewsTest {
    @Test void agedSnapshotDoesNotKeepDisplayingOldMeters(){
        var model=new AutomaticApDistance();model.accept("a","E1","aa:bb:cc:dd:ee:01","5 GHz",List.of(-46),0);
        var value=model.snapshot(true,1);
        assertTrue(ApDistanceViews.summary("Client",value,0).contains("chưa hiệu chuẩn"));
        assertEquals("— m",ApDistanceViews.distance(value,16000));assertEquals("Số đo đã cũ",ApDistanceViews.status(value,16000));
    }
}
