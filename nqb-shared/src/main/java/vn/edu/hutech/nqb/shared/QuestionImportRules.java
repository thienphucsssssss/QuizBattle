package vn.edu.hutech.nqb.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Same field contract on Client preview and Server import; no owner/ID is accepted. */
public final class QuestionImportRules {
    public static final int MAX_ROWS=500,MAX_BATCH=20,MAX_JSON_BYTES=2*1024*1024;
    public static final List<String> KEYS=List.of("content","option_a","option_b","option_c","option_d","correct_answer","category");
    public static final List<String> HEADERS=List.of("Câu hỏi","A","B","C","D","Đáp án đúng","Chủ đề");
    private QuestionImportRules() { }
    public static ObjectNode normalize(JsonNode source) {
        var row=JsonNodeFactory.instance.objectNode();
        for(String key:KEYS)row.put(key,source.path(key).isTextual()?source.path(key).asText().strip():"");
        row.put("correct_answer",row.path("correct_answer").asText().toUpperCase(Locale.ROOT));row.put("is_active",true);return row;
    }
    public static List<String> errors(JsonNode source) {
        var errors=new ArrayList<String>();
        for(int i=0;i<KEYS.size();i++) {
            String key=KEYS.get(i),value=source.path(key).isTextual()?source.path(key).asText().strip():"";
            int limit=i==0?2000:i<5?500:i==5?1:80;
            if(value.isEmpty()||value.length()>limit||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))errors.add(HEADERS.get(i)+": cần 1–"+limit+" ký tự hợp lệ.");
        }
        if(!source.path("correct_answer").asText().strip().toUpperCase(Locale.ROOT).matches("[ABCD]"))errors.add("Đáp án đúng phải là A, B, C hoặc D.");
        return List.copyOf(errors);
    }
}
