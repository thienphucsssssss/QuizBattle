package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import vn.edu.hutech.nqb.client.*;
import vn.edu.hutech.nqb.server.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Opt-in desktop QA; test classpath only. Real UI form -> TLS -> production Server -> SQLite. */
public final class LiveLanUiCheck {
    private static Path output, trust;
    private static int port;
    private static QuizServer liveServer;
    private static final String SECRET = "ephemeral-ui-test-only";
    public static void main(String[] args) throws Exception {
        output = Path.of(args.length == 0 ? "../output/lan-ui-qa" : args[0]).toAbsolutePath(); Files.createDirectories(output);
        Path temp = Files.createTempDirectory("nqb-live-ui-"); Path key = temp.resolve("server.p12"); trust = temp.resolve("trust.p12"); Path cert = temp.resolve("server.cer");
        keytool("-genkeypair", "-alias", "qa", "-keyalg", "RSA", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1,dns:localhost", "-storetype", "PKCS12", "-keystore", key.toString(), "-storepass", SECRET);
        keytool("-exportcert", "-alias", "qa", "-keystore", key.toString(), "-storepass", SECRET, "-file", cert.toString());
        keytool("-importcert", "-noprompt", "-alias", "qa", "-storetype", "PKCS12", "-keystore", trust.toString(), "-storepass", SECRET, "-file", cert.toString());
        Path profile=temp.resolve("client.properties");
        Files.writeString(profile,"tls.truststore="+trust.toString().replace('\\','/')+"\ntls.truststorePassword="+SECRET+"\n");
        System.setProperty("nqb.client.config",profile.toString());
        var store = new QuizStore(temp.resolve("qa.db")); store.bootstrap("admin", "Giảng viên QA", SECRET.toCharArray());
        var config = new ServerConfig("127.0.0.1", 0, temp.resolve("qa.db"), key, 10, 32, 1500, 1600, 1800);
        try (var server = new QuizServer(config, config.tls(SECRET.toCharArray()), store)) {
            server.start(); liveServer=server; port = server.port();
            Files.writeString(profile,"server.host=127.0.0.1\nserver.port=1\ndiscovery.port="+server.discoveryPort()+"\ntls.truststore="+trust.toString().replace('\\','/')+"\ntls.truststorePassword="+SECRET+"\n");
            ArcadeApplication.qaDriver = app -> Thread.ofPlatform().daemon().name("live-ui-check").start(() -> {
                try { run(app); System.out.println("LIVE_LAN_UI_PASS: UDP discovery with wrong configured TCP port, list JOIN, code JOIN, invalid code, no Client IP/port, avatar, game, ranking, LOGIN, REGISTER, bank, history, verified TLS"); }
                catch (Throwable failure) { ArcadeApplication.qaFailed(failure); failure.printStackTrace(); }
                finally { Platform.runLater(Platform::exit); }
            });
            ArcadeApplication.launchFor("STUDENT", new String[]{"--width="+(args.length>1?args[1]:"1366"), "--height="+(args.length>2?args[2]:"768")});
        } finally { ArcadeApplication.qaDriver = null; liveServer=null; System.clearProperty("nqb.client.config"); }
    }
    private static TlsQuizGateway connect() throws Exception { return TlsQuizGateway.connect(new Endpoint("127.0.0.1", port), trust, SECRET.toCharArray()); }
    private static JsonNode request(QuizGateway gateway, String type, com.fasterxml.jackson.databind.node.ObjectNode payload) throws Exception { return gateway.request(type, payload).get(8, TimeUnit.SECONDS); }
    private static void run(ArcadeApplication app) throws Exception {
        fx(() -> { assertLogin(app); snapshot(app,"00-login-role-selection"); });
        try (var host = connect(); var opponent = connect();var third=connect()) {
            request(host, "LOGIN", object().put("username", "admin").put("password", SECRET));
            if(request(host,"QUESTION_LIST",object()).path("total").asInt()!=0)throw new AssertionError("New Server must have an empty bank");
            for(int i=1;i<=20;i++)request(host,"QUESTION_CREATE",object().put("content","Test-only question "+i).put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A").put("category","QA"));
            request(host,"USER_CREATE",object().put("username","teacher.qa").put("display_name","Teacher QA").put("temporary_password",SECRET));
            fx(app::qaStudentAuth);
            await(()->app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().equals("Chờ giảng viên mở phòng.")));
            fx(()->snapshot(app,"00-empty-room-list"));
            try(var teacher=connect()) {
                if(!request(teacher,"LOGIN",object().put("username","teacher.qa").put("password",SECRET)).path("must_change_password").asBoolean())throw new AssertionError("New teacher must change password");
                request(teacher,"PASSWORD_CHANGE",object().put("current_password",SECRET).put("new_password",SECRET+"-changed"));
            }
            fx(() -> { app.qaAuth(); fillForm(app,"admin",""); ((TextField)app.qaScene().lookup("#login-password")).setText("wrong-password-only"); app.qaClick("Đăng nhập   →"); });
            await(() -> app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("không đúng")));
            fx(() -> { if(app.qaConnected())throw new AssertionError("Wrong password granted a session"); snapshot(app,"00-wrong-password"); });
            String code = request(host, "ROOM_CONFIGURE", object().put("room_name", "Lớp LAN · Java").put("question_count", 2).put("duration_seconds", 5)).path("room_code").asText();
            request(opponent, "JOIN", object().put("nickname", "Quốc Bảo").put("room_code", code)); request(opponent, "READY_SET", object().put("ready", true));
            request(third,"JOIN",object().put("nickname","Lan Anh").put("room_code",code));request(third,"READY_SET",object().put("ready",true));
            fx(() -> {app.qaStudentAuth();app.qaScene().getRoot().applyCss();app.qaScene().getRoot().layout();((TextField)app.qaScene().lookup("#identity")).setText("Bạn từ danh sách LAN");});
            await(()->app.qaScene().lookup("#lan-rooms") instanceof javafx.scene.control.ListView<?> list && list.getItems().size()==1);
            fx(()->{snapshot(app,"00-discovered-room-list");((javafx.scene.control.ToggleButton)app.qaScene().lookup("#lan-code-mode")).fire();app.qaScene().getRoot().applyCss();app.qaScene().getRoot().layout();((TextField)app.qaScene().lookup("#room-code-input")).setText(code.startsWith("A")?"BBBBBB":"AAAAAA");app.qaClick("Tham gia phòng   →");});
            await(()->app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().contains("Không tìm thấy mã phòng")));
            fx(()->{snapshot(app,"00-code-not-found");((javafx.scene.control.ToggleButton)app.qaScene().lookup("#lan-browse-mode")).fire();((javafx.scene.control.ListView<?>)app.qaScene().lookup("#lan-rooms")).getSelectionModel().select(0);app.qaClick("Tham gia phòng   →");});
            await(app::qaConnected);fx(()->{app.qaCloseAuxiliary();snapshot(app,"00-joined-by-lan-list");app.qaLogout();});
            fx(() -> { app.qaStudentAuth(); fillForm(app, "Minh Ánh", code); app.qaClick("Tham gia phòng   →"); });
            await(() -> app.qaConnected() && app.qaPhase().equals("WAITING"));
            fx(() -> { app.qaCloseAuxiliary(); snapshot(app, "01-live-lobby"); app.qaClick("Sẵn sàng   →"); });
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (request(host, "ROOM_STATE", object()).path("ready_count").asInt() < 2) { if (System.nanoTime() > until) throw new AssertionError("UI ready did not reach Server"); Thread.sleep(80); }
            request(host, "START_GAME", object()); await(() -> app.qaPhase().equals("COUNTDOWN")); fx(() -> { assertMusic(app, MusicSlots.Track.GAME); snapshot(app, "02-live-countdown"); });
            for (int i = 1; i <= 2; i++) {
                final int number = i; await(() -> app.qaPhase().equals("QUESTION_ACTIVE"));
                if(number==2)Thread.sleep(1100);
                fx(() -> { assertMusic(app, MusicSlots.Track.GAME); snapshot(app, "03-question-" + number); app.qaAnswer("A"); });
                if(number==1){var state=request(host,"ROOM_STATE",object());request(opponent,"ANSWER",object().put("session_id",state.path("session_id").asText()).put("question_id",state.path("question").path("question_id").asInt()).put("answer","A"));}
                await(() -> app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("Đã nhận đáp án A")));
                await(() -> app.qaPhase().equals("SHOW_RESULT")); fx(() -> {
                    // Attach ScrollPane skins/layout before inspecting newly rendered labels.
                    snapshot(app, "04-result-" + number);
                    assertMusic(app, MusicSlots.Track.GAME);int points=app.qaResult().path("points").asInt();
                    if(points<50||points>100||(number==2&&points==100))throw new AssertionError("Speed award not applied: "+points);
                    if(app.qaScene().getRoot().lookupAll(".label").stream().noneMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().equals("+"+points+" điểm")))throw new AssertionError("UI does not show Server speed award");
                });
                await(() -> app.qaPhase().equals("SHOW_RANKING")); Thread.sleep(1400); fx(() -> { assertMusic(app, MusicSlots.Track.GAME); app.qaAssertRanksSettled(); snapshot(app, "05-ranking-" + number); });
            }
            await(() -> app.qaPhase().equals("FINISHED"));Thread.sleep(950); fx(() -> { assertMusic(app, MusicSlots.Track.VICTORY); if (!app.qaResult().path("result_saved").asBoolean() || app.qaResult().path("ranking").size() != 3 || app.qaScene().getRoot().lookupAll(".victory-column").size()!=3) throw new AssertionError("UI final result/podium missing persistence/ranking"); snapshot(app, "06-live-final"); });
            request(host, "ROOM_RESET", object()); await(() -> app.qaPhase().equals("WAITING")); fx(() -> { assertMusic(app, MusicSlots.Track.NONE); snapshot(app, "07-reset-lobby"); });
            request(host, "ROOM_CLOSE", object()); await(() -> !app.qaConnected());
        }
        fx(() -> { app.qaAuth(); fillForm(app, "admin", ""); app.qaClick("Đăng nhập   →"); });
        await(app::qaConnected); fx(app::bankView); await(() -> tableSize(app) == 10); fx(() -> snapshot(app, "08-live-bank"));
        fx(app::historyView); await(() -> tableSize(app) == 1); fx(() -> snapshot(app, "09-live-history"));
        checkCommander(app);
        fx(() -> { app.qaSettings(); var dialog = javafx.stage.Window.getWindows().stream().filter(w -> w.isShowing()).toList().getLast().getScene(); dialog.getRoot().applyCss(); dialog.getRoot().layout();
            var sound = (javafx.scene.control.CheckBox)dialog.getRoot().lookupAll(".check-box").stream().filter(n -> n instanceof javafx.scene.control.CheckBox c && c.getText().equals("Âm thanh giao diện")).findFirst().orElseThrow();
            if (sound.isDisabled() || dialog.lookup(".slider") == null) throw new AssertionError("SFX controls unavailable");
            if (!(dialog.lookup("#music-enabled") instanceof javafx.scene.control.CheckBox background) || background.isDisabled() || dialog.lookup("#music-volume") == null) throw new AssertionError("Music controls unavailable");
            saveImage(dialog.snapshot(null), "10-sound-settings"); app.qaCloseAuxiliary(); });
        fx(app::qaLogout); await(() -> app.qaScene().lookup("#login-role")!=null); fx(() -> { assertLogin(app); snapshot(app,"11-logout-clears-role"); });
        fx(() -> { app.qaTeacherAuth(); fillForm(app,"admin",""); app.qaClick("Đăng nhập   →"); });
        await(() -> app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("không thuộc vai trò")));
        fx(() -> { if(app.qaConnected())throw new AssertionError("Role mismatch granted a session"); snapshot(app,"12-role-mismatch"); });
        fx(() -> { app.qaWelcome(); app.qaTeacherAuth(); fillForm(app,"teacher.qa",""); ((TextField)app.qaScene().lookup("#login-password")).setText(SECRET+"-changed"); app.qaClick("Đăng nhập   →"); });
        await(app::qaConnected); fx(app::bankView); await(() -> tableSize(app)==0 && app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().startsWith("0 câu phù hợp")));
        fx(() -> { if(app.qaScene().getRoot().lookupAll(".button").stream().anyMatch(n -> n instanceof javafx.scene.control.Button b && b.getText().equals("Người dùng")))throw new AssertionError("Teacher sees Admin navigation"); snapshot(app,"13-teacher-empty-bank"); });
        checkQuestionImport(app);
        fx(app::qaLogout); await(() -> app.qaScene().lookup("#login-role")!=null); fx(() -> assertLogin(app));
        fx(app::qaStudentAuth); await(() -> app.qaScene().lookup("#identity")!=null);
        fx(() -> { if(!((TextField)app.qaScene().lookup("#identity")).getText().isEmpty())throw new AssertionError("Sample nickname prefilled"); snapshot(app,"14-student-blank-nickname"); });
        fx(() -> { app.qaWelcome(); app.qaScene().getRoot().applyCss(); app.qaScene().getRoot().layout(); app.qaClick("Tạo tài khoản giảng viên"); fillForm(app,"registered.qa",""); ((TextField)app.qaScene().lookup("#register-name")).setText("Giảng viên mới"); ((TextField)app.qaScene().lookup("#register-confirm")).setText("mismatch"); app.qaClick("Tạo tài khoản   →"); snapshot(app,"15-signup-validation"); });
        fx(() -> { if(app.qaConnected())throw new AssertionError("Invalid confirmation submitted"); ((TextField)app.qaScene().lookup("#register-confirm")).setText(SECRET); app.qaClick("Tạo tài khoản   →"); });
        await(app::qaConnected); fx(app::bankView); await(() -> tableSize(app)==0 && app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().startsWith("0 câu phù hợp")));
        fx(() -> snapshot(app,"17-signup-success-empty-bank"));
        fx(app::qaLogout);liveServer.close();
        fx(app::qaStudentAuth);
        await(()->app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().startsWith("Chưa tìm thấy máy chủ.")));
        fx(()->{snapshot(app,"18-server-offline");if(app.qaConnected())throw new AssertionError("Offline screen retained a session");var refresh=(javafx.scene.control.Button)app.qaScene().lookup("#lan-refresh");if(refresh.getWidth()+1<refresh.prefWidth(-1))throw new AssertionError("Refresh button label is clipped");});
    }
    private static void assertMusic(ArcadeApplication app, MusicSlots.Track track) { if (app.qaMusicTrack() != track) throw new AssertionError("Wrong music for " + app.qaPhase() + ": " + app.qaMusicTrack()); }
    private static void checkCommander(ArcadeApplication app)throws Exception {
        fx(()->{
            for(String title:List.of("Tổng quan","Ngân hàng câu hỏi","Phòng thi","Giám sát thi đấu","Đấu trường thí sinh","Lịch sử & Bảng xếp hạng","Cài đặt","Người dùng","Đổi mật khẩu","Đăng xuất")) {
                var nav=app.qaScene().getRoot().lookupAll(".sidebar .button").stream().filter(n->n instanceof javafx.scene.control.Button b&&b.getText().equals(title)).findFirst();
                if(nav.isEmpty())throw new AssertionError("Missing functional sidebar entry: "+title);
            }
            app.qaClick("Tổng quan");
        });
        await(()->app.qaScene().lookup("#overview-questions") instanceof javafx.scene.control.Label l&&l.getText().equals("20"));
        await(()->app.qaScene().lookup("#overview-matches") instanceof javafx.scene.control.Label l&&l.getText().equals("1"));
        fx(()->{snapshot(app,"09a-commander-overview");app.qaClick("Giám sát thi đấu");});
        await(()->app.qaScene().lookup("#monitor-players")!=null);
        fx(()->{if(tableSize(app)!=0)throw new AssertionError("Closed room retained players");snapshot(app,"09b-monitor-empty");});
        var created=new CompletableFuture<JsonNode>();
        fx(()->app.call("ROOM_CONFIGURE",object().put("room_name","Giám sát LAN").put("question_count",1).put("duration_seconds",5),created::complete,created::completeExceptionally));
        String code=created.get(8,TimeUnit.SECONDS).path("room_code").asText();
        try(var a=connect();var b=connect()){
            request(a,"JOIN",object().put("room_code",code).put("nickname","Minh Giám sát"));request(b,"JOIN",object().put("room_code",code).put("nickname","Lan Đấu trường"));
            request(a,"READY_SET",object().put("ready",true));request(b,"READY_SET",object().put("ready",true));
            await(()->tableSize(app)==2);
            fx(()->{snapshot(app,"09c-monitor-ready");app.qaClick("Bắt đầu ván thi");});
            await(()->app.qaPhase().equals("QUESTION_ACTIVE"));
            await(()->app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().startsWith("Câu 1/1")));
            fx(()->assertTableText(app,"Đang trả lời"));
            fx(()->{if(app.qaScene().lookup("#monitor-players")==null)throw new AssertionError("Game event replaced monitor page");snapshot(app,"09d-monitor-active");});
            var state=request(a,"ROOM_STATE",object());
            request(a,"ANSWER",object().put("session_id",state.path("session_id").asText()).put("question_id",state.path("question").path("question_id").asInt()).put("answer","A"));
            await(()->app.qaScene().lookup("#monitor-submitted") instanceof javafx.scene.control.Label l&&l.getText().contains("1/2"));
            fx(()->{snapshot(app,"09e-monitor-submitted");app.qaClick("Đấu trường thí sinh");});
            await(()->app.qaScene().lookup("#arena-search")!=null&&tableSize(app)==2);
            fx(()->((TextField)app.qaScene().lookup("#arena-search")).setText("Minh"));await(()->tableSize(app)==1);
            await(()->app.qaPhase().equals("FINISHED"));
            fx(()->assertTableText(app,"Đã kết thúc"));
            fx(()->{snapshot(app,"09f-arena-filtered-final");if(app.qaScene().lookup("#arena-search")==null||tableSize(app)!=1)throw new AssertionError("Arena search or page lost during game: rows="+tableSize(app)+", search="+app.qaScene().lookup("#arena-search"));((TextField)app.qaScene().lookup("#arena-search")).clear();});
            await(()->tableSize(app)==2);
        }
        await(()->app.qaScene().lookup("#arena-summary") instanceof javafx.scene.control.Label l&&l.getText().contains("0 đang kết nối"));
        fx(()->{snapshot(app,"09g-arena-disconnected");app.qaClick("Cài đặt");});
        await(()->app.qaScene().lookup("#management-settings")!=null);
        fx(()->{snapshot(app,"09h-commander-settings");app.qaClick("Âm thanh & giao diện");app.qaCloseAuxiliary();});
        var closed=new CompletableFuture<JsonNode>();fx(()->app.call("ROOM_CLOSE",object(),closed::complete,closed::completeExceptionally));closed.get(8,TimeUnit.SECONDS);
        fx(()->app.qaClick("Tổng quan"));await(()->app.qaScene().lookup("#overview-matches") instanceof javafx.scene.control.Label l&&l.getText().equals("2"));
        System.out.println("COMMANDER_UI_PASS: real overview, all sidebar routes, monitor submissions, arena search, retained routes, disconnects, settings");
    }
    private static void assertTableText(ArcadeApplication app,String expected) {
        app.qaScene().getRoot().applyCss();app.qaScene().getRoot().layout();
        if(app.qaScene().getRoot().lookupAll(".table-cell").stream().noneMatch(n->n instanceof javafx.scene.control.TableCell<?,?> c&&expected.equals(c.getText())))
            throw new AssertionError("Visible table cells did not update to current phase: "+expected);
    }
    private static void assertLogin(ArcadeApplication app) {
        if(app.qaConnected())throw new AssertionError("Unauthenticated screen has a session");
        if(app.qaScene().lookup("#server-host")!=null||app.qaScene().lookup("#server-port")!=null)throw new AssertionError("Client still exposes IP/port setup");
        var role=(javafx.scene.control.ComboBox<?>)app.qaScene().lookup("#login-role");
        if(role==null || role.getValue()!=null || !((javafx.scene.control.Button)app.qaScene().lookup("#login-submit")).isDisabled())throw new AssertionError("Startup/logout did not reset role");
        if(app.qaScene().getRoot().lookupAll(".button").stream().anyMatch(n -> n instanceof javafx.scene.control.Button b && (b.getText().contains("Xem thử") || b.getText().equals("Người dùng"))))throw new AssertionError("Unauthenticated mock/Admin entry remains");
    }
    private static javafx.scene.Scene importScene(){return javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#import-confirm")!=null).findFirst().orElseThrow().getScene();}
    private static void importDetails(){if(importScene().lookup("#import-tabs") instanceof javafx.scene.control.TabPane tabs)tabs.getSelectionModel().select(1);importScene().getRoot().applyCss();importScene().getRoot().layout();}
    private static javafx.scene.control.Button importConfirm(){return (javafx.scene.control.Button)importScene().lookup("#import-confirm");}
    private static JsonNode uiRequest(ArcadeApplication app,String type)throws Exception {return uiRequest(app,type,object());}
    private static JsonNode uiRequest(ArcadeApplication app,String type,com.fasterxml.jackson.databind.node.ObjectNode payload)throws Exception {var result=new CompletableFuture<JsonNode>();fx(()->app.call(type,payload,result::complete,result::completeExceptionally));return result.get(8,TimeUnit.SECONDS);}
    private static void setImportName(String name) {
        if(!(importScene().lookup("#import-set-name") instanceof TextField input))throw new AssertionError("Cannot choose a question set for imported questions");
        input.setText(name);
    }
    private static void checkQuestionImport(ArcadeApplication app)throws Exception {
        Path file=output.resolve("qa-only-import.xlsx");var bad=new ArrayList<>(QuestionFilesTest.VALUES);bad.set(5,"E");var another=new ArrayList<>(QuestionFilesTest.VALUES);another.set(0,"Câu hỏi thứ hai có xuống dòng\ntrong bản xem trước");
        QuestionFilesTest.excelFixture(file,List.of(QuestionFilesTest.VALUES,QuestionFilesTest.VALUES,bad,another));
        QuestionImportView[] view={null};fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(file);});
        await(()->!importConfirm().isDisabled());
        fx(()->{var scene=importScene();scene.getRoot().applyCss();scene.getRoot().layout();setImportName("Bộ QA Excel");if(!importConfirm().getText().equals("Nhập 2 câu hỏi"))throw new AssertionError("Import did not default to valid unique rows: "+importConfirm().getText());if(!((javafx.scene.control.Label)scene.lookup("#import-count")).getText().contains("Lỗi 1"))throw new AssertionError("Import errors not shown");saveImage(scene.snapshot(null),"13a-import-preview-excel");});
        if(uiRequest(app,"QUESTION_LIST").path("total").asInt()!=0)throw new AssertionError("Preview mutated the bank");
        fx(()->{var table=(javafx.scene.control.TableView<?>)importScene().lookup("#import-table");table.getSelectionModel().select(2);importDetails();saveImage(importScene().snapshot(null),"13b-import-row-error");app.qaCloseAuxiliary();});
        if(uiRequest(app,"QUESTION_LIST").path("total").asInt()!=0)throw new AssertionError("Cancel mutated the bank");
        if(uiRequest(app,"QUESTION_SET_LIST").path("total").asInt()!=0)throw new AssertionError("Cancel created an empty question set");
        fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(file);});await(()->!importConfirm().isDisabled());fx(()->{setImportName("Bộ QA Excel");importConfirm().fire();});await(()->tableSize(app)==2);fx(()->snapshot(app,"13c-import-saved-bank"));
        fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(file);});await(()->((javafx.scene.control.Label)importScene().lookup("#import-status")).getText().startsWith("Xem lại"));
        fx(()->{if(!importConfirm().isDisabled())throw new AssertionError("Bank duplicates selected by default");((javafx.scene.control.CheckBox)importScene().lookup("#import-allow-duplicates")).fire();if(importConfirm().isDisabled()||!importConfirm().getText().equals("Nhập 3 câu hỏi"))throw new AssertionError("Explicit duplicate option not applied");saveImage(importScene().snapshot(null),"13d-import-duplicates");app.qaCloseAuxiliary();});
        Path templates=output.resolve("templates");Files.createDirectories(templates);for(var format:QuestionFiles.Format.values()){Path target=templates.resolve("Mau_cau_hoi_QuizBattle."+format.extension);QuestionFiles.writeTemplate(target,format);if(!QuestionFiles.readSafely(target).isEmpty())throw new AssertionError("Template contains mock data");}
        fx(()->{QuestionImportView.showTemplates(app,app.ownerStage());var scene=javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#template-status")!=null).findFirst().orElseThrow().getScene();scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13e-template-preview");app.qaCloseAuxiliary();});
        Path word=output.resolve("qa-only-import.docx");QuestionFiles.writeTemplate(word,QuestionFiles.Format.WORD);
        try(var in=Files.newInputStream(word);var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(in)){var values=new ArrayList<>(QuestionFilesTest.VALUES);values.set(0,"Câu hỏi được nhập từ Word");for(int c=0;c<7;c++)doc.getTables().getFirst().getRow(1).getCell(c).setText(values.get(c));try(var out=Files.newOutputStream(word)){doc.write(out);}}
        fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(word);});await(()->!importConfirm().isDisabled());fx(()->{importDetails();saveImage(importScene().snapshot(null),"13f-import-preview-word");importConfirm().fire();});await(()->tableSize(app)==3);
        if(uiRequest(app,"QUESTION_LIST").path("total").asInt()!=3)throw new AssertionError("Excel + Word not committed");
        System.out.println("QUESTION_IMPORT_UI_PASS: XLSX/DOCX, preview, validation, duplicate opt-in, cancel, selected atomic commit, template roundtrip");
        checkQuestionSets(app);
    }
    private static javafx.scene.Scene dialogScene(String id){return javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#"+id)!=null).findFirst().orElseThrow().getScene();}
    private static void selectNamedSet(javafx.scene.control.ComboBox<?> combo,String name) {
        for(int i=0;i<combo.getItems().size();i++)if(combo.getItems().get(i).toString().startsWith(name+" · ")){combo.getSelectionModel().select(i);return;}
        throw new AssertionError("Question set missing from selector: "+name);
    }
    private static void checkQuestionSets(ArcadeApplication app)throws Exception {
        JsonNode sets=uiRequest(app,"QUESTION_SET_LIST");
        if(sets.path("total").asInt()!=2)throw new AssertionError("Excel/Word imports did not create private sets");
        var excel=java.util.stream.StreamSupport.stream(sets.path("items").spliterator(),false).filter(s->s.path("name").asText().equals("Bộ QA Excel")).findFirst().orElseThrow();
        if(excel.path("question_count").asInt()!=2)throw new AssertionError("Imported set contains unexpected questions");
        await(()->app.qaScene().lookup("#bank-question-set") instanceof javafx.scene.control.ComboBox<?> box&&!box.isDisabled()&&box.getItems().size()==3);
        fx(()->selectNamedSet((javafx.scene.control.ComboBox<?>)app.qaScene().lookup("#bank-question-set"),"Bộ QA Excel"));
        await(()->tableSize(app)==2);fx(()->{snapshot(app,"13g-bank-set-filter");app.qaClick("Bộ câu hỏi");});
        await(()->dialogScene("set-create-filtered").lookup("#set-create-filtered") instanceof javafx.scene.control.Button button&&!button.isDisabled());
        fx(()->{var scene=dialogScene("question-set-name");((TextField)scene.lookup("#question-set-name")).setText("Bộ tạo từ lọc");((javafx.scene.control.Button)scene.lookup("#set-create-filtered")).fire();});
        await(()->dialogScene("question-sets-table").lookup("#question-sets-table") instanceof javafx.scene.control.TableView<?> table&&table.getItems().size()==3);
        fx(()->{var scene=dialogScene("question-sets-table");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13h-question-set-manager");app.qaCloseAuxiliary();});
        sets=uiRequest(app,"QUESTION_SET_LIST");
        var created=java.util.stream.StreamSupport.stream(sets.path("items").spliterator(),false).filter(s->s.path("name").asText().equals("Bộ tạo từ lọc")).findFirst().orElseThrow();
        int setId=created.path("question_set_id").asInt();if(created.path("question_count").asInt()!=2)throw new AssertionError("Filtered set ignored bank filters");
        var allowed=new HashSet<Integer>();uiRequest(app,"QUESTION_LIST",object().put("question_set_id",setId)).path("items").forEach(q->allowed.add(q.path("question_id").asInt()));
        fx(()->app.qaClick("Phòng thi"));
        await(()->app.qaScene().getRoot().lookupAll(".button").stream().anyMatch(n->n instanceof javafx.scene.control.Button b&&b.getText().equals("Tạo phòng")));
        fx(()->app.qaClick("Tạo phòng"));
        await(()->dialogScene("room-question-set").lookup("#room-question-set") instanceof javafx.scene.control.ComboBox<?> box&&!box.isDisabled());
        fx(()->{var scene=dialogScene("room-question-set");((TextField)scene.lookup("#room-name")).setText("Phòng chọn bộ QA");selectNamedSet((javafx.scene.control.ComboBox<?>)scene.lookup("#room-question-set"),"Bộ tạo từ lọc");((javafx.scene.control.Spinner<Integer>)scene.lookup("#room-question-count")).getValueFactory().setValue(1);scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13i-room-set-selection");((javafx.scene.control.Spinner<Integer>)scene.lookup("#room-question-count")).getValueFactory().setValue(3);((javafx.scene.control.Button)scene.lookup("#room-config-save")).fire();});
        fx(()->{var scene=dialogScene("room-question-set");scene.getRoot().applyCss();scene.getRoot().layout();if(scene.getRoot().lookupAll(".label").stream().noneMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().contains("Bộ chỉ có 2 câu")))throw new AssertionError("Insufficient-set warning missing");saveImage(scene.snapshot(null),"13i-room-set-insufficient");});
        if(!uiRequest(app,"ROOM_STATE").path("room_code").asText().isEmpty())throw new AssertionError("Invalid config created a room");
        fx(()->{var scene=dialogScene("room-question-set");((javafx.scene.control.Spinner<Integer>)scene.lookup("#room-question-count")).getValueFactory().setValue(1);((javafx.scene.control.Spinner<Integer>)scene.lookup("#room-question-seconds")).getValueFactory().setValue(5);((javafx.scene.control.Button)scene.lookup("#room-config-save")).fire();});
        await(()->app.roomSnapshot().path("question_set_id").asInt()==setId);
        fx(()->snapshot(app,"13j-room-selected-set"));
        String code=uiRequest(app,"ROOM_STATE").path("room_code").asText();
        try(var a=connect();var b=connect()){
            request(a,"JOIN",object().put("room_code",code).put("nickname","Set Player A"));request(b,"JOIN",object().put("room_code",code).put("nickname","Set Player B"));
            request(a,"READY_SET",object().put("ready",true));request(b,"READY_SET",object().put("ready",true));await(()->app.roomSnapshot().path("ready_count").asInt()==2);
            fx(()->app.qaClick("Bắt đầu ván thi   →"));await(()->app.qaPhase().equals("QUESTION_ACTIVE"));
            if(!allowed.contains(uiRequest(app,"ROOM_STATE").path("question").path("question_id").asInt()))throw new AssertionError("Room used a question outside its set");
            fx(()->snapshot(app,"13k-selected-set-game"));uiRequest(app,"ABORT_GAME");await(()->app.qaPhase().equals("FINISHED"));
            if(uiRequest(app,"ROOM_RESET").path("question_set_id").asInt()!=setId)throw new AssertionError("Reset lost the selected set");
            uiRequest(app,"ROOM_CLOSE");
        }
        System.out.println("QUESTION_SET_UI_PASS: import grouping, cancel, bank filter, create from filter, room selection, insufficient pool, actual selected-set gameplay, reset");
    }
    private static int tableSize(ArcadeApplication app) { var table = app.qaScene().getRoot().lookup(".table-view"); return table instanceof javafx.scene.control.TableView<?> t ? t.getItems().size() : -1; }
    private static void fillForm(ArcadeApplication app, String identity, String code) {
        var root = app.qaScene().getRoot(); root.applyCss(); root.layout();
        if(root.lookup("#server-host")!=null || root.lookup("#server-port")!=null)throw new AssertionError("IP/port fields remain in Client");
        ((TextField)root.lookup("#identity")).setText(identity);
        if(root.lookup("#truststore-path")!=null || root.lookup("#trust-password")!=null)throw new AssertionError("Normal user is still asked to configure TLS");
        if (code.isEmpty()) ((TextField)root.lookup("#login-password")).setText(SECRET);
        else {((javafx.scene.control.ToggleButton)root.lookup("#lan-code-mode")).fire();root.applyCss();root.layout();((TextField)root.lookup("#room-code-input")).setText(code);}
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < until) { boolean[] okay = {false}; fx(() -> okay[0] = condition.getAsBoolean()); if (okay[0]) return; Thread.sleep(60); } throw new AssertionError("Timed out waiting for live UI state");
    }
    private static void fx(Runnable action) throws Exception { var future = new CompletableFuture<Void>(); Platform.runLater(() -> { try { action.run(); future.complete(null); } catch (Throwable failure) { future.completeExceptionally(failure); } }); future.get(5, TimeUnit.SECONDS); }
    private static void snapshot(ArcadeApplication app, String name) {
        var scene = app.qaScene(); scene.getRoot().applyCss(); scene.getRoot().layout(); saveImage(scene.snapshot(null), name);
    }
    private static void saveImage(WritableImage image, String name) { try { var buffer = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB); for (int y=0; y<buffer.getHeight(); y++) for (int x=0; x<buffer.getWidth(); x++) buffer.setRGB(x,y,image.getPixelReader().getArgb(x,y)); ImageIO.write(buffer,"png",output.resolve(name+".png").toFile()); } catch(Exception error) { throw new RuntimeException(error); } }
    private static void keytool(String... args) throws Exception { var command = new ArrayList<String>(); command.add(Path.of(System.getProperty("java.home"), "bin", "keytool.exe").toString()); command.addAll(List.of(args)); var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(); if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IllegalStateException("Temporary QA certificate failed"); }
}
