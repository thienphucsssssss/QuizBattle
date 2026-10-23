package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PodiumModelTest {
    private ArrayNode ranks(int... values){var result=JsonNodeFactory.instance.arrayNode();for(int i=0;i<values.length;i++)result.addObject().put("player_id","p"+i).put("nickname","Bạn "+i).put("avatar_id","quizling-"+(i+1)).put("rank",values[i]).put("score",500-i*100);return result;}
    @Test void normalPodiumUsesServerRankAndOrder213(){var winners=PodiumModel.winners(ranks(1,2,3,4));assertEquals(java.util.List.of(2,1,3),winners.stream().map(PodiumModel.Winner::rank).toList());assertEquals("silver",winners.getFirst().tone());assertEquals("gold",winners.get(1).tone());assertEquals("bronze",winners.getLast().tone());}
    @Test void retainsAllTiesAndDoesNotInventSecondPlace(){var winners=PodiumModel.winners(ranks(1,1,3,4));assertEquals(java.util.List.of(1,1,3),winners.stream().map(PodiumModel.Winner::rank).toList());assertEquals("Huy chương vàng",winners.get(0).medalName());assertEquals("Huy chương vàng",winners.get(1).medalName());}
    @Test void tenSharedFirstPlacesAreNotTruncatedToThree(){assertEquals(10,PodiumModel.winners(ranks(1,1,1,1,1,1,1,1,1,1)).size());}
    @Test void twoPlayersEmptyAndInvalidRanksHaveNoFakeWinners(){assertEquals(2,PodiumModel.winners(ranks(1,2)).size());assertTrue(PodiumModel.winners(ranks()).isEmpty());assertTrue(PodiumModel.winners(ranks(0,4,-1)).isEmpty());}
    @Test void rankHeightsAndScoresRemainAuthoritative(){for(boolean compact:new boolean[]{false,true})assertTrue(PodiumModel.height(1,compact)>PodiumModel.height(2,compact)&&PodiumModel.height(2,compact)>PodiumModel.height(3,compact));var winners=PodiumModel.winners(ranks(1,1,3));assertEquals(500,winners.get(0).score());assertEquals(400,winners.get(1).score());assertEquals(PodiumModel.height(winners.get(0).rank(),true),PodiumModel.height(winners.get(1).rank(),true));}
}
