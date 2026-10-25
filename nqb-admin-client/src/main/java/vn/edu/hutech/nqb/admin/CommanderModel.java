package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.hutech.nqb.client.TextKey;
import java.text.Normalizer;
import java.util.*;

/** Presentation of authenticated room snapshots; never computes ranks or scores. */
final class CommanderModel {
    enum Filter { ALL, PENDING, OFFLINE, ONLINE, READY }
    record Counts(int total, int online, int ready, int answered) {
        int offline() { return total - online; }
        int pending() { return total - answered; }
        double progress(boolean waiting) { return total == 0 ? 0 : (double)(waiting ? ready : answered) / total; }
    }
    record Controls(boolean start, boolean abort, boolean projection) { }
    private CommanderModel() { }

    static Counts counts(JsonNode room) {
        int total = 0, online = 0, ready = 0, answered = 0;
        for (var player : room.path("players")) {
            total++;
            if (player.path("connected").asBoolean()) { online++; if (player.path("ready").asBoolean()) ready++; }
            if (player.path("answered").asBoolean()) answered++;
        }
        return new Counts(total, online, ready, answered);
    }
    static Controls controls(JsonNode room, String role, boolean connected) {
        boolean owner = connected && (role.equals("ADMIN") || role.equals("TEACHER"))
            && room.path("is_owner").asBoolean() && !room.path("room_code").asText().isBlank();
        var counts = counts(room);
        return new Controls(owner && waiting(room) && counts.total() > 0 && counts.ready() == counts.total(),
            owner && running(room.path("phase").asText()), owner);
    }
    static List<JsonNode> players(JsonNode room, Filter filter, String keyword, boolean ranked) {
        String key = searchKey(keyword);
        var values = new ArrayList<JsonNode>();
        for (var player : room.path("players")) {
            if (!key.isEmpty() && !searchKey(player.path("nickname").asText()).contains(key)) continue;
            boolean online = player.path("connected").asBoolean(), ready = online && player.path("ready").asBoolean();
            boolean include = switch (filter) {
                case ALL -> true;
                case ONLINE -> online;
                case OFFLINE -> !online;
                case READY -> waiting(room) && ready;
                case PENDING -> waiting(room) ? !ready : !player.path("answered").asBoolean();
            };
            if (include) values.add(player);
        }
        if (ranked) values.sort(Comparator.comparingInt((JsonNode p) -> rank(room, p)).thenComparing(p -> TextKey.of(p.path("nickname").asText())));
        return List.copyOf(values);
    }
    static int rank(JsonNode room, JsonNode player) {
        for (var entry : room.path("ranking"))
            if (entry.path("player_id").asText().equals(player.path("player_id").asText()) && entry.path("rank").asInt() > 0) return entry.path("rank").asInt();
        return Integer.MAX_VALUE;
    }
    static List<JsonNode> leaders(JsonNode room) {
        String phase = room.path("phase").asText();
        if (waiting(room) || phase.equals("COUNTDOWN") || (phase.equals("QUESTION_ACTIVE") && room.path("question_number").asInt() <= 1)) return List.of();
        var entries = new ArrayList<JsonNode>();
        for (var entry : room.path("ranking")) {
            int rank = entry.path("rank").asInt();
            if (rank >= 1 && rank <= 3) entries.add(entry);
        }
        entries.sort(Comparator.comparingInt(p -> p.path("rank").asInt()));
        return List.copyOf(entries);
    }
    static String status(JsonNode room, JsonNode player, boolean connected) {
        if (!connected) return "Chưa xác định · mất kết nối";
        if (!player.path("connected").asBoolean()) return "Mất kết nối";
        return switch (room.path("phase").asText("WAITING")) {
            case "WAITING" -> player.path("ready").asBoolean() ? "Sẵn sàng" : "Đang chuẩn bị";
            case "COUNTDOWN" -> "Sắp bắt đầu";
            case "QUESTION_ACTIVE" -> player.path("answered").asBoolean() ? "Đã nộp bài" : "Đang trả lời";
            case "FINISHED" -> "Đã kết thúc";
            default -> player.path("answered").asBoolean() ? "Đã nộp bài" : "Không nộp câu này";
        };
    }
    static boolean waiting(JsonNode room) { return room.path("phase").asText("WAITING").equals("WAITING"); }
    static boolean running(String phase) { return List.of("COUNTDOWN", "QUESTION_ACTIVE", "SHOW_RESULT", "SHOW_RANKING").contains(phase); }
    private static String searchKey(String value) { return Normalizer.normalize(TextKey.of(value), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd'); }
}
