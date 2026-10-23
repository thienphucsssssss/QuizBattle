package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import vn.edu.hutech.nqb.shared.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static vn.edu.hutech.nqb.server.Wire.*;

/** One bounded, expiring, read-only preview per authenticated connection. */
final class QuestionImportSession {
    private final String id=UUID.randomUUID().toString();
    private final long expires=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
    private final List<ObjectNode> rows=new ArrayList<>();
    private final Map<String,Integer> seen=new HashMap<>();
    private int bytes;
    private boolean complete;
    boolean expired(){return System.nanoTime()>=expires;}
    ObjectNode append(QuizStore store,int owner,JsonNode payload)throws SQLException {
        requireSession(payload);
        if(complete)throw fail("IMPORT_INVALID_STATE","Bản xem trước đã hoàn tất. Hãy kiểm tra lại tệp.");
        if(integer(payload,"offset",0,0,QuestionImportRules.MAX_ROWS)!=rows.size())throw fail("IMPORT_INVALID_STATE","Thứ tự dữ liệu nhập không hợp lệ. Hãy kiểm tra lại tệp.");
        JsonNode incoming=payload.path("rows");
        if(!incoming.isArray()||incoming.isEmpty()||incoming.size()>QuestionImportRules.MAX_BATCH||rows.size()+incoming.size()>QuestionImportRules.MAX_ROWS)throw fail("INVALID_INPUT","Mỗi tệp tối đa 500 câu, mỗi phần tối đa 20 câu.");
        int addedBytes=incoming.toString().getBytes(StandardCharsets.UTF_8).length;
        if(bytes+addedBytes>QuestionImportRules.MAX_JSON_BYTES)throw fail("INVALID_INPUT","Nội dung câu hỏi vượt giới hạn 2 MiB. Hãy chia nhỏ tệp.");
        boolean finished=bool(payload,"complete",false);var items=array();var prepared=new ArrayList<ObjectNode>();var pendingSeen=new HashMap<>(seen);
        for(var incomingRow:incoming) {
            if(!incomingRow.isObject())throw fail("INVALID_INPUT","Dòng câu hỏi không hợp lệ.");
            int index=rows.size()+prepared.size();var row=QuestionImportRules.normalize(incomingRow);var errors=QuestionImportRules.errors(incomingRow);
            var item=obj().put("index",index).put("valid",errors.isEmpty()).put("duplicate_in_bank",false);
            var issues=item.putArray("errors");errors.forEach(issues::add);
            if(errors.isEmpty()) {
                String key=TextNormalizer.key(row.path("content").asText());Integer first=pendingSeen.putIfAbsent(key,index);
                if(first!=null)item.put("duplicate_in_file",first);
                item.put("duplicate_in_bank",store.duplicates(owner,row.path("content").asText(),0).path("has_duplicate").asBoolean());
            }
            items.add(item);prepared.add(row);
        }
        rows.addAll(prepared);seen.clear();seen.putAll(pendingSeen);bytes+=addedBytes;complete=finished;
        return obj().put("import_id",id).put("total",rows.size()).put("complete",complete).set("items",items);
    }
    private void requireSession(JsonNode payload) {
        if(expired())throw fail("IMPORT_EXPIRED","Bản xem trước đã hết hạn. Hãy kiểm tra lại tệp.");
        if((!rows.isEmpty()||payload.has("import_id"))&&!id.equals(payload.path("import_id").asText()))throw fail("IMPORT_INVALID_STATE","Bản xem trước không thuộc kết nối này.");
    }
    ObjectNode commit(QuizStore store,int owner,JsonNode payload)throws SQLException {
        requireSession(payload);
        if(!complete)throw fail("IMPORT_INVALID_STATE","Chưa nhận đủ dữ liệu xem trước.");
        var indexes=payload.path("selected_indexes");
        if(!indexes.isArray()||indexes.isEmpty()||indexes.size()>QuestionImportRules.MAX_ROWS)throw fail("INVALID_INPUT","Chọn ít nhất một câu để nhập.");
        var selected=new ArrayList<JsonNode>();var used=new HashSet<Integer>();
        for(var index:indexes) {
            if(!index.isIntegralNumber()||!index.canConvertToInt()||index.asInt()<0||index.asInt()>=rows.size()||!used.add(index.asInt()))throw fail("INVALID_INPUT","Danh sách câu được chọn không hợp lệ.");
            var row=rows.get(index.asInt());if(!QuestionImportRules.errors(row).isEmpty())throw fail("INVALID_INPUT","Có câu được chọn chưa hợp lệ. Chưa lưu dữ liệu.");selected.add(row);
        }
        var result=store.importQuestions(owner,selected,bool(payload,"allow_duplicates",false));
        return result.put("skipped",rows.size()-selected.size());
    }
}
