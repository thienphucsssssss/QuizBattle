package vn.edu.hutech.nqb.admin;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.*;
import vn.edu.hutech.nqb.shared.fingerprint.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Offline evaluation only: no gateway, scan provider, or database writes. */
final class FingerprintViews implements AutoCloseable {
    private record Choice(Run run,BooleanProperty test) { }
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),
        Thread.ofPlatform().daemon().name("fingerprint-evaluation").factory(),new ThreadPoolExecutor.AbortPolicy());
    private final Dialog<ButtonType> dialog=new Dialog<>();
    private final Label origin=text("Chưa chọn dữ liệu.","caption"),status=text("Mở khảo sát đã xuất hoặc dùng mô phỏng tại nhà.","muted");
    private final Label selection=text("", "muted"),metrics=text("", "muted"),detail=text("", "muted");
    private final TableView<Choice> choices=new TableView<>();
    private final TableView<FingerprintEvaluation.Row> results=new TableView<>();
    private final Spinner<Integer> k=new Spinner<>(1,100,3);
    private final ComboBox<FingerprintModel.Method> method=new ComboBox<>();
    private final FingerprintMapView map=new FingerprintMapView();
    private final ProgressBar progress=new ProgressBar();
    private final Button load, demo, evaluate, export, toggleChoice;
    private final ToggleButton setupTab=new ToggleButton("1. Chọn lượt"),resultTab=new ToggleButton("2. Kết quả"),mapTab=new ToggleButton("Sơ đồ");
    private final ScrollPane scroller;
    private Bundle data;
    private boolean synthetic,busy,closed,loadingChoices;
    private FingerprintEvaluation.Report report;

    FingerprintViews(ArcadeApplication app,Window owner) {
        dialog.initOwner(owner);dialog.setTitle("Đánh giá định vị Wi-Fi");dialog.setHeaderText("Đánh giá offline · KNN / WKNN");styleDialog(dialog);dialog.setResizable(true);
        dialog.getDialogPane().setId("fingerprint-dialog");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
        origin.setId("fingerprint-origin");status.setId("fingerprint-status");selection.setId("fingerprint-selection");detail.setId("fingerprint-detail");metrics.setId("fingerprint-metrics");
        load=button("Mở khảo sát JSON","quiet",()->{var file=chooser("Mở một phiên bản khảo sát").showOpenDialog(window());if(file!=null)loadFile(file.toPath());});load.setId("fingerprint-load");
        demo=button("Thử mô phỏng tại nhà","quiet",this::loadDemo);demo.setId("fingerprint-demo");
        evaluate=button("Đánh giá","primary",this::evaluate);evaluate.setId("fingerprint-evaluate");
        export=button("Xuất báo cáo JSON","quiet",()->{
            var chooser=chooser("Lưu báo cáo đánh giá");chooser.setInitialFileName(synthetic?"DEMO-khong-phai-do-that.json":"wifi-evaluation.json");
            var file=chooser.showSaveDialog(window());if(file!=null)exportTo(file.toPath());
        });export.setId("fingerprint-export");
        k.setId("fingerprint-k");k.setPrefWidth(100);k.setEditable(false);k.valueProperty().addListener((o,b,a)->invalidate());
        method.getItems().setAll(FingerprintModel.Method.values());method.setValue(FingerprintModel.Method.WKNN);method.setId("fingerprint-method");method.setPrefWidth(120);
        method.valueProperty().addListener((o,b,a)->renderResults());
        choices.setId("fingerprint-runs");choices.setPrefHeight(210);choices.setMinHeight(150);choices.setEditable(true);
        choices.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);choices.setPlaceholder(text("Chỉ các lượt đã xác nhận mới dùng để đánh giá.","muted"));
        var check=new TableColumn<Choice,Boolean>("Kiểm tra");check.setCellValueFactory(v->v.getValue().test());check.setCellFactory(CheckBoxTableCell.forTableColumn(check));check.setPrefWidth(85);check.setMaxWidth(95);choices.getColumns().add(check);
        col(choices,"Điểm đo",v->pointName(v.run.pointId()),180);col(choices,"Thời gian",v->time(v.run.startedAtMillis()),145);
        col(choices,"Laptop",v->v.run.deviceLabel(),170);col(choices,"Hướng cầm",v->v.run.orientation(),130);
        toggleChoice=button("Chọn / bỏ lượt đang xem","quiet",this::toggleChoice);toggleChoice.setId("fingerprint-toggle-run");
        choices.setOnKeyPressed(e->{if(e.getCode()==KeyCode.SPACE&&!busy){toggleChoice();e.consume();}});
        var setupActions=new FlowPane(10,10,field("Số hàng xóm K",k),evaluate,toggleChoice,selection);
        selection.setPrefWidth(300);
        var setup=column(10,text("Đánh dấu cả lượt làm tập kiểm tra. Những lượt còn lại dùng huấn luyện; cần ít nhất 2 điểm huấn luyện.","muted"),choices,
            text("Thu các lượt kiểm tra vào thời điểm riêng. Nếu dùng kết quả để chỉnh K, cần thêm tập xác nhận mới trước khi báo sai số cuối cùng.","caption"));
        results.setId("fingerprint-results");results.setPrefHeight(200);results.setMinHeight(150);results.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        col(results,"Điểm kiểm tra",r->pointName(r.pointId()),170);col(results,"Kết quả",r->statusName(r.estimate().status()),195);
        col(results,"Tới Server (m)",r->number(r.estimate().serverDistanceMeters()),110);col(results,"Lỗi vị trí (m)",r->number(r.positionErrorMeters()),100);col(results,"Lỗi tới Server (m)",r->number(r.serverDistanceErrorMeters()),130);
        results.getSelectionModel().selectedItemProperty().addListener((o,b,r)->showRow(r));
        var resultBody=column(10,metrics,results,text("Lỗi trung vị/P90 chỉ tính trên lượt định vị được. Luôn xem cùng tỷ lệ từ chối; “—” không có nghĩa là 0 m.","caption"));
        map.setId("fingerprint-map");map.setPrefHeight(260);map.setMinHeight(260);
        var mapBody=column(8,map,text("Vuông: Server · Chấm nhỏ: điểm khảo sát · Vòng tròn: vị trí đã biết · Dấu ×: ước lượng\nNét liền: khoảng cách thẳng tới Server · Nét đứt: sai số vị trí. Trục X/Y dùng cùng tỷ lệ, đơn vị mét.","caption"));
        var views=new StackPane(setup,resultBody,mapBody);
        var tabs=new ToggleGroup();setupTab.setToggleGroup(tabs);resultTab.setToggleGroup(tabs);mapTab.setToggleGroup(tabs);setupTab.setSelected(true);
        setupTab.setId("fingerprint-setup-tab");resultTab.setId("fingerprint-result-tab");mapTab.setId("fingerprint-map-tab");
        var methodBar=new FlowPane(10,10,field("Thuật toán đang xem",method),export);
        var body=column(10,new FlowPane(10,10,load,demo),new FlowPane(10,10,setupTab,resultTab,mapTab),methodBar,views,detail);
        scroller=scroll(body);scroller.setId("fingerprint-scroll");scroller.setPrefViewportWidth(Math.min(1000,app.ownerStage().getScene().getWidth()-130));
        Runnable showMode=()->{
            for(var node:List.of(setup,resultBody,mapBody)){boolean visible=node==(setupTab.isSelected()?setup:resultTab.isSelected()?resultBody:mapBody);node.setVisible(visible);node.setManaged(visible);}
            boolean resultMode=!setupTab.isSelected();methodBar.setVisible(resultMode);methodBar.setManaged(resultMode);detail.setVisible(resultMode);detail.setManaged(resultMode);
            setupActions.setVisible(!resultMode);setupActions.setManaged(!resultMode);
            scroller.setPrefViewportHeight(Math.max(220,app.ownerStage().getScene().getHeight()-(resultMode?280:355)));
        };
        tabs.selectedToggleProperty().addListener((o,b,a)->{if(a==null){setupTab.setSelected(true);return;}showMode.run();});showMode.run();
        progress.setMaxWidth(Double.MAX_VALUE);progress.setPrefHeight(5);progress.setVisible(false);progress.setManaged(false);
        status.setMaxWidth(scroller.getPrefViewportWidth());status.setMinHeight(Region.USE_PREF_SIZE);
        origin.setMaxWidth(scroller.getPrefViewportWidth());
        origin.setMinHeight(Region.USE_PREF_SIZE);
        dialog.getDialogPane().setContent(column(8,origin,scroller,setupActions,progress,status));dialog.setOnHidden(e->close());updateControls();
    }
    void open(Bundle initial) { dialog.show();if(initial!=null)loadBundle(initial); }
    boolean showing() { return !closed&&dialog.isShowing(); }
    private Window window() { return dialog.getDialogPane().getScene().getWindow(); }
    private FileChooser chooser(String title) { var value=new FileChooser();value.setTitle(title);value.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON","*.json"));return value; }
    void loadFile(Path file) { task(()->FingerprintEvaluationFiles.readSurvey(file),value->setData(value,false,Set.of()),"Đang đọc và kiểm tra khảo sát…"); }
    void loadBundle(Bundle bundle) { task(()->{validate(bundle);if(bundle.areas().size()!=1)throw invalid("Chọn một phiên bản khu.");return bundle;},value->setData(value,false,Set.of()),"Đang mở dữ liệu đã lưu…"); }
    private void loadDemo() { task(FingerprintDemo::create,sample->{k.getValueFactory().setValue(2);setData(sample.bundle(),true,sample.heldOut());},"Đang mở mô phỏng…"); }
    private void setData(Bundle value,boolean isSynthetic,Set<String> selected) {
        data=value;synthetic=isSynthetic;loadingChoices=true;choices.getItems().clear();
        for(var run:data.runs())if(run.state()==State.ACCEPTED){
            var choice=new Choice(run,new SimpleBooleanProperty(selected.contains(run.id())));choice.test.addListener((o,b,a)->invalidate());choices.getItems().add(choice);
        }
        loadingChoices=false;choices.getSelectionModel().selectFirst();
        var area=data.areas().getFirst();origin.setText((synthetic?"MÔ PHỎNG · không phải dữ liệu đo tại trường":"KHẢO SÁT ĐÃ LƯU · "+area.name())+"\nSSID: "+area.ssid()+" · phiên bản "+area.revision()+" · "+choices.getItems().size()+" lượt đã xác nhận / "+data.runs().size()+" lượt");
        invalidate();setupTab.setSelected(true);scroller.setVvalue(0);status.setText(synthetic?"Mô phỏng độc lập, không lưu vào kho khảo sát. Nhấn Đánh giá để thử.":"Đánh dấu lượt kiểm tra; chưa chọn sẵn để tránh dùng nhầm dữ liệu huấn luyện.");
    }
    private void toggleChoice() { var choice=choices.getSelectionModel().getSelectedItem();if(choice!=null)choice.test.set(!choice.test.get()); }
    private Set<String> heldOut() { var ids=new LinkedHashSet<String>();for(var choice:choices.getItems())if(choice.test.get())ids.add(choice.run.id());return ids; }
    private long trainingPoints() { return choices.getItems().stream().filter(c->!c.test.get()).map(c->c.run.pointId()).distinct().count(); }
    private void invalidate() {
        if(loadingChoices)return;report=null;results.getItems().clear();metrics.setText("");detail.setText("");map.update(null,null);setupTab.setSelected(true);
        int tests=heldOut().size();selection.setText(tests+" lượt kiểm tra · "+(choices.getItems().size()-tests)+" lượt huấn luyện tại "+trainingPoints()+" điểm.");
        if(data!=null)status.setText("Lựa chọn đã đổi; nhấn Đánh giá để tạo kết quả mới.");updateControls();
    }
    private void evaluate() {
        var selected=heldOut();var snapshot=data;var parameters=FingerprintModel.Parameters.baseline(k.getValue());
        task(()->FingerprintEvaluation.evaluate(snapshot,selected,parameters),value->{report=value;renderResults();resultTab.setSelected(true);scroller.setVvalue(0);status.setText(synthetic?"Kết quả mô phỏng; không dùng làm bằng chứng độ chính xác ở HUTECH.":"Đã đánh giá các lượt giữ riêng. Chưa phải kết quả định vị trực tiếp.");},"Đang tính KNN và WKNN…");
    }
    private void renderResults() {
        if(report==null)return;String selected=results.getSelectionModel().getSelectedItem()==null?null:results.getSelectionModel().getSelectedItem().runId();
        metrics.setText(String.join("\n",report.methods().stream().map(m->{var s=m.metrics();return m.method()+": "+s.estimatedRuns()+"/"+s.testRuns()+" lượt định vị · từ chối "+number(s.rejectionRate()*100)+"%\nLỗi vị trí: trung vị "+number(s.medianPositionErrorMeters())+" / P90 "+number(s.p90PositionErrorMeters())+" m · lỗi tới Server: trung vị "+number(s.medianServerDistanceErrorMeters())+" / P90 "+number(s.p90ServerDistanceErrorMeters())+" m";}).toList()));
        var values=report.methods().stream().filter(m->m.method()==method.getValue()).findFirst().orElseThrow().rows();results.getItems().setAll(values);
        values.stream().filter(r->r.runId().equals(selected)).findFirst().ifPresentOrElse(results.getSelectionModel()::select,()->results.getSelectionModel().selectFirst());
    }
    private void showRow(FingerprintEvaluation.Row row) {
        map.update(data==null?null:data.areas().getFirst(),row);
        if(row==null){detail.setText("");return;}
        var point=data.areas().getFirst().points().stream().filter(p->p.id().equals(row.pointId())).findFirst().orElseThrow();
        var run=data.runs().stream().filter(r->r.id().equals(row.runId())).findFirst().orElseThrow();var e=row.estimate();
        detail.setText(point.name()+" · "+time(run.startedAtMillis())+" · "+row.device()+"\nĐã biết ("+number(point.x())+", "+number(point.y())+") m · "+(e.estimated()?"Ước lượng ("+number(e.x())+", "+number(e.y())+") m · tới Server ≈ "+number(e.serverDistanceMeters())+" m":statusName(e.status()))
            +(row.deviceSeenInTraining()?"":"\nLaptop/card này chưa có trong tập huấn luyện.")+"\n"+run.note());
        map.setAccessibleText(detail.getText());
    }
    void exportTo(Path target) {
        if(report==null||busy)return;var snapshot=report;String areaName=data.areas().getFirst().name();boolean isSynthetic=synthetic;
        task(()->{FingerprintEvaluationFiles.writeReport(target,snapshot,areaName,isSynthetic);return target;},path->status.setText("Đã xuất báo cáo "+(isSynthetic?"MÔ PHỎNG: ":": ")+path.getFileName()),"Đang lưu báo cáo…");
    }
    private <T> void task(Callable<T> work,Consumer<T> success,String message) {
        if(busy||closed)return;busy=true;updateControls();status.setText(message);
        CompletableFuture.supplyAsync(()->{try{return work.call();}catch(Exception e){throw new CompletionException(e);}},worker).whenComplete((value,error)->Platform.runLater(()->{
            if(closed)return;busy=false;if(error!=null)status.setText(ArcadeApplication.message(error));else success.accept(value);updateControls();
        }));
    }
    private void updateControls() {
        load.setDisable(busy);demo.setDisable(busy);choices.setDisable(busy);k.setDisable(busy);toggleChoice.setDisable(busy||choices.getItems().isEmpty());
        evaluate.setDisable(busy||data==null||heldOut().isEmpty()||trainingPoints()<2);export.setDisable(busy||report==null);method.setDisable(busy||report==null);
        resultTab.setDisable(busy||report==null);mapTab.setDisable(busy||report==null);progress.setVisible(busy);progress.setManaged(busy);
    }
    private String pointName(String id) { return data==null?id:data.areas().getFirst().points().stream().filter(p->p.id().equals(id)).map(Point::name).findFirst().orElse(id); }
    static String number(Double value) { return value==null?"—":String.format(Locale.ROOT,"%.2f",value); }
    private static String time(long value) { return DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())); }
    static String statusName(FingerprintModel.Status value) { return switch(value){
        case ESTIMATED->"Ước lượng được";case NO_FRESH_DATA->"Không có mẫu mới";case INSUFFICIENT_OVERLAP->"Thiếu radio trùng";case OUTSIDE_SIGNAL_MAP->"Tín hiệu không phù hợp";
        case AMBIGUOUS_ZONE->"Mơ hồ phòng/hành lang";case AMBIGUOUS_EXACT_MATCH->"Trùng tín hiệu, khác vị trí";case NEIGHBORS_TOO_FAR_APART->"Các mốc quá xa nhau";}; }
    private static <T> void col(TableView<T> table,String title,Function<T,String> display,int width) {
        var c=new TableColumn<T,String>(title);c.setCellValueFactory(v->new ReadOnlyStringWrapper(display.apply(v.getValue())));c.setPrefWidth(width);c.setMinWidth(65);table.getColumns().add(c);
    }
    @Override public void close() { if(closed)return;closed=true;worker.shutdownNow();if(dialog.isShowing())dialog.close(); }
}
