package vn.edu.hutech.nqb.shared.fingerprint;

import java.util.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Offline baseline adapter over AnyplaceKnn. Not a calibrated model or a live telemetry API. */
public final class FingerprintModel {
    public enum Method { KNN, WKNN }
    public enum Status { ESTIMATED, NO_FRESH_DATA, INSUFFICIENT_OVERLAP, OUTSIDE_SIGNAL_MAP, AMBIGUOUS_ZONE, AMBIGUOUS_EXACT_MATCH, NEIGHBORS_TOO_FAR_APART }
    public record Parameters(int k,int minimumCommonRadios,double minimumOverlap,double maximumRmsDb,double maximumSpreadMeters,double missingRssiDbm) {
        public Parameters {
            if(k<1||k>100||minimumCommonRadios<2||minimumCommonRadios>64||!Double.isFinite(minimumOverlap)||minimumOverlap<=0||minimumOverlap>1
                ||!Double.isFinite(maximumRmsDb)||maximumRmsDb<=0||maximumRmsDb>100||!Double.isFinite(maximumSpreadMeters)||maximumSpreadMeters<=0||maximumSpreadMeters>10000
                ||!Double.isFinite(missingRssiDbm)||missingRssiDbm> -100||missingRssiDbm< -130)throw invalid("Tham số baseline không hợp lệ.");
        }
        /** Initial experiments only; all thresholds must be evaluated on the pilot dataset. */
        public static Parameters baseline(int k){return new Parameters(k,2,0.5,20,10,-110);}
    }
    public record Estimate(Status status,Double x,Double y,Double serverDistanceMeters,Double bestRmsDb,int commonRadios,List<String> neighbors) {
        public Estimate {neighbors=List.copyOf(neighbors);}
        public boolean estimated(){return status==Status.ESTIMATED;}
    }
    private record Reference(Point point,Map<String,Double> signal) { }
    private record Candidate(Reference reference,AnyplaceKnn.Neighbor neighbor,int common,double rms) { }
    private final Area area;
    private final Parameters parameters;
    private final List<Reference> references;
    private final Set<String> trainingRuns,trainingScans,trainingMeasurements;

    public FingerprintModel(Area area,List<Run> training,Parameters parameters) {
        validate(Bundle.of(List.of(area),training));this.area=area;this.parameters=Objects.requireNonNull(parameters);
        var accepted=training.stream().filter(r->r.state()==State.ACCEPTED).toList();
        var grouped=new TreeMap<String,List<Run>>();
        for(var run:accepted)grouped.computeIfAbsent(run.pointId(),key->new ArrayList<>()).add(run);
        if(grouped.size()<2)throw invalid("Baseline cần ít nhất 2 điểm huấn luyện có lượt đã xác nhận.");
        var result=new ArrayList<Reference>();
        for(var entry:grouped.entrySet()){
            Point point=area.points().stream().filter(p->p.id().equals(entry.getKey())).findFirst().orElseThrow();
            // Equal run weight: taking a longer capture must not give that point more votes.
            var averages=new TreeMap<String,DoubleSummaryStatistics>();
            for(var run:entry.getValue())signal(run).forEach((key,value)->averages.computeIfAbsent(key,k->new DoubleSummaryStatistics()).accept(value));
            var fingerprint=new TreeMap<String,Double>();averages.forEach((key,value)->fingerprint.put(key,value.getAverage()));
            result.add(new Reference(point,Map.copyOf(fingerprint)));
        }
        references=List.copyOf(result);trainingRuns=new HashSet<>();trainingScans=new HashSet<>();trainingMeasurements=new HashSet<>();
        for(var run:accepted){trainingRuns.add(run.id());for(var scan:run.scans()){trainingScans.add(scan.scanId());for(var observation:scan.observations())if(observation.fresh())trainingMeasurements.add(measurement(run,observation));}}
    }
    public int pointCount(){return references.size();}
    private static String radio(Observation value){return value.bssid()+"/"+value.frequencyKhz();}
    private static String measurement(Run run,Observation value){return run.adapterId()+"/"+radio(value)+"/"+value.hostTimestamp100ns();}
    private static Map<String,Double> signal(Run run){
        var sums=new TreeMap<String,DoubleSummaryStatistics>();
        for(var scan:run.scans())for(var observation:scan.observations())if(observation.fresh())sums.computeIfAbsent(radio(observation),key->new DoubleSummaryStatistics()).accept(observation.rssiDbm());
        var result=new TreeMap<String,Double>();sums.forEach((key,value)->result.put(key,value.getAverage()));return result;
    }
    /** Testing an entire held-out capture. Wall-clock age now is irrelevant for offline evaluation. */
    public Estimate estimateHeldOut(Run query,Method method){
        validate(query,area);Objects.requireNonNull(method);
        if(query.state()!=State.ACCEPTED)throw invalid("Chỉ đánh giá lượt kiểm tra đã xác nhận.");
        if(trainingRuns.contains(query.id()))throw invalid("Lượt kiểm tra đã nằm trong tập huấn luyện.");
        for(var scan:query.scans()){
            if(trainingScans.contains(scan.scanId()))throw invalid("Scan kiểm tra trùng tập huấn luyện.");
            for(var o:scan.observations())if(o.fresh()&&trainingMeasurements.contains(measurement(query,o)))throw invalid("Timestamp/BSSID/adapter của mẫu kiểm tra trùng huấn luyện dù mã lượt khác.");
        }
        var observed=signal(query);
        if(observed.isEmpty())return reject(Status.NO_FRESH_DATA,List.of());
        var candidates=new ArrayList<Candidate>();boolean overlapFound=false;
        for(var reference:references){
            var union=new TreeSet<>(reference.signal.keySet());union.addAll(observed.keySet());
            int common=(int)reference.signal.keySet().stream().filter(observed::containsKey).count();
            if(common<parameters.minimumCommonRadios||common/(double)union.size()<parameters.minimumOverlap)continue;
            overlapFound=true;
            double[] expected=new double[union.size()],actual=new double[union.size()];int i=0;
            for(String key:union){expected[i]=reference.signal.getOrDefault(key,parameters.missingRssiDbm);actual[i++]=observed.getOrDefault(key,parameters.missingRssiDbm);}
            // Normalize by participating dimensions; never compare vector positions without radio keys.
            double rms=AnyplaceKnn.euclidean(expected,actual)/Math.sqrt(union.size());
            if(rms>parameters.maximumRmsDb)continue;
            candidates.add(new Candidate(reference,new AnyplaceKnn.Neighbor(reference.point.id(),rms,reference.point.x(),reference.point.y()),common,rms));
        }
        if(candidates.isEmpty())return reject(overlapFound?Status.OUTSIDE_SIGNAL_MAP:Status.INSUFFICIENT_OVERLAP,List.of());
        var selected=AnyplaceKnn.nearest(candidates.stream().map(Candidate::neighbor).toList(),parameters.k);
        var ids=selected.stream().map(AnyplaceKnn.Neighbor::id).toList();
        var chosen=candidates.stream().filter(c->ids.contains(c.reference.point.id())).toList();
        if(chosen.stream().map(c->c.reference.point.zone()).distinct().count()>1)return reject(Status.AMBIGUOUS_ZONE,ids);
        double spread=0;for(var left:selected)for(var right:selected)spread=Math.max(spread,Math.hypot(left.x()-right.x(),left.y()-right.y()));
        if(selected.getFirst().distance()==0&&spread>0.001)return reject(Status.AMBIGUOUS_EXACT_MATCH,ids);
        if(spread>parameters.maximumSpreadMeters)return reject(Status.NEIGHBORS_TOO_FAR_APART,ids);
        var position=AnyplaceKnn.interpolate(selected,method==Method.WKNN);
        var best=chosen.stream().min(Comparator.comparingDouble(Candidate::rms)).orElseThrow();
        return new Estimate(Status.ESTIMATED,position.x(),position.y(),Math.hypot(position.x()-area.serverX(),position.y()-area.serverY()),best.rms,best.common,ids);
    }
    private static Estimate reject(Status status,List<String> neighbors){return new Estimate(status,null,null,null,null,0,neighbors);}
}
