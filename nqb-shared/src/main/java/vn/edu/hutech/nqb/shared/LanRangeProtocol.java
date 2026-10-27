package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Physical distances are operator input; addresses and RTT are never inferred from them. */
public final class LanRangeProtocol {
    public static final int INTERVAL_MS=1000, DEADLINE_MS=1000, MAX_ACTIVE_RUNS=4;
    public enum Direction { CLIENT_TO_SERVER, SERVER_TO_CLIENT }
    public enum Status { ON_TIME, TIMEOUT, SEND_FAILED, DISCONNECTED, CANCELLED }
    public enum EndReason { COMPLETED, STOPPED, DISCONNECTED, GAME_STARTED, ROOM_CHANGED, STORAGE_ERROR, CONTROL_TIMEOUT, APP_CLOSED }
    public enum Topology { SERVER_HOTSPOT, DEDICATED_AP, SHARED_WIFI, UNSPECIFIED }
    public enum DistanceBasis { STRAIGHT_LINE, CORRIDOR_ROUTE, UNSPECIFIED }
    public record Options(BigDecimal distanceMeters,int durationSeconds,String surveyId,String surveyName,String route,
                          Topology topology,DistanceBasis distanceBasis,String notes) {
        public Options {
            if(distanceMeters==null||distanceMeters.signum()<0||distanceMeters.compareTo(BigDecimal.valueOf(500))>0
                ||distanceMeters.stripTrailingZeros().scale()>1)throw new IllegalArgumentException("Khoảng cách cần từ 0 đến 500 m, tối đa một số lẻ.");
            if(durationSeconds!=30&&durationSeconds!=60)throw new IllegalArgumentException("Chọn thời lượng 30 hoặc 60 giây.");
            if(surveyId==null||!UUID.fromString(surveyId).toString().equals(surveyId))throw new IllegalArgumentException("Mã đợt đo không hợp lệ.");
            surveyName=clean(surveyName,80,false);route=clean(route,80,false);notes=clean(notes,500,true);
            if(topology==null||distanceBasis==null)throw new IllegalArgumentException("Chọn bố trí và cách đo khoảng cách.");
        }
    }
    public record Sample(Direction direction,int sequence,Status status,Long rttNanos) {
        public Sample {
            if(direction==null||status==null||sequence<1||sequence>60)throw new IllegalArgumentException("Mẫu đo không hợp lệ.");
            if(status==Status.ON_TIME?(rttNanos==null||rttNanos<0||rttNanos>1_000_000_000L):rttNanos!=null)
                throw new IllegalArgumentException("RTT không hợp lệ.");
        }
    }
    public static ObjectNode object(){return JsonNodeFactory.instance.objectNode();}
    private static String clean(String value,int max,boolean empty) {
        if(value==null)throw new IllegalArgumentException("Thiếu thông tin đợt đo.");
        value=value.strip();
        if((!empty&&value.isEmpty())||value.length()>max||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))
            throw new IllegalArgumentException("Thông tin đợt đo không hợp lệ (tối đa "+max+" ký tự).");
        return value;
    }
    public static Options parseOptions(JsonNode p) {
        if(!p.path("distance_m").isNumber()||!p.path("duration_seconds").isIntegralNumber()||!p.path("duration_seconds").canConvertToInt())
            throw new IllegalArgumentException("Khoảng cách và thời lượng phải là số.");
        for(String key:new String[]{"survey_id","survey_name","route","topology","distance_basis"})
            if(!p.path(key).isTextual())throw new IllegalArgumentException("Thiếu trường "+key+".");
        if(p.has("notes")&&!p.path("notes").isTextual())throw new IllegalArgumentException("Ghi chú không hợp lệ.");
        return new Options(p.path("distance_m").decimalValue(),p.path("duration_seconds").asInt(),p.path("survey_id").asText(),
            p.path("survey_name").asText(),p.path("route").asText(),Topology.valueOf(p.path("topology").asText()),
            DistanceBasis.valueOf(p.path("distance_basis").asText()),p.path("notes").asText(""));
    }
    public static ObjectNode optionsPayload(Options o) {
        return object().put("distance_m",o.distanceMeters()).put("duration_seconds",o.durationSeconds()).put("survey_id",o.surveyId())
            .put("survey_name",o.surveyName()).put("route",o.route()).put("topology",o.topology().name())
            .put("distance_basis",o.distanceBasis().name()).put("notes",o.notes()).put("interval_ms",INTERVAL_MS).put("deadline_ms",DEADLINE_MS);
    }
    public static ObjectNode samplePayload(Sample s) {
        var p=object().put("direction",s.direction().name()).put("sequence",s.sequence()).put("status",s.status().name())
            .put("measured_by",s.direction()==Direction.CLIENT_TO_SERVER?"client":"server");
        if(s.rttNanos()==null)p.putNull("rtt_ms");else p.put("rtt_ms",BigDecimal.valueOf(s.rttNanos(),6));
        return p;
    }
    public static Sample parseSample(JsonNode p) {
        if(!p.path("sequence").isIntegralNumber()||!p.path("sequence").canConvertToInt())throw new IllegalArgumentException("Sequence không hợp lệ.");
        Status status=Status.valueOf(p.path("status").asText());
        Long nanos=null;
        if(status==Status.ON_TIME){
            if(!p.path("rtt_ms").isNumber())throw new IllegalArgumentException("Thiếu RTT.");
            nanos=p.path("rtt_ms").decimalValue().movePointRight(6).longValueExact();
        }
        return new Sample(Direction.valueOf(p.path("direction").asText()),p.path("sequence").asInt(),status,nanos);
    }
    private LanRangeProtocol(){}
}
