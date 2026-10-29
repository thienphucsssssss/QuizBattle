package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Private question-set choices shared by the bank and room configuration. */
final class QuestionSets {
    record Choice(int id,String name,int total,int active) {
        static Choice all(){return new Choice(0,"Toàn bộ ngân hàng",0,0);}
        static Choice from(JsonNode value){return new Choice(value.path("question_set_id").asInt(),value.path("name").asText(),value.path("question_count").asInt(),value.path("active_count").asInt());}
        @Override public String toString(){return id==0?name:name+" · "+active+"/"+total+" câu bật";}
    }
    private QuestionSets() { }
    static void load(ArcadeApplication app,BooleanSupplier alive,Consumer<List<Choice>> done,Consumer<Throwable> failure) {
        loadPage(app,alive,done,failure,new ArrayList<>(),1);
    }
    private static void loadPage(ArcadeApplication app,BooleanSupplier alive,Consumer<List<Choice>> done,Consumer<Throwable> failure,List<Choice> result,int page) {
        if(!alive.getAsBoolean())return;
        app.call("QUESTION_SET_LIST",object().put("page",page).put("page_size",100),response->{
            if(!alive.getAsBoolean())return;
            response.path("items").forEach(value->result.add(Choice.from(value)));
            if(response.path("has_more").asBoolean())loadPage(app,alive,done,failure,result,page+1);else done.accept(List.copyOf(result));
        },error->{if(alive.getAsBoolean())failure.accept(error);});
    }
    static void manage(ArcadeApplication app,ObjectNode filters,List<Integer> selectedIds) {
        var dialog=new Dialog<ButtonType>();dialog.initOwner(app.ownerStage());styleDialog(dialog);dialog.setTitle("Bộ câu hỏi");dialog.setHeaderText("Bộ câu hỏi của tôi");dialog.setResizable(true);
        var table=new TableView<Choice>();table.setId("question-sets-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setMinHeight(100);table.setPrefHeight(180);
        table.getColumns().addAll(List.of(col("Tên bộ",Choice::name,350),col("Tổng câu",s->Integer.toString(s.total()),100),col("Đang bật",s->Integer.toString(s.active()),100)));
        var name=new TextField();name.setId("question-set-name");name.setPromptText("VD: Ôn tập TCP/IP");
        var status=text(selectedIds.size()>500?"Bạn đang chọn "+selectedIds.size()+" câu. Mỗi bộ tối đa 500 câu; hãy bỏ bớt lựa chọn trong Ngân hàng.":"","muted");status.setId("question-set-status");
        var fromFilter=button("Tạo từ kết quả lọc","ink-button",()->{});fromFilter.setId("set-create-filtered");
        var fromSelection=button("Tạo từ "+selectedIds.size()+" câu đã chọn","quiet",()->{});fromSelection.setId("set-create-selected");
        var delete=button("Xóa bộ","danger",()->{});delete.setId("set-delete");delete.setDisable(true);
        var buttons=new FlowPane(10,8,fromFilter,fromSelection,delete);
        Consumer<Boolean> busy=value->{name.setDisable(value);table.setDisable(value);fromFilter.setDisable(value||!app.editsAllowed());fromSelection.setDisable(value||selectedIds.isEmpty()||selectedIds.size()>500||!app.editsAllowed());delete.setDisable(value||table.getSelectionModel().getSelectedItem()==null||!app.editsAllowed());};
        Runnable refresh=()->{busy.accept(true);load(app,dialog::isShowing,values->{table.setItems(FXCollections.observableArrayList(values));table.setPlaceholder(text("Chưa có bộ câu hỏi. Nhập từ file hoặc tạo từ ngân hàng.","muted"));busy.accept(false);},error->{busy.accept(false);status.setText(ArcadeApplication.message(error));});};
        table.getSelectionModel().selectedItemProperty().addListener((o,before,after)->delete.setDisable(after==null||!app.editsAllowed()));
        Consumer<Boolean> create=selectedOnly->{
            String value=name.getText().strip();if(value.isEmpty()||value.length()>80){status.setText("Nhập tên bộ từ 1–80 ký tự.");name.requestFocus();return;}
            var payload=object().put("name",value);
            if(selectedOnly){var ids=payload.putArray("question_ids");selectedIds.forEach(ids::add);}else payload.set("filters",filters.deepCopy());
            busy.accept(true);status.setText("Đang tạo bộ câu hỏi…");
            app.call("QUESTION_SET_CREATE",payload,response->{
                if(!dialog.isShowing())return;name.clear();status.setText("Đã tạo "+response.path("name").asText()+" · "+response.path("question_count").asInt()+" câu.");refresh.run();app.bankView();
            },error->{if(dialog.isShowing()){busy.accept(false);status.setText(ArcadeApplication.message(error));}});
        };
        fromFilter.setOnAction(e->create.accept(false));fromSelection.setOnAction(e->create.accept(true));
        delete.setOnAction(e->{
            Choice selected=table.getSelectionModel().getSelectedItem();if(selected==null)return;
            var confirm=new Alert(Alert.AlertType.CONFIRMATION,"Xóa bộ “"+selected.name()+"”? Các câu hỏi vẫn được giữ trong ngân hàng.",ButtonType.CANCEL,ButtonType.OK);confirm.initOwner(dialog.getDialogPane().getScene().getWindow());styleDialog(confirm);confirm.setHeaderText("Xóa bộ câu hỏi");
            if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            busy.accept(true);app.call("QUESTION_SET_DELETE",object().put("question_set_id",selected.id()),ignored->{
                if(!dialog.isShowing())return;if(app.bankQuestionSet()==selected.id())app.setBankQuestionSet(0);status.setText("Đã xóa bộ. Các câu hỏi vẫn ở trong ngân hàng.");refresh.run();app.bankView();
            },error->{if(dialog.isShowing()){busy.accept(false);status.setText(ArcadeApplication.message(error));}});
        });
        var help=text("Chọn thêm câu ở các trang Ngân hàng để tăng số trên nút. Tạo từ kết quả lọc lấy toàn bộ câu phù hợp. Tối đa 500 câu/bộ.","muted");help.setMaxWidth(Double.MAX_VALUE);help.setMinHeight(Region.USE_PREF_SIZE);status.setMinHeight(Region.USE_PREF_SIZE);
        var content=column(12,help,table,field("Tên bộ mới",name),buttons,status);VBox.setVgrow(table,Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);dialog.getDialogPane().setPrefSize(760,Math.min(560,app.ownerStage().getHeight()-40));dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");dialog.show();refresh.run();
    }
    private static TableColumn<Choice,String> col(String title,Function<Choice,String> value,double width){var col=new TableColumn<Choice,String>(title);col.setPrefWidth(width);col.setSortable(false);col.setCellValueFactory(c->new ReadOnlyStringWrapper(value.apply(c.getValue())));return col;}
}
