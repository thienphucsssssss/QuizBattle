package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/** Regression for restoring avatar seats after removing the room topology. Fixtures prove layout only. */
public final class RoomTopologyUiCheck {
    private static Path output;
    private static final String SECRET="ephemeral-topology-ui-only";
    public static void main(String[] args)throws Exception{
        output=Path.of(args.length>0?args[0]:"tmp/room-topology-ui").toAbsolutePath();Files.createDirectories(output);
        Path temp=Files.createTempDirectory("nqb-topology-ui-"),key=temp.resolve("server.p12"),trust=temp.resolve("trust.p12"),cert=temp.resolve("cert.cer");
        keytool("-genkeypair","-alias","qa","-keyalg","RSA","-validity","2","-dname","CN=localhost","-ext","SAN=ip:127.0.0.1,dns:localhost","-storetype","PKCS12","-keystore",key.toString(),"-storepass",SECRET);
        keytool("-exportcert","-alias","qa","-keystore",key.toString(),"-storepass",SECRET,"-file",cert.toString());
        keytool("-importcert","-noprompt","-alias","qa","-storetype","PKCS12","-keystore",trust.toString(),"-storepass",SECRET,"-file",cert.toString());
        var store=new QuizStore(temp.resolve("quiz.db"));store.bootstrap("admin","Giảng viên topology",SECRET.toCharArray());
        var config=new ServerConfig("127.0.0.1",0,temp.resolve("quiz.db"),key,10,32,100,100,100);
        try(var server=new QuizServer(config,config.tls(SECRET.toCharArray()),store)){
            server.start();
            ArcadeApplication.qaDriver=app->Thread.ofPlatform().daemon().name("topology-ui-qa").start(()->{
                try(var host=TlsQuizGateway.connect(new Endpoint("127.0.0.1",server.port()),trust,SECRET.toCharArray());
                    var player=TlsQuizGateway.connect(new Endpoint("127.0.0.1",server.port()),trust,SECRET.toCharArray())){
                    var user=host.request("LOGIN",object().put("username","admin").put("password",SECRET)).get(5,TimeUnit.SECONDS);
                    String code=host.request("ROOM_CONFIGURE",object().put("room_name","Phòng LAN thật").put("question_count",1).put("duration_seconds",15)).get(5,TimeUnit.SECONDS).path("room_code").asText();
                    fx(()->{set(app,"gateway",host);set(app,"user",user);set(app,"role","ADMIN");call(app,"attachEvents");call(app,"shell");app.roomView();});
                    await(()->app.qaScene().lookup("#room-players")!=null);
                    fx(()->{layout(app);check(nodes(app).isEmpty(),"Empty room invented clients");snapshot(app,"empty-real");});
                    var joined=player.request("JOIN",object().put("room_code",code).put("nickname","Client cùng máy")).get(5,TimeUnit.SECONDS);
                    await(()->nodes(app).size()==1);
                    player.request("READY_SET",object().put("ready",true)).get(5,TimeUnit.SECONDS);
                    await(()->app.qaScene().getRoot().lookupAll(".room-player-state").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("Sẵn sàng")));
                    fx(()->{
                        layout(app);var roomCode=(Label)app.qaScene().lookup("#lobby-room-code");
                        check(roomCode.getText().equals(code),"Lobby displays wrong room");
                        check(roomCode.getFont().getSize()>=40,"Original prominent room code was not restored");
                        check(app.qaScene().lookup("#room-topology")==null,"Room topology still appears in restored lobby");
                        snapshot(app,"single-real");
                    });
                    for(int width:new int[]{1366,1024}){
                        fx(()->resize(app,width,width==1366?768:640));Thread.sleep(200);
                        for(int count:new int[]{8,10}){
                            int total=count;
                            fx(()->{set(app,"room",fixture(code,total,server.port()));call(app,"lobby");layout(app);
                                check(nodes(app).size()==total,"Layout dropped clients");
                                var graph=app.qaScene().lookup("#room-players");
                                graph.applyCss();((Parent)graph).layout();
                                snapshotGraph(app,"fixture-"+total+"-"+width);
                                if(total==8&&width==1024){
                                    app.qaScene().getRoot().getStyleClass().add("game");layout(app);
                                    snapshotGraph(app,"fixture-8-student-1024");
                                    app.qaScene().getRoot().getStyleClass().remove("game");layout(app);
                                }
                                var footer=app.qaScene().lookup("#room-start-game").localToScene(app.qaScene().lookup("#room-start-game").getBoundsInLocal());
                                check(footer.getMaxY()<=app.qaScene().getHeight()+1,"Room start control clipped");
                            });
                        }
                    }
                    fx(app::roomView);await(()->nodes(app).size()==1);
                    server.close();await(()->!app.qaConnected());
                    fx(()->{check(app.qaScene().getRoot().lookupAll(".room-player-state").stream().anyMatch(n->n instanceof Label l&&l.getText().equals("Chưa xác nhận")),"Cached client still appears live");snapshotGraph(app,"disconnected-real");});
                    System.out.println("ROOM_LOBBY_UI_PASS: original avatar seats and large room code; real empty/JOIN/ready/disconnect; 8/10-player fixture layout at 1366/1024");
                }catch(Throwable error){ArcadeApplication.qaFailed(error);error.printStackTrace();}
                finally{Platform.runLater(Platform::exit);}
            });
            ArcadeApplication.launchFor("MANAGE",new String[]{"--width=1366","--height=768"});
        }finally{ArcadeApplication.qaDriver=null;}
    }
    private static ObjectNode fixture(String code,int count,int port){
        var room=object().put("phase","WAITING").put("is_owner",true).put("room_code",code).put("room_name","Kiểm thử bố cục · fixture")
            .put("question_count",1).put("duration_seconds",15).put("connected_player_count",count-1).put("ready_count",4).put("max_players",10).put("server_port",port);
        var players=room.putArray("players");
        for(int i=0;i<count;i++)players.add(object().put("player_id","fixture-"+i).put("nickname",i==3?"Thí sinh có biệt danh rất dài để kiểm tra bố cục":"Client bố cục "+(i+1))
            .put("avatar_id","quizling-"+(i+1)).put("connected",i!=count-1).put("ready",i<4));
        return room;
    }
    private static Set<Node> nodes(ArcadeApplication app){layout(app);return app.qaScene().getRoot().lookupAll(".player-seat");}
    private static void layout(ArcadeApplication app){app.qaScene().getRoot().applyCss();app.qaScene().getRoot().layout();}
    private static void snapshotGraph(ArcadeApplication app,String name){save(app.qaScene().getRoot(),name);}
    private static void snapshot(ArcadeApplication app,String name){save(app.qaScene().getRoot(),name);}
    private static void save(Node node,String name){
        var image=node.snapshot(null,null);var bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        try{ImageIO.write(bitmap,"png",output.resolve(name+".png").toFile());}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void resize(ArcadeApplication app,int width,int height){var s=(Stage)app.qaScene().getWindow();s.setWidth(width+s.getWidth()-app.qaScene().getWidth());s.setHeight(height+s.getHeight()-app.qaScene().getHeight());}
    private static void set(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    private static void call(Object o,String name){try{var m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    private static double contrastToWhite(javafx.scene.paint.Color c){
        double[] values={c.getRed(),c.getGreen(),c.getBlue()};
        for(int i=0;i<3;i++)values[i]=values[i]<=0.04045?values[i]/12.92:Math.pow((values[i]+0.055)/1.055,2.4);
        return 1.05/(0.2126*values[0]+0.7152*values[1]+0.0722*values[2]+0.05);
    }
    private static void fx(Runnable action)throws Exception{var f=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();f.complete(null);}catch(Throwable e){f.completeExceptionally(e);}});f.get(10,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<until){var ok=new boolean[1];fx(()->ok[0]=condition.getAsBoolean());if(ok[0])return;Thread.sleep(50);}
        throw new AssertionError("Topology UI condition timed out");
    }
    private static void keytool(String...args)throws Exception{
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString());command.addAll(List.of(args));
        var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        check(p.waitFor(20,TimeUnit.SECONDS)&&p.exitValue()==0,"Ephemeral certificate failed");
    }
}
