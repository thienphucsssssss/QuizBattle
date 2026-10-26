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
                var missing=QuizGateway.object().put("run_id","fixture-missing").put("survey_id","fixture").put("distance_m",32).put("duration_seconds",30)
                    .put("active",false).put("saved",true).put("loopback",false).put("passed",false).put("end_reason","DISCONNECTED").put("server_summary_received",false);
                missing.set("client_summary",summary(2,0,28));missing.set("server_summary",summary(0,0,30));view.update(missing);page.applyCss();page.layout();
                var table=(TableView<?>)page.lookup("#lan-range-history");
                check(table.getColumns().get(2).getCellData(0).equals("Chưa xác nhận"),"Missing server evidence rendered as a measured zero in history");
                check(((Label)page.lookup("#lan-range-server-stats")).getText().contains("Chưa nhận đủ kết quả server"),"Missing server summary lost its explanation");
                check(((Label)page.lookup("#lan-range-farthest")).getText().equals("Chưa có mốc đạt"),"Incomplete run counted as a passing marker");
                save(page,"fixture-disconnected");
                var pass=missing.deepCopy().put("run_id","fixture-pass").put("distance_m",30).put("passed",true).put("end_reason","COMPLETED").put("server_summary_received",true);
                pass.set("client_summary",summary(29,1,0));pass.set("server_summary",summary(30,0,0));view.update(pass);table.getSelectionModel().selectFirst();page.applyCss();page.layout();
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
