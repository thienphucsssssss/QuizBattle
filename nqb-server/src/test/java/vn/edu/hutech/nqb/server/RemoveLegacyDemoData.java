package vn.edu.hutech.nqb.server;

import java.nio.file.*;
import java.sql.*;

/** One-off maintenance tool on test classpath, not shipped in the Server. */
public final class RemoveLegacyDemoData {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Database and new backup file required; stop Server first");
        Path db=Path.of(args[0]).toAbsolutePath(),backup=Path.of(args[1]).toAbsolutePath();
        if(!Files.isRegularFile(db)||Files.exists(backup))throw new IllegalArgumentException("Missing DB or backup target already exists");
        Files.createDirectories(backup.getParent());
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+db)) {
            try(var save=connection.prepareStatement("VACUUM INTO ?")){save.setString(1,backup.toString());save.execute();}
            connection.setAutoCommit(false);
            int removed=0;
            try(var delete=connection.prepareStatement("DELETE FROM questions WHERE content=? AND option_a=? AND option_b=? AND option_c=? AND option_d=? AND correct_answer=? AND category=? AND owner_user_id IN (SELECT id FROM users WHERE role='ADMIN')")) {
                for(var q:ServerTestData.QUESTIONS){for(int i=0;i<6;i++)delete.setString(i+1,q[i]);delete.setString(7,"Lập trình mạng");removed+=delete.executeUpdate();}
                connection.commit();
            }catch(Exception failure){connection.rollback();throw failure;}
            System.out.println("REMOVED_EXACT_LEGACY_DEMO_QUESTIONS="+removed);
            try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT (SELECT count(*) FROM users) AS users,(SELECT count(*) FROM questions) AS questions,(SELECT count(*) FROM matches) AS matches")) {
                if(result.next())System.out.println("REMAINING users="+result.getInt(1)+" questions="+result.getInt(2)+" matches="+result.getInt(3));
            }
            System.out.println("RECOVERABLE_BACKUP="+backup);
        }
    }
}
