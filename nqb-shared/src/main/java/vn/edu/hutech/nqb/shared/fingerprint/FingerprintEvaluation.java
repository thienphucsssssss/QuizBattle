package vn.edu.hutech.nqb.shared.fingerprint;

import java.util.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Reproducible holdout evaluation. Test coordinates are read only after prediction. */
public final class FingerprintEvaluation {
    private FingerprintEvaluation() { }
    public record Row(String runId,String device,String pointId,boolean deviceSeenInTraining,FingerprintModel.Estimate estimate,
                      Double positionErrorMeters,Double serverDistanceErrorMeters) { }
    public record Metrics(int testRuns,int estimatedRuns,int rejectedRuns,Double rejectionRate,Double medianPositionErrorMeters,
                          Double p90PositionErrorMeters,Double medianServerDistanceErrorMeters,Double p90ServerDistanceErrorMeters) { }
    public record MethodReport(FingerprintModel.Method method,Metrics metrics,List<Row> rows) { }
    public record Report(int schemaVersion,String purpose,String source,String datasetSha256,String areaId,int revision,
                         FingerprintModel.Parameters parameters,int trainingPoints,List<String> trainingRunIds,List<String> heldOutRunIds,
                         List<String> notes,List<MethodReport> methods) { }

    public static Report evaluate(Bundle data,Set<String> heldOut,FingerprintModel.Parameters parameters){
        validate(data);if(data.areas().size()!=1)throw invalid("Chọn đúng một phiên bản khu để đánh giá.");
        if(heldOut.isEmpty())throw invalid("Chọn ít nhất một lượt kiểm tra độc lập.");
        var area=data.areas().getFirst();var training=new ArrayList<Run>();var test=new ArrayList<Run>();
        for(var run:data.runs())if(run.state()==State.ACCEPTED){if(heldOut.contains(run.id()))test.add(run);else training.add(run);}
        if(test.size()!=heldOut.size())throw invalid("Có mã lượt kiểm tra không tồn tại hoặc chưa được xác nhận.");
        training.sort(Comparator.comparing(Run::id));test.sort(Comparator.comparing(Run::id));
        var model=new FingerprintModel(area,training,parameters);var methods=new ArrayList<MethodReport>();
        var devices=new HashSet<String>();for(var run:training)devices.add(run.deviceLabel()+"/"+run.adapterId());
        for(var method:FingerprintModel.Method.values()){
            var rows=new ArrayList<Row>();
            for(var run:test){
                var estimate=model.estimateHeldOut(run,method);
                Point truth=area.points().stream().filter(p->p.id().equals(run.pointId())).findFirst().orElseThrow();
                double trueServerDistance=Math.hypot(truth.x()-area.serverX(),truth.y()-area.serverY());
                rows.add(new Row(run.id(),run.deviceLabel(),run.pointId(),devices.contains(run.deviceLabel()+"/"+run.adapterId()),estimate,
                    estimate.estimated()?Math.hypot(estimate.x()-truth.x(),estimate.y()-truth.y()):null,
                    estimate.estimated()?Math.abs(estimate.serverDistanceMeters()-trueServerDistance):null));
            }
            methods.add(new MethodReport(method,metrics(rows),List.copyOf(rows)));
        }
        return new Report(1,"wifi_fingerprint_offline_evaluation","Anyplace MIT / 7229551823757cc39174cc693947d9077e770bfb",digest(data),area.id(),area.revision(),parameters,model.pointCount(),
            training.stream().map(Run::id).toList(),test.stream().map(Run::id).toList(),List.of(
                "Kết quả offline theo cả lượt; không phải độ trễ hay độ chính xác định vị trực tiếp.",
                "Tham số baseline chưa hiệu chỉnh; không tự chọn mô hình tốt nhất bằng tập kiểm tra này.",
                "Sai số chỉ tính trên các lượt được định vị; cần xem đồng thời tỷ lệ bị từ chối. P90 dùng nearest-rank.",
                "Phân tách ID/timestamp không chứng minh độc lập thực nghiệm. Thu lượt kiểm tra ở thời điểm riêng, giữ nguyên tọa độ đã đo.",
                "Nhãn laptop/adapter dùng để nhận biết thiết bị mới trong báo cáo; chưa hiệu chỉnh chênh lệch giữa laptop.",
                "Chỉ tách vùng phòng/hành lang; chưa có đa giác tường hoặc ràng buộc mặt bằng chi tiết."),List.copyOf(methods));
    }
    private static Metrics metrics(List<Row> rows){
        var position=rows.stream().filter(r->r.estimate.estimated()).map(Row::positionErrorMeters).sorted().toList();
        var distance=rows.stream().filter(r->r.estimate.estimated()).map(Row::serverDistanceErrorMeters).sorted().toList();
        int rejected=rows.size()-position.size();return new Metrics(rows.size(),position.size(),rejected,rejected/(double)rows.size(),median(position),p90(position),median(distance),p90(distance));
    }
    private static Double median(List<Double> sorted){int size=sorted.size();return size==0?null:size%2==1?sorted.get(size/2):(sorted.get(size/2-1)+sorted.get(size/2))/2;}
    private static Double p90(List<Double> sorted){return sorted.isEmpty()?null:sorted.get((int)Math.ceil(sorted.size()*0.9)-1);}
}
