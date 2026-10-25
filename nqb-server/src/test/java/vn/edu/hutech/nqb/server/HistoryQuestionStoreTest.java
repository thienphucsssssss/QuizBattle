package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

class HistoryQuestionStoreTest {
    @TempDir Path temp;
    private Path file(){return temp.resolve("history.db");}
    private int seed(QuizStore store)throws Exception {
        int owner=store.bootstrap("admin","Chủ phòng","History-test-only-1".toCharArray());
        for(int i=1;i<=2;i++)store.saveQuestion(owner,obj().put("content","Câu hỏi gốc "+i).put("option_a","TCP").put("option_b","UDP").put("option_c","IP").put("option_d","ARP").put("correct_answer","A").put("category","Mạng"),false);
        save(store,owner,"saved");return owner;
    }
    private void save(QuizStore store,int owner,String session)throws Exception {
        var ranking=array().add(obj().put("player_id","p1").put("nickname","Học sinh").put("avatar_id","quizling-1").put("score",177).put("correct_count",2).put("rank",1).put("connected",true));
        store.saveMatch(session,owner,"Phòng đã lưu",2,"2026-10-25T00:00:00Z",ranking,20,List.of(store.question(owner,1),store.question(owner,2)));
    }
    private void downgrade()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement()){
            sql.execute("DROP TABLE match_questions");sql.execute("ALTER TABLE matches DROP COLUMN duration_seconds");sql.execute("PRAGMA user_version=2");
        }
    }
    @Test void snapshotSurvivesBankEditsDeletesAndStoreRestart()throws Exception {
        int owner;try(var store=new QuizStore(file())){
            owner=seed(store);var edited=store.question(owner,1).deepCopy().put("content","Nội dung đã thay đổi").put("correct_answer","B");store.saveQuestion(owner,edited,true);
            store.mutateQuestion(owner,"QUESTION_DELETE",obj().put("question_id",2));
        }
        try(var store=new QuizStore(file())){
            var questions=store.historyQuestions(owner,"saved",2);assertEquals(2,questions.size());
            assertEquals("Câu hỏi gốc 1",questions.get(0).path("content").asText());assertEquals("Câu hỏi gốc 2",questions.get(1).path("content").asText());
            assertEquals("A",questions.get(0).path("correct_answer").asText());assertEquals("TCP",questions.get(1).path("option_a").asText());
            assertEquals(1,store.questions(owner,obj()).path("total").asInt());assertEquals("Nội dung đã thay đổi",store.question(owner,1).path("content").asText());
            var match=store.historyDetail(owner,"saved");assertTrue(match.path("reuse_available").asBoolean());assertEquals(20,match.path("duration_seconds").asInt());assertEquals(177,match.path("ranking").get(0).path("score").asInt());
        }
    }
    @Test void oldHistoryIsPreservedWithoutInventingQuestions()throws Exception {
        int owner;try(var store=new QuizStore(file())){owner=seed(store);}downgrade();
        try(var store=new QuizStore(file())){
            var match=store.historyDetail(owner,"saved");assertEquals(177,match.path("ranking").get(0).path("score").asInt());assertFalse(match.path("reuse_available").asBoolean());assertEquals(0,match.path("saved_question_count").asInt());
            assertEquals("HISTORY_QUESTIONS_UNAVAILABLE",assertThrows(Wire.Failure.class,()->store.historyQuestions(owner,"saved",1)).code);
            assertEquals(2,store.questions(owner,obj()).path("total").asInt());assertTrue(store.hasAdmin());
        }
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement();var rows=sql.executeQuery("PRAGMA user_version")){assertEquals(3,rows.getInt(1));}
    }
    @Test void privateIncompleteAndOversizedSourcesCannotBeUsed()throws Exception {
        try(var store=new QuizStore(file())){
            int owner=seed(store);int other=store.createUser("other","Người khác","TEACHER",PasswordHasher.hash("Other-test-only-1".toCharArray()),false).path("user_id").asInt();
            assertEquals("NOT_FOUND",assertThrows(Wire.Failure.class,()->store.historyQuestions(other,"saved",1)).code);
            assertEquals("NOT_ENOUGH_QUESTIONS",assertThrows(Wire.Failure.class,()->store.historyQuestions(owner,"saved",3)).code);
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement()){sql.execute("DELETE FROM match_questions WHERE position=1");}
            assertFalse(store.history(owner,obj()).path("items").get(0).path("reuse_available").asBoolean());
            assertEquals("HISTORY_QUESTIONS_UNAVAILABLE",assertThrows(Wire.Failure.class,()->store.historyQuestions(owner,"saved",1)).code);
        }
    }
    @Test void failedSnapshotWriteRollsBackMatchAndResultsTogether()throws Exception {
        try(var store=new QuizStore(file())){
            int owner=seed(store);
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement()){sql.execute("CREATE TRIGGER reject_snapshot BEFORE INSERT ON match_questions BEGIN SELECT RAISE(ABORT,'QA snapshot write failure'); END");}
            assertThrows(SQLException.class,()->save(store,owner,"failed"));assertEquals(1,store.history(owner,obj()).path("total").asInt());
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM match_results WHERE session_id='failed'")){assertEquals(0,rows.getInt(1));}
        }
    }
    @Test void failedMigrationRollsBackColumnsAndKeepsLegacyHistory()throws Exception {
        try(var store=new QuizStore(file())){seed(store);}downgrade();
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement()){sql.execute("CREATE TABLE match_questions(legacy_marker TEXT)");}
        assertThrows(SQLException.class,()->new QuizStore(file()));
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file());var sql=db.createStatement()){
            try(var rows=sql.executeQuery("PRAGMA user_version")){assertEquals(2,rows.getInt(1));}
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM pragma_table_info('matches') WHERE name='duration_seconds'")){assertEquals(0,rows.getInt(1));}
            try(var rows=sql.executeQuery("SELECT score FROM match_results WHERE session_id='saved'")){assertEquals(177,rows.getInt(1));}
        }
    }
}
