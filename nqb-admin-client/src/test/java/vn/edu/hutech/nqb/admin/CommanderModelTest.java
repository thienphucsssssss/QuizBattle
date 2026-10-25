package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

class CommanderModelTest {
    private ObjectNode room(String phase) {
        var room = object().put("phase", phase).put("room_code", "ABC123").put("is_owner", true).put("question_number", 2);
        var players = room.putArray("players");
        players.addObject().put("player_id", "a").put("nickname", "Minh Ánh").put("connected", true).put("ready", true).put("answered", true);
        players.addObject().put("player_id", "b").put("nickname", "Quốc Bảo").put("connected", true).put("ready", false).put("answered", false);
        players.addObject().put("player_id", "c").put("nickname", "Lan Anh").put("connected", false).put("ready", true).put("answered", true);
        players.addObject().put("player_id", "d").put("nickname", "Đặng Long").put("connected", false).put("ready", false).put("answered", false);
        return room;
    }

    @Test void submissionsRetainDisconnectedParticipantsAndTheirRecordedAnswers() {
        var counts = CommanderModel.counts(room("QUESTION_ACTIVE"));
        assertEquals(4, counts.total()); assertEquals(2, counts.online());
        assertEquals(1, counts.ready()); assertEquals(2, counts.answered());
        assertEquals(2, counts.offline()); assertEquals(2, counts.pending());
        assertEquals(.5, counts.progress(false)); assertEquals(.25, counts.progress(true));
        assertEquals(0, CommanderModel.counts(object()).progress(false));
    }

    @Test void pendingFilterChangesFromReadinessToSubmissionAndSearchDoesNotMutateRoster() {
        var room = room("QUESTION_ACTIVE");
        assertEquals(List.of("b", "d"), CommanderModel.players(room, CommanderModel.Filter.PENDING, "", false).stream().map(p -> p.path("player_id").asText()).toList());
        room.put("phase", "WAITING");
        assertEquals(List.of("b", "c", "d"), CommanderModel.players(room, CommanderModel.Filter.PENDING, "", false).stream().map(p -> p.path("player_id").asText()).toList());
        assertEquals("b", CommanderModel.players(room, CommanderModel.Filter.ALL, "quoc bao", false).getFirst().path("player_id").asText());
        assertEquals("d", CommanderModel.players(room, CommanderModel.Filter.OFFLINE, "dang", false).getFirst().path("player_id").asText());
        assertEquals(4, room.path("players").size());
    }

    @Test void managementControlsRequireAnAuthenticatedOwnerAndEveryParticipantReadyOnline() {
        var room = room("WAITING");
        assertFalse(CommanderModel.controls(room, "ADMIN", true).start());
        room.withArray("players").removeAll();
        assertFalse(CommanderModel.controls(room, "TEACHER", true).start());
        room.withArray("players").addObject().put("connected", true).put("ready", true);
        assertTrue(CommanderModel.controls(room, "TEACHER", true).start());
        assertTrue(CommanderModel.controls(room, "ADMIN", true).projection());
        assertFalse(CommanderModel.controls(room, "STUDENT", true).projection());
        assertFalse(CommanderModel.controls(room, "ADMIN", false).start());
        room.put("is_owner", false);
        assertFalse(CommanderModel.controls(room, "ADMIN", true).projection());
        room.put("is_owner", true).put("phase", "QUESTION_ACTIVE");
        assertTrue(CommanderModel.controls(room, "TEACHER", true).abort());
        assertFalse(CommanderModel.controls(room, "TEACHER", true).start());
        assertFalse(CommanderModel.controls(room, "TEACHER", false).abort());
        room.put("phase", "FINISHED");
        assertFalse(CommanderModel.controls(room, "ADMIN", true).abort());
    }

    @Test void connectionLossDoesNotClaimCachedReadinessOrSubmissionAsLive() {
        var room = room("QUESTION_ACTIVE");
        assertTrue(CommanderModel.status(room, room.path("players").get(0), false).contains("Chưa xác định"));
        assertEquals("Mất kết nối", CommanderModel.status(room, room.path("players").get(2), true));
        assertEquals("Đã nộp bài", CommanderModel.status(room, room.path("players").get(0), true));
    }

    @Test void leadersAndTableUseEveryAuthoritativeTieWithoutInferringRankFromScore() {
        var room = room("SHOW_RANKING");
        var ranking = room.putArray("ranking");
        ranking.addObject().put("player_id", "b").put("rank", 1).put("score", 100);
        ranking.addObject().put("player_id", "a").put("rank", 1).put("score", 100);
        ranking.addObject().put("player_id", "c").put("rank", 3).put("score", 90);
        ranking.addObject().put("player_id", "d").put("rank", 4).put("score", 999);
        assertEquals(List.of(1, 1, 3), CommanderModel.leaders(room).stream().map(p -> p.path("rank").asInt()).toList());
        assertEquals(4, CommanderModel.rank(room, room.path("players").get(3)));
        assertEquals("d", CommanderModel.players(room, CommanderModel.Filter.OFFLINE, "", true).getLast().path("player_id").asText());
        for (int i=0;i<7;i++) ranking.addObject().put("player_id", "tied-"+i).put("rank", 1).put("score", 100);
        assertEquals(10, CommanderModel.leaders(room).size());
        room.put("phase", "WAITING"); assertTrue(CommanderModel.leaders(room).isEmpty());
        room.put("phase", "QUESTION_ACTIVE").put("question_number", 1); assertTrue(CommanderModel.leaders(room).isEmpty());
    }
}
