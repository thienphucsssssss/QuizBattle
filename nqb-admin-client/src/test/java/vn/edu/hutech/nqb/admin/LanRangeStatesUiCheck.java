package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import vn.edu.hutech.nqb.client.QuizGateway;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Rendering fixtures only: never writes measurements or pretends these are field observations. */
public final class LanRangeStatesUiCheck {
    private static Throwable failure;
    public static void main(String[] args){Application.launch(Check.class,args);if(failure!=null)throw new AssertionError("LAN states UI failed",failure);}
    public static final class Check extends Application {
        @Override public void start(Stage stage){
            LanRangeView view=null;
            try{
                FxKit.loadFonts();view=new LanRangeView(stage,()->null,Path.of("unused-ui-fixture-directory"),()->false,()->{});
                var page=(BorderPane)view.content();
                ((VBox)page.getTop()).getChildren().add(FxKit.text("Dữ liệu kiểm thử giao diện · không phải phép đo thực tế","muted"));
                var scene=new Scene(page,1060,730);scene.getStylesheets().add(FxKit.CSS);stage.setScene(scene);stage.show();
                var roster=QuizGateway.object();
                roster.put("is_owner",true);
                roster.set("connection_info",QuizGateway.object().put("server_ip","127.0.0.1").put("server_port",0).put("client_ip_observed","127.0.0.9").put("player_id",""));
                var peers=roster.putArray("players");
                peers.add(QuizGateway.object().put("player_id","layout-a").put("nickname","Client A").put("ip_address","127.0.0.2").put("connected",true));
                peers.add(QuizGateway.object().put("player_id","layout-b").put("nickname","Client B").put("ip_address","127.0.0.3").put("connected",true));
                view.updateConnections(roster,true);
                check(page.lookupAll(".range-peer-address").stream().noneMatch(n->n instanceof Label label&&label.getText().contains("127.0.0.9")),"Manager socket shown as a room client");
                var addresses=(Label)page.lookup("#lan-range-client-connections");
                check(addresses.getText().contains("Client A")&&addresses.getText().contains("127.0.0.2")&&addresses.getText().contains("Client B")&&addresses.getText().contains("127.0.0.3"),"Connected clients lost their individual identities");
                @SuppressWarnings("unchecked") var targets=(ComboBox<JsonNode>)page.lookup("#lan-range-target");
                check(targets!=null&&targets.getItems().size()==2,"Host cannot select a connected room client");
                targets.getSelectionModel().selectFirst();((com.fasterxml.jackson.databind.node.ObjectNode)peers.get(0)).put("connected",false);view.updateConnections(roster,true);
                check(targets.getValue()==null,"Disconnected client silently changed to a different target");
                ((com.fasterxml.jackson.databind.node.ObjectNode)peers.get(0)).put("connected",true);view.updateConnections(roster,true);
                page.applyCss();page.layout();
                check(page.lookup("#lan-range-peers").localToScene(page.lookup("#lan-range-peers").getBoundsInLocal()).getMinX()
                    >page.lookup("#lan-range-server-ip").localToScene(page.lookup("#lan-range-server-ip").getBoundsInLocal()).getMaxX(),"Room clients are not beside the Server");
                view.updateConnections(roster,false);check(!addresses.getText().contains("Đang kết nối"),"Cached clients still advertised as live after disconnect");
                view.updateConnections(roster,true);
                var missing=QuizGateway.object().put("run_id","fixture-missing").put("survey_id","fixture").put("distance_m",32).put("duration_seconds",30)
                    .put("active",false).put("saved",true).put("loopback",false).put("passed",false).put("end_reason","DISCONNECTED").put("server_summary_received",false)
                    .put("client_ip_observed","127.0.0.7").put("server_ip","127.0.0.8").put("server_port",8080);
                missing.set("client_summary",summary(2,0,28));missing.set("server_summary",summary(0,0,30));view.update(missing);page.applyCss();page.layout();
                var table=(TableView<?>)page.lookup("#lan-range-history");
                check(table.getColumns().get(3).getCellData(0).equals("Chưa xác nhận"),"Missing server evidence rendered as a measured zero in history");
                check(((Label)page.lookup("#lan-range-server-stats")).getText().contains("Chưa nhận đủ kết quả server"),"Missing server summary lost its explanation");
                check(((Label)page.lookup("#lan-range-farthest")).getText().equals("Chưa có mốc đạt"),"Incomplete run counted as a passing marker");
                ((com.fasterxml.jackson.databind.node.ObjectNode)roster.path("connection_info")).put("server_ip","127.0.0.11").put("client_ip_observed","127.0.0.12");
                view.updateConnections(roster,true);
                check(addresses.getText().contains("Client A")&&!addresses.getText().contains("127.0.0.12"),"Manager socket replaced the connected-client roster");
                check(((Label)page.lookup("#lan-range-server-ip")).getText().contains("127.0.0.11"),"History replaced the live server address");
                check(((Label)page.lookup("#lan-range-run-client-ip")).getText().contains("127.0.0.7"),"Live roster relabelled the historical measurement's client");
                check(((Label)page.lookup("#lan-range-run-server-ip")).getText().contains("127.0.0.8:8080"),"Live roster relabelled the historical measurement's server");
                ((com.fasterxml.jackson.databind.node.ObjectNode)roster.path("connection_info")).put("player_id","layout-a").put("client_ip_observed","127.0.0.2");
                view.updateConnections(roster,true);
                check(addresses.getText().contains("127.0.0.2")&&!addresses.getText().contains("Client B")&&!addresses.getText().contains("127.0.0.3"),"Student sees another client's identity");
                ((com.fasterxml.jackson.databind.node.ObjectNode)roster.path("connection_info")).put("player_id","");view.updateConnections(roster,true);
                ((TitledPane)page.lookup("#lan-range-history-section")).setExpanded(true);page.applyCss();page.layout();
                save(page,"fixture-disconnected");
                var pass=missing.deepCopy().put("run_id","fixture-pass").put("distance_m",30).put("passed",true).put("end_reason","COMPLETED").put("server_summary_received",true);
                pass.set("client_summary",summary(29,1,0));pass.set("server_summary",summary(30,0,0));view.update(pass);table.getSelectionModel().selectFirst();page.applyCss();page.layout();
                check(page.lookup("#lan-range-server-panel").lookupAll(".range-count").stream().anyMatch(n->n instanceof Label l&&l.getText().equals("30 / 30")),"Server side is using the Client's measurements");
                check(page.lookup("#lan-range-client-panel").lookupAll(".range-count").stream().anyMatch(n->n instanceof Label l&&l.getText().equals("29 / 30")),"Client side is using the Server's measurements");
                check(((Label)page.lookup("#lan-range-farthest")).getText().equals("30 m"),"Passing survey marker not displayed");save(page,"fixture-pass");
                var local=pass.deepCopy().put("run_id","fixture-local").put("distance_m",40).put("loopback",true);view.update(local);table.getSelectionModel().selectFirst();
                check(((Label)page.lookup("#lan-range-farthest")).getText().equals("30 m"),"Local run replaced farthest field marker");
                System.out.println("LAN_RANGE_STATES_UI_PASS: unknown reverse direction, incomplete evidence, passing marker, local exclusion; fixtures only");
            }catch(Throwable error){failure=error;error.printStackTrace();}
            finally{if(view!=null)view.close();stage.close();Platform.exit();}
        }
    }
    private static com.fasterxml.jackson.databind.node.ObjectNode summary(int onTime,int timeout,int unconfirmed){
        var p=QuizGateway.object().put("planned",30).put("on_time",onTime).put("timeout",timeout).put("unconfirmed",unconfirmed)
            .put("confirmed",onTime+timeout).put("on_time_rate",onTime/30.0).put("send_failed",0).put("disconnected",0).put("cancelled",0);
        if(onTime>0)p.put("rtt_median_ms",14).put("rtt_p95_ms",28);return p;
    }
    private static void save(Parent page,String name)throws Exception{
        var image=page.snapshot(null,null);var bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        var dir=Path.of("tmp/lan-workspace-ui");Files.createDirectories(dir);ImageIO.write(bitmap,"png",dir.resolve(name+".png").toFile());
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
