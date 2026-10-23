package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;
import static org.junit.jupiter.api.Assertions.*;

/** Fixture/UI tests only, not authentication, database or physical LAN evidence. */
class PreviewGatewayTest {
    @Test void avatarSelectionUsesRoomOwnershipNotClientPlayerId() throws Exception {
        try (var g = new PreviewGateway("STUDENT")) {
            var before = get(g, "ROOM_STATE", object());
            String occupied = before.path("players").get(1).path("avatar_id").asText();
            assertEquals("AVATAR_TAKEN", error(g,"AVATAR_SET",object().put("avatar_id",occupied).put("player_id","p2")).code());
            assertEquals(before, get(g,"ROOM_STATE",object()));
            var after = get(g,"AVATAR_SET",object().put("avatar_id","quizling-8"));
            assertEquals("quizling-8",after.path("players").get(0).path("avatar_id").asText());
            get(g,"READY_SET",object().put("ready",true));
            assertEquals("ALREADY_READY",error(g,"AVATAR_SET",object().put("avatar_id","quizling-9")).code());
            get(g,"READY_SET",object().put("ready",false));
            get(g,"LEAVE",object()); assertEquals(2,get(g,"ROOM_STATE",object()).path("players").size());
        }
    }
    @Test void everyRoundPublishesRankingBeforeNextQuestionOrFinal() throws Exception {
        try (var g = new PreviewGateway("TEACHER")) {
            var events = new LinkedBlockingQueue<JsonNode>(); g.onEvent(events::add);
            get(g,"ROOM_CONFIGURE",object().put("question_count",2).put("duration_seconds",5)); get(g,"START_GAME",object());
            var sequence = new java.util.ArrayList<String>(); int rankings = 0;
            long stop = System.nanoTime()+TimeUnit.SECONDS.toNanos(34);
            while (System.nanoTime()<stop) {
                var e = events.poll(1,TimeUnit.SECONDS); if (e==null) continue;
                String type=e.path("type").asText(); if (java.util.List.of("QUESTION","QUESTION_RESULT","ROUND_RANKING","GAME_OVER").contains(type)) sequence.add(type);
                if (type.equals("ROUND_RANKING")) { rankings++; assertEquals(rankings,e.path("payload").path("number").asInt()); assertEquals(3,e.path("payload").path("ranking").size()); }
                if (type.equals("GAME_OVER")) break;
            }
            assertEquals(java.util.List.of("QUESTION","QUESTION_RESULT","ROUND_RANKING","QUESTION","QUESTION_RESULT","ROUND_RANKING","GAME_OVER"),sequence);
        }
    }
    @Test void closedRoomReleasesAllAvatarsAndRejectsJoin() throws Exception {
        try (var g = new PreviewGateway("TEACHER")) {
            assertEquals("FORBIDDEN", error(g,"AVATAR_SET",object().put("avatar_id","quizling-9")).code());
            var closed = get(g,"ROOM_CLOSE",object());
            assertEquals(0,closed.path("players").size()); assertEquals(0,closed.path("ready_count").asInt());
            assertEquals("WRONG_ROOM",error(g,"JOIN",object().put("room_code","QZ7K2").put("nickname","Test")).code());
            get(g,"ROOM_CONFIGURE",object());
            assertEquals(3,get(g,"ROOM_STATE",object()).path("players").size());
        }
    }
    private JsonNode get(QuizGateway gateway, String type, ObjectNode p) throws Exception { return gateway.request(type, p).get(2, TimeUnit.SECONDS); }
    private GatewayException error(QuizGateway g, String type, ObjectNode p) {
        var failure = assertThrows(ExecutionException.class, () -> get(g, type, p));
        return assertInstanceOf(GatewayException.class, failure.getCause());
    }
    @Test void filtersAreAndAndPagesDoNotExposeAnswers() throws Exception {
        try (var g = new PreviewGateway("TEACHER")) {
            var all = get(g, "QUESTION_LIST", object().put("page_size", 10).put("sort", "ID_ASC"));
            var second = get(g, "QUESTION_LIST", object().put("page", 2).put("page_size", 10).put("sort", "ID_ASC"));
            assertEquals(20, all.path("total").asInt()); assertEquals(1, all.path("items").get(0).path("question_id").asInt());
            assertEquals(11, second.path("items").get(0).path("question_id").asInt()); assertFalse(second.path("has_more").asBoolean());
            assertFalse(all.path("items").get(0).has("correct_answer"));
            var filtered = get(g, "QUESTION_LIST", object().put("keyword", "  lan  ").put("category", "TCP/IP").put("is_active", false));
            assertEquals(1, filtered.path("total").asInt()); assertEquals(18, filtered.path("items").get(0).path("question_id").asInt());
        }
    }
    @Test void duplicateIsNotSavedUntilExplicitConfirmationEvenWhenInactive() throws Exception {
        try (var g = new PreviewGateway("TEACHER")) {
            var q = (ObjectNode)get(g, "QUESTION_GET", object().put("question_id", 18)); q.remove("question_id");
            q.put("content", "  " + q.path("content").asText().toUpperCase(java.util.Locale.ROOT) + "  "); q.put("category", "Chủ đề khác");
            assertEquals("DUPLICATE_QUESTION", error(g, "QUESTION_CREATE", q).code());
            assertEquals(20, get(g, "QUESTION_LIST", object()).path("total").asInt());
            assertEquals(21, get(g, "QUESTION_CREATE", q.put("allow_duplicate", true)).path("question_id").asInt());
            assertEquals(21, get(g, "QUESTION_LIST", object()).path("total").asInt());
        }
    }
    @Test void editExcludesItsOwnIdAndFixtureInstancesAreIsolated() throws Exception {
        try (var a = new PreviewGateway("TEACHER"); var b = new PreviewGateway("TEACHER")) {
            var q = (ObjectNode)get(a, "QUESTION_GET", object().put("question_id", 1)); q.put("category", "Mới");
            assertEquals(1, get(a, "QUESTION_UPDATE", q).path("question_id").asInt());
            assertEquals("TCP/IP", get(b, "QUESTION_GET", object().put("question_id", 1)).path("category").asText());
            assertFalse(get(a, "QUESTION_CHECK_DUPLICATE", object().put("content", q.path("content").asText()).put("exclude_question_id", 1)).path("has_duplicate").asBoolean());
        }
    }
    @Test void fixtureHonorsUiRoleBoundaries() {
        try (var student = new PreviewGateway("STUDENT"); var teacher = new PreviewGateway("TEACHER"); var admin = new PreviewGateway("ADMIN")) {
            assertEquals("FORBIDDEN", error(student, "QUESTION_LIST", object()).code());
            assertEquals("FORBIDDEN", error(teacher, "USER_LIST", object()).code());
            assertEquals("FORBIDDEN", error(admin, "USER_SET_ACTIVE", object().put("user_id", 1).put("is_active", false)).code());
        }
    }
    @Test void fixtureDoesNotReturnOrStoreTemporaryPassword() throws Exception {
        try (var g = new PreviewGateway("ADMIN")) {
            var created = get(g, "USER_CREATE", object().put("username", "teacher.test").put("display_name", "Kiểm thử").put("temporary_password", "test-only-secret"));
            assertEquals("TEACHER", created.path("role").asText()); assertTrue(created.path("must_change_password").asBoolean());
            assertFalse(get(g, "USER_LIST", object()).toString().contains("test-only-secret")); assertFalse(created.has("temporary_password"));
        }
    }
    @Test void invalidConfigurationDoesNotAlterPreviousRoom() throws Exception {
        try (var g = new PreviewGateway("TEACHER")) {
            var before = get(g, "ROOM_STATE", object());
            assertEquals("INVALID_CONFIG", error(g, "ROOM_CONFIGURE", object().put("question_count", 0).put("duration_seconds", 1)).code());
            assertEquals(before, get(g, "ROOM_STATE", object()));
        }
    }
    @Test void simulatedRoundConcealsAnswerAndAckDoesNotScoreBeforeDeadline() throws Exception {
        try (var g = new PreviewGateway("TEACHER")) {
            var events = new LinkedBlockingQueue<JsonNode>(); g.onEvent(events::add);
            get(g, "ROOM_CONFIGURE", object().put("question_count", 1).put("duration_seconds", 5)); get(g, "START_GAME", object());
            JsonNode event; long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            do { event = events.poll(1, TimeUnit.SECONDS); } while (System.nanoTime() < stop && (event == null || !event.path("type").asText().equals("QUESTION")));
            assertNotNull(event); assertEquals("QUESTION", event.path("type").asText());
            var q = event.path("payload"); assertFalse(q.has("correct_answer"));
            var answer = object().put("session_id", q.path("session_id").asText()).put("question_id", q.path("question_id").asInt()).put("answer", "B");
            var ack = get(g, "ANSWER", answer); assertTrue(ack.path("accepted").asBoolean()); assertFalse(ack.has("correct")); assertFalse(ack.has("score"));
            assertFalse(get(g, "ANSWER", answer.put("answer", "C")).path("accepted").asBoolean());
            assertEquals("INVALID_STATE", error(g, "QUESTION_DELETE", object().put("question_id", 1)).code());
        }
    }
    @Test void closedPreviewNeverBecomesARealConnection() {
        var g = new PreviewGateway("TEACHER"); assertTrue(g.isPreview()); g.close(); assertTrue(g.request("PING", object()).isCompletedExceptionally());
    }
}
