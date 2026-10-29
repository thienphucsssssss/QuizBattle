package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Shared SQLite implementation; one bounded worker owns the connection after construction. */
public final class SurveyRepository implements AutoCloseable {
    private final Connection db;
    private final ThreadPoolExecutor worker;
    private final AtomicBoolean closed=new AtomicBoolean();
    public static final String LOCAL_OWNER="local";
    public SurveyRepository(Path path,boolean local)throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(var s=db.createStatement()){
            s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=3000");s.execute("PRAGMA journal_mode=WAL");
            if(local){int version;try(var r=s.executeQuery("PRAGMA user_version")){version=r.getInt(1);}if(version>1)throw new SQLException("Phiên bản kho khảo sát mới hơn ứng dụng.");
                db.setAutoCommit(false);try{createSchema(db);s.execute("PRAGMA user_version=1");db.commit();}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
                // Preserve every persisted scan after an application crash; never silently accept it.
                try(var rows=s.executeQuery("SELECT owner_id,run_id,header_json FROM wifi_survey_runs WHERE owner_id='local' AND state='CAPTURING'")){
                    var abandoned=new ArrayList<Run>();while(rows.next())abandoned.add(read(rows.getString(3),Run.class));
                    for(var r:abandoned){var scans=readScans(LOCAL_OWNER,r.id());long end=scans.isEmpty()?r.startedAtMillis():scans.getLast().completedAtMillis();
                        updateHeader(LOCAL_OWNER,r.withScans(List.of()).finish(State.INTERRUPTED,"Ứng dụng đóng khi đang thu; cần đo lại tại điểm.",end));}
                }
            }
        }catch(Exception e){db.close();throw e;}
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),Thread.ofPlatform().daemon().name("wifi-survey-db").factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    /** Called inside the main server migration transaction, or the independent local DB migration. */
    public static void createSchema(Connection db)throws SQLException {
        try(var s=db.createStatement()){
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_areas(owner_id TEXT NOT NULL,area_id TEXT NOT NULL,revision INTEGER NOT NULL,definition_json TEXT NOT NULL,PRIMARY KEY(owner_id,area_id,revision))");
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_points(owner_id TEXT NOT NULL,area_id TEXT NOT NULL,revision INTEGER NOT NULL,point_id TEXT NOT NULL,definition_json TEXT NOT NULL,PRIMARY KEY(owner_id,area_id,revision,point_id),FOREIGN KEY(owner_id,area_id,revision) REFERENCES wifi_survey_areas(owner_id,area_id,revision))");
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_runs(owner_id TEXT NOT NULL,run_id TEXT NOT NULL,area_id TEXT NOT NULL,revision INTEGER NOT NULL,point_id TEXT NOT NULL,state TEXT NOT NULL,header_json TEXT NOT NULL,PRIMARY KEY(owner_id,run_id),FOREIGN KEY(owner_id,area_id,revision,point_id) REFERENCES wifi_survey_points(owner_id,area_id,revision,point_id))");
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_scans(owner_id TEXT NOT NULL,run_id TEXT NOT NULL,sequence INTEGER NOT NULL,scan_id TEXT NOT NULL,metadata_json TEXT NOT NULL,PRIMARY KEY(owner_id,run_id,sequence),UNIQUE(owner_id,run_id,scan_id),FOREIGN KEY(owner_id,run_id) REFERENCES wifi_survey_runs(owner_id,run_id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_observations(owner_id TEXT NOT NULL,run_id TEXT NOT NULL,sequence INTEGER NOT NULL,ordinal INTEGER NOT NULL,bssid TEXT NOT NULL,frequency_khz INTEGER NOT NULL,rssi_dbm INTEGER NOT NULL,fresh INTEGER NOT NULL,observation_json TEXT NOT NULL,PRIMARY KEY(owner_id,run_id,sequence,ordinal),FOREIGN KEY(owner_id,run_id,sequence) REFERENCES wifi_survey_scans(owner_id,run_id,sequence) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_uploads(owner_id TEXT NOT NULL,run_id TEXT NOT NULL,header_json TEXT NOT NULL,expected_count INTEGER NOT NULL,digest TEXT NOT NULL,created_ms INTEGER NOT NULL,PRIMARY KEY(owner_id,run_id))");
            s.execute("CREATE TABLE IF NOT EXISTS wifi_survey_upload_scans(owner_id TEXT NOT NULL,run_id TEXT NOT NULL,sequence INTEGER NOT NULL,scan_json TEXT NOT NULL,PRIMARY KEY(owner_id,run_id,sequence),FOREIGN KEY(owner_id,run_id) REFERENCES wifi_survey_uploads(owner_id,run_id) ON DELETE CASCADE)");
            s.execute("CREATE INDEX IF NOT EXISTS wifi_survey_runs_area ON wifi_survey_runs(owner_id,area_id,revision)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS wifi_survey_scan_identity ON wifi_survey_scans(owner_id,scan_id)");
        }
    }
    public <T>CompletableFuture<T> submit(Callable<T> job){
        var result=new CompletableFuture<T>();if(closed.get())return CompletableFuture.failedFuture(new IllegalStateException("Kho khảo sát đã đóng."));
        try{worker.execute(()->{try{result.complete(job.call());}catch(Exception e){result.completeExceptionally(e);}});}catch(RejectedExecutionException e){result.completeExceptionally(new IllegalStateException("Kho khảo sát đang bận. Hãy thử lại.",e));}return result;
    }
    private <T>T transaction(Callable<T> action)throws Exception {
        db.setAutoCommit(false);try{T v=action.call();db.commit();return v;}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    private PreparedStatement statement(String sql,Object... args)throws SQLException{var s=db.prepareStatement(sql);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;}
    private int update(String sql,Object... args)throws SQLException{try(var s=statement(sql,args)){return s.executeUpdate();}}
    private String scalar(String sql,Object... args)throws SQLException{try(var s=statement(sql,args);var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    private int count(String sql,Object... args)throws SQLException{return Integer.parseInt(scalar(sql,args));}
    private static <T>T read(String json,Class<T> type)throws Exception{return JSON.readValue(json,type);}
    private Area area(String owner,String id,int revision)throws Exception{String json=scalar("SELECT definition_json FROM wifi_survey_areas WHERE owner_id=? AND area_id=? AND revision=?",owner,id,revision);if(json==null)throw invalid("Không tìm thấy phiên bản khu khảo sát của tài khoản này.");return read(json,Area.class);}
    private void putArea(String owner,Area a)throws Exception {
        validate(a);String old=scalar("SELECT definition_json FROM wifi_survey_areas WHERE owner_id=? AND area_id=? AND revision=?",owner,a.id(),a.revision());
        if(old!=null){if(!read(old,Area.class).equals(a))throw invalid("Phiên bản khu đã tồn tại với nội dung khác. Hãy tạo phiên bản mới.");return;}
        if(count("SELECT count(*) FROM wifi_survey_areas WHERE owner_id=?",owner)>=200)throw invalid("Kho đã đủ 200 phiên bản khu.");
        update("INSERT INTO wifi_survey_areas VALUES(?,?,?,?)",owner,a.id(),a.revision(),encode(a));
        for(var p:a.points())update("INSERT INTO wifi_survey_points VALUES(?,?,?,?,?)",owner,a.id(),a.revision(),p.id(),encode(p));
    }
    public CompletableFuture<Area> saveArea(String owner,Area a){return submit(()->transaction(()->{putArea(owner,a);return a;}));}
    public CompletableFuture<List<Area>> areas(String owner){return submit(()->listAreas(owner));}
    private List<Area> listAreas(String owner)throws Exception{
        var out=new ArrayList<Area>();try(var s=statement("SELECT definition_json FROM wifi_survey_areas WHERE owner_id=? ORDER BY rowid DESC",owner);var r=s.executeQuery()){while(r.next())out.add(read(r.getString(1),Area.class));}return List.copyOf(out);
    }
    public CompletableFuture<List<Run>> runs(String owner,String areaId,int revision){return submit(()->{
        var out=new ArrayList<Run>();try(var s=statement("SELECT run_id FROM wifi_survey_runs WHERE owner_id=? AND area_id=? AND revision=? ORDER BY rowid DESC",owner,areaId,revision);var r=s.executeQuery()){while(r.next())out.add(run(owner,r.getString(1)));}return List.copyOf(out);
    });}
    private Run run(String owner,String id)throws Exception{String json=scalar("SELECT header_json FROM wifi_survey_runs WHERE owner_id=? AND run_id=?",owner,id);if(json==null)throw invalid("Không tìm thấy lượt thu của tài khoản này.");return read(json,Run.class).withScans(readScans(owner,id));}
    public CompletableFuture<Run> getRun(String owner,String id){return submit(()->run(owner,id));}
    private List<Scan> readScans(String owner,String id)throws Exception{
        var out=new ArrayList<Scan>();try(var s=statement("SELECT sequence,metadata_json FROM wifi_survey_scans WHERE owner_id=? AND run_id=? ORDER BY sequence",owner,id);var rows=s.executeQuery()){
            while(rows.next()){var metadata=JSON.readTree(rows.getString(2));var observations=JSON.createArrayNode();
                try(var q=statement("SELECT observation_json FROM wifi_survey_observations WHERE owner_id=? AND run_id=? AND sequence=? ORDER BY ordinal",owner,id,rows.getInt(1));var r=q.executeQuery()){while(r.next())observations.add(JSON.readTree(r.getString(1)));}
                ((com.fasterxml.jackson.databind.node.ObjectNode)metadata).set("observations",observations);out.add(decode(metadata,Scan.class));}
        }return List.copyOf(out);
    }
    private void updateHeader(String owner,Run r)throws Exception{update("UPDATE wifi_survey_runs SET state=?,header_json=? WHERE owner_id=? AND run_id=?",r.state().name(),encode(r.withScans(List.of())),owner,r.id());}
    private void insertScan(String owner,String runId,int sequence,Scan scan)throws Exception{
        if(scalar("SELECT run_id FROM wifi_survey_scans WHERE owner_id=? AND scan_id=?",owner,scan.scanId())!=null)throw invalid("Scan đã thuộc một lượt thu khác; không gán cùng scan cho nhiều điểm.");
        var metadata=JSON.valueToTree(scan);((com.fasterxml.jackson.databind.node.ObjectNode)metadata).remove("observations");
        update("INSERT INTO wifi_survey_scans VALUES(?,?,?,?,?)",owner,runId,sequence,scan.scanId(),encode(metadata));int ordinal=0;
        for(var o:scan.observations())update("INSERT INTO wifi_survey_observations VALUES(?,?,?,?,?,?,?,?,?)",owner,runId,sequence,ordinal++,o.bssid(),o.frequencyKhz(),o.rssiDbm(),o.fresh(),encode(o));
    }
    private void insertRun(String owner,Run r)throws Exception{
        if(count("SELECT count(*) FROM wifi_survey_runs WHERE owner_id=?",owner)>=MAX_RUNS)throw invalid("Kho đã đủ 500 lượt thu; xuất bản sao trước khi tạo kho mới.");
        update("INSERT INTO wifi_survey_runs VALUES(?,?,?,?,?,?,?)",owner,r.id(),r.areaId(),r.revision(),r.pointId(),r.state().name(),encode(r.withScans(List.of())));
        for(int i=0;i<r.scans().size();i++)insertScan(owner,r.id(),i,r.scans().get(i));
    }
    public CompletableFuture<Run> start(Run r){return submit(()->transaction(()->{
        if(r.state()!=State.CAPTURING||!r.scans().isEmpty())throw invalid("Lượt thu mới phải trống.");validate(r,area(LOCAL_OWNER,r.areaId(),r.revision()));insertRun(LOCAL_OWNER,r);return r;
    }));}
    public CompletableFuture<Run> append(String id,Scan scan){return submit(()->transaction(()->{
        var r=run(LOCAL_OWNER,id);if(r.state()!=State.CAPTURING)throw invalid("Lượt thu đã kết thúc.");var scans=new ArrayList<>(r.scans());scans.add(scan);
        var next=r.withScans(scans).finish(State.CAPTURING,"",scan.completedAtMillis());validate(next,area(LOCAL_OWNER,r.areaId(),r.revision()));insertScan(LOCAL_OWNER,id,scans.size()-1,scan);updateHeader(LOCAL_OWNER,next);return next;
    }));}
    public CompletableFuture<Run> finish(String id,State state,String note){return submit(()->transaction(()->{
        var r=run(LOCAL_OWNER,id);boolean allowed=r.state()==State.CAPTURING&&Set.of(State.REVIEW,State.DISCARDED,State.INTERRUPTED).contains(state)
            ||r.state()==State.REVIEW&&Set.of(State.ACCEPTED,State.DISCARDED).contains(state);
        if(!allowed)throw invalid("Không được sửa lượt đã xác nhận hoặc lượt bị gián đoạn.");
        // Review may happen days later; capture time is the last persisted observation, not approval time.
        var next=r.finish(state,note,r.endedAtMillis());validate(next,area(LOCAL_OWNER,r.areaId(),r.revision()));updateHeader(LOCAL_OWNER,next);return next;
    }));}
    public CompletableFuture<Bundle> bundle(String owner,String areaId,int revision){return submit(()->{
        Area a=area(owner,areaId,revision);var runs=new ArrayList<Run>();try(var s=statement("SELECT run_id FROM wifi_survey_runs WHERE owner_id=? AND area_id=? AND revision=? ORDER BY rowid",owner,areaId,revision);var r=s.executeQuery()){
            while(r.next()){var value=run(owner,r.getString(1));if(value.state()==State.CAPTURING)throw invalid("Kết thúc lượt thu trước khi xuất/đồng bộ.");runs.add(value);}}
        return Bundle.of(List.of(a),runs);
    });}
    public CompletableFuture<Integer> importBundle(String owner,Bundle bundle){return submit(()->{
        validate(bundle);return transaction(()->{for(var a:bundle.areas())putArea(owner,a);int added=0;
            for(var r:bundle.runs()){String existing=scalar("SELECT run_id FROM wifi_survey_runs WHERE owner_id=? AND run_id=?",owner,r.id());
                if(existing!=null){if(!run(owner,r.id()).equals(r))throw invalid("Lượt thu trùng mã nhưng khác nội dung; không ghi đè.");}else{insertRun(owner,r);added++;}}
            return added;});
    });}
    public CompletableFuture<Void> exportFile(Path path,String owner,String areaId,int revision){return bundle(owner,areaId,revision).thenCompose(bundle->submit(()->{
        byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);if(bytes.length>MAX_FILE_BYTES)throw invalid("Tệp vượt giới hạn 32 MiB.");
        Path target=path.toAbsolutePath();Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"survey-",".tmp");
        try{Files.write(temp,bytes);try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}return null;
    }));}
    public CompletableFuture<Bundle> previewFile(Path path){return submit(()->{
        // Bounded read also covers a file that grows after the size check.
        byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(MAX_FILE_BYTES+1);}if(bytes.length>MAX_FILE_BYTES)throw invalid("Tệp vượt giới hạn 32 MiB.");
        var bundle=JSON.readValue(bytes,Bundle.class);validate(bundle);return bundle;
    });}
    /** Authenticated server operations: a scan per frame, immutable commit and persisted idempotency. */
    public CompletableFuture<JsonNode> handle(String owner,String type,JsonNode payload){return submit(()->transaction(()->{
        return switch(type){
            case "SURVEY_PUT_AREA"->{var a=decode(payload.path("area"),Area.class);putArea(owner,a);yield JSON.createObjectNode().put("revision",a.revision());}
            case "SURVEY_UPLOAD_BEGIN"->{
                var header=decode(payload.path("run"),Run.class);validate(header,area(owner,header.areaId(),header.revision()));
                // Headers carry the final state separately: ACCEPTED validation needs the scans at commit.
                if(header.state()!=State.REVIEW||!header.scans().isEmpty())throw invalid("Header upload phải ở trạng thái REVIEW và không chứa scan.");
                String finalState=payload.path("final_state").asText();if(!Set.of("ACCEPTED","DISCARDED","INTERRUPTED","REVIEW").contains(finalState))throw invalid("Trạng thái đích không hợp lệ.");
                int n=payload.path("scan_count").asInt(-1);String hash=payload.path("digest").asText();if(n<0||n>MAX_SCANS||!hash.matches("[0-9a-f]{64}"))throw invalid("Số scan hoặc dấu kiểm không hợp lệ.");
                var finalHeader=header.finish(State.valueOf(finalState),header.note(),header.endedAtMillis());String headerJson=encode(finalHeader);
                String existing=scalar("SELECT run_id FROM wifi_survey_runs WHERE owner_id=? AND run_id=?",owner,header.id());
                if(existing!=null){if(!digest(run(owner,header.id())).equals(hash))throw invalid("Lượt thu trên Server khác nội dung.");yield JSON.createObjectNode().put("committed",true);}
                update("DELETE FROM wifi_survey_uploads WHERE created_ms<?",System.currentTimeMillis()-86400000L);
                String old=scalar("SELECT header_json FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,header.id());
                if(old!=null){if(!old.equals(headerJson)||!hash.equals(scalar("SELECT digest FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,header.id()))||n!=count("SELECT expected_count FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,header.id()))throw invalid("Upload cùng mã có nội dung khác.");}
                else {if(count("SELECT count(*) FROM wifi_survey_uploads WHERE owner_id=?",owner)>=4)throw invalid("Đã có 4 lượt đang chờ đồng bộ. Hoàn tất hoặc thử lại sau 24 giờ.");update("INSERT INTO wifi_survey_uploads VALUES(?,?,?,?,?,?)",owner,header.id(),headerJson,n,hash,System.currentTimeMillis());}
                yield JSON.createObjectNode().put("committed",false);
            }
            case "SURVEY_UPLOAD_SCAN"->{
                String id=payload.path("run_id").asText();String header=scalar("SELECT header_json FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,id);if(header==null)throw invalid("Bắt đầu đồng bộ lượt thu trước.");
                int seq=payload.path("sequence").asInt(-1),expected=count("SELECT expected_count FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,id);if(seq<0||seq>=expected)throw invalid("Số thứ tự scan không hợp lệ.");
                var scan=decode(payload.path("scan"),Scan.class);var r=read(header,Run.class);validate(r.withScans(List.of(scan)).finish(State.REVIEW,r.note(),r.endedAtMillis()),area(owner,r.areaId(),r.revision()));
                String encoded=encode(scan),old=scalar("SELECT scan_json FROM wifi_survey_upload_scans WHERE owner_id=? AND run_id=? AND sequence=?",owner,id,seq);
                if(old!=null&&!old.equals(encoded))throw invalid("Scan đồng bộ trùng vị trí nhưng khác dữ liệu.");if(old==null)update("INSERT INTO wifi_survey_upload_scans VALUES(?,?,?,?)",owner,id,seq,encoded);
                yield JSON.createObjectNode().put("sequence",seq);
            }
            case "SURVEY_UPLOAD_COMMIT"->{
                String id=payload.path("run_id").asText(),hash=payload.path("digest").asText();String header=scalar("SELECT header_json FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,id);
                if(header==null){if(!digest(run(owner,id)).equals(hash))throw invalid("Dấu kiểm lượt thu không khớp.");yield JSON.createObjectNode().put("committed",true);}
                if(!hash.equals(scalar("SELECT digest FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,id)))throw invalid("Dấu kiểm upload không khớp.");
                var scans=new ArrayList<Scan>();try(var s=statement("SELECT scan_json FROM wifi_survey_upload_scans WHERE owner_id=? AND run_id=? ORDER BY sequence",owner,id);var r=s.executeQuery()){while(r.next())scans.add(read(r.getString(1),Scan.class));}
                if(scans.size()!=count("SELECT expected_count FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,id))throw invalid("Chưa nhận đủ scan; dữ liệu chưa được công bố.");
                var r=read(header,Run.class).withScans(scans);validate(r,area(owner,r.areaId(),r.revision()));if(!digest(r).equals(hash))throw invalid("Nội dung lượt thu không khớp dấu kiểm.");insertRun(owner,r);update("DELETE FROM wifi_survey_uploads WHERE owner_id=? AND run_id=?",owner,id);
                yield JSON.createObjectNode().put("committed",true);
            }
            default->throw invalid("Yêu cầu khảo sát chưa được hỗ trợ.");
        };
    }));}
    @Override public void close(){if(closed.compareAndSet(false,true)){worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS)){worker.shutdownNow();worker.awaitTermination(3,TimeUnit.SECONDS);}db.close();}catch(Exception e){Thread.currentThread().interrupt();}}}
}
