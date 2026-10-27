package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import vn.edu.hutech.nqb.client.*;
import vn.edu.hutech.nqb.server.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Real TLS, same physical machine/IP. Screenshots prove UI only, never a distance in metres. */
public final class LanRangeUiCheck {
    private static Path output,reports;
    private static ArcadeApplication application;
    private static final String SECRET="ephemeral-range-ui-only";
    public static void main(String[] args)throws Exception{
        output=Path.of(args.length>0?args[0]:"../tmp/lan-range-ui").toAbsolutePath();Files.createDirectories(output);
        Path temp=Files.createTempDirectory("nqb-range-ui-"),key=temp.resolve("server.p12"),trust=temp.resolve("trust.p12"),cert=temp.resolve("cert.cer");
        reports=temp.resolve("client-reports");System.setProperty("nqb.lanRange.dir",reports.toString());
        keytool("-genkeypair","-alias","qa","-keyalg","RSA","-validity","2","-dname","CN=localhost","-ext","SAN=ip:127.0.0.1,dns:localhost","-storetype","PKCS12","-keystore",key.toString(),"-storepass",SECRET);
        keytool("-exportcert","-alias","qa","-keystore",key.toString(),"-storepass",SECRET,"-file",cert.toString());
        keytool("-importcert","-noprompt","-alias","qa","-storetype","PKCS12","-keystore",trust.toString(),"-storepass",SECRET,"-file",cert.toString());
        var store=new QuizStore(temp.resolve("quiz.db"));store.bootstrap("admin","Giảng viên kiểm thử LAN",SECRET.toCharArray());
        var config=new ServerConfig("127.0.0.1",0,temp.resolve("quiz.db"),key,10,32,100,100,100);
        try(var server=new QuizServer(config,config.tls(SECRET.toCharArray()),store)){
            server.start();
            ArcadeApplication.qaDriver=app->Thread.ofPlatform().daemon().name("range-ui-qa").start(()->{
                try(var gateway=TlsQuizGateway.connect(new Endpoint("127.0.0.1",server.port()),trust,SECRET.toCharArray())){
                    JsonNode user=gateway.request("LOGIN",object().put("username","admin").put("password",SECRET)).get(5,TimeUnit.SECONDS);
                    fx(()->{
                        application=app;
                        set(app,"gateway",gateway);set(app,"user",user);set(app,"role","ADMIN");
                        call(app,"attachEvents");call(app,"shell");
                        app.qaScene().getRoot().applyCss();app.qaScene().getRoot().layout();
                        app.qaClick("Đo tầm kết nối LAN");
                        check(pane()!=null,"LAN tool must open as a dedicated management page");
                        check(Window.getWindows().stream().filter(Window::isShowing).count()==1,"Manager LAN tab opened an extra dialog");
                    });
                    await(()->pane()!=null);
                    await(()->pane().lookup("#lan-range-server-ip") instanceof Label label&&label.getText().contains("127.0.0.1"));
                    fx(()->{
                        check(pane().lookup("#lan-range-server-panel")!=null&&pane().lookup("#lan-range-client-panel")!=null,"Missing explicit Server / Client panels");
                        check(((Label)pane().lookup("#lan-range-client-connections")).getText().contains("Chưa có client"),"Empty room invented a connected student");
                        check(button("#lan-range-start").isDisabled(),"Manager can start a self-measurement with no selected client");
                    });
                    String code=gateway.request("ROOM_CONFIGURE",object().put("room_name","Phòng kiểm thử IP").put("question_count",1).put("duration_seconds",15)).get(5,TimeUnit.SECONDS).path("room_code").asText();
                    try(var player=TlsQuizGateway.connect(new Endpoint("127.0.0.1",server.port()),trust,SECRET.toCharArray())){
                        player.request("JOIN",object().put("room_code",code).put("nickname","Client kiểm thử IP")).get(5,TimeUnit.SECONDS);
                        await(()->((Label)pane().lookup("#lan-range-client-connections")).getText().contains("Client kiểm thử IP")
                            &&((Label)pane().lookup("#lan-range-client-connections")).getText().contains("127.0.0.1"));
                        fx(()->{check(((Label)pane().lookup("#lan-range-client-stats")).getText().equals("Chưa có kết quả"),"Student JOIN started or invented a measurement");snapshot("connected-client-before-measurement-1366");});
                    }
                    await(()->((Label)pane().lookup("#lan-range-client-connections")).getText().contains("Chưa có client"));
                    try(var rejoined=TlsQuizGateway.connect(new Endpoint("127.0.0.1",server.port()),trust,SECRET.toCharArray())){
                        rejoined.request("JOIN",object().put("room_code",code).put("nickname","Client kết nối lại")).get(5,TimeUnit.SECONDS);
                        await(()->((Label)pane().lookup("#lan-range-client-connections")).getText().contains("Client kết nối lại")
                            &&!((Label)pane().lookup("#lan-range-client-connections")).getText().contains("Client kiểm thử IP"));
                    }
                    await(()->((Label)pane().lookup("#lan-range-client-connections")).getText().contains("Chưa có client"));
                    try(var measuredPeer=TlsQuizGateway.connect(new Endpoint("127.0.0.1",server.port()),trust,SECRET.toCharArray());
                        var measuredRange=new LanRangeClient(measuredPeer,reports.resolve("qa-target"),s->{})){
                    measuredPeer.onEvent(measuredRange::onEvent);
                    String measuredPlayer=measuredPeer.request("JOIN",object().put("room_code",code).put("nickname","Client được chọn để đo")).get(5,TimeUnit.SECONDS).path("player_id").asText();
                    await(()->((ComboBox<?>)pane().lookup("#lan-range-target")).getItems().size()==1);
                    fx(()->{
                        @SuppressWarnings("unchecked") var targets=(ComboBox<JsonNode>)pane().lookup("#lan-range-target");targets.getSelectionModel().selectFirst();
                        ((TextField)pane().lookup("#lan-range-survey")).setText("Kiểm thử cục bộ");
                        ((TextField)pane().lookup("#lan-range-route")).setText("Một máy · cùng IP");
                        ((TextField)pane().lookup("#lan-range-distance")).setText("-1");
                        button("#lan-range-start").fire();check(button("#lan-range-stop").isDisabled(),"Invalid distance started a run");
                        ((TextField)pane().lookup("#lan-range-distance")).setText("0");snapshot("ready-1366");button("#lan-range-start").fire();
                    });
                    await(()->((Label)pane().lookup("#lan-range-client-stats")).getText().contains("Đã có kết quả: 2")
                        &&((Label)pane().lookup("#lan-range-server-stats")).getText().contains("Đã có kết quả: 2"));
                    fx(()->{check(((LanRangeClient)get(app,"lanRangeClient")).snapshot().path("player_id").asText().equals(measuredPlayer),"UI is measuring the manager instead of the chosen client");check(measuredRange.snapshot().path("active").asBoolean(),"Chosen client did not receive the measure command");check(((ComboBox<?>)pane().lookup("#lan-range-target")).isDisabled(),"Target can change during measurement");check(((Label)pane().lookup("#lan-range-evidence")).getText().contains("Kiểm thử cục bộ"),"Same-host test labelled as physical range");check(!((TitledPane)pane().lookup("#lan-range-setup")).isExpanded(),"Preparation obscures results while measuring");bounds("#lan-range-stop");((ScrollPane)pane().lookup("#lan-range-scroll")).setVvalue(0);snapshot("active-1366");app.qaClick("Cài đặt");});
                    fx(()->app.qaClick("Đo tầm kết nối LAN"));
                    fx(()->{check(button("#lan-range-start").isDisabled(),"Tab switch lost the active run");check(((TextField)pane().lookup("#lan-range-distance")).getText().equals("0"),"Tab switch changed measured marker");button("#lan-range-stop").fire();});
                    await(()->!button("#lan-range-start").isDisabled());
                    fx(()->{snapshot("stopped-1366");app.qaClick("Cài đặt");resize(app,1024,640);});
                    Thread.sleep(250);
                    fx(()->{app.qaClick("Đo tầm kết nối LAN");});await(()->pane()!=null);
                    fx(()->{check(((TableView<?>)pane().lookup("#lan-range-history")).getItems().size()==1,"Saved run lost on reopen");bounds("#lan-range-stop");bounds("#lan-range-start");snapshot("saved-1024");});
                    var restored=LanRangeReport.load(reports);check(restored.size()==1,"Client journal missing");
                    LanRangeReport.exportSummary(output.resolve("local-summary.csv"),restored);
                    LanRangeReport.exportSamples(output.resolve("local-samples.csv"),restored);
                    fx(()->{button("#lan-range-start").fire();});
                    await(()->!button("#lan-range-start").isDisabled(),38);
                    fx(()->{
                        check(((Label)pane().lookup("#lan-range-state")).getText().contains("Kết quả"),"Completed run did not render its result");
                        check(((Label)pane().lookup("#lan-range-farthest")).getText().equals("Chưa có mốc đạt"),"Local run became physical range evidence");
                        ((TitledPane)pane().lookup("#lan-range-history-section")).setExpanded(true);((ScrollPane)pane().lookup("#lan-range-scroll")).setVvalue(1);snapshot("completed-local-history-1024");
                    });
                    server.close();await(()->!app.qaConnected());
                    fx(()->{check(button("#lan-range-start").isDisabled(),"Disconnected controller can start measurement");snapshot("disconnected-1024");});
                    fx(()->{
                        var studentDialog=new LanRangeView((Stage)app.qaScene().getWindow(),()->null,reports,()->false,()->{});
                        studentDialog.show();
                        var dialogPage=Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#lan-range-dialog")!=null).findFirst().orElseThrow().getScene().lookup("#lan-range-page");
                        dialogPage.applyCss();((Parent)dialogPage).layout();
                        for(String id:List.of("#lan-range-start","#lan-range-stop","#lan-range-close")){
                            var control=dialogPage.lookup(id);var bounds=control.localToScene(control.getBoundsInLocal());
                            check(bounds.getMaxY()<=dialogPage.getScene().getHeight()+1,"Student dialog footer clipped: "+id);
                        }
                        studentDialog.close();
                    });
                    }
                    System.out.println("LAN_RANGE_UI_PASS: dedicated sidebar page, real TLS both ways, local evidence, invalid distance, tab switch while running, stop/reopen, full 30s completion, CSV, 1366/1024, disconnect, student dialog host");
                }catch(Throwable error){ArcadeApplication.qaFailed(error);error.printStackTrace();}
                finally{Platform.runLater(Platform::exit);}
            });
            ArcadeApplication.launchFor("MANAGE",new String[]{"--width=1366","--height=768"});
        }finally{ArcadeApplication.qaDriver=null;System.clearProperty("nqb.lanRange.dir");}
    }
    private static Parent pane(){
        if(application==null)return null;
        var node=application.qaScene().lookup("#lan-range-page");return node instanceof Parent parent?parent:null;
    }
    private static Button button(String id){return (Button)pane().lookup(id);}
    private static void closeDialog(){var window=pane().getScene().getWindow();window.fireEvent(new WindowEvent(window,WindowEvent.WINDOW_CLOSE_REQUEST));}
    private static void resize(ArcadeApplication app,int width,int height){var stage=(Stage)app.qaScene().getWindow();stage.setWidth(width+stage.getWidth()-app.qaScene().getWidth());stage.setHeight(height+stage.getHeight()-app.qaScene().getHeight());}
    private static void bounds(String id){
        var node=pane().lookup(id);var before=node.localToScene(node.getBoundsInLocal());
        var root=pane().getScene().getRoot();root.applyCss();root.layout();var b=node.localToScene(node.getBoundsInLocal());
        if(!before.equals(b))System.out.println("Bounds settled after layout "+id+": "+before+" -> "+b);
        check(b.getMinY()>=0&&b.getMaxY()<=pane().getScene().getHeight()+1&&b.getMinX()>=0&&b.getMaxX()<=pane().getScene().getWidth()+1,"Clipped button "+id+": "+b+", scene="+pane().getScene().getWidth()+"x"+pane().getScene().getHeight());
    }
    private static void snapshot(String name){
        pane().applyCss();pane().layout();
        var heading=pane().lookup(".range-heading .title");var glyph=heading.lookup(".text");
        var glyphBounds=glyph.localToScene(glyph.getBoundsInLocal());
        check(glyphBounds.getMinY()>=0,"Page heading clipped in "+name+": "+glyphBounds);
        var image=pane().getScene().snapshot(null);
        var bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        try{ImageIO.write(bitmap,"png",output.resolve(name+".png").toFile());}catch(Exception error){throw new RuntimeException(error);}
    }
    private static Object get(Object obj,String field){try{var f=obj.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private static void set(Object obj,String field,Object value){try{var f=obj.getClass().getDeclaredField(field);f.setAccessible(true);f.set(obj,value);}catch(Exception e){throw new RuntimeException(e);}}
    private static void call(Object obj,String method){try{var m=obj.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(obj);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fx(Runnable action)throws Exception{var done=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();done.complete(null);}catch(Throwable error){done.completeExceptionally(error);}});done.get(10,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{
        await(condition,8);
    }
    private static void await(BooleanSupplier condition,int seconds)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(System.nanoTime()<end){var ok=new boolean[1];fx(()->ok[0]=condition.getAsBoolean());if(ok[0])return;Thread.sleep(50);}
        throw new AssertionError("UI condition not reached");
    }
    private static void keytool(String... args)throws Exception{
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString());command.addAll(List.of(args));
        var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        check(p.waitFor(20,TimeUnit.SECONDS)&&p.exitValue()==0,"Ephemeral certificate failed");
    }
}
