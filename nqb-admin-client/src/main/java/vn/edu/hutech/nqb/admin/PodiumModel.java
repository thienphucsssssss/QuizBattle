package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Presentation only: preserve the Server's competition ranks, including all ties. */
final class PodiumModel {
    record Winner(String playerId,String nickname,String avatarId,int rank,int score) {
        String tone(){return rank==1?"gold":rank==2?"silver":"bronze";}
        String medalName(){return rank==1?"Huy chương vàng":rank==2?"Huy chương bạc":"Huy chương đồng";}
    }
    private PodiumModel(){ }
    static List<Winner> winners(JsonNode ranking) {
        var winners=new ArrayList<Winner>();
        if(!ranking.isArray())return List.of();
        for(var entry:ranking){int rank=entry.path("rank").asInt();if(rank>=1&&rank<=3)winners.add(new Winner(entry.path("player_id").asText(),entry.path("nickname").asText(),entry.path("avatar_id").asText(),rank,entry.path("score").asInt()));}
        // A podium is arranged 2–1–3, not a re-computation of rank from array index/score.
        winners.sort(Comparator.comparingInt(w->w.rank()==2?0:w.rank()==1?1:2));
        return List.copyOf(winners);
    }
    static double height(int rank,boolean compact){return compact?(rank==1?124:rank==2?96:72):(rank==1?168:rank==2?128:96);}
}
