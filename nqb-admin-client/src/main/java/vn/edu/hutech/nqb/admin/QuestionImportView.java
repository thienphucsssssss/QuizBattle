package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import vn.edu.hutech.nqb.client.GatewayException;
import vn.edu.hutech.nqb.shared.QuestionImportRules;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** File preview is a draft. Only the explicit final action writes the private question bank. */
final class QuestionImportView {
    private final ArcadeApplication app;
    private final Dialog<ButtonType> dialog=new Dialog<>();
    private final TableView<PreviewRow> table=new TableView<>();
    private final VBox detail=column(8,text("Chọn một dòng để xem đủ câu hỏi và đáp án.","muted"));
    private final Label status=text("Chọn tệp .xlsx hoặc .docx, tối đa 500 câu và 5 MiB.","muted"),count=text("Chưa chọn tệp","caption");
    private final CheckBox allowDuplicates=new CheckBox("Cho phép nhập câu trùng (tạo bản mới)");
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"question-file");t.setDaemon(true);return t;});
    private final PauseTransition nextBatch=new PauseTransition(Duration.millis(45));
    private final List<PreviewRow> rows=new ArrayList<>();
    private final Button choose=button("Chọn tệp…","quiet",this::chooseFile),retry=button("Kiểm tra lại","quiet",this::validateOnServer);
    private Button commit;
    private Future<?> reading;
    private int version;
    private boolean busy,committing,validated,uncertain,closed;
    private String importId="";
    static final class PreviewRow {
        final QuestionFiles.Draft draft;final BooleanProperty selected=new SimpleBooleanProperty(false);
        final List<String> issues=new ArrayList<>();boolean checked,bankDuplicate;int duplicateIndex=-1;
        PreviewRow(QuestionFiles.Draft draft){this.draft=draft;issues.addAll(draft.issues());}
        boolean duplicate(){return bankDuplicate||duplicateIndex>=0;}
        boolean valid(){return checked&&issues.isEmpty();}
        String state(){if(!issues.isEmpty())return "Cần sửa";if(!checked)return "Đang kiểm tra";if(bankDuplicate)return "Trùng ngân hàng";if(duplicateIndex>=0)return "Trùng trong tệp";return "Hợp lệ";}
    }
    QuestionImportView(ArcadeApplication app) {
        this.app=app;dialog.initOwner(app.ownerStage());dialog.setTitle("Nhập câu hỏi");dialog.setHeaderText("Nhập câu hỏi từ file");styleDialog(dialog);
        table.setId("import-table");table.setMinHeight(115);table.setPrefHeight(190);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setPlaceholder(text("Dữ liệu trong tệp sẽ hiện ở đây","muted"));
        var select=new TableColumn<PreviewRow,PreviewRow>("Nhập");select.setPrefWidth(62);select.setMinWidth(62);select.setMaxWidth(70);select.setSortable(false);select.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
        select.setCellFactory(c->new TableCell<>() {
            final CheckBox check=new CheckBox();{check.setAccessibleText("Chọn câu để nhập");check.setOnAction(e->{if(getItem()!=null)getItem().selected.set(check.isSelected());});setAlignment(Pos.CENTER);}
            @Override protected void updateItem(PreviewRow row,boolean empty){super.updateItem(row,empty);setGraphic(empty||row==null?null:check);if(!empty&&row!=null){check.setSelected(row.selected.get());check.setDisable(busy||!row.valid()||row.duplicate()&&!allowDuplicates.isSelected());}}
        });
        table.getColumns().addAll(select,col("Vị trí",r->r.draft.source(),125),col("Câu hỏi",r->r.draft.question().path("content").asText().replace('\n',' '),340),col("Đúng",r->r.draft.question().path("correct_answer").asText(),60),col("Trạng thái",PreviewRow::state,165));
        table.getSelectionModel().selectedItemProperty().addListener((o,old,row)->showDetail(row));
        allowDuplicates.setId("import-allow-duplicates");allowDuplicates.setWrapText(true);allowDuplicates.setOnAction(e->{for(var row:rows)if(row.valid()&&row.duplicate())row.selected.set(allowDuplicates.isSelected());table.refresh();update();});
        var selectAll=button("Chọn câu hợp lệ","link-button",()->{rows.forEach(r->r.selected.set(r.valid()&&(!r.duplicate()||allowDuplicates.isSelected())));table.refresh();update();});
        var selectNone=button("Bỏ chọn","link-button",()->{rows.forEach(r->r.selected.set(false));table.refresh();update();});
        selectAll.disableProperty().bind(choose.disableProperty());selectNone.disableProperty().bind(choose.disableProperty());
        var toolbar=new FlowPane(10,8,choose,button("Xem mẫu / Tải mẫu","quiet",()->showTemplates(app,dialog.getDialogPane().getScene().getWindow())));retry.setId("import-recheck");toolbar.getChildren().add(retry);
        var details=scroll(detail);details.setFitToWidth(true);details.setPrefViewportHeight(190);details.setMinHeight(90);details.setId("import-detail");
        double height=Math.min(730,Math.min(app.ownerStage().getHeight()-50,javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()-70));
        javafx.scene.Node preview;
        if(height<680){
            var listTab=new Tab("Danh sách câu hỏi",table);var detailTab=new Tab("Chi tiết câu hỏi",details);var tabs=new TabPane(listTab,detailTab);tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);tabs.setId("import-tabs");
            table.setOnMouseClicked(e->{if(e.getClickCount()==2&&table.getSelectionModel().getSelectedItem()!=null)tabs.getSelectionModel().select(detailTab);});table.setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ENTER&&table.getSelectionModel().getSelectedItem()!=null){tabs.getSelectionModel().select(detailTab);e.consume();}});preview=tabs;
        }else{var split=new SplitPane(table,details);split.setOrientation(Orientation.VERTICAL);split.setDividerPositions(.5);preview=split;}
        VBox.setVgrow(preview,Priority.ALWAYS);
        status.setId("import-status");status.setFocusTraversable(true);count.setId("import-count");
        var content=column(10,toolbar,status,preview,row(12,count,space(),selectAll,selectNone),allowDuplicates);
        dialog.getDialogPane().setContent(content);dialog.getDialogPane().setPrefSize(Math.min(1000,Math.min(app.ownerStage().getWidth()-32,javafx.stage.Screen.getPrimary().getVisualBounds().getWidth()-70)),height);dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);var cancel=(Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL);cancel.setText("Hủy");
        commit=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);commit.setId("import-confirm");commit.getStyleClass().add("primary");commit.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();commit();});
        dialog.setOnCloseRequest(e->{if(committing)e.consume();});dialog.setOnHidden(e->dispose());dialog.setOnShown(e->dialog.getDialogPane().getScene().getWindow().addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN,event->dispose()));update();
    }
    private void dispose(){if(closed)return;closed=true;version++;nextBatch.stop();if(reading!=null)reading.cancel(true);worker.shutdownNow();}
    void show(){dialog.show();}
    private TableColumn<PreviewRow,String> col(String title,Function<PreviewRow,String> value,int width){var c=new TableColumn<PreviewRow,String>(title);c.setPrefWidth(width);c.setSortable(false);c.setCellValueFactory(r->new ReadOnlyStringWrapper(value.apply(r.getValue())));return c;}
    private void chooseFile(){var picker=new FileChooser();picker.setTitle("Chọn tệp câu hỏi");picker.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel / Word (*.xlsx, *.docx)","*.xlsx","*.docx"));var file=picker.showOpenDialog(dialog.getDialogPane().getScene().getWindow());if(file!=null)loadFile(file.toPath());}
    void loadFile(Path file) {
        if(committing)return;int generation=++version;nextBatch.stop();if(reading!=null)reading.cancel(true);rows.clear();table.getItems().clear();showDetail(null);allowDuplicates.setSelected(false);validated=false;uncertain=false;busy=true;status.setText("Đang đọc "+file.getFileName()+"…");update();
        reading=worker.submit(()->{
            try {var drafts=QuestionFiles.readSafely(file);Platform.runLater(()->{
                if(generation!=version||!dialog.isShowing())return;busy=false;
                for(var draft:drafts){var row=new PreviewRow(draft);row.selected.addListener((o,old,value)->update());rows.add(row);}
                table.setItems(FXCollections.observableArrayList(rows));if(rows.isEmpty()){status.setText("Tệp chưa có câu hỏi. Điền dữ liệu bên dưới hàng tiêu đề rồi chọn lại tệp.");update();return;}
                table.getSelectionModel().select(0);validateOnServer();
            });}catch(Exception failure){Platform.runLater(()->{if(generation!=version||!dialog.isShowing())return;busy=false;status.setText(safeMessage(failure));status.requestFocus();update();});}
        });
    }
    private void validateOnServer() {
        if(busy||committing||uncertain||rows.isEmpty())return;
        int generation=++version;validated=false;busy=true;importId="";
        rows.forEach(r->{r.checked=false;r.selected.set(false);r.issues.clear();r.issues.addAll(r.draft.issues());r.bankDuplicate=false;r.duplicateIndex=-1;});table.refresh();update();previewBatch(0,generation);
    }
    private void previewBatch(int start,int generation) {
        if(generation!=version||!dialog.isShowing())return;
        var payload=object().put("offset",start);if(start>0)payload.put("import_id",importId);var part=payload.putArray("rows");int end=start,bytes=0;
        while(end<rows.size()&&end-start<QuestionImportRules.MAX_BATCH){var q=rows.get(end).draft.question();int size=q.toString().getBytes(StandardCharsets.UTF_8).length+2;if(bytes+size>40*1024&&end>start)break;part.add(q);bytes+=size;end++;}
        int next=end;payload.put("complete",next==rows.size());status.setText("Đang kiểm tra "+next+" / "+rows.size()+" câu…");
        app.call("QUESTION_IMPORT_PREVIEW",payload,response->{
            if(generation!=version||!dialog.isShowing())return;importId=response.path("import_id").asText();
            for(var item:response.path("items")){int index=item.path("index").asInt(-1);if(index<start||index>=next)continue;var row=rows.get(index);row.checked=true;for(var error:item.path("errors"))if(!row.issues.contains(error.asText()))row.issues.add(error.asText());row.bankDuplicate=item.path("duplicate_in_bank").asBoolean();row.duplicateIndex=item.has("duplicate_in_file")?item.path("duplicate_in_file").asInt():-1;row.selected.set(row.valid()&&(!row.duplicate()||allowDuplicates.isSelected()));}
            table.refresh();showDetail(table.getSelectionModel().getSelectedItem());
            if(next<rows.size()){nextBatch.setOnFinished(e->previewBatch(next,generation));nextBatch.playFromStart();}
            else{busy=false;validated=true;status.setText("Xem lại các câu đã chọn trước khi nhập. Câu trùng được bỏ chọn mặc định.");table.refresh();update();}
        },failure->{if(generation!=version||!dialog.isShowing())return;busy=false;validated=false;rows.forEach(r->r.selected.set(false));status.setText(ArcadeApplication.message(failure));status.requestFocus();table.refresh();update();});
    }
    private void commit() {
        if(busy||!validated||uncertain)return;if(!app.editsAllowed()){status.setText("Chỉ nhập câu hỏi khi phòng đang chờ và tài khoản còn kết nối.");return;}
        var payload=object().put("import_id",importId).put("allow_duplicates",allowDuplicates.isSelected());var selected=payload.putArray("selected_indexes");
        for(int i=0;i<rows.size();i++)if(rows.get(i).selected.get())selected.add(i);if(selected.isEmpty())return;
        busy=true;committing=true;status.setText("Đang lưu "+selected.size()+" câu hỏi…");update();
        app.call("QUESTION_IMPORT_COMMIT",payload,response->{committing=false;dialog.setResult(ButtonType.OK);dialog.close();app.bankView();app.notice("Đã nhập "+response.path("imported").asInt()+" câu hỏi · Bỏ qua "+response.path("skipped").asInt()+" câu.",false);},failure->{
            busy=false;committing=false;validated=false;
            String code=failure instanceof GatewayException gateway?gateway.code():"UNKNOWN";
            uncertain=Set.of("TIMEOUT","DISCONNECTED","SEND_FAILED","UNKNOWN").contains(code);
            status.setText(uncertain?"Chưa xác nhận được kết quả lưu. Đóng cửa sổ và kiểm tra ngân hàng trước khi nhập lại.":ArcadeApplication.message(failure)+" Bấm Kiểm tra lại để cập nhật bản xem trước.");status.requestFocus();table.refresh();update();
        });
    }
    private void update() {
        long selected=rows.stream().filter(r->r.selected.get()).count(),errors=rows.stream().filter(r->!r.issues.isEmpty()).count(),duplicates=rows.stream().filter(PreviewRow::duplicate).count();
        count.setText(rows.isEmpty()?"Chưa có dữ liệu":"Chọn "+selected+" / "+rows.size()+" · Lỗi "+errors+" · Trùng "+duplicates);
        choose.setDisable(busy);retry.setDisable(busy||rows.isEmpty()||uncertain);allowDuplicates.setDisable(busy||!validated||uncertain);commit.setText(committing?"Đang nhập…":"Nhập "+selected+" câu hỏi");commit.setDisable(busy||!validated||uncertain||selected==0);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setDisable(committing);
    }
    private void showDetail(PreviewRow row) {
        detail.getChildren().clear();if(row==null){detail.getChildren().add(text("Chọn một dòng để xem đủ câu hỏi và đáp án.","muted"));return;}
        detail.getChildren().add(text(row.draft.source()+" · "+row.draft.question().path("category").asText(),"caption"));detail.getChildren().add(text(row.draft.question().path("content").asText(),"subtitle"));
        var options=new GridPane();options.setHgap(12);options.setVgap(8);for(int c=0;c<2;c++){var constraint=new ColumnConstraints();constraint.setPercentWidth(50);options.getColumnConstraints().add(constraint);}
        for(int i=0;i<4;i++){String letter="ABCD".substring(i,i+1);boolean correct=letter.equals(row.draft.question().path("correct_answer").asText());var label=text(letter+". "+row.draft.question().path("option_"+letter.toLowerCase(Locale.ROOT)).asText()+(correct?"  ✓ Đúng":""),correct?"import-correct":"import-option");label.setMaxWidth(Double.MAX_VALUE);options.add(label,i%2,i/2);}
        detail.getChildren().add(options);
        if(!row.issues.isEmpty())detail.getChildren().add(text(String.join("\n",row.issues),"feedback","error"));
        if(row.bankDuplicate)detail.getChildren().add(text("Nội dung câu hỏi đã có trong ngân hàng của bạn.","import-warning"));
        if(row.duplicateIndex>=0&&row.duplicateIndex<rows.size())detail.getChildren().add(text("Trùng nội dung với "+rows.get(row.duplicateIndex).draft.source().toLowerCase(Locale.ROOT)+" trong tệp.","import-warning"));
    }
    static void showTemplates(ArcadeApplication app,javafx.stage.Window owner) {
        var dialog=new Dialog<ButtonType>();dialog.initOwner(owner);dialog.setTitle("Mẫu nhập câu hỏi");dialog.setHeaderText("Điền câu hỏi theo mẫu");styleDialog(dialog);
        var headers=new FlowPane(8,8);for(String header:QuestionImportRules.HEADERS)headers.getChildren().add(text(header,"status"));
        var message=text("Giữ nguyên 7 cột. Mỗi dòng là một câu hỏi, đáp án đúng ghi A/B/C/D.\nWord cần điền vào bảng có sẵn; Excel điền trong trang CauHoi.","muted");
        var state=text("Mẫu để trống, không chứa câu hỏi hay tài khoản mẫu.","muted");state.setId("template-status");
        var excel=button("Tải mẫu Excel","quiet",()->{});var word=button("Tải mẫu Word","quiet",()->{});
        excel.setOnAction(e->chooseTemplate(dialog,QuestionFiles.Format.EXCEL,state,excel,word));word.setOnAction(e->chooseTemplate(dialog,QuestionFiles.Format.WORD,state,excel,word));
        dialog.getDialogPane().setContent(column(18,headers,message,row(12,excel,word),state));dialog.getDialogPane().setPrefWidth(670);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");dialog.show();
    }
    private static void chooseTemplate(Dialog<?> dialog,QuestionFiles.Format format,Label state,Button... buttons) {
        var picker=new FileChooser();picker.setTitle("Lưu mẫu câu hỏi");picker.setInitialFileName("Mau_cau_hoi_QuizBattle."+format.extension);picker.getExtensionFilters().add(new FileChooser.ExtensionFilter(format==QuestionFiles.Format.EXCEL?"Excel (*.xlsx)":"Word (*.docx)","*."+format.extension));
        var file=picker.showSaveDialog(dialog.getDialogPane().getScene().getWindow());if(file==null)return;
        Path path=file.toPath();if(!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("."+format.extension)){state.setText("Đuôi tệp phải là ."+format.extension+". Hãy chọn Tải mẫu để lưu lại.");return;}
        Arrays.stream(buttons).forEach(b->b.setDisable(true));state.setText("Đang tạo mẫu…");
        Thread.ofPlatform().daemon().name("question-template").start(()->{String result;try{QuestionFiles.writeTemplate(path,format);result="Đã lưu: "+path.toAbsolutePath();}catch(Exception failure){result="Không lưu được mẫu. Kiểm tra quyền ghi hoặc đóng tệp đang mở rồi thử lại.";}String message=result;Platform.runLater(()->{state.setText(message);Arrays.stream(buttons).forEach(b->b.setDisable(false));});});
    }
    private static String safeMessage(Exception failure){return failure instanceof java.io.IOException?failure.getMessage():"Không đọc được tệp. Hãy dùng mẫu Excel hoặc Word của QuizBattle.";}
}
