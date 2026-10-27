package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import vn.edu.hutech.nqb.client.LanRangeReport;
import vn.edu.hutech.nqb.shared.LanRangeJournal;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Test-only saved journal; not a physical field measurement. */
public final class LanRangeHistoryUiCheck {
    private static Path directory;
    private static Throwable failure;
    public static void main(String[] args)throws Exception{
        directory=Files.createTempDirectory("nqb-history-ui-");String id=UUID.randomUUID().toString();
        var journal=new LanRangeJournal(directory,"client",id);
        journal.append(object().put("record_type","RUN_STARTED").put("survey_id",UUID.randomUUID().toString()).put("survey_name","Nhật ký kiểm thử giao diện")
            .put("route","Dữ liệu kiểm thử").put("distance_m",30).put("duration_seconds",30).put("client_ip_observed","127.0.0.1")
            .put("server_ip","127.0.0.1").put("server_port",5050).put("loopback",true).put("started_at","2026-10-25T02:00:00Z")).get(3,TimeUnit.SECONDS);
        journal.append(object().put("record_type","RUN_FINISHED").put("end_reason","STOPPED").put("server_summary_received",false)).get(3,TimeUnit.SECONDS);
        journal.closeAsync().get(3,TimeUnit.SECONDS);
        Application.launch(Check.class,args);if(failure!=null)throw new AssertionError("LAN history UI failed",failure);
    }
    public static final class Check extends Application {
        @Override public void start(Stage stage){
            FxKit.loadFonts();var view=new LanRangeView(stage,()->null,directory,()->false,()->{});
            var page=(BorderPane)view.content();var scene=new Scene(page,1060,730);scene.getStylesheets().add(FxKit.CSS);stage.setScene(scene);stage.show();
            var room=object();room.putArray("players");room.set("connection_info",object().put("server_ip","127.0.0.1").put("server_port",5050).put("player_id",""));view.updateConnections(room,true);
            Thread.ofPlatform().daemon().name("history-ui-check").start(()->{
                try{
                    @SuppressWarnings("unchecked") var table=(TableView<JsonNode>)onFx(()->page.lookup("#lan-range-history"));
                    fx(view::activate);await(()->table.getItems().size()==1);
                    fx(()->{
                        check(!page.lookup("#lan-range-run-scope").getParent().isVisible(),"Opening the tool displayed a saved IP with no current measurement");
                        check(((Label)page.lookup("#lan-range-run-client-ip")).getText().isBlank(),"Old client IP remained in the empty measurement state");
                        check(table.getSelectionModel().getSelectedItem()==null,"History was auto-selected");
                        ((TitledPane)page.lookup("#lan-range-history-section")).setExpanded(true);table.getSelectionModel().selectFirst();
                        check(page.lookup("#lan-range-run-scope").getParent().isVisible(),"Explicit history selection did not show the saved result");
                        check(((Label)page.lookup("#lan-range-run-client-ip")).getText().contains("127.0.0.1"),"Saved client identity was lost");
                        check(((Label)page.lookup("#lan-range-result-context")).getText().contains("Lịch sử"),"Saved result can be mistaken for a live connection");
                    });
                    var before=onFx(table::getItems);fx(view::activate);await(()->table.getItems()!=before);
                    fx(()->{
                        check(table.getSelectionModel().getSelectedItem()!=null,"Reopening lost explicitly selected history");
                        ((Button)page.lookup("#lan-range-close-history")).fire();
                        check(!page.lookup("#lan-range-run-scope").getParent().isVisible(),"Closing history left the old result visible");
                    });
                    var previousItems=onFx(table::getItems);fx(view::activate);await(()->table.getItems()!=previousItems);
                    fx(()->check(!page.lookup("#lan-range-run-scope").getParent().isVisible(),"Reopening resurrected closed history"));
                    check(LanRangeReport.load(directory).size()==1,"Closing history deleted the saved report");
                    System.out.println("LAN_RANGE_HISTORY_UI_PASS: no implicit saved IP, explicit history preserved, close/reopen stays empty, journal retained");
                }catch(Throwable error){failure=error;error.printStackTrace();}
                finally{Platform.runLater(()->{view.close();stage.close();Platform.exit();});}
            });
        }
    }
    private static <T>T onFx(Callable<T> action)throws Exception{var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable error){result.completeExceptionally(error);}});return result.get(8,TimeUnit.SECONDS);}
    private static void fx(Runnable action)throws Exception{onFx(()->{action.run();return null;});}
    private static void await(BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<until){if(onFx(condition::getAsBoolean))return;Thread.sleep(40);}throw new AssertionError("UI condition not reached");}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
