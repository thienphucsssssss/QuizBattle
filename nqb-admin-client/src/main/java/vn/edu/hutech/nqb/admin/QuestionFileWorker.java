package vn.edu.hutech.nqb.admin;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Only launched by QuestionFiles. Its input is never executed or sent to the Server. */
public final class QuestionFileWorker {
    private QuestionFileWorker() { }
    public static void main(String[] args) throws Exception {
        var result=QuestionFiles.JSON.createObjectNode();
        try {
            var rows=QuestionFiles.readDirect(Path.of(args[0]));result.put("ok",true);var output=result.putArray("rows");
            for(var row:rows){var item=output.addObject().put("source",row.source());item.set("question",row.question());var issues=item.putArray("issues");row.issues().forEach(issues::add);}
            Files.writeString(Path.of(args[1]),result.toString(),StandardCharsets.UTF_8);
        } catch(Throwable failure) {
            result.removeAll();result.put("ok",false).put("error",failure instanceof java.io.IOException?failure.getMessage():"Không đọc được tệp. Hãy lưu lại theo mẫu .xlsx/.docx, không đặt mật khẩu.");Files.writeString(Path.of(args[1]),result.toString(),StandardCharsets.UTF_8);System.exit(1);
        }
    }
}
