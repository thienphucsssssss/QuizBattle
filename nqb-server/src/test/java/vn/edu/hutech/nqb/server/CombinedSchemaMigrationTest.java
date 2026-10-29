package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.server.Wire.*;

class CombinedSchemaMigrationTest {
    @TempDir Path temp;
    @Test void versionFourUpgradePreservesCoreAndProximityData()throws Exception {
        Path file=temp.resolve("v4.db");int owner;
        try(var store=new QuizStore(file)){
            owner=store.bootstrap("admin","Admin","Migration-test-only-1".toCharArray());
            store.saveQuestion(owner,obj().put("content","Giữ câu hỏi v4").put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A").put("category","LAN"),false);
        }
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()){
            for(String table:List.of("wifi_survey_upload_scans","wifi_survey_uploads","wifi_survey_observations","wifi_survey_scans","wifi_survey_runs","wifi_survey_points","wifi_survey_areas"))s.execute("DROP TABLE "+table);
            s.execute("INSERT INTO proximity_sessions VALUES('v4',"+owner+",'Phòng cũ','ABCDEF',1,'AP','aa:bb:cc:dd:ee:01','5 GHz',10,'COMPLETED','start','end','end')");s.execute("PRAGMA user_version=4");
        }
        try(var store=new QuizStore(file)){assertEquals(5,store.schemaVersion());assertTrue(PasswordHasher.verify("Migration-test-only-1".toCharArray(),store.account("admin").hash()));assertEquals("Giữ câu hỏi v4",store.question(owner,1).path("content").asText());}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()){
            try(var r=s.executeQuery("SELECT count(*) FROM proximity_sessions WHERE session_id='v4'")){assertEquals(1,r.getInt(1));}
            try(var r=s.executeQuery("SELECT count(*) FROM wifi_survey_areas")){assertEquals(0,r.getInt(1));}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(r.next());}
        }
    }
    @Test void bothVersionTwoLayoutsAndRemoteVersionThreeUpgradeWithoutLosingTheirData() throws Exception {
        for(String shape:List.of("local-v2","remote-v2","remote-v3")) {
            Path file=temp.resolve(shape+".db");int owner;
            try(var store=new QuizStore(file)) {
                owner=store.bootstrap("admin","Admin","Migration-test-only-1".toCharArray());
                store.saveQuestion(owner,obj().put("content","Câu cũ").put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A").put("category","LAN"),false);
                var set=obj().put("name","Bộ cũ");set.putArray("question_ids").add(1);store.createQuestionSet(owner,set);
                var ranks=array();ranks.add(obj().put("player_id","p").put("nickname","Cũ").put("avatar_id","quizling-1").put("score",100).put("correct_count",1).put("rank",1).put("connected",true));
                store.saveMatch("saved",owner,"Phòng cũ",1,"2026-10-01T00:00:00Z",ranks,15,List.of(store.question(owner,1)));
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                if(shape.equals("local-v2")) {
                    s.execute("DROP TABLE question_set_items");s.execute("DROP TABLE question_sets");
                    s.execute("INSERT INTO proximity_sessions VALUES('prox',"+owner+",'Phòng cũ','ABCDEF',1,'AP','aa:bb:cc:dd:ee:01','5 GHz',10,'COMPLETED','start','end','end')");
                } else {s.execute("DROP TABLE proximity_alert_events");s.execute("DROP TABLE proximity_player_summaries");s.execute("DROP TABLE proximity_sessions");}
                if(!shape.equals("remote-v3")){s.execute("DROP TABLE match_questions");s.execute("ALTER TABLE matches DROP COLUMN duration_seconds");}
                s.execute("PRAGMA user_version="+(shape.equals("remote-v3")?3:2));
            }
            try(var store=new QuizStore(file)) {
                assertEquals(5,store.schemaVersion());assertTrue(PasswordHasher.verify("Migration-test-only-1".toCharArray(),store.account("admin").hash()));
                assertEquals("Câu cũ",store.question(owner,1).path("content").asText());
                assertEquals(100,store.historyDetail(owner,"saved").path("ranking").get(0).path("score").asInt());
                assertEquals(shape.equals("local-v2")?0:1,store.questionSets(owner,obj()).path("total").asInt());
                assertEquals(shape.equals("remote-v3"),store.historyDetail(owner,"saved").path("reuse_available").asBoolean());
            }
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var s=db.createStatement()) {
                try(var rows=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(rows.next());}
                try(var rows=s.executeQuery("SELECT count(*) FROM proximity_sessions")){assertEquals(shape.equals("local-v2")?1:0,rows.getInt(1));}
            }
        }
    }
}
