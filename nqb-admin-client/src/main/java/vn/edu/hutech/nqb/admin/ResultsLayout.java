package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import static vn.edu.hutech.nqb.admin.FxKit.*;

final class ResultsLayout {
    private ResultsLayout() { }
    static AdaptiveColumns create(JsonNode ranking,String ownId,boolean compact,boolean reduced,String boardId){
        var podium=new VictoryPodium(ranking,compact,reduced);
        var left=column(14,text("Bục vinh danh","subtitle"),podium);left.getStyleClass().add("results-podium-panel");
        var board=new RankBoard(ranking,ranking,ownId,true,true);board.setId(boardId);
        var right=column(14,text("Bảng xếp hạng","subtitle"),board);right.getStyleClass().add("results-ranking-panel");
        var result=new AdaptiveColumns(left,right,.6);result.setId("results-layout");return result;
    }
}
