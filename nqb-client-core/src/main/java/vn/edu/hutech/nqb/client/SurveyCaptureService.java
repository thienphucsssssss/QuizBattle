package vn.edu.hutech.nqb.client;

import vn.edu.hutech.nqb.shared.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** One fixed point per capture, durable after each scan, no UI or network dependency. */
public final class SurveyCaptureService implements AutoCloseable {
    public record Progress(Run run,int elapsedSeconds,int durationSeconds) { }
    private final ProximityClientService wifi;
    private final SurveyRepository repository;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("survey-capture").factory());
    private final long intervalMillis;
    private final AtomicBoolean busy=new AtomicBoolean(),cancelled=new AtomicBoolean(),closed=new AtomicBoolean();
    private volatile CompletableFuture<WifiScanSnapshot> pending;
    private volatile CompletableFuture<Run> result;
    public SurveyCaptureService(ProximityClientService wifi,SurveyRepository repository){this(wifi,repository,5000);}
    public SurveyCaptureService(ProximityClientService wifi,SurveyRepository repository,long intervalMillis){this.wifi=wifi;this.repository=repository;this.intervalMillis=Math.max(1,intervalMillis);}
    public CompletableFuture<Run> start(Area area,Point point,WifiScanProvider.Adapter adapter,String deviceLabel,String orientation,int seconds,Consumer<Progress> progress){
        if(closed.get()||!busy.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Đang có lượt khảo sát."));
        cancelled.set(false);result=new CompletableFuture<>();var answer=result;
        long now=System.currentTimeMillis(),started=System.nanoTime();
        var initial=new Run(id(),area.id(),area.revision(),point.id(),adapter.id(),adapter.name(),deviceLabel,
            System.getProperty("os.name")+" / "+System.getProperty("os.arch")+" / Java "+System.getProperty("java.version"),orientation,now,now,State.CAPTURING,"",List.of());
        try{validate(initial,area);if(seconds<1||seconds>120)throw invalid("Thời lượng khảo sát cần 1–120 giây.");}
        catch(Exception e){busy.set(false);answer.completeExceptionally(e);return answer;}
        wifi.reserveSurvey().whenComplete((v,error)->{
            if(error!=null){busy.set(false);if(closed.get())worker.shutdown();answer.completeExceptionally(error);return;}
            repository.start(initial).whenComplete((run,failure)->{
                if(failure!=null){release();answer.completeExceptionally(failure);return;}
                worker.execute(()->step(run,area,seconds,started,progress,answer));
            });
        });return answer;
    }
    private void step(Run run,Area area,int seconds,long started,Consumer<Progress> progress,CompletableFuture<Run> answer){
        if(cancelled.get()||closed.get()){finish(run,State.DISCARDED,"Người khảo sát hủy lượt; không dùng cho định vị.",answer);return;}
        if(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)>=seconds||run.scans().size()>=MAX_SCANS){finish(run,State.REVIEW,"Chờ xác nhận đã đứng yên đúng điểm trong cả lượt.",answer);return;}
        pending=wifi.surveyScanAsync(run.adapterId(),area.ssid());
        pending.whenComplete((snapshot,error)->worker.execute(()->{
            pending=null;
            if(cancelled.get()||closed.get()){finish(run,State.DISCARDED,"Người khảo sát hủy lượt; không dùng cho định vị.",answer);return;}
            if(error!=null){finish(run,State.INTERRUPTED,"Lỗi quét Wi-Fi; dữ liệu đã thu được giữ lại, cần đo lại.",answer);return;}
            // Failure snapshots may omit adapter context. Preserve the reason with the fixed capture context.
            var scan=new Scan(snapshot.scanId(),run.adapterId(),run.adapterName(),area.ssid(),snapshot.associatedBssid(),snapshot.startedAtMillis(),snapshot.completedAtMillis(),snapshot.status().name(),snapshot.diagnostic(),snapshot.omittedCount(),
                snapshot.observations().stream().map(o->new Observation(o.ssid(),o.bssid(),o.rssiDbm(),o.frequencyKhz(),o.hostTimestamp100ns(),o.ageMillis(),o.fresh(),o.associated())).toList());
            if(!snapshot.adapterId().isEmpty()&&!snapshot.adapterId().equals(run.adapterId())){finish(run,State.INTERRUPTED,"Card Wi-Fi đã đổi; cần đo lại.",answer);return;}
            repository.append(run.id(),scan).whenComplete((saved,failure)->worker.execute(()->{
                if(failure!=null){finish(run,State.INTERRUPTED,"Không lưu được scan: "+safeMessage(failure),answer);return;}
                if(progress!=null)try{progress.accept(new Progress(saved,(int)Math.min(seconds,TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)),seconds));}catch(RuntimeException ignored){}
                if(Set.of("PERMISSION_DENIED","NO_ADAPTER","WIFI_DISABLED","UNSUPPORTED","ERROR").contains(scan.status())){finish(saved,State.INTERRUPTED,"Quét dừng: "+scan.diagnostic(),answer);return;}
                worker.schedule(()->step(saved,area,seconds,started,progress,answer),cancelled.get()?0:intervalMillis,TimeUnit.MILLISECONDS);
            }));
        }));
    }
    private static String safeMessage(Throwable error){while(error.getCause()!=null)error=error.getCause();String text=String.valueOf(error.getMessage()).replaceAll("[\\p{Cntrl}]"," ");return text.substring(0,Math.min(250,text.length()));}
    private void finish(Run run,State state,String note,CompletableFuture<Run> answer){
        repository.finish(run.id(),state,note).whenComplete((value,error)->{release();if(error!=null)answer.completeExceptionally(error);else answer.complete(value);});
    }
    private void release(){wifi.releaseSurvey();busy.set(false);if(closed.get())worker.shutdown();}
    public boolean busy(){return busy.get();}
    public void cancel(){cancelled.set(true);var scan=pending;if(scan!=null)scan.cancel(false);}
    public CompletableFuture<Run> completion(){return result;}
    @Override public void close(){closed.set(true);cancel();if(!busy.get())worker.shutdown();}
}
