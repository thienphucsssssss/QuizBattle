package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

class QuestionSetStoreTest {
    @TempDir Path temp;
    private Path database(){return temp.resolve("quiz.db");}
    private int seed(QuizStore store)throws Exception {
        int owner=store.bootstrap("admin","Admin cũ","Set-store-test-1".toCharArray());
        for(int i=1;i<=4;i++)store.saveQuestion(owner,obj().put("content","Câu bộ đề "+i).put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A").put("category","Mạng"),false);
        var ranking=array();ranking.add(obj().put("player_id","p1").put("nickname","Học sinh cũ").put("avatar_id","quizling-1").put("score",123).put("correct_count",2).put("rank",1).put("connected",true));
        store.saveMatch("old-session",owner,"Phòng cũ",2,"2026-10-24T00:00:00Z",ranking,15,store.gameQuestions(owner,2));return owner;
    }
    private void downgrade(boolean collision)throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+database());var sql=db.createStatement()){
            sql.execute("DROP TABLE match_questions");sql.execute("ALTER TABLE matches DROP COLUMN duration_seconds");
            sql.execute("DROP TABLE question_set_items");sql.execute("DROP TABLE question_sets");sql.execute("PRAGMA user_version=1");
            if(collision)sql.execute("CREATE TABLE question_set_items(legacy_marker TEXT)");
        }
    }
    private int version()throws Exception {
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+database());var sql=db.createStatement();var rows=sql.executeQuery("PRAGMA user_version")){return rows.getInt(1);}
    }
    @Test void versionOneUpgradeKeepsAccountsQuestionsAndHistory()throws Exception {
        int owner;try(var store=new QuizStore(database())){owner=seed(store);}downgrade(false);
        try(var store=new QuizStore(database())){
            assertTrue(PasswordHasher.verify("Set-store-test-1".toCharArray(),store.account("admin").hash()));
            assertEquals(4,store.questions(owner,obj()).path("total").asInt());
            assertEquals(123,store.historyDetail(owner,"old-session").path("ranking").get(0).path("score").asInt());
            var p=obj().put("name","Bộ được lưu");p.putArray("question_ids").add(1).add(2);store.createQuestionSet(owner,p);
        }
        assertEquals(5,version());
        try(var store=new QuizStore(database())){assertEquals(1,store.questionSets(owner,obj()).path("total").asInt());assertEquals(2,store.questionSets(owner,obj()).path("items").get(0).path("question_count").asInt());}
    }
    @Test void failedMigrationRollsBackNewTablesAndVersion()throws Exception {
        try(var store=new QuizStore(database())){seed(store);}downgrade(true);
        assertThrows(SQLException.class,()->new QuizStore(database()));assertEquals(1,version());
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+database());var sql=db.createStatement()){
            try(var r=sql.executeQuery("SELECT COUNT(*) FROM users")){assertEquals(1,r.getInt(1));}
            try(var r=sql.executeQuery("SELECT COUNT(*) FROM questions")){assertEquals(4,r.getInt(1));}
            try(var r=sql.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='question_sets'")){assertEquals(0,r.getInt(1));}
        }
    }
    @Test void selectedPoolNeverBorrowsFromOtherQuestions()throws Exception {
        try(var store=new QuizStore(database())){
            int owner=seed(store);var p=obj().put("name","Chỉ câu 1 và 2");p.putArray("question_ids").add(1).add(2);
            int id=store.createQuestionSet(owner,p).path("question_set_id").asInt();
            var error=assertThrows(Wire.Failure.class,()->store.gameQuestions(owner,3,id));assertEquals("NOT_ENOUGH_QUESTIONS",error.code);
            var chosen=store.gameQuestions(owner,2,id);assertEquals(Set.of(1,2),new HashSet<>(chosen.stream().map(q->q.path("question_id").asInt()).toList()));
            store.mutateQuestion(owner,"QUESTION_SET_ACTIVE",obj().put("question_id",2).put("is_active",false));
            assertEquals(1,store.gameQuestions(owner,1,id).get(0).path("question_id").asInt());
            assertThrows(Wire.Failure.class,()->store.gameQuestions(owner,2,id));
            store.deleteQuestionSet(owner,id);assertEquals(4,store.questions(owner,obj()).path("total").asInt());
        }
    }
    @Test void futureDatabaseVersionIsRejected()throws Exception {
        try(var store=new QuizStore(database())){seed(store);}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+database());var sql=db.createStatement()){sql.execute("PRAGMA user_version=99");}
        assertThrows(SQLException.class,()->new QuizStore(database()));assertEquals(99,version());
    }
    @Test void filteredSetIncludesQuestionsBeyondTheFirstPage()throws Exception {
        try(var store=new QuizStore(database())){
            int owner=seed(store);
            for(int i=1;i<=25;i++)store.saveQuestion(owner,obj().put("content","Filtered question "+i).put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A").put("category","Bộ lọc"),false);
            var p=obj().put("name","Toàn bộ 25 câu");p.putObject("filters").put("keyword","Filtered question").put("page",1).put("page_size",10);
            assertEquals(25,store.createQuestionSet(owner,p).path("question_count").asInt());
        }
    }
}
