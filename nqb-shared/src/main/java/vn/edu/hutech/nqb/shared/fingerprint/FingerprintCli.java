package vn.edu.hutech.nqb.shared.fingerprint;

import java.nio.file.*;
import java.util.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Offline only. Never connects to the exam server or changes a survey database. */
public final class FingerprintCli {
    private FingerprintCli() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=3&&args.length!=4)throw invalid("Dùng: FingerprintCli survey.json report.json holdout_run_id[,id...] [k=3]");
        var ids=new LinkedHashSet<>(Arrays.asList(args[2].split(",",-1)));int k=args.length==4?Integer.parseInt(args[3]):3;
        var report=evaluate(Path.of(args[0]),Path.of(args[1]),ids,FingerprintModel.Parameters.baseline(k));
        System.out.println("OFFLINE ONLY · "+report.trainingPoints()+" điểm huấn luyện · "+report.heldOutRunIds().size()+" lượt kiểm tra");
        for(var method:report.methods()){var m=method.metrics();System.out.println(method.method()+": định vị "+m.estimatedRuns()+"/"+m.testRuns()+", lỗi vị trí median="+m.medianPositionErrorMeters()+" m, P90="+m.p90PositionErrorMeters()+" m; lỗi khoảng cách Server median="+m.medianServerDistanceErrorMeters()+" m");}
    }
    public static FingerprintEvaluation.Report evaluate(Path input,Path output,Set<String> ids,FingerprintModel.Parameters parameters)throws Exception {
        byte[] bytes;try(var stream=Files.newInputStream(input)){bytes=stream.readNBytes(MAX_FILE_BYTES+1);}
        if(bytes.length>MAX_FILE_BYTES)throw invalid("Tệp khảo sát vượt 32 MiB.");
        var bundle=JSON.readValue(bytes,Bundle.class);var report=FingerprintEvaluation.evaluate(bundle,ids,parameters);
        // Refuse overwriting either the source or an existing evaluation, including aliases/hard links.
        var target=output.toAbsolutePath();Files.createDirectories(target.getParent());
        try(var stream=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            JSON.writerWithDefaultPrettyPrinter().writeValue(stream,report);
        }
        return report;
    }
}
