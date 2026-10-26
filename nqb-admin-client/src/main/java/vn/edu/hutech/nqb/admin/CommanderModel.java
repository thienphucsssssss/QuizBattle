package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Presentation of authenticated room snapshots; never computes ranks or scores. */
final class CommanderModel {
    enum Filter { ALL, PENDING, OFFLINE }
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
    static List<JsonNode> players(JsonNode room, Filter filter) {
        var values = new ArrayList<JsonNode>();
        for (var player : room.path("players")) {
            boolean online = player.path("connected").asBoolean(), ready = online && player.path("ready").asBoolean();
            boolean include = switch (filter) {
                case ALL -> true;
                case OFFLINE -> !online;
                case PENDING -> waiting(room) ? !ready : !player.path("answered").asBoolean();
            };
            if (include) values.add(player);
        }
        return List.copyOf(values);
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
}
