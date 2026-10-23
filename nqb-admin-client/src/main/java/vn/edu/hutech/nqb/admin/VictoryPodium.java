package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.util.Duration;
import java.util.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Rank-height celebration, not a numeric score chart. Scores/ranks are Server-authoritative. */
final class VictoryPodium extends FlowPane {
    private final ParallelTransition entrance=new ParallelTransition();
    private final List<VBox> columns=new ArrayList<>();
    VictoryPodium(JsonNode ranking,boolean compact,boolean reducedMotion) {
        super(20,24);setId("victory-podium");getStyleClass().add("victory-podium");
        if(compact)getStyleClass().add("compact-podium");
        setAlignment(Pos.BOTTOM_CENTER);setRowValignment(VPos.BOTTOM);setPrefWrapLength(800);setMaxWidth(840);
        int order=0;
        for(var winner:PodiumModel.winners(ranking)) {
            double diameter=compact?86:104;
            var frame=new StackPane(QuizlingArt.avatar(winner.avatarId(),diameter-18));frame.getStyleClass().add("winner-frame");frame.setMinSize(diameter,diameter);frame.setPrefSize(diameter,diameter);frame.setMaxSize(diameter,diameter);
            var portrait=new StackPane(frame);portrait.setMinHeight(diameter+14);portrait.setPrefHeight(diameter+14);portrait.setMaxWidth(diameter+20);
            var medal=medal(winner.rank(),winner.medalName());StackPane.setAlignment(medal,Pos.BOTTOM_RIGHT);portrait.getChildren().add(medal);
            var name=text(winner.nickname(),"winner-name");name.setAlignment(Pos.CENTER);name.setMinHeight(compact?42:50);name.setMaxWidth(220);
            var score=text(winner.score()+" điểm","winner-score");score.setAlignment(Pos.CENTER);
            var bar=column(0,text("HẠNG","podium-caption"),text(Integer.toString(winner.rank()),"podium-place"));bar.setAlignment(Pos.CENTER);bar.getStyleClass().add("victory-bar");double height=PodiumModel.height(winner.rank(),compact);bar.setMinHeight(height);bar.setPrefHeight(height);bar.setMaxHeight(height);bar.setMaxWidth(Double.MAX_VALUE);
            var column=column(compact?6:9,portrait,name,score,bar);column.setAlignment(Pos.BOTTOM_CENTER);column.setMinWidth(180);column.setPrefWidth(220);column.setMaxWidth(220);column.setId("winner-"+winner.playerId());column.getStyleClass().addAll("victory-column","medal-"+winner.tone());
            column.setAccessibleText(winner.medalName()+", hạng "+winner.rank()+", "+winner.nickname()+", "+winner.score()+" điểm.");
            getChildren().add(column);columns.add(column);
            if(!reducedMotion) {
                column.setTranslateY(24);column.setOpacity(0);
                var fade=new FadeTransition(Duration.millis(300),column);fade.setFromValue(0);fade.setToValue(1);
                var rise=new TranslateTransition(Duration.millis(400),column);rise.setFromY(24);rise.setToY(0);rise.setInterpolator(Interpolator.SPLINE(.18,.7,.2,1));
                var reveal=new ParallelTransition(fade,rise);reveal.setDelay(Duration.millis((winner.rank()==1?180:winner.rank()==2?90:0)+Math.min(order,5)*30));entrance.getChildren().add(reveal);
            }
            order++;
        }
        if(columns.isEmpty())getChildren().add(text("Chưa có kết quả xếp hạng.","muted"));
        sceneProperty().addListener((o,before,after)->{if(after==null)settle();else if(!reducedMotion)entrance.playFromStart();});
    }
    private static Node medal(int rank,String accessibleName) {
        var badge=new StackPane();badge.setMinSize(40,46);badge.setPrefSize(40,46);badge.setMaxSize(40,46);badge.setMouseTransparent(true);badge.setAccessibleText(accessibleName);
        var left=new Polygon(7d,14d,21d,18d,14d,45d,8d,37d,1d,40d);left.getStyleClass().add("medal-ribbon");
        var right=new Polygon(20d,18d,33d,14d,39d,40d,32d,37d,26d,45d);right.getStyleClass().add("medal-ribbon");
        var face=new Circle(20,19,17);face.getStyleClass().add("medal-face");
        var ring=new Circle(20,19,12);ring.setFill(javafx.scene.paint.Color.TRANSPARENT);ring.getStyleClass().add("medal-ring");
        var numeral=text(Integer.toString(rank),"medal-value");numeral.setAlignment(Pos.CENTER);numeral.setMouseTransparent(true);
        var art=new Pane(left,right,face,ring);art.setPrefSize(40,46);art.setMinSize(40,46);art.setMaxSize(40,46);badge.getChildren().addAll(art,numeral);StackPane.setMargin(numeral,new Insets(0,0,8,0));return badge;
    }
    void settle(){entrance.stop();columns.forEach(column->{column.setOpacity(1);column.setTranslateY(0);});}
    boolean settled(){return columns.stream().allMatch(column->column.getOpacity()>=.999&&Math.abs(column.getTranslateY())<.1);}
}
