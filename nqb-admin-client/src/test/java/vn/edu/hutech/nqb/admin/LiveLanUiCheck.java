package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.Scene;
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
        System.setProperty("nqb.connection.settings",temp.resolve("connection.properties").toString());
        System.setProperty("nqb.wifi.catalog",temp.resolve("wifi-observed.json").toString());
        System.setProperty("nqb.survey.database",temp.resolve("wifi-surveys.db").toString());
        var store = new QuizStore(temp.resolve("qa.db")); store.bootstrap("admin", "Giảng viên QA", SECRET.toCharArray());
        var config = new ServerConfig("auto", 0, temp.resolve("qa.db"), key, 10, 32, 1500, 1600, 1800);
        try (var server = new QuizServer(config, config.tls(SECRET.toCharArray()), store)) {
            server.start(); liveServer=server; port = server.port();
            Files.writeString(profile,"server.host=auto\nserver.port=1\ndiscovery.port="+server.discoveryPort()+"\ntls.truststore="+trust.toString().replace('\\','/')+"\ntls.truststorePassword="+SECRET+"\n");
            ArcadeApplication.qaDriver = app -> Thread.ofPlatform().daemon().name("live-ui-check").start(() -> {
                try { if(args.length>3&&args[3].equals("phase-a")){checkPhaseA(app);System.out.println("PHASE_A_UI_PASS");}
                    else if(args.length>3&&args[3].equals("phase-b")){checkPhaseB(app);System.out.println("PHASE_B_UI_PASS");}
                    else if(args.length>3&&args[3].equals("phase-c")){checkPhaseC(app);System.out.println("PHASE_C_UI_PASS");}
                    else if(args.length>3&&args[3].equals("floor-map")){checkFloorMap(app);System.out.println("FLOOR_MAP_UI_PASS");}
                    else if(args.length>3&&args[3].equals("ap-only")){checkAccessPoints(app);System.out.println("ACCESS_POINT_UI_PASS");}
                    else if(args.length>3&&args[3].equals("permission-only")){checkPermissionRecovery(app);System.out.println("PERMISSION_ONLY_UI_PASS");}
                    else {run(app);System.out.println("LIVE_LAN_UI_PASS: UDP discovery with wrong configured TCP port, list JOIN, code JOIN, invalid code, optional direct endpoint, avatar, game, ranking, LOGIN, REGISTER, bank, history, verified TLS");} }
                catch (Throwable failure) { ArcadeApplication.qaFailed(failure); failure.printStackTrace(); }
                finally { Platform.runLater(Platform::exit); }
            });
            ArcadeApplication.launchFor("STUDENT", new String[]{"--width="+(args.length>1?args[1]:"1366"), "--height="+(args.length>2?args[2]:"768")});
        } finally { ArcadeApplication.qaDriver = null; liveServer=null; System.clearProperty("nqb.client.config");System.clearProperty("nqb.connection.settings");System.clearProperty("nqb.wifi.catalog");System.clearProperty("nqb.survey.database"); }
    }
    private static void checkFloorMap(ArcadeApplication app)throws Exception {
        fx(()->{app.qaSettings();((javafx.scene.control.Button)auxiliary("#floor-open").lookup("#floor-open")).fire();});
        fx(()->{
            var scene=auxiliary("#floor-map");scene.getRoot().applyCss();scene.getRoot().layout();
            if(scene.getRoot().lookupAll(".corridor-room").size()!=12||scene.getRoot().lookupAll(".corridor-client").size()!=2)throw new AssertionError("Missing corridor rooms/clients");
            if(!((javafx.scene.control.Label)scene.lookup("#floor-origin")).getText().contains("MÔ PHỎNG"))throw new AssertionError("Unlabelled simulation");
            ((javafx.scene.control.CheckBox)scene.lookup("#floor-smooth")).setSelected(false);
            ((javafx.scene.control.Slider)scene.lookup("#floor-motion")).setValue(100);
            if(!((javafx.scene.control.Label)scene.lookup("#floor-distance")).getText().contains("32.5"))throw new AssertionError("Distance did not follow movement");
            scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"floor-01-corridor");
            var map=scene.lookup("#floor-map");var bounds=map.localToScene(map.getBoundsInLocal());
            if(bounds.getMaxX()>scene.getWidth()||bounds.getMinX()<0)throw new AssertionError("Map clipped horizontally");
            ((javafx.scene.control.ComboBox<?>)scene.lookup("#floor-server-room")).getSelectionModel().select(7);
            ((javafx.scene.control.Button)scene.lookup("#floor-play")).fire();
        });
        await(()->((javafx.scene.control.Slider)auxiliary("#floor-map").lookup("#floor-motion")).getValue()>0);
        fx(()->{
            var scene=auxiliary("#floor-map");((javafx.scene.control.Button)scene.lookup("#floor-play")).fire();
            ((javafx.scene.control.ToggleButton)scene.lookup("#floor-live")).fire();
            if(!scene.lookup("#floor-activate").isDisabled())throw new AssertionError("Unconnected activation enabled");
            if(!scene.getRoot().lookupAll(".corridor-client").isEmpty())throw new AssertionError("Simulation leaked into live map");
            var sample=vn.edu.hutech.nqb.shared.fingerprint.FingerprintDemo.create();var data=object().put("enabled",true).put("epoch","qa");
            data.set("area",vn.edu.hutech.nqb.shared.WifiSurvey.JSON.valueToTree(sample.bundle().areas().getFirst()));data.set("plan",vn.edu.hutech.nqb.shared.WifiSurvey.JSON.valueToTree(vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan.DEFAULT));
            data.putArray("players").add(object().put("player_id","qa").put("nickname","Client QA").put("located",true).put("status","ESTIMATED").put("x",20).put("y",0).put("age_ms",1000));app.qaFloorMap().accept(data);
            scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"floor-02-live-fixture");
            if(scene.getRoot().lookupAll(".corridor-client").size()!=1)throw new AssertionError("Missing live point");
            ((com.fasterxml.jackson.databind.node.ObjectNode)data.path("players").path(0)).put("located",false).put("status","STALE");app.qaFloorMap().accept(data);
            if(!scene.getRoot().lookupAll(".corridor-client").isEmpty())throw new AssertionError("Stale position is still shown");
            app.qaFloorMap().close();
        });
        if(Files.exists(Path.of(System.getProperty("nqb.survey.database"))))throw new AssertionError("Simulation wrote survey storage");
    }
    private static void checkPhaseC(ArcadeApplication app)throws Exception {
        fx(()->{app.qaSettings();((javafx.scene.control.Button)auxiliary("#fingerprint-open").lookup("#fingerprint-open")).fire();});
        fx(()->{
            var scene=auxiliary("#fingerprint-dialog");if(!scene.lookup("#fingerprint-evaluate").isDisabled())throw new AssertionError("Empty evaluator enabled");
            ((javafx.scene.control.Button)scene.lookup("#fingerprint-demo")).fire();
        });
        await(()->!auxiliary("#fingerprint-dialog").lookup("#fingerprint-evaluate").isDisabled());
        fx(()->{
            var scene=auxiliary("#fingerprint-dialog");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-c-01-select-runs");
            var evaluateButton=scene.lookup("#fingerprint-evaluate");var bounds=evaluateButton.localToScene(evaluateButton.getBoundsInLocal());
            if(bounds.getMaxY()>scene.getHeight()||bounds.getMinY()<0)throw new AssertionError("Evaluate button outside window");
            var origin=(javafx.scene.control.Label)scene.lookup("#fingerprint-origin");if(origin.getHeight()+1<origin.prefHeight(origin.getWidth()))throw new AssertionError("Dataset provenance clipped");
            if(!((javafx.scene.control.Label)scene.lookup("#fingerprint-origin")).getText().contains("MÔ PHỎNG"))throw new AssertionError("Demo is not labelled");
            ((javafx.scene.control.Button)scene.lookup("#fingerprint-evaluate")).fire();
        });
        await(()->!auxiliary("#fingerprint-dialog").lookup("#fingerprint-export").isDisabled());
        fx(()->{
            var scene=auxiliary("#fingerprint-dialog");var table=(javafx.scene.control.TableView<vn.edu.hutech.nqb.shared.fingerprint.FingerprintEvaluation.Row>)scene.lookup("#fingerprint-results");
            if(table.getItems().size()!=3)throw new AssertionError("Missing holdout rows");
            if(!((javafx.scene.control.Label)scene.lookup("#fingerprint-metrics")).getText().contains("2/3"))throw new AssertionError("Missing rejected rate");
            scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-c-02-results");
            table.getSelectionModel().select(table.getItems().stream().filter(r->r.estimate().estimated()).findFirst().orElseThrow());
            ((javafx.scene.control.ToggleButton)scene.lookup("#fingerprint-map-tab")).fire();((javafx.scene.control.ScrollPane)scene.lookup("#fingerprint-scroll")).setVvalue(1);
        });
        fx(()->{var scene=auxiliary("#fingerprint-dialog");scene.getRoot().applyCss();scene.getRoot().layout();if(scene.getRoot().lookupAll(".fingerprint-estimate").size()!=2)throw new AssertionError("Estimated position not drawn");saveImage(scene.snapshot(null),"phase-c-03-map");});
        fx(()->{
            var scene=auxiliary("#fingerprint-dialog");var table=(javafx.scene.control.TableView<vn.edu.hutech.nqb.shared.fingerprint.FingerprintEvaluation.Row>)scene.lookup("#fingerprint-results");
            table.getSelectionModel().select(table.getItems().stream().filter(r->!r.estimate().estimated()).findFirst().orElseThrow());
            scene.getRoot().applyCss();scene.getRoot().layout();if(!scene.getRoot().lookupAll(".fingerprint-estimate").isEmpty())throw new AssertionError("Rejected row retained stale estimate");saveImage(scene.snapshot(null),"phase-c-04-rejected");
        });
        Path report=Files.createTempDirectory("nqb-eval-report-").resolve("report.json");fx(()->app.qaFingerprintView().exportTo(report));
        await(()->((javafx.scene.control.Label)auxiliary("#fingerprint-dialog").lookup("#fingerprint-status")).getText().startsWith("Đã xuất"));
        if(!vn.edu.hutech.nqb.shared.WifiSurvey.JSON.readTree(Files.readAllBytes(report)).path("datasetKind").asText().equals("SYNTHETIC_DEMO"))throw new AssertionError("Lost synthetic provenance");
        fx(()->{
            var scene=auxiliary("#fingerprint-dialog");((javafx.scene.control.ToggleButton)scene.lookup("#fingerprint-setup-tab")).fire();
            ((javafx.scene.control.Spinner<Integer>)scene.lookup("#fingerprint-k")).getValueFactory().setValue(3);
            if(!scene.lookup("#fingerprint-export").isDisabled()||!scene.lookup("#fingerprint-map-tab").isDisabled())throw new AssertionError("Changed K retained stale report");
        });
        Path input=report.resolveSibling("survey.json");Files.writeString(input,vn.edu.hutech.nqb.shared.WifiSurvey.encode(vn.edu.hutech.nqb.shared.fingerprint.FingerprintDemo.create().bundle()));
        fx(()->app.qaFingerprintView().loadFile(input));await(()->((javafx.scene.control.Label)auxiliary("#fingerprint-dialog").lookup("#fingerprint-origin")).getText().startsWith("KHẢO SÁT"));
        fx(()->{var scene=auxiliary("#fingerprint-dialog");if(!scene.lookup("#fingerprint-evaluate").isDisabled())throw new AssertionError("User holdouts preselected");
            ((javafx.scene.control.Button)scene.lookup("#fingerprint-toggle-run")).fire();if(scene.lookup("#fingerprint-evaluate").isDisabled())throw new AssertionError("Selecting a held-out run did not enable evaluation");
            ((javafx.scene.control.Button)scene.lookup("#fingerprint-toggle-run")).fire();if(!scene.lookup("#fingerprint-evaluate").isDisabled())throw new AssertionError("Removing last holdout did not disable evaluation");
            ((javafx.scene.control.Button)scene.lookup("#fingerprint-demo")).fire();app.qaCloseAuxiliary();});
        fx(()->{app.openFingerprint(null,app.ownerStage());var scene=auxiliary("#fingerprint-dialog");if(!((javafx.scene.control.Label)scene.lookup("#fingerprint-origin")).getText().equals("Chưa chọn dữ liệu."))throw new AssertionError("Closed view callback polluted reopened view");app.qaCloseAuxiliary();});
        if(Files.exists(Path.of(System.getProperty("nqb.survey.database"))))throw new AssertionError("Offline demo created survey database");
    }
    private static void checkPhaseB(ArcadeApplication app)throws Exception {
        class Fixture implements WifiSignalProvider,WifiScanProvider {
            public Reading readSignal(){return Reading.failure(WifiSignalProvider.Status.DISCONNECTED,"QA",System.currentTimeMillis());}
            public List<Adapter> scanAdapters(){return List.of(new Adapter("qa","Card Wi-Fi QA giả lập",true));}
            public WifiScanSnapshot scan(String adapter,String ssid){long now=System.currentTimeMillis();return new WifiScanSnapshot(UUID.randomUUID().toString(),"qa","Card Wi-Fi QA giả lập",ssid,"02:00:00:00:00:01",now,now,WifiScanSnapshot.Status.COMPLETE,"KIỂM THỬ UI · DỮ LIỆU GIẢ LẬP",0,List.of(
                new WifiObservation(ssid,"02:00:00:00:00:01",-50,5180000,(now+vn.edu.hutech.nqb.shared.WifiSurvey.WINDOWS_EPOCH_MS)*10000,0,true,true),new WifiObservation(ssid,"02:00:00:00:00:02",-66,5220000,(now+vn.edu.hutech.nqb.shared.WifiSurvey.WINDOWS_EPOCH_MS)*10000,0,true,false),new WifiObservation(ssid,"02:00:00:00:00:03",-80,5240000,(now-30000+vn.edu.hutech.nqb.shared.WifiSurvey.WINDOWS_EPOCH_MS)*10000,30000,false,false)));}
            public void cancelScan(){}public void close(){}
        }
        fx(()->{app.setProximityClientForTest(new ProximityClientService(new Fixture(),new ProximityCalibrationStore(output.resolve("qa-calibration.json")),1000,5000,30));app.openSurvey();});
        await(()->!auxiliary("#survey-dialog").lookup("#survey-new-area").isDisabled());
        fx(()->((javafx.scene.control.Button)auxiliary("#survey-dialog").lookup("#survey-new-area")).fire());
        fx(()->{
            var scene=auxiliary("#survey-area-editor");((TextField)scene.lookup("#survey-area-name")).setText("Khu QA giả lập · hành lang thẳng");((TextField)scene.lookup("#survey-collector")).setText("Nhóm kiểm thử UI");((TextField)scene.lookup("#survey-server-x")).setText("2");((TextField)scene.lookup("#survey-server-y")).setText("4");
            ((javafx.scene.control.TextArea)scene.lookup("#survey-point-entries")).setText("Cửa phòng; Hành lang; 0; 0\nBàn Server; Phòng; 2; 4\nMốc 5 m; Hành lang; 5; 0");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-b-01-area-editor");((javafx.scene.control.Button)scene.lookup("#survey-area-save")).fire();
        });
        await(()->((javafx.scene.control.ComboBox<?>)auxiliary("#survey-dialog").lookup("#survey-area")).getValue()!=null);
        fx(()->{
            var scene=auxiliary("#survey-dialog");((TextField)scene.lookup("#survey-device")).setText("Laptop QA · dữ liệu giả lập");((TextField)scene.lookup("#survey-orientation")).setText("Nhìn theo X tăng");((javafx.scene.control.ComboBox<Integer>)scene.lookup("#survey-duration")).setValue(20);((javafx.scene.control.Button)scene.lookup("#survey-start")).fire();
        });
        await(()->((javafx.scene.control.Label)auxiliary("#survey-dialog").lookup("#survey-status")).getText().contains("scan mới hợp lệ"));
        fx(()->{var scene=auxiliary("#survey-dialog");if(!scene.lookup("#survey-point").isDisabled()||!scene.lookup("#survey-adapter").isDisabled())throw new AssertionError("Capture context is editable");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-b-02-capturing");});
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(28);boolean[] finished={false};while(!finished[0]&&System.nanoTime()<end){Thread.sleep(100);fx(()->finished[0]=!auxiliary("#survey-dialog").lookup("#survey-start").isDisabled());}if(!finished[0])throw new AssertionError("Survey capture did not finish");
        await(()->((javafx.scene.control.TableView<?>)auxiliary("#survey-dialog").lookup("#survey-runs")).getItems().size()==1);
        fx(()->{var scene=auxiliary("#survey-dialog");((javafx.scene.control.ToggleButton)scene.lookup("#survey-saved-tab")).fire();((javafx.scene.control.TableView<?>)scene.lookup("#survey-runs")).getSelectionModel().selectFirst();if(scene.lookup("#survey-accept").isDisabled())throw new AssertionError("Valid sample cannot be accepted");});
        Platform.runLater(()->((javafx.scene.control.Button)auxiliary("#survey-dialog").lookup("#survey-accept")).fire());
        await(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w.isShowing()&&w.getScene().getRoot() instanceof javafx.scene.control.DialogPane d&&d.getHeaderText()!=null&&d.getHeaderText().equals("Xác nhận lượt thu")));
        fx(()->{for(var w:List.copyOf(javafx.stage.Window.getWindows()))if(w.isShowing()&&w.getScene().getRoot() instanceof javafx.scene.control.DialogPane d&&"Xác nhận lượt thu".equals(d.getHeaderText()))((javafx.scene.control.Button)d.lookupButton(javafx.scene.control.ButtonType.OK)).fire();});
        await(()->((javafx.scene.control.Label)auxiliary("#survey-dialog").lookup("#survey-status")).getText().startsWith("Đã xác nhận"));
        fx(()->{var scene=auxiliary("#survey-dialog");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-b-03-accepted");((javafx.scene.control.Button)((javafx.scene.control.DialogPane)scene.getRoot()).lookupButton(javafx.scene.control.ButtonType.CLOSE)).fire();app.openSurvey();});
        await(()->((javafx.scene.control.TableView<?>)auxiliary("#survey-dialog").lookup("#survey-runs")).getItems().size()==1);
        fx(()->{var scene=auxiliary("#survey-dialog");var table=(javafx.scene.control.TableView<?>)scene.lookup("#survey-runs");var run=(vn.edu.hutech.nqb.shared.WifiSurvey.Run)table.getItems().getFirst();if(run.state()!=vn.edu.hutech.nqb.shared.WifiSurvey.State.ACCEPTED||run.scans().size()<3)throw new AssertionError("Reopen lost accepted data");
            ((TextField)scene.lookup("#survey-device")).setText("Laptop QA");((TextField)scene.lookup("#survey-orientation")).setText("Quay lại");((javafx.scene.control.Button)scene.lookup("#survey-start")).fire();});
        await(()->!auxiliary("#survey-dialog").lookup("#survey-cancel").isDisabled());fx(()->((javafx.scene.control.Button)auxiliary("#survey-dialog").lookup("#survey-cancel")).fire());
        await(()->((javafx.scene.control.TableView<?>)auxiliary("#survey-dialog").lookup("#survey-runs")).getItems().size()==2);
        fx(()->{var scene=auxiliary("#survey-dialog");((javafx.scene.control.ToggleButton)scene.lookup("#survey-saved-tab")).fire();var table=(javafx.scene.control.TableView<?>)scene.lookup("#survey-runs");table.getSelectionModel().selectFirst();if(((vn.edu.hutech.nqb.shared.WifiSurvey.Run)table.getItems().getFirst()).state()!=vn.edu.hutech.nqb.shared.WifiSurvey.State.DISCARDED)throw new AssertionError("Cancelled run not discarded");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-b-04-cancelled");app.qaCloseAuxiliary();});
    }
    private static TlsQuizGateway connect() throws Exception { return TlsQuizGateway.connect(new Endpoint("127.0.0.1", port), trust, SECRET.toCharArray()); }
    private static void checkPhaseA(ArcadeApplication app)throws Exception {
        try(var host=connect();var opponent=connect();var proxy=new UiProxy(port)){
            request(host,"LOGIN",object().put("username","admin").put("password",SECRET));
            request(host,"QUESTION_CREATE",object().put("content","Kết nối lại có giữ đáp án đã nộp?").put("option_a","Có").put("option_b","Không").put("option_c","Chỉ đổi IP").put("option_d","Không rõ").put("correct_answer","A").put("category","QA"));
            String code=request(host,"ROOM_CONFIGURE",object().put("room_name","Kiểm thử phục hồi phiên · TLS thật").put("question_count",1).put("duration_seconds",20)).path("room_code").asText();
            request(opponent,"JOIN",object().put("room_code",code).put("nickname","Bạn kiểm thử"));request(opponent,"READY_SET",object().put("ready",true));
            fx(()->{
                app.qaStudentAuth();var root=app.qaScene().getRoot();root.applyCss();root.layout();
                ((javafx.scene.control.CheckBox)root.lookup("#direct-server-mode")).setSelected(true);
                ((TextField)root.lookup("#server-host")).setText("127.0.0.1");((TextField)root.lookup("#server-port")).setText(String.valueOf(proxy.listener.getLocalPort()));
                ((javafx.scene.control.Button)root.lookup("#server-apply")).fire();
            });
            await(()->app.qaScene().lookup("#lan-rooms") instanceof javafx.scene.control.ListView<?> list&&!list.getItems().isEmpty());
            fx(()->{snapshot(app,"phase-a-01-direct-address");fillForm(app,"Thí sinh roaming",code);app.qaClick("Tham gia phòng   →");});
            await(()->app.qaConnected()&&app.qaPhase().equals("WAITING"));
            fx(()->{app.qaCloseAuxiliary();app.qaClick("Sẵn sàng   →");});
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(request(host,"ROOM_STATE",object()).path("ready_count").asInt()<2){if(System.nanoTime()>until)throw new AssertionError("Ready missing");Thread.sleep(50);}
            request(host,"START_GAME",object());await(()->app.qaPhase().equals("QUESTION_ACTIVE"));fx(()->app.qaAnswer("A"));
            await(()->hasLabel(app,"Đã nhận đáp án A"));
            String id=request(host,"ROOM_STATE",object()).path("players").get(1).path("player_id").asText();
            proxy.down=true;proxy.cut();await(()->!app.qaConnected());
            fx(()->{if(!hasLabel(app,"Đang kết nối lại"))throw new AssertionError("Missing recovery status");snapshot(app,"phase-a-02-reconnecting");});
            proxy.down=false;await(app::qaConnected);await(()->hasLabel(app,"Đã nhận đáp án A"));
            var restored=request(host,"ROOM_STATE",object());if(!id.equals(restored.path("players").get(1).path("player_id").asText()))throw new AssertionError("Changed student identity");
            fx(()->snapshot(app,"phase-a-03-answer-restored"));request(host,"ABORT_GAME",object());await(()->app.qaPhase().equals("FINISHED"));
            fx(app::qaWelcome);
        }
        // UI fixture is explicitly labelled; native scan accuracy is tested separately.
        class Fixture implements WifiSignalProvider,WifiScanProvider {
            public Reading readSignal(){return Reading.failure(WifiSignalProvider.Status.DISCONNECTED,"QA",System.currentTimeMillis());}
            public List<Adapter> scanAdapters(){return List.of(new Adapter("qa","Card Wi-Fi QA giả lập",true));}
            public WifiScanSnapshot scan(String adapter,String ssid){long now=System.currentTimeMillis();return new WifiScanSnapshot("qa-scan","qa","Card QA",ssid,"02:00:00:00:00:01",now-100,now,WifiScanSnapshot.Status.COMPLETE,"KIỂM THỬ UI · DỮ LIỆU GIẢ LẬP",0,List.of(new WifiObservation("HUTECH E1","02:00:00:00:00:01",-51,5180000,0,50,true,true),new WifiObservation("HUTECH E1","02:00:00:00:00:02",-67,5220000,0,30000,false,false)));}
            public void cancelScan(){}public void close(){}
        }
        fx(()->{app.setProximityClientForTest(new ProximityClientService(new Fixture(),new ProximityCalibrationStore(output.resolve("qa-calibration.json")),1000,5000,30));WifiScanViews.open(app,app.proximityClient());});
        await(()->!auxiliary("#wifi-scan-dialog").lookup("#wifi-scan-start").isDisabled());
        fx(()->((javafx.scene.control.Button)auxiliary("#wifi-scan-dialog").lookup("#wifi-scan-start")).fire());
        await(()->((javafx.scene.control.TableView<?>)auxiliary("#wifi-scan-dialog").lookup("#wifi-scan-table")).getItems().size()==2);
        fx(()->{var scene=auxiliary("#wifi-scan-dialog");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"phase-a-04-scan-diagnostic");app.qaCloseAuxiliary();});
    }
    private static boolean hasLabel(ArcadeApplication app,String value){return app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().contains(value));}
    private static final class UiProxy implements AutoCloseable {
        final java.net.ServerSocket listener;final Set<java.net.Socket> sockets=ConcurrentHashMap.newKeySet();volatile boolean closed,down;
        UiProxy(int target)throws Exception{listener=new java.net.ServerSocket(0,16,java.net.InetAddress.getLoopbackAddress());Thread.ofVirtual().start(()->{while(!closed)try{var client=listener.accept();if(down){client.close();continue;}var server=new java.net.Socket("127.0.0.1",target);sockets.add(client);sockets.add(server);Thread.ofVirtual().start(()->copy(client,server));Thread.ofVirtual().start(()->copy(server,client));}catch(Exception ignored){}});}
        void copy(java.net.Socket a,java.net.Socket b){try{a.getInputStream().transferTo(b.getOutputStream());}catch(Exception ignored){}finally{shutdown(a);shutdown(b);}}
        void shutdown(java.net.Socket socket){sockets.remove(socket);try{socket.close();}catch(Exception ignored){}}
        void cut(){for(var socket:List.copyOf(sockets))shutdown(socket);}
        public void close(){closed=true;try{listener.close();}catch(Exception ignored){}cut();}
    }
    private static void checkAccessPoints(ArcadeApplication app) throws Exception {
        fx(() -> { app.qaAuth(); fillForm(app, "admin", ""); app.qaClick("Đăng nhập   →"); });
        await(app::qaConnected);
        uiRequest(app, "QUESTION_CREATE", object().put("content", "AP QA fixture").put("option_a", "A")
            .put("option_b", "B").put("option_c", "C").put("option_d", "D").put("correct_answer", "A").put("category", "QA"));
        var room = uiRequest(app, "ROOM_CONFIGURE", object().put("room_name", "Hành lang E1 · QA dữ liệu giả lập")
            .put("question_count", 1).put("duration_seconds", 5).put("proximity_enabled", false));
        await(() -> app.managedRoomSnapshot().path("is_owner").asBoolean());
        fx(app::openProximityMonitor);
        await(() -> app.qaMonitorStage() != null && !app.qaMonitorStage().getScene().lookup("#ap-open-config").isDisabled());
        fx(() -> ((javafx.scene.control.Button)app.qaMonitorStage().getScene().lookup("#ap-open-config")).fire());
        await(() -> javafx.stage.Window.getWindows().stream().anyMatch(w -> w.isShowing() && w.getScene().lookup("#ap-config-dialog") != null));
        fx(() -> {
            var scene = auxiliary("#ap-config-dialog");
            for (int i = 0; i < 2; i++) ((javafx.scene.control.Button)scene.lookup("#ap-add-row")).fire();
            var fields = scene.getRoot().lookupAll(".text-field").stream().filter(TextField.class::isInstance).map(TextField.class::cast).toList();
            for (var field : fields) {
                int row = javafx.scene.layout.GridPane.getRowIndex(field);
                switch (field.getAccessibleText()) {
                    case "Tên Wi-Fi (SSID)" -> field.setText("HUTECH E1");
                    case "BSSID" -> field.setText("aa:bb:cc:dd:ee:0" + row);
                    case "Tên AP" -> field.setText("AP" + row);
                    case "Tầng / hành lang" -> field.setText("Tầng 3 / hành lang A");
                    case "Thứ tự" -> field.setText(String.valueOf(row));
                    default -> throw new AssertionError("Unexpected AP field");
                }
            }
            scene.getRoot().applyCss(); scene.getRoot().layout(); saveImage(scene.snapshot(null), "ap-01-configuration");
            ((javafx.scene.control.Button)scene.lookup("#ap-save-config")).fire();
        });
        await(() -> javafx.stage.Window.getWindows().stream().noneMatch(w -> w.isShowing() && w.getScene().lookup("#ap-config-dialog") != null));
        if (uiRequest(app, "AP_CONFIG_GET").path("access_points").size() != 2) throw new AssertionError("UI AP save did not reach server");
        try (var client = connect()) {
            request(client, "JOIN", object().put("nickname", "Client QA giả lập").put("room_code", room.path("room_code").asText()));
            var report = object().put("config_epoch", room.path("proximity").path("config_epoch").asInt())
                .put("adapter_id", "qa-wifi").put("device_status", "CONNECTED").put("ssid", "HUTECH E1")
                .put("band", "5 GHz").put("rssi_dbm", -55).put("bssid", "aa:bb:cc:dd:ee:01");
            request(client, "PROXIMITY_REPORT", report.put("seq", 1));
            Thread.sleep(5100); request(client, "PROXIMITY_REPORT", report.put("seq", 2));
            Thread.sleep(5100); request(client, "PROXIMITY_REPORT", report.put("seq", 3).put("bssid", "aa:bb:cc:dd:ee:02"));
            Thread.sleep(5100); request(client, "PROXIMITY_REPORT", report.put("seq", 4));
            await(() -> app.latestProximitySnapshot().path("players").path(0).path("access_point").path("direction").asText().equals("FORWARD"));
            fx(() -> {
                var stage = app.qaMonitorStage(); var scene = stage.getScene();
                ((javafx.scene.control.ToggleButton)scene.lookup("#monitor-ap-mode")).fire();
                var table = (javafx.scene.control.TableView<?>)scene.lookup("#ap-monitor-table");
                table.getSelectionModel().selectFirst(); stage.setWidth(1024); stage.setHeight(640);
            });
            Thread.sleep(250);
            fx(() -> {
                var scene = app.qaMonitorStage().getScene(); scene.getRoot().applyCss(); scene.getRoot().layout();
                if (scene.lookup("#ap-monitor-table").getBoundsInParent().getHeight() < 80) throw new AssertionError("AP table clipped at compact size");
                saveImage(scene.snapshot(null), "ap-02-forward-1024");
                app.qaMonitorStage().setWidth(1366); app.qaMonitorStage().setHeight(768);
            });
            Thread.sleep(250);
            fx(() -> { var scene = app.qaMonitorStage().getScene(); scene.getRoot().applyCss(); scene.getRoot().layout(); saveImage(scene.snapshot(null), "ap-03-forward-1366"); });
        }
        fx(() -> app.qaMonitorStage().close());
    }
    private static JsonNode request(QuizGateway gateway, String type, com.fasterxml.jackson.databind.node.ObjectNode payload) throws Exception { return gateway.request(type, payload).get(8, TimeUnit.SECONDS); }
    private static void run(ArcadeApplication app) throws Exception {
        fx(() -> { assertLogin(app); snapshot(app,"00-login-role-selection"); });
        try (var host = connect(); var opponent = connect();var third=connect()) {
            request(host, "LOGIN", object().put("username", "admin").put("password", SECRET));
            if(request(host,"QUESTION_LIST",object()).path("total").asInt()!=0)throw new AssertionError("New Server must have an empty bank");
            for(int i=1;i<=20;i++)request(host,"QUESTION_CREATE",object().put("content","Test-only question "+i).put("option_a","A").put("option_b","B").put("option_c","C").put("option_d","D").put("correct_answer","A").put("category","QA"));
            request(host,"USER_CREATE",object().put("username","teacher.qa").put("display_name","Teacher QA").put("temporary_password",SECRET));
            fx(app::qaStudentAuth);
            await(()->app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().contains("Chờ giảng viên mở phòng.")));
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
                fx(()->{var timing=(javafx.scene.control.Label)app.qaScene().lookup("#answer-time");if(timing==null||timing.isManaged())throw new AssertionError("Previous response time leaked into next question");});
                fx(() -> { assertMusic(app, MusicSlots.Track.GAME); snapshot(app, "03-question-" + number); app.qaAnswer("A"); });
                if(number==1){var state=request(host,"ROOM_STATE",object());request(opponent,"ANSWER",object().put("session_id",state.path("session_id").asText()).put("question_id",state.path("question").path("question_id").asInt()).put("answer","A"));}
                await(() -> app.qaScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof javafx.scene.control.Label l && l.getText().contains("Đã nhận đáp án A")));
                fx(()->{var timing=(javafx.scene.control.Label)app.qaScene().lookup("#answer-time");if(!timing.isManaged()||!timing.getText().matches("[0-9]+,[0-9]{2} s"))throw new AssertionError("Server response time missing beside nickname");if(timing.getFont().getSize()>14)throw new AssertionError("Answer time is not compact");snapshot(app,"03-answer-time-"+number);});
                await(() -> app.qaPhase().equals("SHOW_RESULT")); fx(() -> { assertMusic(app, MusicSlots.Track.GAME); snapshot(app, "04-result-" + number); });
                if(number==2)fx(()->{var streak=(javafx.scene.control.Label)app.qaScene().lookup("#answer-streak");if(!streak.isManaged()||!streak.getText().equals("Chuỗi ×2")||app.qaResult().path("streak_bonus").asInt()!=20)throw new AssertionError("Correct-answer streak not rendered");if(!javafx.scene.paint.Color.web("#202549").equals(streak.getTextFill()))throw new AssertionError("Streak text lost contrast against lime badge");});
                await(() -> app.qaPhase().equals("SHOW_RANKING")); Thread.sleep(1400); fx(() -> { assertMusic(app, MusicSlots.Track.GAME); app.qaAssertRanksSettled(); snapshot(app, "05-ranking-" + number); });
            }
            await(() -> app.qaPhase().equals("FINISHED"));Thread.sleep(950); fx(() -> { assertMusic(app, MusicSlots.Track.VICTORY); if (!app.qaResult().path("result_saved").asBoolean() || app.qaResult().path("ranking").size() != 3 || app.qaScene().getRoot().lookupAll(".victory-column").size()!=3) throw new AssertionError("UI final result/podium missing persistence/ranking"); snapshot(app, "06-live-final"); });
            request(host, "ROOM_RESET", object()); await(() -> app.qaPhase().equals("WAITING")); fx(() -> { assertMusic(app, MusicSlots.Track.NONE); snapshot(app, "07-reset-lobby"); });
            request(host, "ROOM_CLOSE", object()); await(() -> !app.qaConnected());
        }
        fx(() -> { app.qaAuth(); fillForm(app, "admin", ""); app.qaClick("Đăng nhập   →"); });
        await(app::qaConnected);checkManagedTopology(app);fx(app::bankView); await(() -> tableSize(app) == 10); fx(() -> snapshot(app, "08-live-bank"));
        fx(app::historyView); await(() -> tableSize(app) == 1); fx(() -> snapshot(app, "09-live-history"));
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
        checkQuestionSetsUi(app);
        checkTopologyLayouts(app);
        checkProximityUi(app);
        checkHistoryReuseUi(app);
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
    private static void assertLogin(ArcadeApplication app) {
        if(app.qaConnected())throw new AssertionError("Unauthenticated screen has a session");
        var role=(javafx.scene.control.ComboBox<?>)app.qaScene().lookup("#login-role");
        if(role==null || role.getValue()!=null || !((javafx.scene.control.Button)app.qaScene().lookup("#login-submit")).isDisabled())throw new AssertionError("Startup/logout did not reset role");
        if(app.qaScene().getRoot().lookupAll(".button").stream().anyMatch(n -> n instanceof javafx.scene.control.Button b && (b.getText().contains("Xem thử") || b.getText().equals("Người dùng"))))throw new AssertionError("Unauthenticated mock/Admin entry remains");
    }
    private static javafx.scene.Scene importScene(){return javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#import-confirm")!=null).findFirst().orElseThrow().getScene();}
    private static void importDetails(){if(importScene().lookup("#import-tabs") instanceof javafx.scene.control.TabPane tabs)tabs.getSelectionModel().select(1);importScene().getRoot().applyCss();importScene().getRoot().layout();}
    private static javafx.scene.control.Button importConfirm(){return (javafx.scene.control.Button)importScene().lookup("#import-confirm");}
    private static JsonNode uiRequest(ArcadeApplication app,String type)throws Exception {return uiRequest(app,type,object());}
    private static JsonNode uiRequest(ArcadeApplication app,String type,com.fasterxml.jackson.databind.node.ObjectNode payload)throws Exception {var result=new CompletableFuture<JsonNode>();fx(()->app.call(type,payload,result::complete,result::completeExceptionally));return result.get(8,TimeUnit.SECONDS);}
    private static Scene auxiliary(String selector){return javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup(selector)!=null).findFirst().orElseThrow().getScene();}
    private static void checkQuestionSetsUi(ArcadeApplication app)throws Exception {
        for(int i=0;i<12;i++)uiRequest(app,"QUESTION_CREATE",object().put("content","Câu kiểm thử chọn nhiều trang số "+i).put("option_a","TCP").put("option_b","UDP").put("option_c","IP").put("option_d","ARP").put("correct_answer","A").put("category","QA tạm"));
        fx(()->{app.setBankQuestionSet(0);app.bankView();});await(()->tableSize(app)==10);
        fx(()->{((javafx.scene.control.Button)app.qaScene().lookup("#bank-select-page")).fire();app.qaClick("Sau →");});await(()->tableSize(app)==5);
        fx(()->{((javafx.scene.control.Button)app.qaScene().lookup("#bank-select-page")).fire();if(app.bankSelectedQuestions().size()!=15)throw new AssertionError("Selection did not survive pagination");snapshot(app,"13m-bank-cross-page-selection");((javafx.scene.control.Button)app.qaScene().lookup("#bank-manage-sets")).fire();});
        await(()->!((javafx.scene.control.Button)auxiliary("#question-sets-table").lookup("#set-create-selected")).isDisabled());
        fx(()->{var scene=auxiliary("#question-sets-table");((TextField)scene.lookup("#question-set-name")).setText("Bộ QA đa trang");((javafx.scene.control.Button)scene.lookup("#set-create-selected")).fire();});
        await(()->((javafx.scene.control.Label)auxiliary("#question-sets-table").lookup("#question-set-status")).getText().startsWith("Đã tạo"));
        fx(()->{var scene=auxiliary("#question-sets-table");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13n-question-sets");app.qaCloseAuxiliary();app.roomConfig();});
        await(()->!((javafx.scene.control.ComboBox<?>)auxiliary("#room-question-set").lookup("#room-question-set")).isDisabled());
        fx(()->{var scene=auxiliary("#room-question-set");@SuppressWarnings("unchecked") var choices=(javafx.scene.control.ComboBox<QuestionSets.Choice>)scene.lookup("#room-question-set");choices.getItems().stream().filter(v->v.name().equals("Bộ QA đa trang")).findFirst().ifPresentOrElse(choices::setValue,()->{throw new AssertionError("Created set missing in room choices");});((javafx.scene.control.Spinner<Integer>)scene.lookup("#room-question-count")).getValueFactory().setValue(1);var proximity=(javafx.scene.control.CheckBox)scene.lookup("#room-proximity-enabled");if(proximity.isSelected())proximity.fire();scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13o-room-question-source");scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof javafx.scene.control.Button b&&b.getText().equals("Lưu cấu hình")).map(n->(javafx.scene.control.Button)n).findFirst().orElseThrow().fire();});
        await(()->javafx.stage.Window.getWindows().stream().noneMatch(w->w.isShowing()&&w.getScene().lookup("#room-question-set")!=null));var state=uiRequest(app,"ROOM_STATE");if(state.path("question_set_id").asInt()==0)throw new AssertionError("Room source not persisted");
        uiRequest(app,"ROOM_CLOSE");fx(()->{app.clearBankSelection();app.setBankQuestionSet(0);});
        System.out.println("QUESTION_SETS_UI_PASS: cross-page selection, create set, room source, no duplicate LAN controls");
    }
    private static void checkHistoryReuseUi(ArcadeApplication app)throws Exception {
        fx(app::historyView);await(()->tableSize(app)>=1);
        fx(()->{((javafx.scene.control.TableView<?>)app.qaScene().lookup("#history-matches")).getSelectionModel().selectFirst();app.qaClick("Xem bảng điểm");});
        await(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w.isShowing()&&w.getScene().lookup("#history-reuse")!=null));
        fx(()->{var scene=auxiliary("#history-reuse");scene.getRoot().applyCss();scene.getRoot().layout();if(scene.lookup("#history-ranking")==null)throw new AssertionError("History podium missing");saveImage(scene.snapshot(null),"13p-history-podium");((javafx.scene.control.Button)scene.lookup("#history-reuse")).fire();});
        await(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w.isShowing()&&w.getScene().lookup("#room-reuse-history")!=null));
        fx(()->{var scene=auxiliary("#room-reuse-history");if(!((javafx.scene.control.CheckBox)scene.lookup("#room-reuse-history")).isSelected())throw new AssertionError("History questions not selected");scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13q-reuse-history-source");app.qaCloseAuxiliary();});
        System.out.println("HISTORY_REUSE_UI_PASS: persisted ranking/podium, reuse opens room with saved snapshot");
    }
    private static void checkManagedTopology(ArcadeApplication app)throws Exception {
        var configured=uiRequest(app,"ROOM_CONFIGURE",object().put("room_name","Sơ đồ lớp LAN").put("question_count",2).put("duration_seconds",5));
        try(var a=connect();var b=connect()){
            request(a,"JOIN",object().put("room_code",configured.path("room_code").asText()).put("nickname","Máy A"));request(b,"JOIN",object().put("room_code",configured.path("room_code").asText()).put("nickname","Máy B"));
            fx(app::roomView);await(()->app.qaScene().lookup("#lan-topology")!=null&&app.qaScene().getRoot().lookupAll(".topology-player").size()==2);
            fx(()->{snapshot(app,"00-real-manager-topology");app.qaClick("Trình chiếu ↗");});
            fx(()->{var shown=javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#projected-topology")!=null).findFirst().orElseThrow().getScene();shown.getRoot().applyCss();shown.getRoot().layout();if(shown.getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&l.getText().contains("127.0.0.1")))throw new AssertionError("Projection exposes address");saveImage(shown.snapshot(null),"00-real-projected-topology");app.qaCloseAuxiliary();});
            uiRequest(app,"ROOM_CLOSE");
        }
    }
    private static void checkTopologyLayouts(ArcadeApplication app)throws Exception {
        fx(()->{
            var room=object().put("room_code","QA-LAN");var roster=room.putArray("players");var snap=object().put("room_code","QA-LAN").put("anchor_ssid","Điểm phát demo").put("enabled",true);var rows=snap.putArray("players");var ranks=object().putArray("ranking");
            for(int i=0;i<10;i++){var p=object().put("player_id","test-"+i).put("nickname","Sinh viên có biệt danh "+i).put("avatar_id","quizling-"+(i+1)).put("ready",i<8).put("connected",true);roster.add(p);rows.add(p.deepCopy().put("ping_ms",8+i).put("ping_age_ms",0).put("measurement_age_ms",0).put("estimated_meters",2+i*.5).put("status_label","Đang đo").put("status","MEASURING").put("ip_address","10.0.0."+i).put("bssid","aa:bb:cc:dd:ee:01"));ranks.add(p.deepCopy().put("rank",1).put("score",300).put("correct_count",2));}
            var view=new LanTopologyView(true,null);view.update(room,snap);var lifecycle=new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),e->{}));lifecycle.setCycleCount(javafx.animation.Timeline.INDEFINITE);FxKit.runWhenShowing(view,lifecycle);var stage=new javafx.stage.Stage();var scene=new Scene(FxKit.scroll(view),app.qaScene().getWidth(),app.qaScene().getHeight());scene.getStylesheets().add(FxKit.CSS);stage.setScene(scene);stage.show();scene.getRoot().applyCss();scene.getRoot().layout();
            var players=new ArrayList<>(scene.getRoot().lookupAll(".topology-player"));if(players.size()!=10)throw new AssertionError("Missing topology players");
            for(int i=0;i<players.size();i++){var a=players.get(i);if(a.getBoundsInParent().getMinX()<-2||a.getBoundsInParent().getMaxX()>a.getParent().getLayoutBounds().getWidth()+2)throw new AssertionError("Topology node clipped");for(int j=i+1;j<players.size();j++)if(a.getBoundsInParent().intersects(players.get(j).getBoundsInParent()))throw new AssertionError("Topology player nodes overlap");}
            if(scene.getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof javafx.scene.control.Label l&&(l.getText().contains("10.0.0.")||l.getText().contains("aa:bb"))))throw new AssertionError("Public topology exposes private fields");
            saveImage(scene.snapshot(null),"13r-topology-ten-players");if(lifecycle.getStatus()!=javafx.animation.Animation.Status.RUNNING)throw new AssertionError("Visible topology timer did not run");stage.close();if(lifecycle.getStatus()!=javafx.animation.Animation.Status.STOPPED)throw new AssertionError("Hidden topology timer kept running");
            var results=ResultsLayout.create(ranks,"",true,true,"qa-tied-ranking");var resultStage=new javafx.stage.Stage();var resultScene=new Scene(FxKit.scroll(results),app.qaScene().getWidth(),app.qaScene().getHeight());resultScene.getStylesheets().add(FxKit.CSS);resultStage.setScene(resultScene);resultStage.show();resultScene.getRoot().applyCss();resultScene.getRoot().layout();if(resultScene.getRoot().lookupAll(".victory-column").size()!=10)throw new AssertionError("Tied winners were dropped");saveImage(resultScene.snapshot(null),"13s-ten-tied-winners");resultStage.close();
        });
        System.out.println("TOPOLOGY_LAYOUT_UI_PASS: ten nodes, no overlap, private data omitted on projection, all ties retained");
    }
    private static void checkQuestionImport(ArcadeApplication app)throws Exception {
        Path file=output.resolve("qa-only-import.xlsx");var bad=new ArrayList<>(QuestionFilesTest.VALUES);bad.set(5,"E");var another=new ArrayList<>(QuestionFilesTest.VALUES);another.set(0,"Câu hỏi thứ hai có xuống dòng\ntrong bản xem trước");
        QuestionFilesTest.excelFixture(file,List.of(QuestionFilesTest.VALUES,QuestionFilesTest.VALUES,bad,another));
        QuestionImportView[] view={null};fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(file);});
        await(()->!importConfirm().isDisabled());
        fx(()->{var scene=importScene();scene.getRoot().applyCss();scene.getRoot().layout();if(!importConfirm().getText().equals("Nhập 2 câu hỏi"))throw new AssertionError("Import did not default to valid unique rows: "+importConfirm().getText());if(!((javafx.scene.control.Label)scene.lookup("#import-count")).getText().contains("Lỗi 1"))throw new AssertionError("Import errors not shown");saveImage(scene.snapshot(null),"13a-import-preview-excel");});
        if(uiRequest(app,"QUESTION_LIST").path("total").asInt()!=0)throw new AssertionError("Preview mutated the bank");
        fx(()->{var table=(javafx.scene.control.TableView<?>)importScene().lookup("#import-table");table.getSelectionModel().select(2);importDetails();saveImage(importScene().snapshot(null),"13b-import-row-error");app.qaCloseAuxiliary();});
        if(uiRequest(app,"QUESTION_LIST").path("total").asInt()!=0)throw new AssertionError("Cancel mutated the bank");
        fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(file);});await(()->!importConfirm().isDisabled());fx(()->importConfirm().fire());await(()->tableSize(app)==2);fx(()->snapshot(app,"13c-import-saved-bank"));
        fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(file);});await(()->((javafx.scene.control.Label)importScene().lookup("#import-status")).getText().startsWith("Xem lại"));
        fx(()->{if(!importConfirm().isDisabled())throw new AssertionError("Bank duplicates selected by default");((javafx.scene.control.CheckBox)importScene().lookup("#import-allow-duplicates")).fire();if(importConfirm().isDisabled()||!importConfirm().getText().equals("Nhập 3 câu hỏi"))throw new AssertionError("Explicit duplicate option not applied");saveImage(importScene().snapshot(null),"13d-import-duplicates");app.qaCloseAuxiliary();});
        Path templates=output.resolve("templates");Files.createDirectories(templates);for(var format:QuestionFiles.Format.values()){Path target=templates.resolve("Mau_cau_hoi_QuizBattle."+format.extension);QuestionFiles.writeTemplate(target,format);if(!QuestionFiles.readSafely(target).isEmpty())throw new AssertionError("Template contains mock data");}
        fx(()->{QuestionImportView.showTemplates(app,app.ownerStage());var scene=javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#template-status")!=null).findFirst().orElseThrow().getScene();scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13e-template-preview");app.qaCloseAuxiliary();});
        Path word=output.resolve("qa-only-import.docx");QuestionFiles.writeTemplate(word,QuestionFiles.Format.WORD);
        try(var in=Files.newInputStream(word);var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(in)){var values=new ArrayList<>(QuestionFilesTest.VALUES);values.set(0,"Câu hỏi được nhập từ Word");for(int c=0;c<7;c++)doc.getTables().getFirst().getRow(1).getCell(c).setText(values.get(c));try(var out=Files.newOutputStream(word)){doc.write(out);}}
        fx(()->{view[0]=new QuestionImportView(app);view[0].show();view[0].loadFile(word);});await(()->!importConfirm().isDisabled());fx(()->{importDetails();saveImage(importScene().snapshot(null),"13f-import-preview-word");importConfirm().fire();});await(()->tableSize(app)==3);
        if(uiRequest(app,"QUESTION_LIST").path("total").asInt()!=3)throw new AssertionError("Excel + Word not committed");
        System.out.println("QUESTION_IMPORT_UI_PASS: XLSX/DOCX, preview, validation, duplicate opt-in, cancel, selected atomic commit, template roundtrip");
    }
    private static ProximityClientService attachFreshCapture(QuizGateway transport,Path profiles,int epoch,java.util.concurrent.atomic.AtomicReference<String> session){
        WifiSignalProvider provider=new WifiSignalProvider(){public Reading readSignal(){return Reading.connected("a1","Wi-Fi QA","Lab-AP","e4:60:4d:b5:6e:48","5 GHz",161,5805000,-61,70,System.currentTimeMillis());}public void close(){}};
        var service=new ProximityClientService(provider,new ProximityCalibrationStore(profiles),1000,5000,30);
        // Existing fixture reports used seq=1; continue their real TLS connection's sequence.
        QuizGateway relay=new QuizGateway(){public CompletableFuture<JsonNode> request(String type,com.fasterxml.jackson.databind.node.ObjectNode data){var payload=data.deepCopy();if(type.equals("PROXIMITY_REPORT"))payload.put("seq",100+payload.path("seq").asLong());return transport.request(type,payload);}public void onEvent(java.util.function.Consumer<JsonNode> listener){}public boolean isPreview(){return false;}public void close(){}};
        transport.onEvent(event->{if(event.path("type").asText().equals("PROXIMITY_REFRESH_REQUEST")){var data=event.path("payload");service.requestFreshSamples(relay,data.path("refresh_id").asText(),data.path("config_epoch").asInt(),data.path("session_id").asText(),()->epoch,session::get,null);}});
        return service;
    }
    private static void checkProximityUi(ArcadeApplication app) throws Exception {
        // Permission is requested by an action; Settings is only the recovery path.
        fx(() -> {
            new ProximityViews(app).showDiagnosticDialog(
                WifiSignalProvider.Reading.failure(WifiSignalProvider.Status.PERMISSION_DENIED, "adapter-qa", "Intel AX201", "ERROR_ACCESS_DENIED", System.currentTimeMillis()),
                "Lab-AP", "e4:60:4d:b5:6e:48", "5 GHz", null
            );
            var scene = javafx.stage.Window.getWindows().stream().filter(w -> w.isShowing() && w.getScene().lookup("#proximity-diagnostic-message") != null).findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss(); scene.getRoot().layout();
            String msg = ((javafx.scene.control.Label) scene.lookup("#proximity-diagnostic-message")).getText();
            if (!msg.contains("QuizBattle cần quyền vị trí của Windows")) {
                throw new AssertionError("Unexpected permission dialog text: " + msg);
            }
            if (scene.lookup("#proximity-request-access") == null || scene.lookup("#proximity-later") == null || scene.lookup("#proximity-open-settings").isVisible()) {
                throw new AssertionError("Initial permission dialog must offer consent before Settings");
            }
            saveImage(scene.snapshot(null), "13g-proximity-permission-dialog");
            ((javafx.scene.control.Button)scene.lookup("#proximity-later")).fire();
        });
        checkPermissionRecovery(app);

        // 2. Verify 2-step Calibration Wizard UI
        fx(() -> {
            new ProximityViews(app).showCalibrationWizard(
                app.proximityClient(),
                WifiSignalProvider.Reading.connected("adapter-qa", "Intel AX201", "Lab-AP", "e4:60:4d:b5:6e:48", "5 GHz", 161, 5805000, -47, 88, System.currentTimeMillis()),
                "Lab-AP", "e4:60:4d:b5:6e:48", "5 GHz", 1, null
            );
            var scene = javafx.stage.Window.getWindows().stream().filter(w -> w.isShowing() && w.getScene().lookup("#calibration-start-1m") != null).findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss(); scene.getRoot().layout();
            saveImage(scene.snapshot(null), "13h-proximity-calibration-wizard");
            app.qaCloseAuxiliary();
        });

        // 3. Configure room with proximity monitoring enabled, open standalone Monitor Window, and verify it stays open across question transitions
        var roomConfigured = new CompletableFuture<JsonNode>();
        fx(() -> app.call("ROOM_CONFIGURE", object()
            .put("room_name", "Phòng Giám sát Wi-Fi")
            .put("question_count", 1)
            .put("duration_seconds", 8)
            .put("proximity_enabled", true)
            .put("anchor_ssid", "Lab-AP")
            .put("anchor_bssid", "e4:60:4d:b5:6e:48")
            .put("anchor_band", "5 GHz")
            .put("threshold_meters", 10), roomConfigured::complete, roomConfigured::completeExceptionally));
        JsonNode stateNode = roomConfigured.get(8, TimeUnit.SECONDS);
        String code = stateNode.path("room_code").asText();
        int epoch = stateNode.path("proximity").path("config_epoch").asInt();

        fx(app::openProximityMonitor);
        await(() -> app.qaMonitorStage() != null && app.qaMonitorStage().isShowing());
        fx(()->{var top=(javafx.scene.layout.VBox)((javafx.scene.layout.BorderPane)app.qaMonitorStage().getScene().getRoot()).getTop();top.getChildren().add(0,FxKit.text("KIỂM THỬ · RSSI GIẢ LẬP · KHÔNG PHẢI SỐ ĐO THỰC ĐỊA","caption"));});

        try (var s1 = connect(); var s2 = connect()) {
            var captureSession=new java.util.concurrent.atomic.AtomicReference<>("");
            try(var capture1=attachFreshCapture(s1,output.resolve("qa-fresh-1.json"),epoch,captureSession);var capture2=attachFreshCapture(s2,output.resolve("qa-fresh-2.json"),epoch,captureSession)){
            request(s1, "JOIN", object().put("nickname", "SV Đo 1").put("room_code", code));
            request(s2, "JOIN", object().put("nickname", "SV Đo 2").put("room_code", code));
            for (var s : List.of(s1, s2)) {
                var cal = object().put("config_epoch", epoch).put("adapter_id", "a1").put("adapter_name", "Intel Wi-Fi 6").put("bssid", "e4:60:4d:b5:6e:48").put("band", "5 GHz");
                var a1 = cal.putArray("samples_1m"); var a5 = cal.putArray("samples_5m");
                for (int i = 0; i < 22; i++) { a1.add(-45); a5.add(-61); }
                request(s, "PROXIMITY_CALIBRATION", cal);
                var rep = object().put("config_epoch", epoch).put("seq", 1).put("device_status", "CONNECTED").put("adapter_id", "a1").put("adapter_name", "Intel Wi-Fi 6").put("ssid", "Lab-AP").put("bssid", "e4:60:4d:b5:6e:48").put("band", "5 GHz").put("channel", 161).put("rssi_dbm", s == s1 ? -52 : -76).put("signal_quality", 85);
                var samples = rep.putArray("samples");
                for (int i = 0; i < 5; i++) samples.add(s == s1 ? -52 : -76);
                request(s, "PROXIMITY_REPORT", rep);
                request(s, "READY_SET", object().put("ready", true));
            }
            fx(app::refreshProximityMonitor);
            await(() -> {
                var stage = app.qaMonitorStage();
                if (stage == null) return false;
                var tbl = (javafx.scene.control.TableView<?>) stage.getScene().lookup("#proximity-monitor-table");
                return tbl != null && tbl.getItems().size() == 2;
            });
            fx(() -> {
                var mScene = app.qaMonitorStage().getScene();
                mScene.getRoot().applyCss(); mScene.getRoot().layout();
                saveImage(mScene.snapshot(null), "13i-proximity-monitor-live");
            });

            // Start match on teacher UI and verify Monitor Window stays open across QUESTION_ACTIVE and FINISHED
            var startFut = new CompletableFuture<JsonNode>();
            fx(() -> app.call("START_GAME", object(), startFut::complete, startFut::completeExceptionally));
            startFut.get(8, TimeUnit.SECONDS);

            await(() -> app.qaPhase().equals("QUESTION_ACTIVE"));
            fx(() -> {
                if (app.qaMonitorStage() == null || !app.qaMonitorStage().isShowing()) {
                    throw new AssertionError("Monitor window closed during question transition");
                }
            });
            var currentState = uiRequest(app, "ROOM_STATE");
            String sid = currentState.path("session_id").asText();
            captureSession.set(sid);
            int qid = currentState.path("question").path("question_id").asInt();
            request(s1, "ANSWER", object().put("session_id", sid).put("question_id", qid).put("answer", "A"));
            request(s2, "ANSWER", object().put("session_id", sid).put("question_id", qid).put("answer", "A"));
            fx(()->((javafx.scene.control.Button)app.qaMonitorStage().getScene().lookup("#proximity-monitor-refresh")).fire());
            await(()->app.latestProximitySnapshot().path("refresh").path("state").asText().equals("RUNNING"));
            await(()->app.latestProximitySnapshot().path("refresh").path("updated").asInt()==2);
            Thread.sleep(300); // Allow the cooldown/progress control's next display tick.
            fx(()->{var scene=app.qaMonitorStage().getScene();scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13i2-refreshed-during-game");((javafx.scene.control.ToggleButton)scene.lookup("#monitor-list-mode")).fire();scene.getRoot().applyCss();scene.getRoot().layout();saveImage(scene.snapshot(null),"13i3-monitor-list");});

            await(() -> app.qaPhase().equals("FINISHED"));
            fx(() -> {
                if (app.qaMonitorStage() == null || !app.qaMonitorStage().isShowing()) {
                    throw new AssertionError("Monitor window closed at match finish");
                }
                app.qaMonitorStage().close();
            });
            var closeFut = new CompletableFuture<JsonNode>();
            fx(() -> app.call("ROOM_CLOSE", object(), closeFut::complete, closeFut::completeExceptionally));
            closeFut.get(8, TimeUnit.SECONDS);
            }
        }

        // 4. Verify Proximity History view in History screen
        fx(app::historyView);
        await(() -> tableSize(app) >= 1);
        fx(() -> ((javafx.scene.control.Button) app.qaScene().lookup("#history-proximity-list-btn")).fire());
        await(() -> app.qaScene().lookup("#proximity-history-table") instanceof javafx.scene.control.TableView<?> t && !t.getItems().isEmpty());
        fx(() -> snapshot(app, "13j-proximity-history-list"));
        var historyDetail = new CompletableFuture<JsonNode>();
        fx(() -> {
            var table = (javafx.scene.control.TableView<?>) app.qaScene().lookup("#proximity-history-table");
            var first = (JsonNode) table.getItems().getFirst();
            app.call("PROXIMITY_HISTORY_GET", object().put("session_id", first.path("session_id").asText()), historyDetail::complete, historyDetail::completeExceptionally);
        });
        var detail = historyDetail.get(8, TimeUnit.SECONDS);
        fx(() -> {
            new ProximityViews(app).showProximityHistoryDetail(detail);
            var scene = javafx.stage.Window.getWindows().stream().filter(w -> w.isShowing() && w.getScene().lookup("#proximity-history-detail") != null).findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss(); scene.getRoot().layout();
            saveImage(scene.snapshot(null), "13k-proximity-history-detail");
            app.qaCloseAuxiliary();
        });
        System.out.println("PROXIMITY_UI_PASS: Permission dialog, 2-step calibration wizard, live monitor window persisting across questions, proximity history");
    }
    private static Scene permissionScene() {
        return javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().lookup("#proximity-diagnostic-message")!=null).findFirst().orElseThrow().getScene();
    }
    private static void checkPermissionRecovery(ArcadeApplication app) throws Exception {
        var allowed=WifiSignalProvider.Reading.connected("adapter-qa","Intel AX201","Lab-AP","e4:60:4d:b5:6e:48","5 GHz",161,5805000,-47,88,System.currentTimeMillis());
        var denied=WifiSignalProvider.Reading.failure(WifiSignalProvider.Status.PERMISSION_DENIED,"Windows access denied",System.currentTimeMillis());
        var resumed=new java.util.concurrent.atomic.AtomicInteger();
        var decision=new CompletableFuture<WindowsWifiPermission.Result>();
        fx(()->{
            new ProximityViews(app,()->decision,callback->callback.accept(allowed),()->{throw new AssertionError("Granted permission must not open Settings");})
                .showDiagnosticDialog(denied,"Lab-AP","e4:60:4d:b5:6e:48","5 GHz",resumed::incrementAndGet);
            saveImage(permissionScene().snapshot(null),"13g-proximity-permission-dialog");
            ((javafx.scene.control.Button)permissionScene().lookup("#proximity-request-access")).fire();
        });
        decision.complete(new WindowsWifiPermission.Result(WindowsWifiPermission.Status.ALLOWED,"QA"));
        await(()->resumed.get()==1);

        var reading=new java.util.concurrent.atomic.AtomicReference<>(denied);
        javafx.stage.Stage[] settingsWindow={null};javafx.stage.Window[] consentWindow={null};
        fx(()->{
            new ProximityViews(app,()->CompletableFuture.completedFuture(new WindowsWifiPermission.Result(WindowsWifiPermission.Status.DENIED,"QA")),
                callback->callback.accept(reading.get()),()->{Platform.runLater(()->{
                    settingsWindow[0]=new javafx.stage.Stage();settingsWindow[0].setTitle("QA settings stand-in");
                    settingsWindow[0].setScene(new Scene(new javafx.scene.control.Label("QA only"),240,100));settingsWindow[0].show();settingsWindow[0].requestFocus();
                });return true;})
                .showDiagnosticDialog(denied,"Lab-AP","e4:60:4d:b5:6e:48","5 GHz",resumed::incrementAndGet);
            consentWindow[0]=permissionScene().getWindow();
            ((javafx.scene.control.Button)permissionScene().lookup("#proximity-request-access")).fire();
        });
        await(()->permissionScene().lookup("#proximity-open-settings").isVisible());
        fx(()->{
            var scene=permissionScene();scene.getRoot().applyCss();scene.getRoot().layout();
            var status=(javafx.scene.control.Label)scene.lookup("#proximity-permission-status");
            if(status.getHeight()+1<status.prefHeight(status.getWidth()))throw new AssertionError("Permission recovery instructions are clipped");
            saveImage(scene.snapshot(null),"13g2-proximity-permission-recovery");((javafx.scene.control.Button)scene.lookup("#proximity-open-settings")).fire();
        });
        await(()->settingsWindow[0]!=null&&settingsWindow[0].isShowing()&&!consentWindow[0].isFocused());
        reading.set(allowed);
        fx(()->{settingsWindow[0].close();consentWindow[0].requestFocus();});
        await(()->resumed.get()==2);

        var cancelled=new CompletableFuture<WindowsWifiPermission.Result>();
        fx(()->{
            new ProximityViews(app,()->cancelled,callback->{throw new AssertionError("Closed permission dialog must ignore late results");},()->true)
                .showDiagnosticDialog(denied,"Lab-AP","e4:60:4d:b5:6e:48","5 GHz",resumed::incrementAndGet);
            ((javafx.scene.control.Button)permissionScene().lookup("#proximity-request-access")).fire();
            ((javafx.scene.control.Button)permissionScene().lookup("#proximity-later")).fire();
        });
        if(!cancelled.isCancelled()||resumed.get()!=2)throw new AssertionError("Permission cancellation leaked a callback");
        System.out.println("WIFI_PERMISSION_UI_PASS: grant, denied recovery, auto-resume on focus, cancellation");
    }
    private static int tableSize(ArcadeApplication app) { var table = app.qaScene().getRoot().lookup(".table-view"); return table instanceof javafx.scene.control.TableView<?> t ? t.getItems().size() : -1; }
    private static void fillForm(ArcadeApplication app, String identity, String code) {
        var root = app.qaScene().getRoot(); root.applyCss(); root.layout();
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
