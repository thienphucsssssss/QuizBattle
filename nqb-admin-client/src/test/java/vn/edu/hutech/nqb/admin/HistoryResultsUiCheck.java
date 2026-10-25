package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.node.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Opt-in component QA with isolated fixtures; never supplies runtime demo data. */
public final class HistoryResultsUiCheck {
    private static Stage owner;
    private static Path output;
    public static void main(String[] args)throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);
        var started=new CompletableFuture<Void>();
        Platform.startup(()->{Platform.setImplicitExit(false);loadFonts();owner=new Stage();started.complete(null);});started.get(5,TimeUnit.SECONDS);
        try {
            check("01-three-winners",1366,768,true,1,2,3,4,5,6,7,8);
            check("02-compact-tied",1024,640,true,1,1,3);
            check("03-many-ties",1024,640,true,1,1,1,1,1,1,1,1,1,1);
            check("04-empty",1024,640,true);
            check("05-one-player",1024,640,true,1);
            check("06-legacy-without-questions",1024,640,false,1,2,3);
            fx(()->{if(!HistoryResults.endedAt(JsonNodeFactory.instance.objectNode()).contains("Chưa"))throw new AssertionError("Missing timestamp became a fake time");});
            System.out.println("HISTORY_RESULTS_UI_PASS: stored scores/correct counts/ranks, all ties, long names, 1366/1024, empty, single player, reachable close/reuse, legacy reuse disabled, no horizontal clipping");
        } finally {fx(()->{owner.close();Platform.exit();});}
    }
    private static void check(String name,int width,int height,boolean reusable,int... places)throws Exception {
        fx(()->{
            owner.setScene(new Scene(new StackPane(),width,height));owner.show();
            var match=JsonNodeFactory.instance.objectNode().put("room_name","Lớp Lập trình mạng · Tổng kết vòng thi").put("question_count",10).put("player_count",places.length).put("ended_at_utc","2026-10-24T13:30:00Z");
            var details=match.deepCopy().put("reuse_available",reusable).put("saved_question_count",reusable?10:0);var ranking=details.putArray("ranking");
            for(int i=0;i<places.length;i++)ranking.addObject().put("player_id","qa-"+i).put("nickname",i==0?"Nguyễn Hoàng Minh Anh với tên rất dài":i==1?"Trần Quốc Bảo":"Thí sinh "+(i+1)).put("avatar_id","quizling-"+(i+1)).put("rank",places[i]).put("score",places[i]==1?300:200).put("correct_count",places[i]==1?4:3).put("connected",i!=places.length-1);
            var dialog=HistoryResults.dialog(owner,match,details,()->{});dialog.show();
            try {
                var pane=dialog.getDialogPane();pane.applyCss();pane.layout();var scene=pane.getScene();
                var board=(RankBoard)scene.lookup("#history-ranking");
                if(places.length>0&&(board==null||board.getChildren().size()!=places.length))throw new AssertionError("Missing ranking rows");
                int winners=0;for(int rank:places)if(rank<=3)winners++;
                if(pane.lookupAll(".victory-column").size()!=winners)throw new AssertionError("Tied winners were dropped");
                if(board!=null)for(int i=0;i<places.length;i++){
                    String description=board.getChildren().get(i).getAccessibleText();
                    if(!description.contains("Hạng "+places[i]+",")||!description.contains(i<2?(i==0?"Nguyễn Hoàng Minh Anh":"Trần Quốc Bảo"):"Thí sinh "+(i+1))||!description.contains((places[i]==1?"300":"200")+" điểm")||!description.contains((places[i]==1?"4":"3")+" câu đúng"))throw new AssertionError("Stored values lost: "+description);
                }
                for(String selector:new String[]{".victory-column",".ranking-row"})for(var node:pane.lookupAll(selector)){
                    var bounds=node.localToScene(node.getBoundsInLocal());
                    if(bounds.getMinX()<0||bounds.getMaxX()>scene.getWidth()+1)throw new AssertionError("Horizontal clipping: "+name);
                }
                var close=(Button)pane.lookupButton(ButtonType.CLOSE);var bounds=close.localToScene(close.getBoundsInLocal());
                if(bounds.getMaxY()>scene.getHeight()+1||bounds.getMinY()<0)throw new AssertionError("Close action clipped");
                var reuse=(Button)scene.lookup("#history-reuse");if(reuse.isDisabled()==reusable)throw new AssertionError("Reuse availability does not match stored question data");
                var reuseBounds=reuse.localToScene(reuse.getBoundsInLocal());if(reuseBounds.getMinX()<0||reuseBounds.getMaxX()>scene.getWidth()+1||reuseBounds.getMaxY()>scene.getHeight()+1)throw new AssertionError("Reuse action clipped");
                if(reusable&&places.length>0&&places.length<=3){
                    var scroller=(ScrollPane)scene.lookup("#history-results-scroll");var viewport=scroller.lookup(".viewport");var podium=scene.lookup("#victory-podium");
                    if(podium.localToScene(podium.getBoundsInLocal()).getMaxY()>viewport.localToScene(viewport.getBoundsInLocal()).getMaxY()+1)throw new AssertionError("Compact podium needs unnecessary scrolling");
                }
                snapshot(scene, name);
                var scroller=(ScrollPane)scene.lookup("#history-results-scroll");scroller.setVvalue(1);pane.layout();snapshot(scene,name+"-ranking");
            } finally {dialog.close();}
        });
    }
    private static void snapshot(Scene scene,String name) {
        var shot=scene.snapshot(null);var bitmap=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
        try{ImageIO.write(bitmap,"png",output.resolve(name+".png").toFile());}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void fx(Runnable action)throws Exception {
        var completed=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();completed.complete(null);}catch(Throwable error){completed.completeExceptionally(error);}});completed.get(10,TimeUnit.SECONDS);
    }
}
