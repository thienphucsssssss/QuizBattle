package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the CLI that the Windows launcher invokes after an interrupted setup. */
class BootstrapRecoveryTest {
    @TempDir Path temp;
    record Run(int exit,String output) { }
    private Path config() throws Exception {
        Path folder=Files.createDirectories(temp.resolve("config"));Path file=folder.resolve("server.properties");
        Files.writeString(file,"database.path=data/quiz.db\nserver.port=0\n");return file;
    }
    private Run cli(Path config,String password,String option) throws Exception {
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",classpath,ServerMain.class.getName(),"--config="+config,option);
        process.environment().remove("NQB_BOOTSTRAP_PASSWORD");if(password!=null)process.environment().put("NQB_BOOTSTRAP_PASSWORD",password);
        var child=process.redirectErrorStream(true).start();
        if(!child.waitFor(15,TimeUnit.SECONDS)){child.destroyForcibly();fail("Bootstrap CLI timed out");}
        return new Run(child.exitValue(),new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void checkMissingAdminDoesNotCreateDatabase() throws Exception {
        var result=cli(config(),null,"--check-admin");assertEquals(0,result.exit());assertTrue(result.output().contains("NQB_ADMIN_MISSING"));assertFalse(Files.exists(temp.resolve("data/quiz.db")));
    }
    @Test void shortPasswordThenRetryRecoversExistingEmptyDatabaseWithoutResettingAdmin() throws Exception {
        var config=config();var invalid=cli(config,"short!","--init-admin");assertNotEquals(0,invalid.exit());
        assertTrue(Files.exists(temp.resolve("data/quiz.db")));assertTrue(cli(config,null,"--check-admin").output().contains("NQB_ADMIN_MISSING"));
        assertEquals(0,cli(config,"ephemeral-recovery-only-1","--init-admin").exit());assertTrue(cli(config,null,"--check-admin").output().contains("NQB_ADMIN_PRESENT"));
        assertNotEquals(0,cli(config,"ephemeral-other-only-2","--init-admin").exit());
        try(var store=new QuizStore(temp.resolve("data/quiz.db"))){assertTrue(PasswordHasher.verify("ephemeral-recovery-only-1".toCharArray(),store.account("admin").hash()));assertFalse(PasswordHasher.verify("ephemeral-other-only-2".toCharArray(),store.account("admin").hash()));}
    }
}
