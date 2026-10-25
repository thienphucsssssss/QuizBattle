package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.util.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Render authoritative ranks. Animate old visual slots, never compute scores or ranks. */
final class RankBoard extends VBox {
    private final ParallelTransition motion = new ParallelTransition();
    private final List<HBox> rows = new ArrayList<>();
    RankBoard(JsonNode previous, JsonNode current, String ownId, boolean reducedMotion) {
        super(10); setMaxWidth(980);
        Map<String, Integer> oldSlots = new HashMap<>(), oldRanks = new HashMap<>();
        for (int i = 0; i < previous.size(); i++) { var p = previous.get(i); oldSlots.put(key(p), i); oldRanks.put(key(p), p.path("rank").asInt()); }
        int index = 0;
        for (JsonNode entry : current) {
            String id = key(entry); int slot = index++, rank = entry.path("rank").asInt();
            int delta = oldRanks.getOrDefault(id, rank) - rank;
            var rankLabel = text(String.format("%02d", rank), "rank-number"); rankLabel.setMinWidth(52);
            var name = text(entry.path("nickname").asText(), "subtitle"); HBox.setHgrow(name, Priority.ALWAYS); name.setMaxWidth(Double.MAX_VALUE);name.setWrapText(false);name.setTooltip(new Tooltip(name.getText()));
            var identity=column(3);var nameLine=row(8,name);identity.getChildren().add(nameLine);HBox.setHgrow(identity,Priority.ALWAYS);identity.setMaxWidth(Double.MAX_VALUE);
            if(id.equals(ownId))nameLine.getChildren().add(text("Bạn","rank-own-badge"));
            if(entry.hasNonNull("correct_count"))identity.getChildren().add(text(entry.path("correct_count").asInt()+" câu đúng","rank-detail"));
            var movement = text(delta > 0 ? "↑ " + delta : delta < 0 ? "↓ " + -delta : "—", "rank-movement"); movement.setMinWidth(50);
            movement.setAccessibleText(delta > 0 ? "Tăng " + delta + " hạng" : delta < 0 ? "Giảm " + -delta + " hạng" : "Giữ hạng");
            var score = text(entry.path("score").asInt() + "", "rank-score"); score.setMinWidth(70);
            var points=column(0,score,text("điểm","rank-detail"));points.setAlignment(Pos.CENTER_RIGHT);
            var avatar = QuizlingArt.avatar(entry.path("avatar_id").asText(), 56); avatar.getStyleClass().add("rank-avatar");
            var line = row(16, rankLabel, avatar, identity, movement, points);
            line.getStyleClass().add("ranking-row"); line.setMinHeight(80); line.setPrefHeight(80); line.setMaxHeight(80);
            if (id.equals(ownId)) line.getStyleClass().add("own-rank");
            if(rank>=1&&rank<=3)line.getStyleClass().add("rank-top");
            line.setAccessibleText("Hạng " + rank + ", " + entry.path("nickname").asText() + ", " + entry.path("score").asInt() + " điểm. " +(entry.hasNonNull("correct_count")?entry.path("correct_count").asInt()+" câu đúng. ":"")+ movement.getAccessibleText());
            getChildren().add(line); rows.add(line);
            if (!reducedMotion && oldSlots.containsKey(id)) {
                line.setTranslateY((oldSlots.get(id) - slot) * 90d);
                var move = new TranslateTransition(Duration.millis(850), line); move.setFromY(line.getTranslateY()); move.setToY(0); move.setInterpolator(Interpolator.SPLINE(.2,.7,.2,1)); motion.getChildren().add(move);
            }
        }
        motion.setDelay(Duration.millis(450));
        // Start after attachment; detach and reduced-motion changes always settle final state.
        sceneProperty().addListener((o, before, after) -> { if (after == null) settle(); else if (!reducedMotion) motion.playFromStart(); });
    }
    private static String key(JsonNode p) { return p.path("player_id").asText(p.path("nickname").asText()); }
    void settle() { motion.stop(); rows.forEach(row -> row.setTranslateY(0)); }
    boolean settled() { return rows.stream().allMatch(row -> Math.abs(row.getTranslateY()) < .1); }
}
