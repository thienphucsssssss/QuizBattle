package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import vn.edu.hutech.nqb.shared.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

/** Local evidence only. Reload never promotes an interrupted or local run to a physical range result. */
public final class LanRangeReport {
    public static Path defaultDirectory(){
        String override=System.getProperty("nqb.lanRange.dir");
        if(override!=null&&!override.isBlank())return Path.of(override).toAbsolutePath();
        String local=System.getenv("LOCALAPPDATA");
        return local==null||local.isBlank()?Path.of(System.getProperty("user.home"),".network-quiz-battle","lan-range")
            :Path.of(local,"NetworkQuizBattle","lan-range");
    }
    public static List<JsonNode> load(Path directory)throws IOException{
        if(!Files.exists(directory))return List.of();
        var results=new ArrayList<JsonNode>();
        try(var files=Files.newDirectoryStream(directory,"client-*.jsonl")){
            for(Path file:files){
                var records=LanRangeJournal.replay(file);
                ObjectNode row=null,ended=null;var samples=new LinkedHashMap<String,Sample>();
                for(var record:records){
                    switch(record.path("record_type").asText()){
                        case "RUN_STARTED"->row=((ObjectNode)record).deepCopy();
                        case "SAMPLE"->{
                            try{var s=parseSample(record);samples.putIfAbsent(s.direction()+":"+s.sequence(),s);}
                            catch(IllegalArgumentException invalid){throw new IOException("Mẫu nhật ký không hợp lệ: "+file.getFileName(),invalid);}
                        }
                        case "RUN_FINISHED"->ended=((ObjectNode)record).deepCopy();
                        default->{}
                    }
                }
                if(row==null)continue;
                if(ended!=null)row.setAll(ended);
                row.put("active",false).put("saved",true).put("phase","FINISHED").put("journal_path",file.toAbsolutePath().toString());
                if(ended==null)row.put("end_reason",EndReason.APP_CLOSED.name()).put("server_summary_received",false);
                int planned=row.path("duration_seconds").asInt();
                if(planned!=30&&planned!=60)throw new IOException("Thời lượng nhật ký không hợp lệ: "+file.getFileName());
                row.set("client_summary",LanRangeStatistics.summarize(Direction.CLIENT_TO_SERVER,planned,samples.values()));
                row.set("server_summary",LanRangeStatistics.summarize(Direction.SERVER_TO_CLIENT,planned,samples.values()));
                var array=row.putArray("samples");samples.values().forEach(s->array.add(samplePayload(s)));
                EndReason reason;
                try{reason=EndReason.valueOf(row.path("end_reason").asText());}catch(IllegalArgumentException invalid){reason=EndReason.APP_CLOSED;}
                row.put("passed",row.path("server_summary_received").asBoolean()&&row.path("server_journal_healthy").asBoolean()
                    &&LanRangeStatistics.passes(row.path("client_summary"),row.path("server_summary"),reason,row.path("client_journal_healthy").asBoolean()));
                results.add(row);
            }
        }
        results.sort(Comparator.comparing((JsonNode r)->r.path("started_at").asText()).reversed());
        return List.copyOf(results);
    }
    public static Optional<BigDecimal> farthestPassing(List<JsonNode> rows,String survey){
        return rows.stream().filter(r->survey.equals(r.path("survey_id").asText())&&r.path("passed").asBoolean()
            &&!r.path("loopback").asBoolean()&&r.path("distance_m").isNumber()).map(r->r.path("distance_m").decimalValue()).max(Comparator.naturalOrder());
    }
    private static final String[] META={"run_id","survey_id","survey_name","route","topology","distance_basis","notes","distance_m","duration_seconds",
        "client_ip_observed","server_ip","server_port","started_at","ended_at","end_reason","loopback","passed","server_summary_received"};
    private static final String[] STATS={"planned","confirmed","on_time","timeout","send_failed","disconnected","cancelled","unconfirmed","on_time_rate",
        "rtt_min_ms","rtt_median_ms","rtt_p95_ms","rtt_max_ms"};
    public static void exportSummary(Path target,List<JsonNode> rows)throws IOException{
        atomicCsv(target,writer->{
            var header=new ArrayList<>(List.of(META));header.add("direction");header.addAll(List.of(STATS));writeRow(writer,header);
            for(var row:rows)for(var direction:Direction.values()){
                var cells=new ArrayList<String>();for(String key:META)cells.add(cell(row.path(key)));
                cells.add(direction.name());var stats=row.path(direction==Direction.CLIENT_TO_SERVER?"client_summary":"server_summary");
                for(String key:STATS)cells.add(cell(stats.path(key)));writeRow(writer,cells);
            }
        });
    }
    public static void exportSamples(Path target,List<JsonNode> rows)throws IOException{
        atomicCsv(target,writer->{
            var header=new ArrayList<>(List.of(META));header.addAll(List.of("direction","sequence","status","rtt_ms","measured_by"));writeRow(writer,header);
            for(var row:rows)for(var sample:row.path("samples")){
                var cells=new ArrayList<String>();for(String key:META)cells.add(cell(row.path(key)));
                for(String key:new String[]{"direction","sequence","status","rtt_ms","measured_by"})cells.add(cell(sample.path(key)));
                writeRow(writer,cells);
            }
        });
    }
    private static String cell(JsonNode n){
        if(n.isNull()||n.isMissingNode())return "";
        String value=n.asText();
        if(n.isTextual()&&!value.stripLeading().isEmpty()&&"=+-@".indexOf(value.stripLeading().charAt(0))>=0)value="'"+value;
        return value;
    }
    private static void writeRow(Writer out,List<String> cells)throws IOException{
        for(int i=0;i<cells.size();i++){
            if(i>0)out.write(',');
            String value=cells.get(i);
            if(value.contains(",")||value.contains("\"")||value.contains("\n")||value.contains("\r"))out.write("\""+value.replace("\"","\"\"")+"\"");
            else out.write(value);
        }
        out.write("\r\n");
    }
    @FunctionalInterface private interface CsvBody{void write(Writer out)throws IOException;}
    private static void atomicCsv(Path target,CsvBody body)throws IOException{
        Path absolute=target.toAbsolutePath(),temp=Files.createTempFile(absolute.getParent(),"nqb-range-",".csv.tmp");
        try{
            try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){out.write('\uFEFF');body.write(out);}
            try{Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private LanRangeReport(){}
}
