package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import vn.edu.hutech.nqb.shared.TextNormalizer;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static vn.edu.hutech.nqb.server.Wire.*;

/** JDBC connection is owned by the game worker after startup. Never used on auth threads. */
public final class QuizStore implements AutoCloseable {
    private final Connection db;
    record Account(int id,String username,String name,String role,boolean active,boolean change,int version,PasswordHasher.Hash hash) {
        ObjectNode publicData() { return obj().put("user_id",id).put("username",username).put("display_name",name).put("role",role).put("is_active",active).put("must_change_password",change); }
    }
    public QuizStore(Path file) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try (var s=db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=1000"); s.execute("PRAGMA journal_mode=WAL");
            int version; try(var r=s.executeQuery("PRAGMA user_version")) { version=r.getInt(1); }
            if(version!=0 && version!=1 && version!=2) throw new SQLException("Unsupported database version; back up and migrate explicitly");
            if(version==0) {
                try(var r=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) { if(r.getInt(1)>0) throw new SQLException("Unversioned database is not empty; refusing migration"); }
                db.setAutoCommit(false);
                try {
                    s.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT NOT NULL UNIQUE,normalized_name TEXT NOT NULL,display_name TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('ADMIN','TEACHER')),salt TEXT NOT NULL,hash TEXT NOT NULL,iterations INTEGER NOT NULL,credential_version INTEGER NOT NULL DEFAULT 1,is_active INTEGER NOT NULL DEFAULT 1,must_change_password INTEGER NOT NULL DEFAULT 1,created_at_utc TEXT NOT NULL)");
                    s.execute("CREATE TABLE questions(id INTEGER PRIMARY KEY,owner_user_id INTEGER NOT NULL REFERENCES users(id),content TEXT NOT NULL,normalized_content TEXT NOT NULL,option_a TEXT NOT NULL,option_b TEXT NOT NULL,option_c TEXT NOT NULL,option_d TEXT NOT NULL,correct_answer TEXT NOT NULL CHECK(correct_answer IN ('A','B','C','D')),category TEXT NOT NULL,is_active INTEGER NOT NULL,created_at_utc TEXT NOT NULL,updated_at_utc TEXT NOT NULL)");
                    s.execute("CREATE INDEX questions_owner_content ON questions(owner_user_id,normalized_content)");
                    s.execute("CREATE TABLE matches(session_id TEXT PRIMARY KEY,host_user_id INTEGER NOT NULL REFERENCES users(id),room_name TEXT NOT NULL,question_count INTEGER NOT NULL,player_count INTEGER NOT NULL,started_at_utc TEXT NOT NULL,ended_at_utc TEXT NOT NULL)");
                    s.execute("CREATE TABLE match_results(session_id TEXT NOT NULL REFERENCES matches(session_id),player_id TEXT NOT NULL,nickname TEXT NOT NULL,avatar_id TEXT NOT NULL,score INTEGER NOT NULL,correct_count INTEGER NOT NULL,rank INTEGER NOT NULL,connected INTEGER NOT NULL,PRIMARY KEY(session_id,player_id))");
                    s.execute("PRAGMA user_version=1"); db.commit();
                    version = 1;
                } catch(SQLException error) { db.rollback(); throw error; } finally { db.setAutoCommit(true); }
            }
            // --- CẬP NHẬT DATABASE LÊN VERSION 2: TẠO BẢNG LƯU CÂU HỎI VÁN THI ---
            if(version==1) {
                db.setAutoCommit(false);
                try {
                    s.execute("CREATE TABLE match_questions(session_id TEXT NOT NULL REFERENCES matches(session_id), question_id INTEGER NOT NULL REFERENCES questions(id), sort_order INTEGER NOT NULL, PRIMARY KEY(session_id, question_id))");
                    s.execute("PRAGMA user_version=2"); db.commit();
                } catch(SQLException error) { db.rollback(); throw error; } finally { db.setAutoCommit(true); }
            }
            // ----------------------------------------------------------------------
        } catch(Exception error) { db.close(); throw error; }
    }
    private PreparedStatement statement(String sql,Object... values) throws SQLException {
        var s=db.prepareStatement(sql); for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]); return s;
    }
    private int update(String sql,Object... values) throws SQLException { try(var s=statement(sql,values)) { return s.executeUpdate(); } }
    private int count(String sql,Object... values) throws SQLException { try(var s=statement(sql,values);var r=s.executeQuery()) { return r.next()?r.getInt(1):0; } }
    public boolean hasAdmin() throws SQLException { return count("SELECT count(*) FROM users WHERE role='ADMIN'")>0; }
    public int bootstrap(String username,String displayName,char[] password) throws SQLException {
        if(hasAdmin()) throw new IllegalStateException("Admin already initialized; no credentials were changed");
        return createUser(username,displayName,"ADMIN",PasswordHasher.hash(password),false).path("user_id").asInt();
    }
    Account account(String username) throws SQLException { return readAccount("SELECT * FROM users WHERE username=?",username.toLowerCase(Locale.ROOT)); }
    Account account(int id) throws SQLException { return readAccount("SELECT * FROM users WHERE id=?",id); }
    private Account readAccount(String sql,Object arg) throws SQLException {
        try(var s=statement(sql,arg);var r=s.executeQuery()) { return r.next()?new Account(r.getInt("id"),r.getString("username"),r.getString("display_name"),r.getString("role"),r.getBoolean("is_active"),r.getBoolean("must_change_password"),r.getInt("credential_version"),new PasswordHasher.Hash(r.getString("salt"),r.getString("hash"),r.getInt("iterations"))):null; }
    }
    ObjectNode createUser(String username,String name,String role,PasswordHasher.Hash hash,boolean change) throws SQLException {
        username=username.toLowerCase(Locale.ROOT);
        if(!username.matches("[a-z0-9_.-]{3,40}") || name.isBlank() || name.length()>80) throw fail("INVALID_INPUT","Tên đăng nhập cần 3–40 ký tự a–z, 0–9, _, ., -; tên hiển thị tối đa 80 ký tự.");
        if(account(username)!=null) throw fail("USERNAME_TAKEN","Tên đăng nhập đã được sử dụng.");
        update("INSERT INTO users(username,normalized_name,display_name,role,salt,hash,iterations,must_change_password,created_at_utc) VALUES(?,?,?,?,?,?,?,?,?)",username,TextNormalizer.key(username+" "+name),name,role,hash.salt(),hash.value(),hash.iterations(),change,Instant.now().toString());
        return account(username).publicData();
    }
    void password(int id,PasswordHasher.Hash hash,boolean temporary) throws SQLException { update("UPDATE users SET salt=?,hash=?,iterations=?,credential_version=credential_version+1,must_change_password=? WHERE id=?",hash.salt(),hash.value(),hash.iterations(),temporary,id); }
    ObjectNode editUser(String type,JsonNode p) throws SQLException {
        int id=integer(p,"user_id",0,1,Integer.MAX_VALUE); var a=account(id);
        if(a==null) throw fail("NOT_FOUND","Không tìm thấy tài khoản."); if(!a.role.equals("TEACHER")) throw fail("FORBIDDEN","Không sửa Admin bằng chức năng này.");
        if(type.equals("USER_UPDATE")) { String name=text(p,"display_name",80); update("UPDATE users SET display_name=?,normalized_name=? WHERE id=?",name,TextNormalizer.key(a.username+" "+name),id); }
        else update("UPDATE users SET is_active=?,credential_version=credential_version+1 WHERE id=?",bool(p,"is_active",false),id);
        return account(id).publicData();
    }
    ObjectNode users(JsonNode p) throws SQLException {
        String sql=" FROM users WHERE instr(normalized_name,?)>0"; List<Object> args=new ArrayList<>(List.of(TextNormalizer.key(optional(p,"keyword","",120))));
        if(p.hasNonNull("is_active")) {sql+=" AND is_active=?";args.add(bool(p,"is_active",true));}
        if(p.hasNonNull("role")) {String role=text(p,"role",10);if(!Set.of("ADMIN","TEACHER").contains(role))throw fail("INVALID_INPUT","Vai trò không hợp lệ.");sql+=" AND role=?";args.add(role);}
        int page=integer(p,"page",1,1,1_000_000),size=integer(p,"page_size",10,1,20),total=count("SELECT count(*)"+sql,args.toArray());
        args.add(size); args.add((page-1)*size);var items=array();
        try(var s=statement("SELECT id"+sql+" ORDER BY id LIMIT ? OFFSET ?",args.toArray());var r=s.executeQuery()) {while(r.next())items.add(account(r.getInt(1)).publicData());}
        return page(items,page,size,total);
    }
    private ObjectNode page(ArrayNode items,int page,int size,int total) {return obj().put("page",page).put("page_size",size).put("total",total).put("has_more",(long)page*size<total).set("items",items);}
    private ObjectNode question(ResultSet r,boolean summary) throws SQLException {
        String content=r.getString("content");var q=obj().put("question_id",r.getInt("id")).put("category",r.getString("category")).put("is_active",r.getBoolean("is_active")).put("updated_at_utc",r.getString("updated_at_utc"));
        if(summary) return q.put("content_preview",content.length()>110?content.substring(0,110)+"…":content);
        q.put("content",content).put("correct_answer",r.getString("correct_answer"));for(String a:List.of("a","b","c","d"))q.put("option_"+a,r.getString("option_"+a)); return q;
    }
    ObjectNode question(int owner,int id) throws SQLException {try(var s=statement("SELECT * FROM questions WHERE owner_user_id=? AND id=?",owner,id);var r=s.executeQuery()){if(!r.next())throw fail("NOT_FOUND","Không tìm thấy câu hỏi.");return question(r,false);}}
    ObjectNode questions(int owner,JsonNode p) throws SQLException {
        String sql=" FROM questions WHERE owner_user_id=? AND instr(normalized_content,?)>0";List<Object> args=new ArrayList<>(List.of(owner,TextNormalizer.key(optional(p,"keyword","",2000))));
        if(p.hasNonNull("category")){sql+=" AND category=?";args.add(text(p,"category",80));}
        if(p.hasNonNull("is_active")){sql+=" AND is_active=?";args.add(bool(p,"is_active",true));}
        String sort=optional(p,"sort","UPDATED_DESC",20); if(!Set.of("ID_ASC","UPDATED_DESC").contains(sort))throw fail("INVALID_INPUT","Cách sắp xếp không hợp lệ.");
        int page=integer(p,"page",1,1,1_000_000),size=integer(p,"page_size",10,1,20),total=count("SELECT count(*)"+sql,args.toArray());args.add(size);args.add((page-1)*size);var items=array();
        try(var s=statement("SELECT *"+sql+" ORDER BY "+(sort.equals("ID_ASC")?"id ASC":"updated_at_utc DESC,id DESC")+" LIMIT ? OFFSET ?",args.toArray());var r=s.executeQuery()){while(r.next())items.add(question(r,true));}return page(items,page,size,total);
    }
    ObjectNode categories(int owner) throws SQLException {var values=array();try(var s=statement("SELECT DISTINCT category FROM questions WHERE owner_user_id=? ORDER BY category",owner);var r=s.executeQuery()){while(r.next())values.add(r.getString(1));}return obj().set("categories",values);}
    ObjectNode duplicates(int owner,String content,int exclude) throws SQLException {
        if(exclude>0)question(owner,exclude); String key=TextNormalizer.key(content);var items=array();
        int total=count("SELECT count(*) FROM questions WHERE owner_user_id=? AND normalized_content=? AND id<>?",owner,key,exclude);
        try(var s=statement("SELECT * FROM questions WHERE owner_user_id=? AND normalized_content=? AND id<>? ORDER BY id LIMIT 5",owner,key,exclude);var r=s.executeQuery()){while(r.next())items.add(question(r,true));}
        return obj().put("has_duplicate",total>0).put("total_matches",total).set("candidates",items);
    }
    ObjectNode saveQuestion(int owner,JsonNode p,boolean edit) throws SQLException {
        int id=edit?integer(p,"question_id",0,1,Integer.MAX_VALUE):0; if(edit)question(owner,id);
        String content=text(p,"content",2000),category=text(p,"category",80),correct=text(p,"correct_answer",1);
        if(!correct.matches("[ABCD]"))throw fail("INVALID_INPUT","Chọn một đáp án A–D.");
        String a=text(p,"option_a",500),b=text(p,"option_b",500),c=text(p,"option_c",500),d=text(p,"option_d",500);boolean active=bool(p,"is_active",true);
        var duplicate=duplicates(owner,content,id);if(duplicate.path("has_duplicate").asBoolean()&&!bool(p,"allow_duplicate",false))throw new Failure("DUPLICATE_QUESTION","Câu hỏi đã tồn tại. Chưa lưu.",duplicate);
        String now=Instant.now().toString();
        if(edit)update("UPDATE questions SET content=?,normalized_content=?,option_a=?,option_b=?,option_c=?,option_d=?,correct_answer=?,category=?,is_active=?,updated_at_utc=? WHERE id=? AND owner_user_id=?",content,TextNormalizer.key(content),a,b,c,d,correct,category,active,now,id,owner);
        else {update("INSERT INTO questions(content,normalized_content,option_a,option_b,option_c,option_d,correct_answer,category,is_active,created_at_utc,updated_at_utc,owner_user_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",content,TextNormalizer.key(content),a,b,c,d,correct,category,active,now,now,owner);id=count("SELECT last_insert_rowid()");}
        return question(owner,id);
    }
    ObjectNode importQuestions(int owner,List<JsonNode> selected,boolean allowDuplicates)throws SQLException {
        if(selected.isEmpty()||selected.size()>500)throw fail("INVALID_INPUT","Số câu nhập không hợp lệ.");
        db.setAutoCommit(false);
        try {
            var ids=array();
            for(var source:selected){
                if(!vn.edu.hutech.nqb.shared.QuestionImportRules.errors(source).isEmpty())throw fail("INVALID_INPUT","Câu hỏi nhập không hợp lệ. Chưa lưu dữ liệu.");
                var row=vn.edu.hutech.nqb.shared.QuestionImportRules.normalize(source).put("allow_duplicate",allowDuplicates);
                ids.add(saveQuestion(owner,row,false).path("question_id").asInt());
            }
            db.commit();return obj().put("imported",ids.size()).set("question_ids",ids);
        }catch(SQLException|RuntimeException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    void mutateQuestion(int owner,String type,JsonNode p) throws SQLException {
        int id=integer(p,"question_id",0,1,Integer.MAX_VALUE);question(owner,id);
        if(type.equals("QUESTION_DELETE"))update("DELETE FROM questions WHERE id=? AND owner_user_id=?",id,owner);
        else update("UPDATE questions SET is_active=?,updated_at_utc=? WHERE id=? AND owner_user_id=?",bool(p,"is_active",true),Instant.now().toString(),id,owner);
    }
    List<ObjectNode> gameQuestions(int owner,int amount) throws SQLException {
        var values=new ArrayList<ObjectNode>();try(var s=statement("SELECT * FROM questions WHERE owner_user_id=? AND is_active=1 ORDER BY random() LIMIT ?",owner,amount);var r=s.executeQuery()){while(r.next())values.add(question(r,false));}
        if(values.size()!=amount)throw fail("NOT_ENOUGH_QUESTIONS","Ngân hàng chưa đủ câu đang bật.");return List.copyOf(values);
    }

    // --- CẬP NHẬT: Thêm hàm lấy danh sách câu hỏi của một ván cũ ---
    List<ObjectNode> matchQuestions(String session) throws SQLException {
        var values=new ArrayList<ObjectNode>();try(var s=statement("SELECT q.* FROM questions q JOIN match_questions mq ON q.id=mq.question_id WHERE mq.session_id=? ORDER BY mq.sort_order",session);var r=s.executeQuery()){while(r.next())values.add(question(r,false));}
        return values;
    }

    // --- CẬP NHẬT: Lưu kèm danh sách câu hỏi `questions` vào CSDL ---
    void saveMatch(String session,int owner,String roomName,int questionCount,String started,ArrayNode ranking, List<ObjectNode> questions) throws SQLException {
        db.setAutoCommit(false);
        try {
            update("INSERT INTO matches VALUES(?,?,?,?,?,?,?)",session,owner,roomName,questionCount,ranking.size(),started,Instant.now().toString());
            for(var p:ranking)update("INSERT INTO match_results VALUES(?,?,?,?,?,?,?,?)",session,p.path("player_id").asText(),p.path("nickname").asText(),p.path("avatar_id").asText(),p.path("score").asInt(),p.path("correct_count").asInt(),p.path("rank").asInt(),p.path("connected").asBoolean());
            for(int i=0;i<questions.size();i++)update("INSERT INTO match_questions(session_id,question_id,sort_order) VALUES(?,?,?)",session,questions.get(i).path("question_id").asInt(),i);
            db.commit();
        }catch(SQLException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    
    ObjectNode history(int owner,JsonNode p) throws SQLException {
        int page=integer(p,"page",1,1,1_000_000),size=integer(p,"page_size",10,1,20);var items=array();
        try(var s=statement("SELECT * FROM matches WHERE host_user_id=? ORDER BY ended_at_utc DESC,session_id LIMIT ? OFFSET ?",owner,size,(page-1)*size);var r=s.executeQuery()){while(r.next())items.add(obj().put("session_id",r.getString("session_id")).put("room_name",r.getString("room_name")).put("question_count",r.getInt("question_count")).put("player_count",r.getInt("player_count")).put("ended_at_utc",r.getString("ended_at_utc")));}
        return page(items,page,size,count("SELECT count(*) FROM matches WHERE host_user_id=?",owner));
    }
    
    // --- CẬP NHẬT: Trả về kèm danh sách câu hỏi trong lịch sử ---
    ObjectNode historyDetail(int owner,String session) throws SQLException {
        String name;try(var s=statement("SELECT room_name FROM matches WHERE session_id=? AND host_user_id=?",session,owner);var r=s.executeQuery()){if(!r.next())throw fail("NOT_FOUND","Không tìm thấy kết quả.");name=r.getString(1);}
        var ranks=array();try(var s=statement("SELECT * FROM match_results WHERE session_id=? ORDER BY rank,player_id",session);var r=s.executeQuery()){while(r.next())ranks.add(obj().put("player_id",r.getString("player_id")).put("nickname",r.getString("nickname")).put("avatar_id",r.getString("avatar_id")).put("score",r.getInt("score")).put("correct_count",r.getInt("correct_count")).put("rank",r.getInt("rank")).put("connected",r.getBoolean("connected")));}
        var qs=array();for(var q:matchQuestions(session))qs.add(q);
        return obj().put("session_id",session).put("room_name",name).set("ranking",ranks).set("questions",qs);
    }
    
    @Override public void close() throws SQLException { db.close(); }
}