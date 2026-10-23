package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.node.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Opt-in visual component QA only; no production mock entry or persistent data. */
public final class VictoryPodiumUiCheck {
    private static Stage stage;private static VBox root;private static VictoryPodium podium;private static Path output;
    public static void main(String[] args)throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);var ready=new CompletableFuture<Void>();Platform.startup(()->{try{Platform.setImplicitExit(false);loadFonts();stage=new Stage();ready.complete(null);}catch(Throwable failure){ready.completeExceptionally(failure);}});ready.get(5,TimeUnit.SECONDS);
        try {
            run("01-three-medals",1366,768,false,1,2,3,4);
            run("02-compact",1024,640,false,1,2,3);
            run("03-shared-gold",1024,640,false,1,1,3);
            run("04-two-players",1024,640,false,1,2);
            run("05-zero-tie",1024,640,true,1,1);
            run("06-ten-tied",1024,640,true,1,1,1,1,1,1,1,1,1,1);
            run("07-projection",1280,720,false,1,2,3);
            fx(()->{var host=new StackPane();var scene=new Scene(host,800,500);scene.getStylesheets().add(CSS);host.getStyleClass().add("game");stage.setScene(scene);var ranks=ranks(1,2,3);podium=new VictoryPodium(ranks,true,false);host.getChildren().add(podium);podium.settle();if(!podium.settled())throw new AssertionError("Reduced-motion settle failed");host.getChildren().clear();if(!podium.settled())throw new AssertionError("Detach did not settle motion");});
            System.out.println("VICTORY_PODIUM_UI_PASS: gold/silver/bronze, order213, ties, 2players, zero, 10ties, long names, compact, projection, motion/settle/detach");
        } finally {fx(()->{stage.close();Platform.exit();});}
    }
    private static ArrayNode ranks(int... places){var result=JsonNodeFactory.instance.arrayNode();for(int i=0;i<places.length;i++)result.addObject().put("player_id","p"+i).put("nickname",i==0?"Nguyễn Hoàng Minh Anh":i==1?"Trần Quốc Bảo":"Người chơi "+(i+1)).put("avatar_id","quizling-"+(i+1)).put("rank",places[i]).put("score",places.length==2&&places[1]==1?0:(11-places[i])*100);return result;}
    private static void run(String name,int width,int height,boolean reduced,int... places)throws Exception {
        fx(()->{var values=ranks(places);podium=new VictoryPodium(values,height<720,reduced);var centered=new StackPane(podium);centered.setAlignment(Pos.BOTTOM_CENTER);var title=text("Về đích!","game-title");title.setFocusTraversable(true);root=column(16,row(12,text("QUIZ BATTLE","brand"),space(),text("CHUNG CUỘC","caption")),title,centered,button("Chơi ván mới","primary",()->{}),text("Bảng xếp hạng chung cuộc","subtitle"),new RankBoard(values,values,"p0",true));root.setPadding(new Insets(28));root.getStyleClass().add("game");var wrapper=new StackPane(scroll(root));wrapper.getStyleClass().add("game");var scene=new Scene(wrapper,width,height);scene.getStylesheets().add(CSS);stage.setScene(scene);stage.show();title.requestFocus();});
        Thread.sleep(950);
        fx(()->{root.applyCss();root.layout();if(!podium.settled())throw new AssertionError("Podium motion not settled");if(podium.lookupAll(".victory-column").size()!=PodiumModel.winners(ranks(places)).size())throw new AssertionError("Tie was lost");for(var node:podium.lookupAll(".victory-column")){var bounds=node.localToScene(node.getBoundsInLocal());if(bounds.getMinX()<0||bounds.getMaxX()>width+1)throw new AssertionError("Horizontal clipping");}var scene=stage.getScene();var shot=scene.snapshot(null);var image=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,shot.getPixelReader().getArgb(x,y));try{ImageIO.write(image,"png",output.resolve(name+".png").toFile());}catch(Exception failure){throw new RuntimeException(failure);}});
    }
    private static void fx(Runnable action)throws Exception {var done=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();done.complete(null);}catch(Throwable failure){done.completeExceptionally(failure);}});done.get(5,TimeUnit.SECONDS);}
}
