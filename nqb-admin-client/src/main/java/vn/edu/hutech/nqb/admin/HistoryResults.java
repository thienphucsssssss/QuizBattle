package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Read-only presentation of a stored match; scores and competition ranks come from Server. */
final class HistoryResults extends VBox {
    HistoryResults(JsonNode match, JsonNode details) {
        super(16);setId("history-results");getStyleClass().add("history-results");
        var ranking=details.path("ranking");
        var metadata=new FlowPane(8,8);
        metadata.getChildren().addAll(text(match.path("question_count").asInt()+" câu hỏi","history-chip"),text(match.path("player_count").asInt()+" thí sinh","history-chip"),text(endedAt(match),"history-chip"),text("Đã lưu","history-saved"));
        getChildren().add(metadata);
        if(details.has("reuse_available")&&!details.path("reuse_available").asBoolean())getChildren().add(text("Ván này chưa lưu bộ câu hỏi để dùng lại. Các ván hoàn thành từ bản cập nhật mới sẽ có tính năng này.","muted"));
        if(!ranking.isArray()||ranking.isEmpty()) {
            var empty=column(8,text("Chưa có bảng điểm","subtitle"),text("Ván thi này chưa có kết quả thí sinh được lưu.","muted"));
            empty.getStyleClass().add("history-section");getChildren().add(empty);return;
        }
        getChildren().addAll(ResultsLayout.create(ranking,"",true,true,"history-ranking"),text("Các thí sinh bằng điểm được xếp đồng hạng.","muted"));
    }
    static String endedAt(JsonNode match) {
        try{return DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(match.path("ended_at_utc").asText()));}
        catch(DateTimeException invalid){return "Chưa có thời gian kết thúc";}
    }
    static Dialog<ButtonType> dialog(Stage owner,JsonNode match,JsonNode details) {
        return dialog(owner,match,details,null);
    }
    static Dialog<ButtonType> dialog(Stage owner,JsonNode match,JsonNode details,Runnable reuseQuestions) {
        var dialog=new Dialog<ButtonType>();dialog.initOwner(owner);styleDialog(dialog);dialog.setTitle("Bảng điểm ván thi");dialog.setResizable(true);
        dialog.setHeaderText(details.path("room_name").asText(match.path("room_name").asText("Bảng điểm")));
        var pane=dialog.getDialogPane();pane.getStyleClass().add("history-dialog");
        var content=scroll(new HistoryResults(match,details));content.setId("history-results-scroll");content.setPrefViewportHeight(500);
        pane.setContent(content);pane.setPrefWidth(Math.min(920,owner.getScene().getWidth()-64));pane.setMinWidth(560);
        pane.setPrefHeight(Math.min(740,owner.getScene().getHeight()-64));
        pane.getButtonTypes().add(ButtonType.CLOSE);var close=(Button)pane.lookupButton(ButtonType.CLOSE);close.setText("Đóng");close.getStyleClass().add("quiet");
        if(reuseQuestions!=null){
            var reuseType=new ButtonType("Dùng lại bộ câu hỏi",ButtonBar.ButtonData.OTHER);pane.getButtonTypes().add(0,reuseType);
            var reuse=(Button)pane.lookupButton(reuseType);reuse.setId("history-reuse");reuse.getStyleClass().add("primary");reuse.setDisable(!details.path("reuse_available").asBoolean());
            reuse.addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume();dialog.close();reuseQuestions.run();});
        }
        return dialog;
    }
}
