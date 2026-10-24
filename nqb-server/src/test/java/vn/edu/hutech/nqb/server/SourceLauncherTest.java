package vn.edu.hutech.nqb.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Run the source launcher against a temporary project; no real Server, credentials or database. */
@EnabledOnOs(OS.WINDOWS)
class SourceLauncherTest {
    @TempDir Path temp;
    private static final String[] MODULES={"nqb-shared","nqb-server","nqb-client-core","nqb-admin-client","nqb-player-client"};
    private Path fixture() throws Exception {
        Path root=Files.createDirectories(temp.resolve("project with spaces"));
        Path source=Path.of(System.getProperty("basedir")).resolve("..").normalize();
        Files.copy(source.resolve("run.bat"),root.resolve("run.bat"));
        Files.createDirectories(root.resolve("scripts"));
        if(Files.exists(source.resolve("scripts/Source-Build.ps1")))Files.copy(source.resolve("scripts/Source-Build.ps1"),root.resolve("scripts/Source-Build.ps1"));
        Files.writeString(root.resolve("pom.xml"),"fixture-parent");
        for(String module:MODULES){
            Files.createDirectories(root.resolve(module+"/src/main/java"));
            Files.writeString(root.resolve(module+"/pom.xml"),"fixture-module");
            Files.writeString(root.resolve(module+"/src/main/java/Main.java"),"old source");
            Files.createDirectories(root.resolve(module+"/target/classes"));
            Files.writeString(root.resolve(module+"/target/classes/Main.class"),"old compiled output");
            Files.writeString(root.resolve(module+"/target/"+module+"-fixture.jar"),"old jar");
        }
        Files.createDirectories(root.resolve("config"));Files.writeString(root.resolve("config/server.properties"),"fixture-only");
        Files.createDirectories(root.resolve("certs"));Files.writeString(root.resolve("certs/server.p12"),"fixture-only");
        Files.writeString(root.resolve("client.properties"),"fixture-only");Files.createDirectories(root.resolve("tmp"));
        Path bin=Files.createDirectories(root.resolve("bin"));
        Files.writeString(bin.resolve("java.cmd"),"@echo off\r\necho java.specification.version = 21\r\n");
        Files.writeString(bin.resolve("javac.cmd"),"@echo off\r\nexit /b 0\r\n");
        Files.writeString(bin.resolve("mvn.cmd"),"""
            @echo off
            for %%A in (%*) do if "%%~A"=="install" goto build
            echo NQB_ADMIN_PRESENT
            exit /b 0
            :build
            echo built>tmp\\maven-build-ran
            for %%M in (nqb-shared nqb-server nqb-client-core nqb-admin-client nqb-player-client) do (
                echo new jar>%%M\\target\\%%M-fixture.jar
                echo new class>%%M\\target\\classes\\Main.class
            )
            exit /b 0
            """.replace("\n","\r\n"));
        return root;
    }
    private void launch(Path root) throws Exception {
        var builder=new ProcessBuilder("cmd.exe","/d","/c","run.bat","setup").directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("PATH",root.resolve("bin")+";"+System.getenv("PATH"));
        builder.environment().remove("JAVA_HOME");
        Path log=root.resolve("tmp/launcher.log");builder.redirectOutput(log.toFile());
        var process=builder.start();
        if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Source launcher timed out");}
        assertEquals(0,process.exitValue(),Files.readString(log));
    }
    @Test void legacyReadyMarkerCannotSkipUpdatingTheServer() throws Exception {
        Path root=fixture();Files.writeString(root.resolve("tmp/run-build.ok"),"ready");
        launch(root);assertTrue(Files.exists(root.resolve("tmp/maven-build-ran")),"Legacy marker reused old Server classes");
    }
    @Test void sourceChangesInvalidateTheBuildButUnchangedSourcesReuseIt() throws Exception {
        Path root=fixture();launch(root);Files.delete(root.resolve("tmp/maven-build-ran"));
        launch(root);assertFalse(Files.exists(root.resolve("tmp/maven-build-ran")),"Unchanged build should be reused");
        Files.writeString(root.resolve("nqb-server/src/main/java/Main.java"),"new question-set API");
        launch(root);assertTrue(Files.exists(root.resolve("tmp/maven-build-ran")),"Changed Server source was not rebuilt");
    }
    @Test void deletedSourceOrChangedCompiledOutputInvalidatesTheBuild() throws Exception {
        Path root=fixture();launch(root);Files.delete(root.resolve("tmp/maven-build-ran"));
        Files.delete(root.resolve("nqb-server/src/main/java/Main.java"));
        launch(root);assertTrue(Files.exists(root.resolve("tmp/maven-build-ran")),"Deleted source was not detected");
        Files.delete(root.resolve("tmp/maven-build-ran"));
        Files.writeString(root.resolve("nqb-server/target/classes/Main.class"),"different runtime class");
        launch(root);assertTrue(Files.exists(root.resolve("tmp/maven-build-ran")),"Changed runtime classes were not detected");
    }
}
