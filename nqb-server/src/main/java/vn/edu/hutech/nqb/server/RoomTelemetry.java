package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import vn.edu.hutech.nqb.shared.LanTelemetryRules;
import static vn.edu.hutech.nqb.server.Wire.*;

/** Owned only by the game worker. Telemetry never changes scores or readiness. */
final class RoomTelemetry {
    private static final class Link {
        final boolean supported;String probe="";long sent,lastAttempt,lastReply,pingMs;
        Link(boolean supported){this.supported=supported;}
    }
    private static final class Result {
        String status,reason="",updated="";
        Result(String status){this.status=status;}
    }
    private final Map<String,Link> links=new LinkedHashMap<>();
    private final Map<String,Result> targets=new LinkedHashMap<>();
    private String context="",refreshId="",refreshState="IDLE";
    private long refreshStarted,lastRefreshStarted;
    private static long ms(long nanos){return TimeUnit.NANOSECONDS.toMillis(nanos);}
    void join(String player,boolean supported){links.put(player,new Link(supported));}
    void disconnect(String player){var link=links.get(player);if(link!=null){link.lastReply=0;link.probe="";}complete(player,refreshId,"FAILED","Ngắt kết nối");}
    void remove(String player){disconnect(player);links.remove(player);}
    void clear(){cancel("Phòng đã đóng");links.clear();targets.clear();context="";refreshId="";refreshState="IDLE";lastRefreshStarted=0;}
    void context(String next){if(!context.equals(next)){cancel("Phòng hoặc phiên đo đã thay đổi");context=next;}}
    private void cancel(String reason){if(refreshState.equals("RUNNING")){for(var result:targets.values())if(result.status.equals("PENDING")){result.status="CANCELLED";result.reason=reason;}refreshState="CANCELLED";}}
    ObjectNode probe(String player,long now){
        var link=links.get(player);if(link==null||!link.supported)return null;
        if(!link.probe.isEmpty()&&ms(now-link.sent)<LanTelemetryRules.PROBE_TIMEOUT_MS)return null;
        if(link.lastAttempt!=0&&ms(now-link.lastAttempt)<LanTelemetryRules.PROBE_INTERVAL_MS)return null;
        link.probe=UUID.randomUUID().toString();link.sent=link.lastAttempt=now;
        return obj().put("probe_id",link.probe);
    }
    void reply(String player,String probe,long now){
        var link=links.get(player);
        if(link==null||link.probe.isEmpty()||!link.probe.equals(probe)||ms(now-link.sent)>=LanTelemetryRules.PROBE_TIMEOUT_MS)throw fail("INVALID_PROBE","Lượt đo ping đã hết hạn hoặc không hợp lệ.");
        link.pingMs=Math.max(0,ms(now-link.sent));link.lastReply=now;link.probe="";
    }
    String begin(List<String> connected,long now){
        if(refreshState.equals("RUNNING"))return refreshId;
        if(lastRefreshStarted!=0&&ms(now-lastRefreshStarted)<LanTelemetryRules.REFRESH_INTERVAL_MS)throw fail("RATE_LIMITED","Hãy chờ đủ 10 giây giữa hai lượt làm mới.");
        if(connected.isEmpty())throw fail("NO_PLAYERS","Chưa có học sinh kết nối.");
        targets.clear();refreshId=UUID.randomUUID().toString();refreshState="RUNNING";refreshStarted=lastRefreshStarted=now;
        for(var id:connected){var link=links.get(id);var result=new Result(link!=null&&link.supported?"PENDING":"UNSUPPORTED");if(result.status.equals("UNSUPPORTED"))result.reason="Cần cập nhật Client";targets.put(id,result);}
        settle();return refreshId;
    }
    List<String> pending(){return targets.entrySet().stream().filter(e->e.getValue().status.equals("PENDING")).map(Map.Entry::getKey).toList();}
    boolean accepts(String player,String id){var r=targets.get(player);return refreshState.equals("RUNNING")&&refreshId.equals(id)&&r!=null&&r.status.equals("PENDING");}
    void complete(String player,String id,String status,String reason){
        if(!accepts(player,id))return;
        var result=targets.get(player);result.status=status;result.reason=reason;result.updated=Instant.now().toString();settle();
    }
    private void settle(){if(refreshState.equals("RUNNING")&&pending().isEmpty())refreshState="COMPLETED";}
    boolean tick(long now){
        if(!refreshState.equals("RUNNING")||ms(now-refreshStarted)<LanTelemetryRules.REFRESH_TIMEOUT_MS)return false;
        for(var result:targets.values())if(result.status.equals("PENDING")){result.status="TIMEOUT";result.reason="Chưa nhận được mẫu mới";result.updated=Instant.now().toString();}
        refreshState="COMPLETED";return true;
    }
    ObjectNode refresh(long now){
        int completed=(int)targets.values().stream().filter(r->!r.status.equals("PENDING")).count();
        return obj().put("refresh_id",refreshId).put("state",refreshState).put("total",targets.size()).put("completed",completed)
            .put("updated",targets.values().stream().filter(r->r.status.equals("UPDATED")).count())
            .put("remaining_ms",refreshState.equals("RUNNING")?Math.max(0,LanTelemetryRules.REFRESH_TIMEOUT_MS-ms(now-refreshStarted)):0)
            .put("retry_after_ms",lastRefreshStarted==0?0:Math.max(0,LanTelemetryRules.REFRESH_INTERVAL_MS-ms(now-lastRefreshStarted)));
    }
    void decorate(String player,ObjectNode value,boolean connected,long now){
        var link=links.get(player);value.put("topology_supported",link!=null&&link.supported);
        long age=link==null||link.lastReply==0?-1:Math.max(0,ms(now-link.lastReply));value.put("ping_age_ms",age);
        if(connected&&age>=0&&age<LanTelemetryRules.STALE_MS)value.put("ping_ms",link.pingMs);
        var result=targets.get(player);if(result!=null)value.put("refresh_status",result.status).put("refresh_reason",result.reason).put("refreshed_at_utc",result.updated);
    }
}
