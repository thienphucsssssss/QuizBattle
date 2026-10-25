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
            if(version<0 || version>3) throw new SQLException("Unsupported database version; back up and migrate explicitly");
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
                } catch(SQLException error) { db.rollback(); throw error; } finally { db.setAutoCommit(true); }
            }
            if(version<2) {
                db.setAutoCommit(false);
                try {
                    s.execute("CREATE TABLE question_sets(id INTEGER PRIMARY KEY,owner_user_id INTEGER NOT NULL REFERENCES users(id),name TEXT NOT NULL,normalized_name TEXT NOT NULL,created_at_utc TEXT NOT NULL,UNIQUE(owner_user_id,normalized_name))");
                    s.execute("CREATE TABLE question_set_items(set_id INTEGER NOT NULL REFERENCES question_sets(id) ON DELETE CASCADE,question_id INTEGER NOT NULL REFERENCES questions(id) ON DELETE CASCADE,PRIMARY KEY(set_id,question_id))");
                    s.execute("CREATE INDEX question_set_items_question ON question_set_items(question_id)");
                    s.execute("PRAGMA user_version=2");db.commit();
                }catch(SQLException error){db.rollback();throw error;}finally{db.setAutoCommit(true);}
            }
            if(version<3) {
                db.setAutoCommit(false);
                try {
                    s.execute("ALTER TABLE matches ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 15");
                    s.execute("CREATE TABLE match_questions(session_id TEXT NOT NULL REFERENCES matches(session_id) ON DELETE CASCADE,position INTEGER NOT NULL,question_json TEXT NOT NULL,PRIMARY KEY(session_id,position))");
                    s.execute("PRAGMA user_version=3");db.commit();
                }catch(SQLException error){db.rollback();throw error;}finally{db.setAutoCommit(true);}
            }
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
        List<Object> args=new ArrayList<>();String sql=questionFilter(owner,p,args);
        String sort=optional(p,"sort","UPDATED_DESC",20); if(!Set.of("ID_ASC","UPDATED_DESC").contains(sort))throw fail("INVALID_INPUT","Cách sắp xếp không hợp lệ.");
        int page=integer(p,"page",1,1,1_000_000),size=integer(p,"page_size",10,1,20),total=count("SELECT count(*)"+sql,args.toArray());args.add(size);args.add((page-1)*size);var items=array();
        try(var s=statement("SELECT *"+sql+" ORDER BY "+(sort.equals("ID_ASC")?"id ASC":"updated_at_utc DESC,id DESC")+" LIMIT ? OFFSET ?",args.toArray());var r=s.executeQuery()){while(r.next())items.add(question(r,true));}return page(items,page,size,total);
    }
    private String questionFilter(int owner,JsonNode p,List<Object> args)throws SQLException {
        args.add(owner);args.add(TextNormalizer.key(optional(p,"keyword","",2000)));
        String sql=" FROM questions WHERE owner_user_id=? AND instr(normalized_content,?)>0";
        if(p.hasNonNull("category")){sql+=" AND category=?";args.add(text(p,"category",80));}
        if(p.hasNonNull("is_active")){sql+=" AND is_active=?";args.add(bool(p,"is_active",true));}
        int set=integer(p,"question_set_id",0,0,Integer.MAX_VALUE);
        if(set>0){questionSet(owner,set);sql+=" AND id IN (SELECT question_id FROM question_set_items WHERE set_id=?)";args.add(set);}
        return sql;
    }
    private static final String SET_SELECT="SELECT s.id AS question_set_id,s.name,COUNT(q.id) AS question_count,COALESCE(SUM(CASE WHEN q.is_active=1 THEN 1 ELSE 0 END),0) AS active_count FROM question_sets s LEFT JOIN question_set_items i ON i.set_id=s.id LEFT JOIN questions q ON q.id=i.question_id";
    private ObjectNode questionSet(ResultSet r)throws SQLException {
        return obj().put("question_set_id",r.getInt("question_set_id")).put("name",r.getString("name")).put("question_count",r.getInt("question_count")).put("active_count",r.getInt("active_count"));
    }
    ObjectNode questionSet(int owner,int id)throws SQLException {
        try(var s=statement(SET_SELECT+" WHERE s.owner_user_id=? AND s.id=? GROUP BY s.id",owner,id);var r=s.executeQuery()){
            if(!r.next())throw fail("NOT_FOUND","Không tìm thấy bộ câu hỏi của bạn.");return questionSet(r);
        }
    }
    ObjectNode questionSets(int owner,JsonNode p)throws SQLException {
        int page=integer(p,"page",1,1,1_000_000),size=integer(p,"page_size",50,1,100);
        var items=array();
        try(var s=statement(SET_SELECT+" WHERE s.owner_user_id=? GROUP BY s.id ORDER BY s.normalized_name,s.id LIMIT ? OFFSET ?",owner,size,(page-1)*size);var r=s.executeQuery()){
            while(r.next())items.add(questionSet(r));
        }
        return page(items,page,size,count("SELECT COUNT(*) FROM question_sets WHERE owner_user_id=?",owner));
    }
    ObjectNode createQuestionSet(int owner,JsonNode p)throws SQLException {
        String name=text(p,"name",80);var ids=new ArrayList<Integer>();
        if(p.has("question_ids")){
            if(p.has("filters")||!p.path("question_ids").isArray()||p.path("question_ids").isEmpty()||p.path("question_ids").size()>500)throw fail("INVALID_INPUT","Chọn từ 1–500 câu hỏi để tạo bộ.");
            var seen=new HashSet<Integer>();
            for(var value:p.path("question_ids")){
                if(!value.isIntegralNumber()||!value.canConvertToInt()||value.asInt()<=0||!seen.add(value.asInt()))throw fail("INVALID_INPUT","Danh sách câu hỏi không hợp lệ.");
                question(owner,value.asInt());ids.add(value.asInt());
            }
        }else{
            if(!p.path("filters").isObject())throw fail("INVALID_INPUT","Chọn câu hỏi hoặc bộ lọc để tạo bộ.");
            List<Object> args=new ArrayList<>();String sql=questionFilter(owner,p.path("filters"),args);
            try(var s=statement("SELECT id"+sql+" ORDER BY id LIMIT 501",args.toArray());var r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}
            if(ids.isEmpty()||ids.size()>500)throw fail("INVALID_INPUT","Bộ lọc cần có từ 1–500 câu hỏi để tạo bộ.");
        }
        if(count("SELECT COUNT(*) FROM question_sets WHERE owner_user_id=? AND normalized_name=?",owner,TextNormalizer.key(name))>0)throw fail("QUESTION_SET_EXISTS","Tên bộ câu hỏi đã tồn tại. Chọn tên khác.");
        db.setAutoCommit(false);
        try{int id=attachQuestionSet(owner,name,ids);db.commit();return questionSet(owner,id);}catch(SQLException|RuntimeException error){db.rollback();throw error;}finally{db.setAutoCommit(true);}
    }
    private int attachQuestionSet(int owner,String name,List<Integer> ids)throws SQLException {
        int id=0;
        try(var s=statement("SELECT id FROM question_sets WHERE owner_user_id=? AND normalized_name=?",owner,TextNormalizer.key(name));var r=s.executeQuery()){if(r.next())id=r.getInt(1);}
        if(id==0){update("INSERT INTO question_sets(owner_user_id,name,normalized_name,created_at_utc) VALUES(?,?,?,?)",owner,name,TextNormalizer.key(name),Instant.now().toString());id=count("SELECT last_insert_rowid()");}
        if(count("SELECT COUNT(*) FROM question_set_items WHERE set_id=?",id)+ids.size()>500)throw fail("INVALID_INPUT","Mỗi bộ tối đa 500 câu. Hãy tạo bộ mới.");
        for(int questionId:ids)update("INSERT INTO question_set_items(set_id,question_id) VALUES(?,?)",id,questionId);
        return id;
    }
    void deleteQuestionSet(int owner,int id)throws SQLException {questionSet(owner,id);update("DELETE FROM question_sets WHERE owner_user_id=? AND id=?",owner,id);}
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
        return importQuestions(owner,selected,allowDuplicates,"");
    }
    ObjectNode importQuestions(int owner,List<JsonNode> selected,boolean allowDuplicates,String setName)throws SQLException {
        if(selected.isEmpty()||selected.size()>500)throw fail("INVALID_INPUT","Số câu nhập không hợp lệ.");
        db.setAutoCommit(false);
        try {
            var ids=array();
            for(var source:selected){
                if(!vn.edu.hutech.nqb.shared.QuestionImportRules.errors(source).isEmpty())throw fail("INVALID_INPUT","Câu hỏi nhập không hợp lệ. Chưa lưu dữ liệu.");
                var row=vn.edu.hutech.nqb.shared.QuestionImportRules.normalize(source).put("allow_duplicate",allowDuplicates);
                ids.add(saveQuestion(owner,row,false).path("question_id").asInt());
            }
            var result=obj().put("imported",ids.size());result.set("question_ids",ids);
            if(!setName.isBlank()){
                var values=new ArrayList<Integer>();for(var id:ids)values.add(id.asInt());
                int set=attachQuestionSet(owner,setName,values);result.put("question_set_id",set).put("question_set_name",questionSet(owner,set).path("name").asText());
            }
            db.commit();return result;
        }catch(SQLException|RuntimeException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    void mutateQuestion(int owner,String type,JsonNode p) throws SQLException {
        int id=integer(p,"question_id",0,1,Integer.MAX_VALUE);question(owner,id);
        if(type.equals("QUESTION_DELETE"))update("DELETE FROM questions WHERE id=? AND owner_user_id=?",id,owner);
        else update("UPDATE questions SET is_active=?,updated_at_utc=? WHERE id=? AND owner_user_id=?",bool(p,"is_active",true),Instant.now().toString(),id,owner);
    }
    List<ObjectNode> gameQuestions(int owner,int amount) throws SQLException {
        return gameQuestions(owner,amount,0);
    }
    List<ObjectNode> gameQuestions(int owner,int amount,int set) throws SQLException {
        if(set>0)questionSet(owner,set);
        var p=obj().put("is_active",true).put("question_set_id",set);List<Object> args=new ArrayList<>();String sql=questionFilter(owner,p,args);args.add(amount);
        var values=new ArrayList<ObjectNode>();try(var s=statement("SELECT *"+sql+" ORDER BY random() LIMIT ?",args.toArray());var r=s.executeQuery()){while(r.next())values.add(question(r,false));}
        if(values.size()!=amount)throw fail("NOT_ENOUGH_QUESTIONS",set>0?"Bộ câu hỏi đã chọn chưa đủ câu đang bật. Hãy bật thêm câu hoặc giảm số câu.":"Ngân hàng chưa đủ câu đang bật.");return List.copyOf(values);
    }
    void saveMatch(String session,int owner,String roomName,int questionCount,String started,ArrayNode ranking,int durationSeconds,List<ObjectNode> questions) throws SQLException {
        if(questions.size()!=questionCount)throw new SQLException("Match snapshot must contain every played question");
        db.setAutoCommit(false);
        try {
            update("INSERT INTO matches(session_id,host_user_id,room_name,question_count,player_count,started_at_utc,ended_at_utc,duration_seconds) VALUES(?,?,?,?,?,?,?,?)",session,owner,roomName,questionCount,ranking.size(),started,Instant.now().toString(),durationSeconds);
            for(var p:ranking)update("INSERT INTO match_results VALUES(?,?,?,?,?,?,?,?)",session,p.path("player_id").asText(),p.path("nickname").asText(),p.path("avatar_id").asText(),p.path("score").asInt(),p.path("correct_count").asInt(),p.path("rank").asInt(),p.path("connected").asBoolean());
            for(int i=0;i<questions.size();i++)update("INSERT INTO match_questions(session_id,position,question_json) VALUES(?,?,?)",session,i,questions.get(i).toString());
            db.commit();
        }catch(SQLException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
    }
    private static final String MATCH_SELECT="SELECT m.*,(SELECT COUNT(*) FROM match_questions q WHERE q.session_id=m.session_id) AS saved_question_count FROM matches m";
    private ObjectNode matchSummary(ResultSet r)throws SQLException {
        int saved=r.getInt("saved_question_count"),total=r.getInt("question_count");
        return obj().put("session_id",r.getString("session_id")).put("room_name",r.getString("room_name")).put("question_count",total).put("player_count",r.getInt("player_count")).put("ended_at_utc",r.getString("ended_at_utc")).put("duration_seconds",r.getInt("duration_seconds")).put("saved_question_count",saved).put("reuse_available",saved>0&&saved==total);
    }
    ObjectNode historyMatch(int owner,String session)throws SQLException {
        try(var s=statement(MATCH_SELECT+" WHERE m.session_id=? AND m.host_user_id=?",session,owner);var r=s.executeQuery()){
            if(!r.next())throw fail("NOT_FOUND","Không tìm thấy kết quả.");return matchSummary(r);
        }
    }
    List<ObjectNode> historyQuestions(int owner,String session,int amount)throws SQLException {
        var match=historyMatch(owner,session);
        if(!match.path("reuse_available").asBoolean())throw fail("HISTORY_QUESTIONS_UNAVAILABLE","Ván này chưa lưu bộ câu hỏi để dùng lại. Các ván hoàn thành từ bản cập nhật mới sẽ có tính năng này.");
        if(amount<1||amount>match.path("saved_question_count").asInt())throw fail("NOT_ENOUGH_QUESTIONS","Ván đã lưu chỉ có "+match.path("saved_question_count").asInt()+" câu hỏi. Hãy giảm số câu.");
        var result=new ArrayList<ObjectNode>();
        try(var s=statement("SELECT question_json FROM match_questions WHERE session_id=? ORDER BY position LIMIT ?",session,amount);var r=s.executeQuery()){
            while(r.next())try{var value=JSON.readTree(r.getString(1));if(!(value instanceof ObjectNode question))throw new SQLException("Invalid saved question");result.add(question);}catch(java.io.IOException invalid){throw new SQLException("Cannot read saved question",invalid);}
        }
        return List.copyOf(result);
    }
    ObjectNode history(int owner,JsonNode p) throws SQLException {
        int page=integer(p,"page",1,1,1_000_000),size=integer(p,"page_size",10,1,20);var items=array();
        try(var s=statement(MATCH_SELECT+" WHERE m.host_user_id=? ORDER BY m.ended_at_utc DESC,m.session_id LIMIT ? OFFSET ?",owner,size,(page-1)*size);var r=s.executeQuery()){while(r.next())items.add(matchSummary(r));}
        return page(items,page,size,count("SELECT count(*) FROM matches WHERE host_user_id=?",owner));
    }
    ObjectNode historyDetail(int owner,String session) throws SQLException {
        var match=historyMatch(owner,session);
        var ranks=array();try(var s=statement("SELECT * FROM match_results WHERE session_id=? ORDER BY rank,player_id",session);var r=s.executeQuery()){while(r.next())ranks.add(obj().put("player_id",r.getString("player_id")).put("nickname",r.getString("nickname")).put("avatar_id",r.getString("avatar_id")).put("score",r.getInt("score")).put("correct_count",r.getInt("correct_count")).put("rank",r.getInt("rank")).put("connected",r.getBoolean("connected")));}
        return match.set("ranking",ranks);
    }
    @Override public void close() throws SQLException { db.close(); }
}
