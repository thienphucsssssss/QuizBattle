package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import vn.edu.hutech.nqb.shared.Protocol;

final class Wire {
    static final ObjectMapper JSON = new ObjectMapper();
    static ObjectNode obj() { return JSON.createObjectNode(); }
    static ArrayNode array() { return JSON.createArrayNode(); }
    static ObjectNode envelope(String type, String requestId, JsonNode payload) {
        var e = obj().put("protocol_version", Protocol.VERSION).put("type", type);
        if (requestId != null) e.put("request_id", requestId);
        return e.set("payload", payload);
    }
    static String text(JsonNode p, String key, int max) {
        if (!p.path(key).isTextual()) throw fail("INVALID_INPUT", "Thiếu hoặc sai kiểu trường " + key + ".");
        String s = p.path(key).asText().strip();
        if (s.isEmpty() || s.length() > max || s.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) throw fail("INVALID_INPUT", "Trường " + key + " không hợp lệ (tối đa " + max + " ký tự).");
        return s;
    }
    static String optional(JsonNode p, String key, String fallback, int max) { return p.hasNonNull(key) ? (p.path(key).asText().isEmpty() && p.path(key).isTextual() ? "" : text(p,key,max)) : fallback; }
    static int integer(JsonNode p, String key, int fallback, int min, int max) {
        if (!p.has(key)) return fallback;
        var n = p.path(key);
        if (!n.isIntegralNumber() || !n.canConvertToInt() || n.asInt() < min || n.asInt() > max) throw fail("INVALID_INPUT", "Giá trị " + key + " không hợp lệ.");
        return n.asInt();
    }
    static boolean bool(JsonNode p, String key, boolean fallback) {
        if (!p.has(key)) return fallback;
        if (!p.path(key).isBoolean()) throw fail("INVALID_INPUT", "Trường " + key + " phải là true/false.");
        return p.path(key).asBoolean();
    }
    static String password(JsonNode p, String key) {
        if (!p.path(key).isTextual() || p.path(key).asText().length() < 8 || p.path(key).asText().length() > 128) throw fail("INVALID_INPUT", "Mật khẩu cần 8–128 ký tự.");
        return p.path(key).asText();
    }
    static Failure fail(String code, String message) { return new Failure(code,message,obj()); }
    static final class Failure extends RuntimeException {
        final String code; final ObjectNode details;
        Failure(String code,String message,ObjectNode details) { super(message); this.code=code; this.details=details; }
        ObjectNode payload() { return details.deepCopy().put("code",code).put("message",getMessage()); }
    }
}
