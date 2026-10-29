package vn.edu.hutech.nqb.server;

import java.sql.*;
import java.time.Instant;

/** Combines local proximity schema v2 and Viet My's question-set v2/history v3 without version ambiguity. */
final class DatabaseSchema {
    static final int VERSION=4;
    private DatabaseSchema() { }
    static void migrate(Connection db) throws SQLException {
        try(var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=1000");s.execute("PRAGMA journal_mode=WAL");
            int version;try(var r=s.executeQuery("PRAGMA user_version")){version=r.getInt(1);}
            if(version<0||version>VERSION)throw new SQLException("Unsupported database version; back up and migrate explicitly");
            if(version==0)try(var r=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                if(r.getInt(1)>0)throw new SQLException("Unversioned database is not empty; refusing migration");
            }
            if(version<VERSION) {
                db.setAutoCommit(false);
                try {
                    if(version==0) {
                        s.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT NOT NULL UNIQUE,normalized_name TEXT NOT NULL,display_name TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('ADMIN','TEACHER')),salt TEXT NOT NULL,hash TEXT NOT NULL,iterations INTEGER NOT NULL,credential_version INTEGER NOT NULL DEFAULT 1,is_active INTEGER NOT NULL DEFAULT 1,must_change_password INTEGER NOT NULL DEFAULT 1,created_at_utc TEXT NOT NULL)");
                        s.execute("CREATE TABLE questions(id INTEGER PRIMARY KEY,owner_user_id INTEGER NOT NULL REFERENCES users(id),content TEXT NOT NULL,normalized_content TEXT NOT NULL,option_a TEXT NOT NULL,option_b TEXT NOT NULL,option_c TEXT NOT NULL,option_d TEXT NOT NULL,correct_answer TEXT NOT NULL CHECK(correct_answer IN ('A','B','C','D')),category TEXT NOT NULL,is_active INTEGER NOT NULL,created_at_utc TEXT NOT NULL,updated_at_utc TEXT NOT NULL)");
                        s.execute("CREATE INDEX questions_owner_content ON questions(owner_user_id,normalized_content)");
                        s.execute("CREATE TABLE matches(session_id TEXT PRIMARY KEY,host_user_id INTEGER NOT NULL REFERENCES users(id),room_name TEXT NOT NULL,question_count INTEGER NOT NULL,player_count INTEGER NOT NULL,started_at_utc TEXT NOT NULL,ended_at_utc TEXT NOT NULL)");
                        s.execute("CREATE TABLE match_results(session_id TEXT NOT NULL REFERENCES matches(session_id),player_id TEXT NOT NULL,nickname TEXT NOT NULL,avatar_id TEXT NOT NULL,score INTEGER NOT NULL,correct_count INTEGER NOT NULL,rank INTEGER NOT NULL,connected INTEGER NOT NULL,PRIMARY KEY(session_id,player_id))");
                    }
                    // Inspect the actual schema: both branches assigned version 2 to different tables.
                    s.execute("CREATE TABLE IF NOT EXISTS question_sets(id INTEGER PRIMARY KEY,owner_user_id INTEGER NOT NULL REFERENCES users(id),name TEXT NOT NULL,normalized_name TEXT NOT NULL,created_at_utc TEXT NOT NULL,UNIQUE(owner_user_id,normalized_name))");
                    s.execute("CREATE TABLE IF NOT EXISTS question_set_items(set_id INTEGER NOT NULL REFERENCES question_sets(id) ON DELETE CASCADE,question_id INTEGER NOT NULL REFERENCES questions(id) ON DELETE CASCADE,PRIMARY KEY(set_id,question_id))");
                    s.execute("CREATE INDEX IF NOT EXISTS question_set_items_question ON question_set_items(question_id)");
                    if(!hasColumn(db,"matches","duration_seconds"))s.execute("ALTER TABLE matches ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 15");
                    s.execute("CREATE TABLE IF NOT EXISTS match_questions(session_id TEXT NOT NULL REFERENCES matches(session_id) ON DELETE CASCADE,position INTEGER NOT NULL,question_json TEXT NOT NULL,PRIMARY KEY(session_id,position))");
                    s.execute("CREATE TABLE IF NOT EXISTS proximity_sessions(session_id TEXT PRIMARY KEY,host_user_id INTEGER NOT NULL REFERENCES users(id),room_name TEXT NOT NULL,room_code TEXT NOT NULL,proximity_enabled INTEGER NOT NULL,anchor_ssid TEXT NOT NULL,anchor_bssid TEXT NOT NULL,anchor_band TEXT NOT NULL,threshold_meters REAL NOT NULL,status TEXT NOT NULL CHECK(status IN ('RUNNING','COMPLETED','ABORTED','INTERRUPTED')),started_at_utc TEXT NOT NULL,updated_at_utc TEXT NOT NULL,ended_at_utc TEXT)");
                    s.execute("CREATE TABLE IF NOT EXISTS proximity_player_summaries(session_id TEXT NOT NULL REFERENCES proximity_sessions(session_id),player_id TEXT NOT NULL,nickname TEXT NOT NULL,avatar_id TEXT NOT NULL,calibrated INTEGER NOT NULL,adapter_name TEXT NOT NULL,path_loss_n REAL,min_distance_m REAL,max_distance_m REAL,last_distance_m REAL,last_status TEXT NOT NULL,missing_data_ms INTEGER NOT NULL DEFAULT 0,over_threshold_count INTEGER NOT NULL DEFAULT 0,alert_count INTEGER NOT NULL DEFAULT 0,updated_at_utc TEXT NOT NULL,PRIMARY KEY(session_id,player_id))");
                    s.execute("CREATE TABLE IF NOT EXISTS proximity_alert_events(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT NOT NULL REFERENCES proximity_sessions(session_id),player_id TEXT NOT NULL,nickname TEXT NOT NULL,event_type TEXT NOT NULL,distance_m REAL,rssi_dbm INTEGER,detail TEXT NOT NULL,created_at_utc TEXT NOT NULL)");
                    s.execute("CREATE INDEX IF NOT EXISTS proximity_sessions_host_time ON proximity_sessions(host_user_id,started_at_utc DESC)");
                    s.execute("CREATE INDEX IF NOT EXISTS proximity_alerts_session ON proximity_alert_events(session_id,id)");
                    // IF NOT EXISTS must not silently accept a table with an incompatible layout.
                    s.executeQuery("SELECT session_id,position,question_json FROM match_questions LIMIT 0").close();
                    s.executeQuery("SELECT id,owner_user_id,name,normalized_name,created_at_utc FROM question_sets LIMIT 0").close();
                    s.executeQuery("SELECT set_id,question_id FROM question_set_items LIMIT 0").close();
                    s.execute("PRAGMA user_version="+VERSION);db.commit();
                } catch(SQLException|RuntimeException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
            }
            try(var recover=db.prepareStatement("UPDATE proximity_sessions SET status='INTERRUPTED',ended_at_utc=coalesce(ended_at_utc,updated_at_utc),updated_at_utc=? WHERE status='RUNNING'")) {
                recover.setString(1,Instant.now().toString());recover.executeUpdate();
            }
        }
    }
    private static boolean hasColumn(Connection db,String table,String name) throws SQLException {
        try(var s=db.createStatement();var r=s.executeQuery("PRAGMA table_info("+table+")")){while(r.next())if(name.equals(r.getString("name")))return true;}
        return false;
    }
}
