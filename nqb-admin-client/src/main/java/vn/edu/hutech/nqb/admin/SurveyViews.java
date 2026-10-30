package vn.edu.hutech.nqb.admin;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vn.edu.hutech.nqb.client.*;
import vn.edu.hutech.nqb.shared.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;
import static vn.edu.hutech.nqb.shared.SurveyRepository.LOCAL_OWNER;

/** Offline field notebook, using the existing dialog, typography and button conventions. */
final class SurveyViews implements AutoCloseable {
    private final ArcadeApplication app;
    private final CompletableFuture<SurveyRepository> repository;
    private SurveyCaptureService capture;
    private Dialog<ButtonType> dialog;
    private ComboBox<Area> areas;
    private ComboBox<Point> points;
    private ComboBox<WifiScanProvider.Adapter> adapters;
    private Label status,details;
    private TableView<Run> runs;
    private Button start,cancel,accept,reject,sync;
    private ProgressBar progress;
    private VBox selectors;
    private final List<Button> actionButtons=new ArrayList<>();
    private final List<Control> captureInputs=new ArrayList<>();
    private boolean closing,ioBusy,closed;
    private int refreshGeneration;
    SurveyViews(ArcadeApplication app){
        this.app=app;repository=CompletableFuture.supplyAsync(()->{try{return new SurveyRepository(Path.of(System.getProperty("nqb.survey.database",Path.of(System.getProperty("user.home"),".quizbattle","wifi-surveys.db").toString())),true);}catch(Exception e){throw new CompletionException(e);}});
    }
    void open(){
        if(closed)return;if(dialog!=null&&dialog.isShowing()){dialog.getDialogPane().getScene().getWindow().requestFocus();return;}
        closing=false;actionButtons.clear();captureInputs.clear();dialog=new Dialog<>();dialog.initOwner(app.ownerStage());dialog.setTitle("Khảo sát Wi-Fi");dialog.setHeaderText("Bản đồ tín hiệu · tọa độ đo bằng mét");styleDialog(dialog);dialog.setResizable(true);
        dialog.getDialogPane().setId("survey-dialog");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        areas=new ComboBox<>();areas.setId("survey-area");areas.setMaxWidth(Double.MAX_VALUE);
        points=new ComboBox<>();points.setId("survey-point");points.setMaxWidth(Double.MAX_VALUE);
        details=text("Cửa phòng là (0, 0); trục X dọc hành lang, Y hướng vào phòng.","muted");
        var newArea=action("Tạo khu",()->editArea(null));newArea.setId("survey-new-area");var revise=action("Phiên bản mới",()->{if(areas.getValue()!=null)editArea(areas.getValue());});
        newArea.setMinWidth(Region.USE_PREF_SIZE);revise.setMinWidth(Region.USE_PREF_SIZE);areas.setMinWidth(0);
        var areaField=field("Khu và phiên bản tọa độ",areas);areaField.setMinWidth(0);HBox.setHgrow(areaField,Priority.ALWAYS);
        selectors=column(10,row(10,areaField,newArea,revise),details);
        adapters=new ComboBox<>();adapters.setId("survey-adapter");adapters.setMaxWidth(Double.MAX_VALUE);
        var device=new TextField();device.setId("survey-device");device.setPromptText("Ví dụ: Laptop Phúc · dùng cùng nhãn khi đo lại");
        var orientation=new TextField();orientation.setId("survey-orientation");orientation.setPromptText("Ví dụ: nhìn theo chiều X tăng");
        var duration=new ComboBox<Integer>(FXCollections.observableArrayList(20,30,60));duration.setValue(30);duration.setId("survey-duration");
        captureInputs.addAll(List.of(device,orientation,duration));
        progress=new ProgressBar(0);progress.setMaxWidth(Double.MAX_VALUE);progress.setAccessibleText("Tiến độ lượt khảo sát");
        start=action("Bắt đầu thu mẫu",()->{
            if(areas.getValue()==null||points.getValue()==null||adapters.getValue()==null){status.setText("Chọn khu, điểm và card Wi-Fi trước khi thu.");return;}
            Area area=areas.getValue();Point point=points.getValue();
            capture=new SurveyCaptureService(app.proximityClient(),repository.join());setBusy(true);cancel.setDisable(false);cancel.setVisible(true);cancel.setManaged(true);progress.setProgress(0);
            status.setText("Đứng yên tại "+point.name()+" trong cả lượt. Mỗi scan được lưu ngay trên máy.");
            capture.start(area,point,adapters.getValue(),device.getText().strip(),orientation.getText().strip(),duration.getValue(),p->Platform.runLater(()->{
                progress.setProgress(p.elapsedSeconds()/(double)p.durationSeconds());var last=p.run().scans().getLast();status.setText("Đang thu tại "+point.name()+" · "+quality(p.run()).summary()+"\n"+last.diagnostic());
            })).whenComplete((run,error)->Platform.runLater(()->{
                capture.close();capture=null;setBusy(false);cancel.setDisable(true);cancel.setVisible(false);cancel.setManaged(false);progress.setProgress(error==null?1:0);
                status.setText(error==null?stateName(run.state())+" · "+quality(run).summary()+". "+run.note():ArcadeApplication.message(error));refreshRuns(run==null?null:run.id());
                if(closing)dialog.close();
            }));
        });start.setId("survey-start");start.setDisable(true);
        cancel=action("Hủy lượt thu",()->{if(capture!=null){capture.cancel();cancel.setDisable(true);status.setText("Đang kết thúc lượt đã hủy; các scan đã lưu sẽ được đánh dấu loại bỏ.");}});cancel.setId("survey-cancel");cancel.setDisable(true);cancel.setVisible(false);cancel.setManaged(false);
        var captureFields=column(10,two(field("Điểm đang đứng",points),field("Card Wi-Fi lấy tín hiệu",adapters)),two(field("Nhãn laptop",device),field("Hướng cầm máy / hướng nhìn",orientation)),row(10,field("Thời lượng (giây)",duration),start),
            text("Thời lượng không thay thế số scan mới. Điểm đo, SSID và card Wi-Fi được khóa trong cả lượt.","caption"));
        runs=new TableView<>();runs.setId("survey-runs");runs.setPrefHeight(175);runs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);runs.setPlaceholder(text("Chưa có lượt thu ở phiên bản khu này.","muted"));
        tableColumn(runs,"Điểm",r->pointName(r.pointId()),145);tableColumn(runs,"Thời gian",r->DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").format(Instant.ofEpochMilli(r.startedAtMillis()).atZone(ZoneId.systemDefault())),145);
        tableColumn(runs,"Trạng thái",r->stateName(r.state()),145);tableColumn(runs,"Scan mới / tổng",r->quality(r).freshScans()+" / "+r.scans().size(),125);tableColumn(runs,"BSSID / tần số",r->String.valueOf(quality(r).radios()),125);
        var runDetail=text("Chọn một lượt để xem và xác nhận dữ liệu.","muted");runDetail.setId("survey-run-detail");
        accept=action("Xác nhận đúng điểm",()->review(State.ACCEPTED));accept.setId("survey-accept");reject=action("Loại lượt thu",()->review(State.DISCARDED));reject.setId("survey-reject");accept.setDisable(true);reject.setDisable(true);
        runs.getSelectionModel().selectedItemProperty().addListener((o,b,r)->{reviewButtons();if(r!=null)runDetail.setText(r.deviceLabel()+" · "+r.adapterName()+" · "+r.orientation()+"\n"+quality(r).summary()+"\n"+r.note());});
        var export=action("Xuất JSON",this::exportFile);export.setId("survey-export");var importFile=action("Nhập JSON",this::importFile);importFile.setId("survey-import");
        sync=action("Đồng bộ lên Server",this::sync);sync.setId("survey-sync");
        var evaluation=action("Đánh giá định vị",()->{
            var area=areas.getValue();if(area==null){status.setText("Chọn một phiên bản khu trước khi đánh giá.");return;}
            setBusy(true);repository.thenCompose(r->r.bundle(LOCAL_OWNER,area.id(),area.revision())).whenComplete((bundle,error)->Platform.runLater(()->{
                if(!showing())return;setBusy(false);if(error!=null)status.setText(ArcadeApplication.message(error));else app.openFingerprint(bundle,dialog.getDialogPane().getScene().getWindow());
            }));
        });evaluation.setId("survey-evaluate");
        var actions=new FlowPane(10,10,accept,reject,export,importFile,sync,evaluation);
        var saved=column(10,runs,runDetail,actions,text("Chỉ xác nhận khi đã đứng yên đúng điểm. Tối thiểu 3 scan mới và 2 BSSID/tần số là điều kiện thu mẫu ban đầu, chưa bảo đảm độ chính xác định vị.","caption"));
        var captureTab=new ToggleButton("Thu mẫu");captureTab.setId("survey-capture-tab");var savedTab=new ToggleButton("Lượt đã lưu");savedTab.setId("survey-saved-tab");var modes=new ToggleGroup();captureTab.setToggleGroup(modes);savedTab.setToggleGroup(modes);captureTab.setSelected(true);
        var views=new StackPane(captureFields,saved);saved.setVisible(false);saved.setManaged(false);
        modes.selectedToggleProperty().addListener((o,b,a)->{if(a==null){captureTab.setSelected(true);return;}boolean collecting=a==captureTab;captureFields.setVisible(collecting);captureFields.setManaged(collecting);saved.setVisible(!collecting);saved.setManaged(!collecting);});
        var tabs=column(10,row(10,captureTab,savedTab),views);
        status=text("Đang mở kho khảo sát trên máy…","muted");status.setId("survey-status");status.setMinHeight(Region.USE_PREF_SIZE);
        status.setPrefWidth(800);status.setMaxWidth(800);
        HBox.setHgrow(progress,Priority.ALWAYS);
        var body=column(12,selectors,tabs);var scroller=scroll(body);scroller.setId("survey-scroll");scroller.setPrefViewportWidth(800);scroller.setPrefViewportHeight(Math.max(260,Math.min(450,app.ownerStage().getScene().getHeight()-330)));dialog.getDialogPane().setContent(column(8,scroller,row(10,progress,cancel),status));
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
        dialog.setOnCloseRequest(e->{if(capture!=null&&capture.busy()){closing=true;capture.cancel();status.setText("Đang lưu trạng thái hủy trước khi đóng…");e.consume();}});
        dialog.setOnHidden(e->{refreshGeneration++;if(capture!=null)capture.cancel();});setBusy(true);dialog.show();
        areas.valueProperty().addListener((o,b,a)->{if(a==null)return;points.setItems(FXCollections.observableArrayList(a.points()));points.getSelectionModel().selectFirst();details.setText("SSID: "+a.ssid()+" · Server ("+a.serverX()+", "+a.serverY()+") m · "+a.collector()+"\n"+a.axisNote());refreshRuns(null);});
        repository.whenComplete((repo,error)->Platform.runLater(()->{if(!showing())return;if(error!=null){status.setText(ArcadeApplication.message(error));return;}refreshAreas(null);status.setText("Dữ liệu lưu trên máy, sử dụng được khi chưa kết nối Server.");setBusy(false);}));
        app.proximityClient().scanAdaptersAsync().whenComplete((values,error)->Platform.runLater(()->{if(!showing())return;if(error!=null){status.setText(ArcadeApplication.message(error));return;}adapters.setItems(FXCollections.observableArrayList(values));values.stream().filter(WifiScanProvider.Adapter::connected).findFirst().or(()->values.stream().findFirst()).ifPresent(adapters::setValue);}));
    }
    private boolean showing(){return !closed&&dialog!=null&&dialog.isShowing();}
    private Button action(String name,Runnable task){var b=new Button(name);b.getStyleClass().add("quiet");b.setOnAction(e->{if(!ioBusy||b==cancel)task.run();});actionButtons.add(b);return b;}
    private HBox two(Region left,Region right){HBox.setHgrow(left,Priority.ALWAYS);HBox.setHgrow(right,Priority.ALWAYS);left.setPrefWidth(360);right.setPrefWidth(360);return row(12,left,right);}
    private void setBusy(boolean busy){ioBusy=busy;for(var b:actionButtons)if(b!=cancel)b.setDisable(busy);captureInputs.forEach(c->c.setDisable(busy));selectors.setDisable(busy);points.setDisable(busy);adapters.setDisable(busy);start.setDisable(busy||!repository.isDone()||repository.isCompletedExceptionally());sync.setDisable(busy||app.surveyGateway()==null);sync.setTooltip(new Tooltip(app.surveyGateway()==null?"Đăng nhập giảng viên/Admin để gửi dữ liệu cho Server.":"Gửi các lượt đã xác nhận hoặc loại bỏ cho tài khoản đang đăng nhập."));reviewButtons();}
    private void reviewButtons(){var r=runs==null?null:runs.getSelectionModel().getSelectedItem();if(accept!=null){accept.setDisable(ioBusy||r==null||r.state()!=State.REVIEW||!quality(r).canAccept());reject.setDisable(ioBusy||r==null||r.state()!=State.REVIEW);}}
    private void refreshAreas(Area select){repository.thenCompose(r->r.areas(LOCAL_OWNER)).whenComplete((values,error)->Platform.runLater(()->{if(!showing())return;if(error!=null){status.setText(ArcadeApplication.message(error));return;}areas.setItems(FXCollections.observableArrayList(values));if(select!=null)areas.setValue(select);else areas.getSelectionModel().selectFirst();}));}
    private void refreshRuns(String selected){var area=areas.getValue();if(area==null)return;int gen=++refreshGeneration;repository.thenCompose(r->r.runs(LOCAL_OWNER,area.id(),area.revision())).whenComplete((values,error)->Platform.runLater(()->{if(!showing()||gen!=refreshGeneration)return;if(error!=null){status.setText(ArcadeApplication.message(error));return;}runs.setItems(FXCollections.observableArrayList(values));if(selected!=null)values.stream().filter(r->r.id().equals(selected)).findFirst().ifPresent(runs.getSelectionModel()::select);}));}
    private String pointName(String id){var a=areas.getValue();return a==null?id:a.points().stream().filter(p->p.id().equals(id)).map(Point::name).findFirst().orElse(id);}
    private static String stateName(State state){return switch(state){case CAPTURING->"Đang thu";case REVIEW->"Chờ xác nhận";case ACCEPTED->"Đã xác nhận";case DISCARDED->"Đã loại";case INTERRUPTED->"Bị gián đoạn";};}
    private void review(State state){var run=runs.getSelectionModel().getSelectedItem();if(run==null)return;
        if(state==State.ACCEPTED&&!confirm("Xác nhận lượt thu", "Bạn đã đứng yên tại "+pointName(run.pointId())+" suốt lượt thu? Xác nhận sẽ khóa dữ liệu lượt này."))return;
        setBusy(true);repository.thenCompose(r->r.finish(run.id(),state,state==State.ACCEPTED?"Người khảo sát xác nhận đứng yên đúng điểm.":"Người khảo sát loại lượt thu.")).whenComplete((value,error)->Platform.runLater(()->{if(!showing())return;setBusy(false);status.setText(error==null?stateName(state)+"; dữ liệu thô vẫn được giữ.":ArcadeApplication.message(error));refreshRuns(run.id());}));
    }
    private boolean confirm(String title,String message){var a=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.OK,ButtonType.CANCEL);a.initOwner(dialog.getDialogPane().getScene().getWindow());a.setHeaderText(title);styleDialog(a);((Button)a.getDialogPane().lookupButton(ButtonType.OK)).setText("Xác nhận");((Button)a.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Hủy");return a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private FileChooser chooser(String title){var c=new FileChooser();c.setTitle(title);c.getExtensionFilters().add(new FileChooser.ExtensionFilter("Khảo sát Wi-Fi JSON","*.json"));return c;}
    private void exportFile(){var a=areas.getValue();if(a==null)return;var c=chooser("Xuất khu khảo sát");c.setInitialFileName("wifi-survey-v"+a.revision()+".json");var file=c.showSaveDialog(dialog.getDialogPane().getScene().getWindow());if(file==null)return;setBusy(true);repository.thenCompose(r->r.exportFile(file.toPath(),LOCAL_OWNER,a.id(),a.revision())).whenComplete((v,e)->done(e,"Đã xuất khu và toàn bộ lượt thu của phiên bản này."));}
    private void importFile(){var file=chooser("Nhập khu khảo sát").showOpenDialog(dialog.getDialogPane().getScene().getWindow());if(file==null)return;setBusy(true);repository.thenCompose(r->r.previewFile(file.toPath())).whenComplete((bundle,error)->Platform.runLater(()->{
        if(!showing())return;if(error!=null){setBusy(false);status.setText(ArcadeApplication.message(error));return;}
        if(!confirm("Nhập dữ liệu đã kiểm tra",bundle.areas().size()+" phiên bản khu, "+bundle.runs().size()+" lượt thu. Dữ liệu trùng khớp được bỏ qua; cùng mã khác nội dung sẽ hủy toàn bộ lần nhập.")){setBusy(false);return;}
        repository.thenCompose(r->r.importBundle(LOCAL_OWNER,bundle)).whenComplete((count,e)->{done(e,e==null?"Đã nhập "+count+" lượt mới.":"");Platform.runLater(()->{if(showing())refreshAreas(null);});});
    }));}
    private void sync(){var area=areas.getValue();var gateway=app.surveyGateway();if(area==null||gateway==null){status.setText("Đăng nhập tài khoản giảng viên/Admin để đồng bộ lên Server.");return;}
        if(!confirm("Đồng bộ khu lên Server", "Gửi tọa độ và các lượt đã xác nhận/loại bỏ của "+area+" cho tài khoản đang đăng nhập? Dữ liệu trên máy vẫn được giữ."))return;
        setBusy(true);repository.thenCompose(r->r.bundle(LOCAL_OWNER,area.id(),area.revision())).thenCompose(b->SurveySync.upload(gateway,b,message->Platform.runLater(()->{if(showing())status.setText(message);}))).whenComplete((count,e)->done(e,e==null?"Server đã nhận "+count+" lượt. Có thể đồng bộ lại an toàn sau mất mạng.":""));
    }
    private void done(Throwable error,String message){Platform.runLater(()->{if(!showing())return;setBusy(false);status.setText(error==null?message:ArcadeApplication.message(error));});}
    private void editArea(Area original){
        var form=new Dialog<ButtonType>();form.initOwner(dialog.getDialogPane().getScene().getWindow());form.setTitle(original==null?"Tạo khu khảo sát":"Tạo phiên bản tọa độ mới");form.setHeaderText("Đo tọa độ trước; cửa phòng làm gốc (0, 0)");styleDialog(form);form.setResizable(true);form.getDialogPane().setId("survey-area-editor");
        var name=input("survey-area-name",original==null?"":original.name());var collector=input("survey-collector",original==null?"":original.collector());var ssid=input("survey-ssid",original==null?"HUTECH E1":original.ssid());
        var x=input("survey-server-x",original==null?"0":String.valueOf(original.serverX()));var y=input("survey-server-y",original==null?"0":String.valueOf(original.serverY()));
        var axis=input("survey-axis",original==null?"X tăng dọc hành lang; Y dương hướng vào phòng.":original.axisNote());
        var entries=new TextArea();entries.setId("survey-point-entries");entries.setPrefRowCount(5);entries.setPromptText("Cửa phòng; Hành lang; 0; 0\nBàn server; Phòng; 2; 4\nMốc 5 m; Hành lang; 5; 0");
        if(original!=null)entries.setText(String.join("\n",original.points().stream().map(p->p.name()+"; "+(p.zone()==Zone.ROOM?"Phòng":"Hành lang")+"; "+p.x()+"; "+p.y()).toList()));
        var error=text("Tọa độ tính bằng mét; dùng dấu chấm cho số thập phân. Điểm hành lang có Y = 0.","muted");error.setId("survey-editor-status");
        var body=column(10,two(field("Tên khu",name),field("Người khảo sát (nhãn)",collector)),field("SSID khảo sát",ssid),two(field("Tọa độ Server X (m)",x),field("Tọa độ Server Y (m)",y)),field("Quy ước trục",axis),field("Mỗi dòng: Tên điểm; Phòng hoặc Hành lang; X; Y",entries),error);
        var scroll=FxKit.scroll(body);scroll.setPrefViewportWidth(740);scroll.setPrefViewportHeight(Math.max(280,app.ownerStage().getScene().getHeight()-280));form.getDialogPane().setContent(scroll);
        var saveType=new ButtonType("Lưu khu",ButtonBar.ButtonData.OK_DONE);form.getDialogPane().getButtonTypes().addAll(saveType,ButtonType.CANCEL);var save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("survey-area-save");
        ((Button)form.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Hủy");
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();try{
            var parsed=new ArrayList<Point>();for(String line:entries.getText().split("\\R")){if(line.isBlank())continue;String[] fields=line.split(";",-1);if(fields.length!=4)throw invalid("Mỗi dòng cần đúng 4 phần phân cách bằng dấu ;.");
                String pointName=fields[0].strip(),zone=fields[1].strip();Zone value=switch(zone.toLowerCase(Locale.ROOT)){case "phòng","phong","room"->Zone.ROOM;case "hành lang","hanh lang","corridor"->Zone.CORRIDOR;default->throw invalid("Vùng phải là Phòng hoặc Hành lang.");};
                String pointId=original==null?id():original.points().stream().filter(p->p.name().equalsIgnoreCase(pointName)).map(Point::id).findFirst().orElseGet(WifiSurvey::id);
                parsed.add(new Point(pointId,pointName,value,Double.parseDouble(fields[2].strip()),Double.parseDouble(fields[3].strip())));}
            var area=new Area(original==null?id():original.id(),original==null?1:original.revision()+1,name.getText().strip(),collector.getText().strip(),ssid.getText(),"m",axis.getText().strip(),Double.parseDouble(x.getText().strip()),Double.parseDouble(y.getText().strip()),System.currentTimeMillis(),parsed);validate(area);
            save.setDisable(true);error.setText("Đang lưu phiên bản tọa độ…");repository.thenCompose(r->r.saveArea(LOCAL_OWNER,area)).whenComplete((saved,failure)->Platform.runLater(()->{if(failure!=null){save.setDisable(false);error.setText(ArcadeApplication.message(failure));}else{form.close();refreshAreas(saved);status.setText("Đã lưu "+saved+". Các lượt thu phiên bản trước được giữ nguyên, không tự gán sang tọa độ mới.");}}));
        }catch(Exception failure){error.setText(failure instanceof NumberFormatException?"Tọa độ phải là số, ví dụ 2.5.":ArcadeApplication.message(failure));}});
        form.show();
    }
    private TextField input(String id,String value){var f=new TextField(value);f.setId(id);return f;}
    private static void tableColumn(TableView<Run> table,String name,Function<Run,String> display,int width){var c=new TableColumn<Run,String>(name);c.setCellValueFactory(v->new ReadOnlyStringWrapper(display.apply(v.getValue())));c.setMinWidth(width);c.setPrefWidth(width);table.getColumns().add(c);}
    @Override public void close(){closed=true;if(capture!=null){capture.close();var pending=capture.completion();if(pending!=null){pending.whenComplete((v,e)->repository.thenAcceptAsync(SurveyRepository::close));return;}}repository.thenAcceptAsync(SurveyRepository::close);}
}
