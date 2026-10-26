package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

/** Only terminal, attributed samples contribute; missing evidence remains unconfirmed. */
public final class LanRangeStatistics {
    public static ObjectNode summarize(Direction direction,int planned,Collection<Sample> samples) {
        if(planned<1||planned>60)throw new IllegalArgumentException("Số mẫu dự kiến không hợp lệ.");
        var unique=new LinkedHashMap<Integer,Sample>();
        for(var sample:samples)if(sample.direction()==direction){
            if(sample.sequence()>planned)throw new IllegalArgumentException("Sequence ngoài lượt đo.");
            unique.putIfAbsent(sample.sequence(),sample);
        }
        var result=object().put("planned",planned).put("confirmed",unique.size()).put("unconfirmed",planned-unique.size());
        for(var status:Status.values())result.put(status.name().toLowerCase(Locale.ROOT),unique.values().stream().filter(s->s.status()==status).count());
        result.put("on_time_rate",result.path("on_time").asInt()/(double)planned);
        var rtts=unique.values().stream().filter(s->s.rttNanos()!=null).mapToDouble(s->s.rttNanos()/1_000_000.0).sorted().toArray();
        if(rtts.length==0)for(String name:new String[]{"min","median","p95","max"})result.putNull("rtt_"+name+"_ms");
        else {
            int n=rtts.length;
            result.put("rtt_min_ms",rtts[0]).put("rtt_max_ms",rtts[n-1])
                .put("rtt_median_ms",n%2==0?(rtts[n/2-1]+rtts[n/2])/2:rtts[n/2])
                .put("rtt_p95_ms",rtts[(int)Math.ceil(n*.95)-1]);
        }
        return result;
    }
    public static boolean passes(JsonNode client,JsonNode server,EndReason reason,boolean journalsHealthy) {
        return reason==EndReason.COMPLETED&&journalsHealthy&&passesDirection(client)&&passesDirection(server);
    }
    private static boolean passesDirection(JsonNode p) {
        int n=p.path("planned").asInt();
        return n>0&&p.path("confirmed").asInt()==n&&p.path("on_time").asInt()*100>=95*n
            &&p.path("send_failed").asInt()==0&&p.path("disconnected").asInt()==0&&p.path("cancelled").asInt()==0;
    }
    private LanRangeStatistics(){}
}
