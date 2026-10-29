package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Shared room source selector; proximity controls remain an independent section. */
final class RoomQuestionSource extends VBox {
    private final ArcadeApplication app;
    private final ComboBox<QuestionSets.Choice> sets=new ComboBox<>();
    private final CheckBox reuse=new CheckBox("Dùng câu hỏi từ ván đã lưu");
    private final Label summary=text("Đang tải bộ câu hỏi…","muted");
    private final Spinner<Integer> count;
    private final JsonNode historical,room;
    private boolean loaded;
    RoomQuestionSource(ArcadeApplication app,JsonNode room,JsonNode historical,Spinner<Integer> count) {
        super(10);this.app=app;this.room=room;this.historical=historical;this.count=count;
        sets.setId("room-question-set");sets.setMaxWidth(Double.MAX_VALUE);sets.setDisable(true);
        sets.getItems().add(QuestionSets.Choice.all());sets.getSelectionModel().selectFirst();
        boolean hasHistory=historical!=null&&historical.path("reuse_available").asBoolean();
        reuse.setId("room-reuse-history");reuse.setWrapText(true);reuse.setSelected(hasHistory);reuse.setVisible(hasHistory);reuse.setManaged(hasHistory);
        summary.setId("room-question-pool");summary.setWrapText(true);summary.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(reuse,field("Bộ câu hỏi",sets),summary);
        reuse.setOnAction(event->update());sets.setOnAction(event->update());count.valueProperty().addListener((o,before,after)->update());
        update();
    }
    void load(BooleanSupplier alive) {
        QuestionSets.load(app,alive,choices->{
            var values=new ArrayList<QuestionSets.Choice>();values.add(QuestionSets.Choice.all());values.addAll(choices);
            sets.setItems(FXCollections.observableArrayList(values));int id=room.path("question_set_id").asInt();
            sets.setValue(values.stream().filter(value->value.id()==id).findFirst().orElse(values.getFirst()));loaded=true;update();
        },error->{
            // An older Server still supports its entire bank; do not make the old workflow unusable.
            if(error instanceof vn.edu.hutech.nqb.client.GatewayException gateway&&gateway.code().equals("UNKNOWN_TYPE")){loaded=true;sets.setValue(QuestionSets.Choice.all());update();}
            else summary.setText("Chưa tải được bộ câu hỏi. Hãy đóng và mở lại cấu hình.");
        });
    }
    private void update() {
        sets.setDisable(!loaded||reuse.isSelected());
        if(reuse.isSelected())summary.setText("Dùng đúng đề của ván “"+historical.path("room_name").asText()+"” · "+historical.path("saved_question_count").asInt()+" câu đã lưu.");
        else if(!loaded)summary.setText("Đang tải bộ câu hỏi…");
        else if(sets.getValue()==null||sets.getValue().id()==0)summary.setText("Chọn ngẫu nhiên từ các câu đang bật trong ngân hàng của bạn.");
        else summary.setText(sets.getValue().active()+" câu đang bật trong bộ đã chọn.");
    }
    void append(ObjectNode payload) {
        if(reuse.isSelected()) {
            if(count.getValue()>historical.path("saved_question_count").asInt())throw new IllegalArgumentException("Số câu vượt quá số câu của ván đã lưu.");
            payload.put("history_session_id",historical.path("session_id").asText());return;
        }
        if(!loaded||sets.getValue()==null)throw new IllegalArgumentException("Hãy chờ tải bộ câu hỏi trước khi lưu.");
        var source=sets.getValue();
        if(source.id()>0&&count.getValue()>source.active())throw new IllegalArgumentException("Bộ đã chọn chưa đủ câu đang bật. Hãy giảm số câu hoặc chọn bộ khác.");
        payload.put("question_set_id",source.id());
    }
}
