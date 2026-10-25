package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;
import vn.edu.hutech.nqb.client.QuizGateway;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Isolated presentation fixtures only. LiveLanUiCheck separately verifies real TLS/role behavior. */
public final class CommanderViewsUiCheck {
    private static Path output;
    private static ObjectNode snapshot;
    private static CommanderViews views;
    public static void main(String[] args) {
        output = Path.of(args[0]);
        ArcadeApplication.qaDriver = app -> Thread.ofPlatform().daemon().start(() -> {
            try {
                Files.createDirectories(output);
                fx(() -> {
                    set(app,"gateway",new FixtureGateway()); set(app,"user",object().put("display_name","Giảng viên kiểm thử")); set(app,"role","ADMIN");
                    call(app,"shell"); views = (CommanderViews)get(app,"commander");
                });
                for (int width : new int[]{1366,1024}) {
                    int height = width == 1366 ? 768 : 640;
                    resize(app,width,height);
                    monitor(app,width); arena(app,width);
                }
                System.out.println("COMMANDER_COMPONENT_UI_PASS: 1366/1024, filters, disconnected denominator, phase refresh, selected details, missing IP permissions, all ties, long names, reachable controls");
            } catch(Throwable error) { ArcadeApplication.qaFailed(error); error.printStackTrace(); }
            finally { Platform.runLater(Platform::exit); }
        });
        try { ArcadeApplication.launchFor("MANAGE", new String[]{"--width=1366","--height=768"}); }
        finally { ArcadeApplication.qaDriver = null; }
    }
    private static void monitor(ArcadeApplication app,int width)throws Exception {
        fx(() -> { publish(app,fixture("QUESTION_ACTIVE")); views.open("Giám sát thi đấu"); });
        fx(() -> {
            layout(app);
            var progress=(ProgressBar)app.qaScene().lookup("#monitor-progress");
            check(Math.abs(progress.getProgress()-.5)<.001,"Disconnected participant changed submission denominator");
            check(((Button)app.qaScene().lookup("#monitor-start")).isDisabled(),"Active game allowed another start");
            check(!((Button)app.qaScene().lookup("#monitor-abort")).isDisabled(),"Owner cannot abort active game");
            ((ToggleButton)app.qaScene().lookup("#monitor-filter-pending")).fire(); check(table(app).getItems().size()==2,"Pending filter lost offline participant");
            ((ToggleButton)app.qaScene().lookup("#monitor-filter-offline")).fire(); check(table(app).getItems().size()==1,"Offline filter incorrect");
            ((ToggleButton)app.qaScene().lookup("#monitor-filter-all")).fire();
            snapshot(app,"monitor-active-"+width);
            bounds(app,"#monitor-start");bounds(app,"#monitor-abort");bounds(app,"#monitor-players");
            snapshot.put("phase","WAITING"); publish(app,snapshot);
            check(((ToggleButton)app.qaScene().lookup("#monitor-filter-pending")).getText().contains("sẵn sàng"),"Phase-only event did not refresh readiness filter");
            snapshot(app,"monitor-waiting-"+width);
            set(app,"disconnected",true); views.update(snapshot);
            check(((Label)app.qaScene().lookup("#monitor-submitted")).getText().equals("—"),"Cached submissions represented as live");
            check(((Button)app.qaScene().lookup("#monitor-start")).isDisabled()&&((Button)app.qaScene().lookup("#monitor-projection")).isDisabled(),"Disconnected controller still enabled");
            snapshot(app,"monitor-offline-"+width);
            set(app,"disconnected",false);set(app,"role","TEACHER");snapshot.put("is_owner",false);publish(app,snapshot);
            check(((Button)app.qaScene().lookup("#monitor-projection")).isDisabled(),"Non-owner teacher can project room");
            check(((Label)app.qaScene().lookup("#monitor-action-hint")).getText().contains("chủ phòng"),"Viewer role lacks a control explanation");
            snapshot(app,"monitor-viewer-"+width);set(app,"role","ADMIN");
        });
    }
    private static void arena(ArcadeApplication app,int width)throws Exception {
        fx(() -> {publish(app,fixture("SHOW_RANKING"));views.open("Đấu trường thí sinh");});
        fx(() -> {
            layout(app); var table=table(app);
            check(app.qaScene().getRoot().lookupAll(".commander-leader").size()==3,"Server's 1/1/3 leaders were dropped or invented");
            table.getSelectionModel().select(0);
            check(((Label)app.qaScene().lookup("#arena-player-details")).getText().contains("192.168.1.21"),"Selected player lacks Server-provided IP");
            var search=(TextField)app.qaScene().lookup("#arena-search");search.setText("quoc");
            check(table.getItems().size()==1,"Unaccented Vietnamese search failed");
            check(table.getSelectionModel().getSelectedItem()==null,"Hidden selection still retained another player's details");
            table.getSelectionModel().select(0);
            check(((Label)app.qaScene().lookup("#arena-player-details")).getText().contains("192.168.1.22"),"Filtered row details use another player's IP");
            publish(app,snapshot.deepCopy().put("phase","FINISHED"));
            check(((Label)app.qaScene().lookup("#arena-player-details")).getText().contains("Đã kết thúc"),"Phase-only update left stale selected-player status");
            search.clear();
            snapshot(app,"arena-ranked-"+width);bounds(app,"#arena-players");bounds(app,"#arena-player-details");
            check(table.getHeight()>=168,"Compact arena must fit two complete participant rows: "+table.getHeight());
            snapshot.put("is_owner",false);for(var player:snapshot.path("players"))((ObjectNode)player).remove("ip_address");
            set(app,"role","TEACHER");publish(app,snapshot);
            check(!((Label)app.qaScene().lookup("#arena-player-details")).getText().contains("192.168"),"Role-restricted IP leaked from old selected row");
            snapshot(app,"arena-viewer-"+width);
            var many=fixture("SHOW_RANKING").put("connected_player_count",12);many.withArray("players").removeAll();many.withArray("ranking").removeAll();
            for(int i=0;i<12;i++) {
                var player=object().put("player_id","tie-"+i).put("nickname","Thí sinh đồng hạng có tên dài "+i).put("avatar_id","quizling-"+(i+1)).put("connected",true).put("score",300).put("correct_count",3);
                many.withArray("players").add(player);many.withArray("ranking").add(player.deepCopy().put("rank",1));
            }
            publish(app,many);layout(app);
            check(app.qaScene().getRoot().lookupAll(".commander-leader").size()==12,"Many tied leaders truncated");
            bounds(app,"#arena-players");bounds(app,"#arena-player-details");snapshot(app,"arena-many-ties-"+width);
            publish(app,object().put("phase","WAITING"));
            check(table(app).getItems().isEmpty(),"Closed room retained players");snapshot(app,"arena-empty-"+width);
            set(app,"role","ADMIN");
        });
    }
    private static ObjectNode fixture(String phase) {
        var room=object().put("phase",phase).put("room_code","TEST42").put("room_name","Lớp Lập trình mạng · Kiểm thử giao diện").put("is_owner",true)
            .put("question_number",2).put("question_count",10).put("remaining_ms",12000).put("connected_player_count",3).put("ready_count",2).put("answered_count",2);
        var players=room.putArray("players");var ranking=room.putArray("ranking");
        String[] names={"Nguyễn Hoàng Minh Anh với biệt danh rất dài","Quốc Bảo","Lan Anh","Đặng Hoàng Long"};
        for(int i=0;i<4;i++) {
            var player=object().put("player_id","p"+i).put("nickname",names[i]).put("avatar_id","quizling-"+(i+1)).put("ip_address","192.168.1."+(21+i))
                .put("connected",i!=3).put("ready",i<2).put("answered",i==0||i==3).put("score",i<2?300:i==2?250:170).put("correct_count",i<2?4:i==2?3:2);
            players.add(player);ranking.add(player.deepCopy().put("rank",i<2?1:i+1));
        }
        return room;
    }
    private static void publish(ArcadeApplication app,ObjectNode next) {snapshot=next;set(app,"room",next);views.update(next);}
    private static TableView<?> table(ArcadeApplication app) {return (TableView<?>)app.qaScene().lookup("#monitor-players")!=null?(TableView<?>)app.qaScene().lookup("#monitor-players"):(TableView<?>)app.qaScene().lookup("#arena-players");}
    private static void bounds(ArcadeApplication app,String selector) {
        layout(app);Node node=app.qaScene().lookup(selector);var b=node.localToScene(node.getBoundsInLocal());
        check(b.getMinX()>=0&&b.getMaxX()<=app.qaScene().getWidth()+1&&b.getMinY()>=0&&b.getMaxY()<=app.qaScene().getHeight()+1,"Clipped "+selector+": "+b);
    }
    private static void layout(ArcadeApplication app) {app.qaScene().getRoot().applyCss();app.qaScene().getRoot().layout();}
    private static void resize(ArcadeApplication app,int width,int height)throws Exception {
        fx(() -> {var stage=(Stage)app.qaScene().getWindow();stage.setWidth(width+stage.getWidth()-app.qaScene().getWidth());stage.setHeight(height+stage.getHeight()-app.qaScene().getHeight());});
        Thread.sleep(200);
    }
    private static void snapshot(ArcadeApplication app,String name) {
        layout(app);var image=app.qaScene().snapshot(null);var bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        try{ImageIO.write(bitmap,"png",output.resolve(name+".png").toFile());}catch(Exception error){throw new RuntimeException(error);}
    }
    private static Object get(Object target,String name) {try{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception error){throw new RuntimeException(error);}}
    private static void set(Object target,String name,Object value) {try{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(Exception error){throw new RuntimeException(error);}}
    private static void call(Object target,String name) {try{var method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(target);}catch(Exception error){throw new RuntimeException(error);}}
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static void fx(Runnable action)throws Exception {var future=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();future.complete(null);}catch(Throwable error){future.completeExceptionally(error);}});future.get(10,TimeUnit.SECONDS);}
    private static final class FixtureGateway implements QuizGateway {
        public CompletableFuture<JsonNode> request(String type,ObjectNode payload) {
            return switch(type) {
                case "ROOM_STATE" -> CompletableFuture.completedFuture(snapshot.deepCopy());
                case "PING" -> CompletableFuture.completedFuture(object());
                default -> CompletableFuture.failedFuture(new AssertionError("Unexpected fixture request: "+type));
            };
        }
        public void onEvent(Consumer<JsonNode> listener) { }
        public boolean isPreview(){return false;}
        public void close(){ }
    }
}
