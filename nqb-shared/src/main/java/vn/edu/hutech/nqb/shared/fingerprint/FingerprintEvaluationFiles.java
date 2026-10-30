package vn.edu.hutech.nqb.shared.fingerprint;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** File operations for the offline viewer. Call off the JavaFX thread. */
public final class FingerprintEvaluationFiles {
    private FingerprintEvaluationFiles() { }
    public static Bundle readSurvey(Path path) throws Exception {
        byte[] bytes;
        try(var input=Files.newInputStream(path)){bytes=input.readNBytes(MAX_FILE_BYTES+1);}
        if(bytes.length>MAX_FILE_BYTES)throw invalid("Tệp khảo sát vượt 32 MiB.");
        var data=JSON.readValue(bytes,Bundle.class);validate(data);
        if(data.areas().size()!=1)throw invalid("Xuất đúng một phiên bản khu từ màn Khảo sát Wi-Fi để đánh giá.");
        return data;
    }
    public static void writeReport(Path path,FingerprintEvaluation.Report report,String areaName,boolean synthetic) throws Exception {
        ObjectNode value=JSON.valueToTree(report);
        value.put("datasetKind",synthetic?"SYNTHETIC_DEMO":"USER_SURVEY");
        value.put("areaName",areaName);
        value.put("measurementNotice",synthetic?"DỮ LIỆU GIẢ LẬP: không dùng để báo cáo độ chính xác tại trường.":"Dữ liệu do người dùng cung cấp; phần mềm chưa xác minh tọa độ hay tính độc lập của các lượt thu.");
        byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        Path target=path.toAbsolutePath();Files.createDirectories(target.getParent());
        Files.write(target,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
    }
}
