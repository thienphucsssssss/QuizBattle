package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.edu.hutech.nqb.client.GatewayException;
import java.util.*;
import java.util.function.Consumer;
import java.time.*;
import java.time.format.DateTimeFormatter;

import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Management screens bind requests, not a second independent database. */
final class ManagementViews {
    private final ArcadeApplication app;
    private int requestVersion, detailVersion;
    ManagementViews(ArcadeApplication app) { this.app = app; }
    private TableView<JsonNode> table() {
        var table = new TableView<JsonNode>(); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(text("Chưa có dữ liệu", "muted")); table.setMinWidth(0); table.setMinHeight(140); return table;
    }
    private TableColumn<JsonNode, String> textColumn(String name, String key, double width) {
        var column = new TableColumn<JsonNode, String>(name); column.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().path(key).asText())); column.setPrefWidth(width); column.setSortable(false); return column;
    }
    private TableColumn<JsonNode, String> activeColumn() {
        var column = new TableColumn<JsonNode, String>("Trạng thái"); column.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().path("is_active").asBoolean() ? "Đang bật" : "Đã tắt")); column.setPrefWidth(115); column.setSortable(false); return column;
    }
    private TableColumn<JsonNode,JsonNode> questionSelectionColumn(Runnable changed) {
        var column=new TableColumn<JsonNode,JsonNode>();column.setId("bank-selection-column");column.setMinWidth(64);column.setPrefWidth(64);column.setMaxWidth(64);column.setSortable(false);column.setResizable(false);column.setReorderable(false);
        column.setCellValueFactory(value->new ReadOnlyObjectWrapper<>(value.getValue()));
        column.setCellFactory(ignored->new TableCell<>() {
            private final CheckBox check=new CheckBox();
            {
                getStyleClass().add("question-select-cell");check.getStyleClass().add("question-pick");check.setTooltip(new Tooltip("Tick để chọn câu hỏi cho bộ. Bỏ tick để bỏ chọn."));
                check.setOnAction(event->{
                    JsonNode question=getItem();if(isEmpty()||question==null)return;int id=question.path("question_id").asInt();
                    app.updateBankSelection(List.of(id),check.isSelected()?List.of(id):List.of());getTableView().getSelectionModel().select(getIndex());changed.run();
                });
            }
            @Override protected void updateItem(JsonNode question,boolean empty) {
                super.updateItem(question,empty);setText(null);
                if(empty||question==null){check.setId(null);setGraphic(null);return;}
                int id=question.path("question_id").asInt();check.setId("question-check-"+id);check.setSelected(app.bankQuestionSelected(id));check.setAccessibleText("Chọn câu hỏi: "+question.path("content_preview").asText());setGraphic(check);
            }
        });
        return column;
    }
    private static String updatedLabel(JsonNode question) {
        try{return "Cập nhật "+DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(question.path("updated_at_utc").asText()));}
        catch(java.time.DateTimeException invalid){return "Chưa có thông tin cập nhật";}
    }
    private TableColumn<JsonNode,JsonNode> questionPreviewColumn() {
        var column=new TableColumn<JsonNode,JsonNode>("Câu hỏi");column.setSortable(false);column.setMinWidth(280);column.setPrefWidth(620);
        column.setCellValueFactory(value->new ReadOnlyObjectWrapper<>(value.getValue()));
        column.setCellFactory(ignored->new TableCell<>() {
            private final Label category=text("","bank-category"),status=text("","bank-status"),title=text("","bank-question-title"),updated=text("","bank-question-meta");
            private final VBox preview=column(6,row(8,category,space(),status),title,updated);
            {
                getStyleClass().add("bank-preview-cell");category.setWrapText(false);category.setMaxWidth(260);status.setWrapText(false);status.setMinWidth(Region.USE_PREF_SIZE);
                title.setAlignment(Pos.TOP_LEFT);title.setMinHeight(0);title.setPrefHeight(44);title.setMaxHeight(44);title.setMaxWidth(Double.MAX_VALUE);updated.setWrapText(false);
                preview.setMaxWidth(Double.MAX_VALUE);preview.prefWidthProperty().bind(widthProperty().subtract(28));
            }
            @Override protected void updateItem(JsonNode question,boolean empty) {
                super.updateItem(question,empty);setText(null);
                if(empty||question==null){setGraphic(null);return;}
                category.setText(question.path("category").asText());category.setTooltip(new Tooltip(category.getText()));
                boolean active=question.path("is_active").asBoolean();status.setText(active?"Đang bật":"Đã tắt");status.getStyleClass().setAll("bank-status",active?"active":"inactive");
                title.setText(question.path("content_preview").asText());title.setTooltip(new Tooltip(title.getText()));updated.setText(updatedLabel(question));setGraphic(preview);
            }
        });
        return column;
    }
    private static void compactFilter(ComboBox<String> combo,String label) {
        combo.setAccessibleText(label);combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?label:label+": "+(item.startsWith("Tất cả")?"Tất cả":item));setTooltip(empty||item==null?null:new Tooltip(label+": "+item));}
        });
    }
    private static VBox emptyInspector() {return column(10,text("Chi tiết câu hỏi","bank-section-title"),text("Chọn một câu để xem nội dung và bốn đáp án.","muted"));}
    private ObjectNode filters(String keyword, String category, String active, int page, String sort) {
        var p = object().put("keyword", keyword).put("page", page).put("page_size", 10).put("sort", sort.equals("ID tăng dần") ? "ID_ASC" : "UPDATED_DESC");
        if (!category.equals("Tất cả chủ đề")) p.put("category", category);
        if (!active.equals("Tất cả trạng thái")) p.put("is_active", active.equals("Đang bật"));
        return p;
    }
    void bank(String keyword, String category, String active, String sort, int page) {
        var data = table();data.setId("bank-questions");data.getStyleClass().add("bank-table");data.setFixedCellSize(128);
        var checkPage=new CheckBox();checkPage.setId("bank-check-page");checkPage.getStyleClass().add("question-pick");checkPage.setAllowIndeterminate(false);checkPage.setAccessibleText("Chọn tất cả câu hỏi trên trang hiện tại");checkPage.setTooltip(new Tooltip("Tick hoặc bỏ tick tất cả câu hỏi trên trang này. Các trang khác giữ lựa chọn hiện tại."));
        TextField search = new TextField(keyword);search.setId("bank-search");search.setAccessibleText("Tìm nội dung câu hỏi"); search.setPromptText("Tìm nội dung câu hỏi…"); search.setMaxWidth(Double.MAX_VALUE);
        var sets=new ComboBox<QuestionSets.Choice>();sets.setId("bank-question-set");sets.setAccessibleText("Lọc bộ câu hỏi");sets.setPrefWidth(255);sets.setMinWidth(180);sets.setValue(QuestionSets.Choice.all());sets.setDisable(true);
        sets.setButtonCell(new ListCell<>(){@Override protected void updateItem(QuestionSets.Choice item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?"Bộ câu hỏi":"Bộ: "+item.name());setTooltip(empty||item==null?null:new Tooltip(item.toString()));}});
        ComboBox<String> categories = new ComboBox<>(FXCollections.observableArrayList("Tất cả chủ đề")); categories.setValue(category); categories.setPrefWidth(220);compactFilter(categories,"Chủ đề");
        ComboBox<String> statuses = new ComboBox<>(FXCollections.observableArrayList("Tất cả trạng thái", "Đang bật", "Đã tắt")); statuses.setValue(active);statuses.setPrefWidth(210);compactFilter(statuses,"Trạng thái");
        ComboBox<String> order = new ComboBox<>(FXCollections.observableArrayList("Mới cập nhật", "ID tăng dần")); order.setValue(sort);order.setPrefWidth(210);compactFilter(order,"Xếp theo");
        Label count = text("Đang tải ngân hàng…", "muted"), paging = text("", "muted");
        var add = button("+ Thêm câu hỏi", "ink-button", () -> editQuestion(object())); add.setDisable(!app.editsAllowed());
        var heading=text("Ngân hàng câu hỏi","bank-title");
        var importFile=button("Nhập từ Excel / Word","quiet",()->new QuestionImportView(app).show());importFile.setDisable(!app.editsAllowed());importFile.setId("bank-import");
        var manageSets=button("Bộ câu hỏi","quiet",()->{
            var p=filters(search.getText(),categories.getValue(),statuses.getValue(),1,order.getValue()).put("question_set_id",app.bankQuestionSet());
            QuestionSets.manage(app,p,app.bankSelectedQuestions());
        });manageSets.setId("bank-manage-sets");manageSets.setDisable(!app.editsAllowed());
        var headerActions=row(10,importFile,add);headerActions.getStyleClass().add("bank-header-actions");
        var header=new BorderPane(column(5,heading,count));header.setRight(headerActions);BorderPane.setAlignment(headerActions,Pos.TOP_RIGHT);BorderPane.setMargin(headerActions,new Insets(0,0,0,18));
        boolean[] stackedHeader={false};header.widthProperty().addListener((o,before,width)->{
            boolean stacked=width.doubleValue()<900;if(stacked==stackedHeader[0])return;stackedHeader[0]=stacked;
            header.setRight(null);header.setBottom(null);if(stacked){header.setBottom(headerActions);BorderPane.setMargin(headerActions,new Insets(10,0,0,0));}else{header.setRight(headerActions);BorderPane.setMargin(headerActions,new Insets(0,0,0,18));}
        });
        var selectedCount=text("","caption","bank-selection-total");selectedCount.setId("bank-selected-count");selectedCount.setTooltip(new Tooltip("Tổng số câu đã tick trên mọi trang và bộ lọc. Tick ô bên cạnh câu hỏi để chọn hoặc bỏ chọn."));
        var selectPage=button("Chọn cả trang","quiet",()->{});selectPage.setId("bank-select-page");selectPage.setDisable(true);
        var clearSelection=button("Bỏ chọn tất cả","link-button",()->{});clearSelection.setId("bank-clear-selection");
        Runnable selectionSummary=()->{
            int total=app.bankSelectedQuestions().size();selectedCount.setText("Đã chọn "+total+" câu");clearSelection.setDisable(total==0);
            long checked=data.getItems().stream().filter(q->app.bankQuestionSelected(q.path("question_id").asInt())).count();int size=data.getItems().size();
            checkPage.setDisable(size==0);checkPage.setSelected(size>0&&checked==size);checkPage.setIndeterminate(checked>0&&checked<size);
        };
        Consumer<Boolean> markPage=checked->{var ids=data.getItems().stream().map(q->q.path("question_id").asInt()).toList();app.updateBankSelection(ids,checked?ids:List.of());data.refresh();selectionSummary.run();};
        selectPage.setOnAction(e->markPage.accept(true));checkPage.setOnAction(e->markPage.accept(checkPage.isSelected()));
        clearSelection.setOnAction(e->{app.clearBankSelection();data.getSelectionModel().clearSelection();data.refresh();selectionSummary.run();});
        var checkColumn=questionSelectionColumn(selectionSummary);checkColumn.setGraphic(checkPage);
        data.getColumns().addAll(checkColumn,questionPreviewColumn());
        selectionSummary.run();
        var templates=button("Mẫu câu hỏi","link-button",()->QuestionImportView.showTemplates(app,app.ownerStage()));
        var selectionActions=new FlowPane(10,8,manageSets,templates,selectPage,clearSelection,selectedCount);selectionActions.getStyleClass().add("bank-toolbar");
        var filterRow = new FlowPane(10, 10, categories,statuses,order);
        var searchRow=row(12,search,sets);HBox.setHgrow(search,Priority.ALWAYS);
        var filters=column(10,searchRow,filterRow);filters.getStyleClass().add("bank-filter-panel");
        var top = column(14, header,filters,selectionActions); top.setPadding(new Insets(0, 0, 10, 0));
        var inspector = emptyInspector();inspector.setPadding(new Insets(18));inspector.setPrefWidth(318);inspector.setMaxWidth(318);
        var content = new BorderPane(data); var detailScroll = scroll(inspector);detailScroll.setId("bank-inspector");detailScroll.getStyleClass().add("bank-inspector");detailScroll.setPrefWidth(318); BorderPane.setMargin(detailScroll, new Insets(0, 0, 0, 16)); content.setRight(detailScroll);
        content.widthProperty().addListener((o, before, width) -> content.setRight(width.doubleValue() < 950 ? null : detailScroll));
        Button previous = new Button("← Trước"), next = new Button("Sau →"); previous.setDisable(page <= 1);
        var inspect = button("Xem chi tiết", "quiet", () -> { var row = data.getSelectionModel().getSelectedItem(); if (row != null) openDetail(row.path("question_id").asInt()); });
        inspect.setDisable(true);
        var clear = button("Xóa bộ lọc", "link-button", () -> { app.setBankQuestionSet(0);app.setBankFilter("", "Tất cả chủ đề", "Tất cả trạng thái", "Mới cập nhật", 1); app.bankView(); });
        var bottom = row(12, paging, clear, space(), inspect, previous, next);bottom.getStyleClass().add("bank-footer"); bottom.setPadding(new Insets(12, 0, 0, 0));
        var layout = new BorderPane(content);layout.getStyleClass().add("bank-view"); layout.setTop(top); layout.setBottom(bottom); app.showManagement("Ngân hàng", layout);
        Runnable refresh = () -> {
            int version = ++requestVersion; count.setText("Đang tải…"); data.setPlaceholder(text("Đang tải câu hỏi…", "muted"));
            app.setBankFilter(search.getText(), categories.getValue(), statuses.getValue(), order.getValue(), page);
            app.call("QUESTION_LIST", filters(search.getText(), categories.getValue(), statuses.getValue(), page, order.getValue()).put("question_set_id",app.bankQuestionSet()), response -> {
                if (version != requestVersion || layout.getScene() == null) return;
                List<JsonNode> rows = new ArrayList<>(); response.path("items").forEach(rows::add);
                data.setItems(FXCollections.observableArrayList(rows));
                if(!rows.isEmpty())data.getSelectionModel().selectFirst();
                selectPage.setDisable(rows.isEmpty());selectionSummary.run();
                data.setPlaceholder(column(8, text("Chưa có câu hỏi phù hợp", "subtitle"), text("Thử bỏ bớt bộ lọc, thêm câu hỏi hoặc nhập từ Excel / Word.", "muted")));
                count.setText(response.path("total").asInt() + " câu phù hợp" + (!app.editsAllowed() ? " · Chỉ xem: phòng không ở trạng thái chờ" : ""));
                paging.setText("Trang " + page + " / " + Math.max(1, (response.path("total").asInt() + 9) / 10)); next.setDisable(!response.path("has_more").asBoolean());
            }, failure -> { if (version == requestVersion) { count.setText("Không tải được ngân hàng. Enter để thử lại."); data.setPlaceholder(text(ArcadeApplication.message(failure), "feedback", "error")); } });
        };
        Runnable newFilter = () -> { app.setBankFilter(search.getText(), categories.getValue(), statuses.getValue(), order.getValue(), 1); app.bankView(); };
        // Search requests use a version guard without rebuilding/focusing the field per keystroke.
        var debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> {
            if (page != 1) newFilter.run(); else refresh.run();
        }); search.textProperty().addListener((o, old, value) -> { requestVersion++; debounce.playFromStart(); }); search.setOnAction(e -> { debounce.stop(); if (page != 1) newFilter.run(); else refresh.run(); });
        categories.setOnAction(e -> newFilter.run()); statuses.setOnAction(e -> newFilter.run()); order.setOnAction(e -> newFilter.run());
        previous.setOnAction(e -> { app.setBankFilter(search.getText(), categories.getValue(), statuses.getValue(), order.getValue(), page - 1); app.bankView(); });
        next.setOnAction(e -> { app.setBankFilter(search.getText(), categories.getValue(), statuses.getValue(), order.getValue(), page + 1); app.bankView(); });
        data.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
            int version = ++detailVersion;
            inspect.setDisable(selected == null);
            if (selected == null){inspector.getChildren().setAll(emptyInspector());return;}
            inspector.getChildren().setAll(text("Đang tải chi tiết…", "muted"));
            app.call("QUESTION_GET", object().put("question_id", selected.path("question_id").asInt()), q -> {
                if (version != detailVersion || layout.getScene() == null) return;
                inspector.getChildren().setAll(detail(q));
            });
        });
        app.call("QUESTION_CATEGORIES", object(), response -> {
            if (layout.getScene() == null) return;
            categories.setOnAction(null); response.path("categories").forEach(v -> { if (!categories.getItems().contains(v.asText())) categories.getItems().add(v.asText()); }); categories.setValue(category); categories.setOnAction(e -> newFilter.run());
        });
        QuestionSets.load(app,()->layout.getScene()!=null,values->{
            var choices=new ArrayList<QuestionSets.Choice>();choices.add(QuestionSets.Choice.all());choices.addAll(values);sets.setItems(FXCollections.observableArrayList(choices));sets.setValue(choices.stream().filter(v->v.id()==app.bankQuestionSet()).findFirst().orElse(choices.getFirst()));sets.setDisable(false);
            sets.setOnAction(e->{if(sets.getValue()!=null){app.setBankQuestionSet(sets.getValue().id());app.bankView();}});
        },failure->app.notice("Không tải được bộ câu hỏi: "+ArcadeApplication.message(failure),true));
        refresh.run();
    }
    private VBox detail(JsonNode question) { return detail(question, false); }
    private VBox detail(JsonNode question, boolean readOnly) {
        var title=text(question.path("content").asText(),"bank-detail-question");title.setMinHeight(Region.USE_PREF_SIZE);title.setMaxWidth(Double.MAX_VALUE);
        var category=text(question.path("category").asText(),"bank-category");category.setMinHeight(Region.USE_PREF_SIZE);
        var nodes = column(14,row(8,text("Chi tiết câu hỏi","bank-section-title"),space(),text("Q"+String.format("%03d",question.path("question_id").asInt()),"bank-question-code")),category,title);
        nodes.setId("bank-detail-content");nodes.getStyleClass().add("bank-detail-content");
        var answers=column(8);
        for (String letter : List.of("A", "B", "C", "D")) {
            boolean correct=letter.equals(question.path("correct_answer").asText());
            var value=text(question.path("option_"+letter.toLowerCase(Locale.ROOT)).asText(),"bank-answer-text");value.setMinHeight(Region.USE_PREF_SIZE);value.setMaxWidth(Double.MAX_VALUE);
            var body=column(4,value);if(correct)body.getChildren().add(text("Đáp án đúng","bank-answer-note"));HBox.setHgrow(body,Priority.ALWAYS);
            var badge=text(letter,"bank-answer-letter");badge.setAlignment(Pos.CENTER);badge.setMinWidth(28);badge.setPrefWidth(28);badge.setMaxWidth(28);
            var option=row(10,badge,body);option.setAlignment(Pos.TOP_LEFT);option.getStyleClass().add("bank-answer-option");if(correct)option.getStyleClass().add("correct");option.setId("bank-answer-"+letter);answers.getChildren().add(option);
        }
        nodes.getChildren().addAll(answers,text(updatedLabel(question),"bank-question-meta"));
        if (readOnly) return nodes;
        var edit = button("Chỉnh sửa câu hỏi", "primary", () -> editQuestion(question));edit.setMaxWidth(Double.MAX_VALUE); edit.setDisable(!app.editsAllowed());
        var toggle = button(question.path("is_active").asBoolean() ? "Tắt câu hỏi" : "Bật câu hỏi", "quiet", () -> app.call("QUESTION_SET_ACTIVE", object().put("question_id", question.path("question_id").asInt()).put("is_active", !question.path("is_active").asBoolean()), ignored -> { app.notice("Đã cập nhật trạng thái câu hỏi.", false); app.bankView(); })); toggle.setDisable(!app.editsAllowed());
        var delete = button("Xóa câu hỏi", "danger", () -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Câu hỏi sẽ bị xóa khỏi ngân hàng. Thao tác này không có Undo.", ButtonType.CANCEL, ButtonType.OK); confirm.initOwner(app.ownerStage()); confirm.setHeaderText("Xóa câu Q" + question.path("question_id").asInt() + "?"); styleDialog(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) app.call("QUESTION_DELETE", object().put("question_id", question.path("question_id").asInt()), ignored -> { app.removeBankSelection(question.path("question_id").asInt());app.bankView(); app.notice("Đã xóa câu hỏi.", false); });
        }); delete.setDisable(!app.editsAllowed());
        var otherActions=new FlowPane(8,8,toggle,delete);nodes.getChildren().addAll(new Separator(), edit,otherActions); return nodes;
    }
    private void openDetail(int id) { openDetail(id, false); }
    private void openDetail(int id, boolean readOnly) { app.call("QUESTION_GET", object().put("question_id", id), q -> { var dialog = new Dialog<ButtonType>(); dialog.initOwner(app.ownerStage()); styleDialog(dialog);dialog.setTitle("Chi tiết câu hỏi");dialog.getDialogPane().getStyleClass().add("bank-detail-dialog"); dialog.getDialogPane().setContent(scroll(detail(q, readOnly))); dialog.getDialogPane().setPrefSize(600,Math.min(650,app.ownerStage().getHeight()-80));dialog.setResizable(true); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng"); dialog.show(); }); }
    void editQuestion(JsonNode original) {
        boolean existing = original.has("question_id");
        var dialog = new Dialog<ButtonType>(); dialog.initOwner(app.ownerStage()); dialog.setTitle("Ngân hàng câu hỏi"); dialog.setHeaderText(existing ? "Chỉnh sửa câu hỏi" : "Thêm câu hỏi mới"); styleDialog(dialog);
        TextArea content = new TextArea(original.path("content").asText()); content.setWrapText(true); content.setPrefRowCount(3);
        content.setId("question-content");
        List<TextArea> options = new ArrayList<>(); GridPane answers = new GridPane(); answers.setHgap(16); answers.setVgap(12);
        for (int i = 0; i < 4; i++) { String letter = "ABCD".substring(i, i+1); var field = new TextArea(original.path("option_" + letter.toLowerCase(Locale.ROOT)).asText()); field.setId("option-" + letter.toLowerCase(Locale.ROOT)); field.setWrapText(true); field.setPrefRowCount(2); options.add(field); var box = field("Lựa chọn " + letter, field); answers.add(box, i%2, i/2); GridPane.setHgrow(box, Priority.ALWAYS); }
        for (int i=0;i<2;i++) { var c = new ColumnConstraints(); c.setPercentWidth(50); answers.getColumnConstraints().add(c); }
        ComboBox<String> correct = new ComboBox<>(FXCollections.observableArrayList("A", "B", "C", "D")); correct.setValue(original.path("correct_answer").asText("A"));
        TextField category = new TextField(original.path("category").asText()); CheckBox active = new CheckBox("Đang bật — có thể dùng trong phòng thi"); active.setSelected(original.path("is_active").asBoolean(true));
        category.setId("question-category"); correct.setId("correct-answer");
        Label error = text("", "feedback", "error"); error.setVisible(false); error.setManaged(false);
        var form = column(14, field("Nội dung · tối đa 2.000 ký tự", content), answers, row(16, field("Đáp án đúng", correct), field("Chủ đề · tối đa 80 ký tự", category)), active, error);
        dialog.getDialogPane().setContent(scroll(form)); dialog.getDialogPane().setPrefSize(760, 630);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK); ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Hủy"); Button save = (Button)dialog.getDialogPane().lookupButton(ButtonType.OK); save.setText("Lưu câu hỏi"); save.getStyleClass().add("primary");
        java.util.function.Supplier<ObjectNode> payload = () -> {
            ObjectNode p = object().put("content", content.getText().strip()).put("correct_answer", correct.getValue()).put("category", category.getText().strip()).put("is_active", active.isSelected());
            for (int i=0;i<4;i++) p.put("option_" + "abcd".charAt(i), options.get(i).getText().strip()); if (existing) p.put("question_id", original.path("question_id").asInt()); return p;
        };
        java.util.function.Predicate<ObjectNode> valid = p -> {
            if (p.path("content").asText().isBlank() || p.path("content").asText().length() > 2000) { showFormError(error, "Điền nội dung từ 1–2.000 ký tự."); content.requestFocus(); return false; }
            for (int i=0;i<4;i++) if (options.get(i).getText().isBlank() || options.get(i).getText().length() > 500) { showFormError(error, "Điền lựa chọn " + "ABCD".charAt(i) + " từ 1–500 ký tự."); options.get(i).requestFocus(); return false; }
            if (category.getText().isBlank() || category.getText().length() > 80) { showFormError(error, "Điền chủ đề từ 1–80 ký tự."); category.requestFocus(); return false; } return true;
        };
        Consumer<ObjectNode> persist = new Consumer<>() {
            @Override public void accept(ObjectNode p) {
                save.setDisable(true); save.setText("Đang lưu…");
                app.call(existing ? "QUESTION_UPDATE" : "QUESTION_CREATE", p, result -> { dialog.setResult(ButtonType.OK); dialog.close(); app.bankView(); app.notice("Đã lưu câu hỏi.", false); }, failure -> {
                    save.setDisable(false); save.setText("Lưu câu hỏi");
                    if (failure instanceof GatewayException duplicate && duplicate.code().equals("DUPLICATE_QUESTION")) duplicateDialog(duplicate, () -> content.requestFocus(), allow -> {
                        // Revalidate the current draft. Confirmation only applies to the shown content.
                        ObjectNode current = payload.get(); if (!current.equals(p)) { showFormError(error, "Bản nháp đã thay đổi. Bấm Lưu để kiểm tra trùng lại."); return; }
                        if (allow) accept(p.deepCopy().put("allow_duplicate", true));
                    }); else showFormError(error, ArcadeApplication.message(failure));
                });
            }
        };
        save.addEventFilter(javafx.event.ActionEvent.ACTION, e -> { e.consume(); ObjectNode p = payload.get(); if (valid.test(p)) persist.accept(p); });
        ObjectNode baseline = payload.get();
        dialog.setOnCloseRequest(e -> {
            if (dialog.getResult() == ButtonType.OK || payload.get().equals(baseline)) return;
            var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Bản nháp chưa lưu sẽ bị bỏ. Bạn có muốn rời form?", ButtonType.CANCEL, ButtonType.OK); confirm.initOwner(dialog.getDialogPane().getScene().getWindow()); styleDialog(confirm); confirm.setHeaderText("Bỏ thay đổi chưa lưu?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) e.consume();
        });
        dialog.setOnShown(e -> dialog.getDialogPane().getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), save::fire)); dialog.show();
    }
    void duplicateDialog(GatewayException failure, Runnable back, Consumer<Boolean> accept) {
        var dialog = new Dialog<ButtonType>(); dialog.initOwner(app.ownerStage()); styleDialog(dialog); dialog.setHeaderText("Câu hỏi đã có trong ngân hàng");
        var content = column(14, text(failure.getMessage(), "subtitle"), text("Bạn có thể xem câu cũ, chỉnh lại bản nháp hoặc chủ động lưu một bản trùng.", "muted"));
        for (JsonNode q : failure.details().path("candidates")) content.getChildren().add(column(8, text("Q" + q.path("question_id").asInt() + " · " + q.path("category").asText(), "caption"), text(q.path("content_preview").asText()), button("Xem câu đã có", "quiet", () -> openDetail(q.path("question_id").asInt(), true))));
        dialog.getDialogPane().setContent(scroll(content)); dialog.getDialogPane().setPrefSize(600, 400);
        ButtonType edit = new ButtonType("Quay lại chỉnh sửa", ButtonBar.ButtonData.CANCEL_CLOSE), allow = new ButtonType("Lưu dù trùng", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(edit, allow);
        ((Button)dialog.getDialogPane().lookupButton(edit)).getStyleClass().add("primary");
        ((Button)dialog.getDialogPane().lookupButton(allow)).setDefaultButton(false);
        ((Button)dialog.getDialogPane().lookupButton(edit)).setDefaultButton(true);
        dialog.setOnShown(e -> dialog.getDialogPane().lookupButton(edit).requestFocus());
        dialog.setResultConverter(value -> value); dialog.setOnHidden(e -> { if (dialog.getResult() == allow) accept.accept(true); else back.run(); }); dialog.show();
    }
    private static void showFormError(Label label, String message) { label.setText(message); label.setVisible(true); label.setManaged(true); }
    void users(String keyword, String status, int page) {
        var table = table(); table.getColumns().addAll(textColumn("Username", "username", 180), textColumn("Tên hiển thị", "display_name", 240), textColumn("Vai trò", "role", 120), activeColumn());
        var search = new TextField(keyword); search.setPromptText("Tìm username hoặc tên giảng viên…");
        var state = new ComboBox<>(FXCollections.observableArrayList("Tất cả trạng thái", "Đang bật", "Đã tắt")); state.setValue(status);
        var total = text("Đang tải tài khoản…", "muted");
        var header = row(16, column(4, text("Người dùng", "title"), text("Quản lý tài khoản giảng viên. Học sinh dùng biệt danh.", "muted")), space(), button("+ Thêm giảng viên", "ink-button", () -> userForm(null)));
        var top = column(16, header, row(12, search, state), total); HBox.setHgrow(search, Priority.ALWAYS); top.setPadding(new Insets(0,0,18,0));
        var actions = row(12, button("Sửa tên", "quiet", () -> { JsonNode u = table.getSelectionModel().getSelectedItem(); if (u != null) userForm(u); }),
            button("Khóa / mở tài khoản", "quiet", () -> { JsonNode u = table.getSelectionModel().getSelectedItem(); if (u != null) userAction(u, "USER_SET_ACTIVE"); }),
            button("Reset mật khẩu", "danger", () -> { JsonNode u = table.getSelectionModel().getSelectedItem(); if (u != null) userAction(u, "USER_RESET_PASSWORD"); }), space());
        var selectedActions = actions.getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast).toList(); selectedActions.forEach(button -> button.setDisable(true));
        table.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> selectedActions.forEach(button -> button.setDisable(value == null || !value.path("role").asText().equals("TEACHER"))));
        var previous = button("← Trước", "quiet", () -> { app.setUserFilter(search.getText(), state.getValue(), page - 1); app.usersView(); }); previous.setDisable(page <= 1);
        var next = button("Sau →", "quiet", () -> { app.setUserFilter(search.getText(), state.getValue(), page + 1); app.usersView(); }); actions.getChildren().addAll(previous, next); actions.setPadding(new Insets(16,0,0,0));
        var layout = new BorderPane(table); layout.setTop(top); layout.setBottom(actions); app.showManagement("Người dùng", layout);
        Runnable refresh = () -> {
            int version = ++requestVersion;
            app.call("USER_LIST", filters(search.getText(), "Tất cả chủ đề", state.getValue(), page, "ID tăng dần"), response -> {
                if (version != requestVersion || layout.getScene() == null) return;
                var values = new ArrayList<JsonNode>(); response.path("items").forEach(values::add); table.setItems(FXCollections.observableArrayList(values)); next.setDisable(!response.path("has_more").asBoolean());
                total.setText(response.path("total").asInt() + " tài khoản · Trang " + page); table.setPlaceholder(text("Không tìm thấy tài khoản phù hợp", "muted"));
            });
        };
        var debounce = new PauseTransition(Duration.millis(300)); debounce.setOnFinished(e -> { app.setUserFilter(search.getText(), state.getValue(), 1); if (page != 1) app.usersView(); else refresh.run(); });
        search.textProperty().addListener((o, old, value) -> { requestVersion++; debounce.playFromStart(); }); state.setOnAction(e -> { app.setUserFilter(search.getText(), state.getValue(), 1); app.usersView(); }); refresh.run();
    }
    private void userForm(JsonNode original) {
        var dialog = new Dialog<ButtonType>(); dialog.initOwner(app.ownerStage()); styleDialog(dialog); dialog.setHeaderText(original == null ? "Thêm tài khoản giảng viên" : "Sửa tên hiển thị");
        var name = new TextField(original == null ? "" : original.path("display_name").asText()); var username = new TextField(); var password = new PasswordField(); var error = text("", "feedback", "error"); error.setVisible(false); error.setManaged(false);
        var form = column(16, field("Tên hiển thị", name)); if (original == null) form.getChildren().addAll(field("Username", username), field("Mật khẩu tạm · ít nhất 8 ký tự", password), text("Giảng viên phải đổi mật khẩu tạm sau khi đăng nhập.", "muted")); else form.getChildren().add(text("Username và vai trò giữ nguyên.", "muted")); form.getChildren().add(error);
        dialog.getDialogPane().setContent(form); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK); Button save = (Button)dialog.getDialogPane().lookupButton(ButtonType.OK); save.setText("Lưu tài khoản");
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume(); if (name.getText().isBlank() || (original == null && (username.getText().isBlank() || password.getText().length() < 8))) { showFormError(error, "Điền tên, username và mật khẩu tạm ít nhất 8 ký tự."); return; }
            ObjectNode p = object().put("display_name", name.getText().strip()); if (original == null) p.put("username", username.getText().strip()).put("temporary_password", password.getText()); else p.put("user_id", original.path("user_id").asInt());
            save.setDisable(true); app.call(original == null ? "USER_CREATE" : "USER_UPDATE", p, ignored -> { password.clear(); p.remove("temporary_password"); dialog.close(); app.usersView(); app.notice("Đã cập nhật tài khoản.", false); }, failure -> { p.remove("temporary_password"); password.clear(); save.setDisable(false); showFormError(error, ArcadeApplication.message(failure)); });
        }); dialog.show();
    }
    private void userAction(JsonNode user, String type) {
        if (!user.path("role").asText().equals("TEACHER")) { app.notice("Không thay đổi Admin qua chức năng quản lý giảng viên.", true); return; }
        var dialog = new Dialog<ButtonType>(); dialog.initOwner(app.ownerStage()); styleDialog(dialog); dialog.setHeaderText(type.equals("USER_RESET_PASSWORD") ? "Reset mật khẩu giảng viên" : "Thay đổi trạng thái tài khoản");
        var password = new PasswordField(); var error = text("", "feedback", "error"); var content = column(14, text(user.path("display_name").asText() + " / " + user.path("username").asText(), "subtitle"), text("Phiên đăng nhập cũ sẽ bị thu hồi. Ngân hàng và lịch sử vẫn được giữ.", "muted"));
        if (type.equals("USER_RESET_PASSWORD")) content.getChildren().add(field("Mật khẩu tạm mới · ít nhất 8 ký tự", password)); else content.getChildren().add(text(user.path("is_active").asBoolean() ? "Tài khoản sẽ bị khóa." : "Tài khoản sẽ được mở lại."));
        content.getChildren().add(error); dialog.getDialogPane().setContent(content); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Button go = (Button)dialog.getDialogPane().lookupButton(ButtonType.OK); go.setText("Xác nhận"); go.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume(); if (type.equals("USER_RESET_PASSWORD") && password.getText().length() < 8) { error.setText("Mật khẩu tạm cần ít nhất 8 ký tự."); return; }
            var p = object().put("user_id", user.path("user_id").asInt()); if (type.equals("USER_RESET_PASSWORD")) p.put("temporary_password", password.getText()); else p.put("is_active", !user.path("is_active").asBoolean());
            go.setDisable(true); app.call(type, p, value -> { password.clear(); p.remove("temporary_password"); dialog.close(); app.usersView(); app.notice("Đã cập nhật tài khoản.", false); }, failure -> { p.remove("temporary_password"); password.clear(); go.setDisable(false); error.setText(ArcadeApplication.message(failure)); });
        }); dialog.show();
    }
    void history(int page) {
        var table=table();table.setId("history-matches");table.getStyleClass().add("history-table");table.setFixedCellSize(84);
        var room=new TableColumn<JsonNode,JsonNode>("Phòng thi");room.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));room.setPrefWidth(310);room.setMinWidth(190);room.setSortable(false);
        room.setCellFactory(ignored->new TableCell<>() {
            private final Label title=text("","history-room-name"),subtitle=text("Kết quả đã lưu","history-room-meta");
            private final VBox content=column(5,title,subtitle);
            {getStyleClass().add("history-room-cell");title.setWrapText(false);title.setMaxWidth(Double.MAX_VALUE);content.prefWidthProperty().bind(widthProperty().subtract(30));}
            @Override protected void updateItem(JsonNode item,boolean empty){super.updateItem(item,empty);setText(null);if(empty||item==null){setGraphic(null);return;}title.setText(item.path("room_name").asText());title.setTooltip(new Tooltip(title.getText()));setGraphic(content);}
        });
        var ended=new TableColumn<JsonNode,String>("Kết thúc");ended.setCellValueFactory(c->new ReadOnlyStringWrapper(HistoryResults.endedAt(c.getValue())));ended.setPrefWidth(220);ended.setMinWidth(175);ended.setSortable(false);
        table.getColumns().addAll(room,ended,historyCountColumn("Số câu","question_count"),historyCountColumn("Thí sinh","player_count"));
        var heading=column(6,text("Lịch sử của tôi","title"),text("Xem lại các ván bạn tổ chức và kết quả từng thí sinh.","muted"));
        var top=row(16,heading,space(),button("Làm mới","quiet",app::historyView));top.setPadding(new Insets(0,0,20,0));
        var count=text("Đang tải…","history-total");var hint=text("Chọn một ván thi để xem bảng điểm và bục vinh danh.","muted");
        var layout=new BorderPane(table);layout.getStyleClass().add("history-view");
        var show=button("Xem bảng điểm","ink-button",()->{});boolean[] loading={false};
        Runnable open=()->{
            JsonNode chosen=table.getSelectionModel().getSelectedItem();if(chosen==null||loading[0])return;
            loading[0]=true;show.setDisable(true);show.setText("Đang tải…");hint.setText("Đang tải bảng điểm…");hint.getStyleClass().remove("error");
            app.call("HISTORY_GET",object().put("session_id",chosen.path("session_id").asText()),details->{
                if(layout.getScene()==null)return;loading[0]=false;show.setText("Xem bảng điểm");show.setDisable(table.getSelectionModel().getSelectedItem()==null);hint.setText("Chọn một ván thi để xem bảng điểm và bục vinh danh.");HistoryResults.dialog(app.ownerStage(),chosen,details,()->app.roomConfigFromHistory(details)).show();
            },failure->{if(layout.getScene()==null)return;loading[0]=false;show.setText("Xem bảng điểm");show.setDisable(table.getSelectionModel().getSelectedItem()==null);hint.setText(ArcadeApplication.message(failure));hint.getStyleClass().add("error");});
        };
        show.setOnAction(event->open.run());show.setDisable(true);table.getSelectionModel().selectedItemProperty().addListener((o,old,value)->show.setDisable(loading[0]||value==null));
        table.setRowFactory(ignored->{var line=new TableRow<JsonNode>();line.setOnMouseClicked(event->{if(event.getButton()==MouseButton.PRIMARY&&event.getClickCount()==2&&!line.isEmpty())open.run();});return line;});
        table.setOnKeyPressed(event->{if(event.getCode()==KeyCode.ENTER){open.run();event.consume();}});
        var previous = button("← Trước", "quiet", () -> { app.setHistoryPage(page - 1); app.historyView(); }); previous.setDisable(page <= 1);
        var next = button("Sau →", "quiet", () -> { app.setHistoryPage(page + 1); app.historyView(); }); next.setDisable(true);
        var footer=column(10,hint,row(10,count,space(),previous,next,show));footer.getStyleClass().add("history-footer");footer.setPadding(new Insets(14,0,0,0));
        layout.setTop(top);layout.setBottom(footer);table.setPlaceholder(text("Đang tải lịch sử thi đấu…","muted"));app.showManagement("Lịch sử",layout);
        app.call("HISTORY_LIST",object().put("page",page).put("page_size",10),response->{
            if(layout.getScene()==null)return;var rows=new ArrayList<JsonNode>();response.path("items").forEach(rows::add);table.setItems(FXCollections.observableArrayList(rows));
            var empty=column(8,text("Chưa có ván thi đã lưu","history-section-title"),text("Kết quả sẽ xuất hiện sau khi bạn tổ chức và hoàn thành một ván thi.","muted"));empty.setAlignment(Pos.CENTER);empty.setMaxWidth(400);table.setPlaceholder(empty);
            count.setText(response.path("total").asInt()+" ván · Trang "+page);next.setDisable(!response.path("has_more").asBoolean());
        },failure->{if(layout.getScene()==null)return;count.setText("Chưa tải được");table.setPlaceholder(column(12,text("Không tải được lịch sử","history-section-title"),button("Thử lại","quiet",app::historyView)));hint.setText(ArcadeApplication.message(failure));hint.getStyleClass().add("error");});
    }
    private TableColumn<JsonNode,String> historyCountColumn(String title,String key) {
        var result=textColumn(title,key,96);result.setMinWidth(76);
        result.setCellFactory(ignored->new TableCell<>(){
            private final Label count=text("","history-count");
            @Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setText(null);if(empty||item==null){setGraphic(null);return;}count.setText(item);setGraphic(count);}
        });return result;
    }
    void password(boolean required) {
        var old = new PasswordField(); var fresh = new PasswordField(); var repeat = new PasswordField(); var error = text("", "feedback", "error"); error.setVisible(false); error.setManaged(false);
        var form = column(18, text(required ? "Đổi mật khẩu tạm" : "Đổi mật khẩu", "title"), text(required ? "Đổi mật khẩu trước khi quản lý ngân hàng hoặc phòng thi." : "Sau khi đổi, đăng nhập lại bằng mật khẩu mới.", "muted"), field("Mật khẩu hiện tại", old), field("Mật khẩu mới · ít nhất 8 ký tự", fresh), field("Nhập lại mật khẩu mới", repeat), error);
        var save = new Button("Đổi mật khẩu"); save.getStyleClass().add("ink-button"); save.setOnAction(e -> {
            if (old.getText().isEmpty() || fresh.getText().length() < 8 || !fresh.getText().equals(repeat.getText())) { showFormError(error, "Kiểm tra mật khẩu hiện tại, độ dài và hai lần nhập mật khẩu mới."); return; }
            ObjectNode p = object().put("current_password", old.getText()).put("new_password", fresh.getText()); old.clear(); fresh.clear(); repeat.clear(); save.setDisable(true);
            app.call("PASSWORD_CHANGE", p, ignored -> { p.removeAll(); app.passwordChanged(); }, failure -> { p.removeAll(); save.setDisable(false); showFormError(error, ArcadeApplication.message(failure)); });
        }); form.getChildren().add(save); if (required) form.getChildren().add(button("Quay lại đăng nhập", "quiet", app::passwordChanged)); form.setMaxWidth(520); app.showManagement("Mật khẩu", new StackPane(form));
    }
}
