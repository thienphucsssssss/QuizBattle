package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.*;
import vn.edu.hutech.nqb.client.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.shared.LanRangeProtocol.*;

/** Shared real-data workspace, embedded for managers and hosted in a dialog for students. */
final class LanRangeView {
    private final BorderPane page=new BorderPane();
    private Dialog<ButtonType> dialog;
    private final Stage owner;
    private final Supplier<LanRangeClient> client;
    private final BooleanSupplier allowed;
    private final Runnable leavePage;
    private final Path directory;
    private final TextField survey=new TextField(),route=new TextField(),distance=new TextField();
    private final TextArea notes=new TextArea();
    private final ComboBox<Topology> topology=new ComboBox<>(FXCollections.observableArrayList(Topology.values()));
    private final ComboBox<DistanceBasis> basis=new ComboBox<>(FXCollections.observableArrayList(DistanceBasis.values()));
    private final ComboBox<Integer> duration=new ComboBox<>(FXCollections.observableArrayList(30,60));
    private final Label error=text("","error"),distanceError=text("","error"),surveyError=text("","error"),routeError=text("","error");
    private final Label state=text("Sẵn sàng kiểm tra kết nối","range-section-title"),connection=text("","muted"),evidence=text("","range-hint");
    private final Label progressCaption=text("Chưa có mẫu đo","range-hint"),verdict=text("Chưa bắt đầu","range-badge");
    private final Label farthest=text("Chưa có mốc đạt","range-farthest-value"),location=text("","range-hint");
    private final Label marker=text("Client · mốc chưa nhập","range-endpoint"),network=text("Wi-Fi trường / dùng chung","range-hint");
    private final ProgressBar progress=new ProgressBar(0);
    private final TableView<JsonNode> history=new TableView<>();
    private final List<JsonNode> runs=new ArrayList<>();
    private final DirectionPanel clientPanel=new DirectionPanel("Client → Server","lan-range-client-stats");
    private final DirectionPanel serverPanel=new DirectionPanel("Server → Client","lan-range-server-stats");
    private final Button start,stop,summary,detail;
    private final TitledPane setup;
    private final Timeline availability;
    private JsonNode current=object();
    private String surveyId=UUID.randomUUID().toString(),surveyKey="";
    private boolean exporting,disposed;
    private int reloadGeneration;

    LanRangeView(Stage owner,Supplier<LanRangeClient> currentClient,Path journalDirectory,BooleanSupplier canStart,Runnable leavePage){
        this.owner=owner;client=currentClient;directory=journalDirectory;allowed=canStart;this.leavePage=leavePage;
        page.setId("lan-range-page");page.getStyleClass().add("lan-range-page");
        survey.setId("lan-range-survey");route.setId("lan-range-route");distance.setId("lan-range-distance");
        survey.setPromptText("Đặt tên đợt đo");route.setPromptText("Tầng / hành lang đang thử");
        distance.setPromptText("Nhập mốc đã đo");duration.setValue(30);duration.setId("lan-range-duration");
        topology.setValue(Topology.SHARED_WIFI);basis.setValue(DistanceBasis.CORRIDOR_ROUTE);
        topology.setConverter(converter(t->switch(t){case DEDICATED_AP->"Router đặt cạnh Server";case SERVER_HOTSPOT->"Server phát Wi-Fi";case SHARED_WIFI->"Wi-Fi trường / dùng chung";}));
        basis.setConverter(converter(b->b==DistanceBasis.STRAIGHT_LINE?"Khoảng cách đường thẳng":"Khoảng cách dọc hành lang"));
        notes.setWrapText(true);notes.setPrefRowCount(2);notes.setPromptText("Vật cản, điểm phát nếu biết…");
        for(var label:List.of(error,distanceError,surveyError,routeError)){label.setVisible(false);label.setManaged(false);}
        var names=equalRow(column(4,field("Tên đợt đo",survey),surveyError),column(4,field("Tầng / tuyến đo",route),routeError));
        var arrangement=equalRow(field("Bố trí mạng",topology),field("Cách đo khoảng cách",basis));
        setup=new TitledPane("Thiết lập đợt đo · tên, tuyến và bố trí mạng",column(12,names,arrangement,field("Ghi chú (không bắt buộc)",notes)));
        setup.setExpanded(false);setup.setId("lan-range-setup");
        var heading=column(5,text("Đo tầm kết nối LAN","title"),text("Nhập mốc đã đo bằng thước, kiểm tra giao tiếp thực tế hai chiều.","muted"));
        heading.getStyleClass().add("range-heading");page.setTop(heading);
        var inputLine=row(8,distance,text("m","range-unit"));inputLine.getStyleClass().add("range-distance-input");HBox.setHgrow(distance,Priority.ALWAYS);
        var input=column(7,text("Khoảng cách đo thực tế","caption"),inputLine,distanceError,text("Nhập mốc bạn đã đo từ vị trí Server.","range-hint"),row(12,text("Thời lượng","caption"),duration));
        var pathLine=new Region();pathLine.getStyleClass().add("range-route-line");HBox.setHgrow(pathLine,Priority.ALWAYS);
        var endpoints=row(10,text("Server · gốc 0 m","range-endpoint"),pathLine,marker);
        var schematic=column(10,text("Vị trí gốc (Server cố định)","caption"),endpoints,network,text("Mốc nhập từ thước đo · sơ đồ không theo tỷ lệ.","range-hint"));
        schematic.getStyleClass().add("range-schematic");
        var parameterTitle=row(12,text("Thông số lượt đo","range-section-title"),space(),text("Mốc thực tế · 0–500 m","range-badge"));
        var parameterCard=card(column(14,parameterTitle,equalRow(input,schematic)));
        var reminder=text("Đứng đúng mốc và giữ nguyên vị trí trong suốt lượt đo. Không cần biết vị trí AP khi dùng Wi-Fi trường.","range-hint");
        reminder.getStyleClass().add("range-reminder");
        state.setId("lan-range-state");connection.setId("lan-range-connection");evidence.setId("lan-range-evidence");
        progress.setId("lan-range-progress");progress.setMaxWidth(Double.MAX_VALUE);progressCaption.setId("lan-range-progress-caption");
        var progressLine=new FlowPane(8,6,state,verdict,progressCaption);progressLine.setAlignment(Pos.CENTER_LEFT);
        var measurement=card(column(10,progressLine,progress,equalRow(clientPanel,serverPanel),
            text("RTT là thời gian khứ hồi. Đạt khi lượt kết thúc bình thường và mỗi chiều có ≥95% phản hồi trong 1.000 ms.","range-hint"),evidence));
        farthest.setId("lan-range-farthest");
        var farthestRow=row(16,column(2,text("Mốc xa nhất đã đạt trong đợt này","caption"),farthest),space(),text("Đạt cả hai chiều · loại lượt kiểm thử cục bộ","range-hint"));
        farthestRow.getStyleClass().add("range-farthest");
        history.setId("lan-range-history");history.setMinHeight(150);history.setPrefHeight(175);history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        history.setPlaceholder(text("Chưa có lượt đo. Nhập mốc thực tế và bắt đầu kiểm tra.","muted"));
        addColumn("Mốc đo",r->r.path("distance_m").asText()+" m",80);
        addColumn("Client → Server",r->count(r.path("client_summary")),125);
        addColumn("Server → Client",r->count(r.path("server_summary")),125);
        addColumn("Trạng thái",LanRangeView::resultLabel,190);
        addColumn("Nguồn / ghi chú",r->r.path("loopback").asBoolean()?"Kiểm thử cục bộ":topologyLabel(r.path("topology").asText()),165);
        history.getSelectionModel().selectedItemProperty().addListener((o,before,after)->{if(after!=null&&!liveActive())display(after);});
        var record=card(column(12,farthestRow,history,text("Lịch sử trên máy này · chọn lượt để xem lại kết quả.","range-hint")));
        var body=column(14,setup,parameterCard,reminder,error,measurement,record,connection,location);body.setPadding(new Insets(0,4,12,0));
        var scroller=scroll(body);scroller.setId("lan-range-scroll");page.setCenter(scroller);
        start=button("Bắt đầu đo","primary",this::start);start.setId("lan-range-start");
        stop=button("Dừng đo","quiet",()->{var c=client.get();if(c!=null)c.stop(EndReason.STOPPED);});stop.setId("lan-range-stop");
        summary=button("Xuất tổng hợp CSV","quiet",()->export(false));summary.setId("lan-range-export-summary");
        detail=button("Xuất chi tiết RTT","quiet",()->export(true));detail.setId("lan-range-export-detail");
        var close=button("Đóng","quiet",()->{if(dialog!=null&&dialog.isShowing())dialog.close();else leavePage.run();});close.setId("lan-range-close");
        var actions=row(8,close,stop,start);
        var footer=new BorderPane(new FlowPane(8,6,summary,detail));footer.setRight(actions);footer.getStyleClass().add("range-footer");
        BorderPane.setMargin(actions,new Insets(0,0,0,12));page.setBottom(footer);
        distance.textProperty().addListener((o,before,after)->updateMarker());topology.valueProperty().addListener((o,before,after)->updateMarker());
        availability=new Timeline(new KeyFrame(Duration.millis(300),e->refreshButtons()));availability.setCycleCount(Animation.INDEFINITE);
        page.sceneProperty().addListener((o,before,after)->{if(after==null)availability.stop();else if(!disposed)availability.play();});
        refreshButtons();updateMarker();
    }
    Parent content(){return page;}
    void activate(){reload();var c=client.get();if(c!=null&&c.snapshot().has("run_id"))update(c.snapshot());location.setText("Nhật ký tự lưu: "+directory.toAbsolutePath());refreshButtons();}
    void show(){
        if(dialog==null){
            dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Đo tầm kết nối LAN");dialog.setResizable(true);styleDialog(dialog);
            dialog.getDialogPane().setId("lan-range-dialog");dialog.getDialogPane().getStyleClass().add("lan-range-dialog");
            dialog.getDialogPane().setContent(page);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            var nativeClose=dialog.getDialogPane().lookupButton(ButtonType.CLOSE);nativeClose.setVisible(false);nativeClose.setManaged(false);
            var bar=dialog.getDialogPane().lookup(".button-bar");if(bar!=null){bar.setVisible(false);bar.setManaged(false);}
            dialog.setOnHidden(e->{var c=client.get();if(c!=null&&liveActive())c.stop(EndReason.STOPPED);});
        }
        if(dialog.isShowing()){dialog.getDialogPane().getScene().getWindow().requestFocus();return;}
        dialog.setWidth(Math.min(1080,Math.max(850,owner.getWidth()-50)));dialog.setHeight(Math.min(710,Math.max(540,owner.getHeight()-50)));
        dialog.show();activate();
    }
    void close(){disposed=true;reloadGeneration++;availability.stop();if(dialog!=null)dialog.close();}
    private static VBox card(VBox content){content.getStyleClass().add("range-card");return content;}
    private static HBox equalRow(Node... nodes){
        var box=row(16,nodes);box.setFillHeight(true);
        for(var node:nodes){HBox.setHgrow(node,Priority.ALWAYS);if(node instanceof Region region){region.setMinWidth(0);region.setPrefWidth(1);region.setMaxWidth(Double.MAX_VALUE);}}
        return box;
    }
    private static <T> StringConverter<T> converter(Function<T,String> label){return new StringConverter<>(){public String toString(T value){return value==null?"":label.apply(value);}public T fromString(String value){throw new UnsupportedOperationException();}};}
    private void updateMarker(){
        String value;try{value=parseDistance(distance.getText()).toPlainString().replace('.',',');}catch(IllegalArgumentException invalid){value="—";}
        marker.setText("Client · "+value+" m");network.setText(topology.getConverter().toString(topology.getValue()));
        start.setText(value.equals("—")?"Bắt đầu đo":"Bắt đầu tại "+value+" m");start.setTooltip(new Tooltip("Chỉ bắt đầu sau khi đứng đúng mốc đã đo bằng thước."));
    }
    static BigDecimal parseDistance(String input){
        if(input==null||!input.strip().matches("\\d{1,3}([.,]\\d)?"))throw new IllegalArgumentException("Nhập số từ 0 đến 500 m, tối đa một số lẻ.");
        var value=new BigDecimal(input.strip().replace(',','.'));if(value.compareTo(BigDecimal.valueOf(500))>0)throw new IllegalArgumentException("Khoảng cách tối đa 500 m.");return value;
    }
    private void addColumn(String title,Function<JsonNode,String> value,double width){
        var column=new TableColumn<JsonNode,String>(title);column.setPrefWidth(width);column.setMinWidth(65);column.setSortable(false);
        column.setCellValueFactory(row->new ReadOnlyStringWrapper(value.apply(row.getValue())));
        column.setCellFactory(ignored->new TableCell<>(){@Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);setText(empty?null:item);setTooltip(empty||item==null?null:new Tooltip(item));}});
        history.getColumns().add(column);
    }
    private void start(){
        var c=client.get();if(c==null||!allowed.getAsBoolean()){showError(error,"Đăng nhập hoặc tham gia phòng; chỉ bắt đầu đo ngoài lúc thi đấu.");return;}
        clearError(error);clearError(distanceError);clearError(surveyError);clearError(routeError);
        BigDecimal metres;try{metres=parseDistance(distance.getText());}catch(IllegalArgumentException invalid){showError(distanceError,invalid.getMessage());distance.requestFocus();return;}
        if(survey.getText().isBlank()){setup.setExpanded(true);showError(surveyError,"Nhập tên đợt đo.");survey.requestFocus();return;}
        if(route.getText().isBlank()){setup.setExpanded(true);showError(routeError,"Nhập tầng hoặc tuyến đo.");route.requestFocus();return;}
        try{
            String key=survey.getText().strip()+"\u0000"+route.getText().strip()+"\u0000"+topology.getValue()+"\u0000"+basis.getValue()+"\u0000"+notes.getText().strip();
            if(!key.equals(surveyKey)){surveyId=UUID.randomUUID().toString();surveyKey=key;}
            var options=new Options(metres,duration.getValue(),surveyId,survey.getText(),route.getText(),topology.getValue(),basis.getValue(),notes.getText());
            start.setDisable(true);setup.setExpanded(false);
            c.start(options).whenComplete((v,failure)->Platform.runLater(()->{if(disposed)return;if(failure!=null&&!(root(failure) instanceof CancellationException))showError(error,ArcadeApplication.message(root(failure)));refreshButtons();}));
        }catch(IllegalArgumentException invalid){setup.setExpanded(true);showError(error,invalid.getMessage());}
    }
    void update(JsonNode snapshot){
        if(disposed)return;current=snapshot.deepCopy();String id=current.path("run_id").asText();
        if(!id.isEmpty()){
            int at=-1;for(int i=0;i<runs.size();i++)if(id.equals(runs.get(i).path("run_id").asText())){at=i;break;}
            String selected=history.getSelectionModel().getSelectedItem()==null?"":history.getSelectionModel().getSelectedItem().path("run_id").asText();
            if(at<0){runs.addFirst(current);history.getItems().addFirst(current);}else{runs.set(at,current);history.getItems().set(at,current);}
            if(selected.isEmpty()||selected.equals(id)||current.path("active").asBoolean())history.getSelectionModel().select(current);
        }
        if(current.path("active").asBoolean()||history.getSelectionModel().getSelectedItem()==null||history.getSelectionModel().getSelectedItem().path("run_id").asText().equals(id))display(current);
        refreshButtons();
    }
    private void display(JsonNode row){
        clearError(error);var far=LanRangeReport.farthestPassing(runs,row.path("survey_id").asText(surveyId));
        farthest.setText(far.map(m->m.stripTrailingZeros().toPlainString().replace('.',',')+" m").orElse("Chưa có mốc đạt"));
        boolean active=row.path("active").asBoolean();
        state.setText(active?switch(row.path("phase").asText()){case "MEASURING"->"Đang kiểm tra tại mốc "+row.path("distance_m").asText()+" m";case "SAVING"->"Đang lưu kết quả…";default->"Đang chuẩn bị kết nối…";}:row.has("distance_m")?"Kết quả tại mốc "+row.path("distance_m").asText()+" m":"Sẵn sàng kiểm tra kết nối");
        verdict.setText(resultLabel(row)+(row.path("saved").asBoolean()?" · Đã lưu":""));
        verdict.getStyleClass().removeAll("range-pass","range-warning");if(row.path("passed").asBoolean())verdict.getStyleClass().add("range-pass");else if(!active&&row.hasNonNull("end_reason"))verdict.getStyleClass().add("range-warning");
        connection.setText(row.has("client_ip_observed")?"Client "+row.path("client_ip_observed").asText()+"  ↔  Server "+row.path("server_ip").asText()+":"+row.path("server_port").asText():"IP thực tế được ghi khi Server chấp nhận lượt đo.");
        evidence.setText(row.path("loopback").asBoolean()?"Kiểm thử cục bộ · không tính vào mốc xa nhất thực địa.":active?"Giữ nguyên vị trí trong lúc đo.":"Số mét do người vận hành đo và nhập; kết quả áp dụng cho tuyến và mạng Wi-Fi đã thử.");
        clientPanel.update(row.path("client_summary"),row,false);serverPanel.update(row.path("server_summary"),row,true);
        int planned=row.path("duration_seconds").asInt(30)*2,confirmed=row.path("client_summary").path("confirmed").asInt()+row.path("server_summary").path("confirmed").asInt();
        progress.setProgress(Math.min(1,confirmed/(double)planned));progressCaption.setText(confirmed+" / "+planned+" mẫu đã có kết quả");
        if(!row.path("error").asText().isBlank())showError(error,row.path("error").asText());
    }
    private static String number(JsonNode p,String key){var n=p.path("rtt_"+key+"_ms");return n.isNumber()?String.format(Locale.ROOT,"%.1f",n.asDouble()):"—";}
    private static String count(JsonNode p){return p.has("planned")&&p.path("confirmed").asInt()>0?p.path("on_time").asInt()+" / "+p.path("planned").asInt():"Chưa xác nhận";}
    private static String topologyLabel(String value){return switch(value){case "DEDICATED_AP"->"Router cạnh Server";case "SERVER_HOTSPOT"->"Server phát Wi-Fi";case "SHARED_WIFI"->"Wi-Fi dùng chung";default->"Đo qua LAN";};}
    private static String resultLabel(JsonNode r){
        if(r.path("active").asBoolean())return r.path("phase").asText().equals("SAVING")?"Đang lưu":"Đang đo";
        if(r.path("passed").asBoolean())return r.path("loopback").asBoolean()?"Đạt kiểm thử cục bộ":"Đạt tiêu chí";
        return switch(r.path("end_reason").asText()){
            case "STOPPED"->"Đã dừng · kết quả một phần";case "DISCONNECTED"->"Mất kết nối";case "GAME_STARTED"->"Dừng vì ván thi bắt đầu";
            case "ROOM_CHANGED"->"Dừng vì phòng thay đổi";case "STORAGE_ERROR"->"Lỗi lưu nhật ký";case "APP_CLOSED"->"Đóng giữa lượt";
            case "CONTROL_TIMEOUT"->"Thiếu xác nhận kết thúc";case "COMPLETED"->"Chưa đạt tiêu chí";default->"Chưa bắt đầu";
        };
    }
    private boolean liveActive(){var c=client.get();return c!=null&&c.snapshot().path("active").asBoolean();}
    private void refreshButtons(){
        var c=client.get();boolean active=liveActive();
        start.setDisable(active||c==null||!allowed.getAsBoolean());stop.setDisable(!active);distance.setDisable(active);duration.setDisable(active);setup.setDisable(active);history.setDisable(active);
        summary.setDisable(exporting||runs.isEmpty());detail.setDisable(exporting||runs.isEmpty());
    }
    void connectionChanged(){refreshButtons();}
    private void reload(){
        int generation=++reloadGeneration;
        CompletableFuture.supplyAsync(()->{try{return LanRangeReport.load(directory);}catch(Exception failure){throw new CompletionException(failure);}})
            .whenComplete((loaded,failure)->Platform.runLater(()->{
                if(disposed||generation!=reloadGeneration)return;
                if(failure!=null){showError(error,"Không đọc được lịch sử đo: "+root(failure).getMessage());return;}
                var c=client.get();var live=c==null?null:c.snapshot();runs.clear();runs.addAll(loaded);history.setItems(FXCollections.observableArrayList(runs));
                if(live!=null&&live.has("run_id"))update(live);else if(!runs.isEmpty()){history.getSelectionModel().selectFirst();display(runs.getFirst());}refreshButtons();
            }));
    }
    private void export(boolean samples){
        var picker=new FileChooser();picker.setTitle(samples?"Xuất mẫu đo chi tiết":"Xuất tổng hợp lượt đo");picker.setInitialFileName(samples?"LAN_mau_do.csv":"LAN_tong_hop.csv");
        picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)","*.csv"));var selected=picker.showSaveDialog(page.getScene().getWindow());if(selected==null)return;
        var copy=runs.stream().map(JsonNode::deepCopy).map(n->(JsonNode)n).toList();exporting=true;refreshButtons();
        CompletableFuture.runAsync(()->{try{if(samples)LanRangeReport.exportSamples(selected.toPath(),copy);else LanRangeReport.exportSummary(selected.toPath(),copy);}catch(Exception failure){throw new CompletionException(failure);}})
            .whenComplete((v,failure)->Platform.runLater(()->{if(disposed)return;exporting=false;refreshButtons();if(failure!=null)showError(error,"Không xuất được CSV: "+root(failure).getMessage());else location.setText("Đã xuất: "+selected.toPath().toAbsolutePath());}));
    }
    private static final class DirectionPanel extends VBox {
        private final Label count=text("—","range-count"),late=text("—","range-count"),rate=text("Chưa có mẫu","range-badge");
        private final Label confirmed=text("Chưa có kết quả","range-hint"),issues=text("","range-hint"),rtt=text("Trung vị: — ms    p95: — ms","range-hint");
        DirectionPanel(String title,String id){
            super(7);getStyleClass().add("range-direction");confirmed.setId(id);
            var counts=equalRow(column(3,text("Phản hồi đúng hạn","range-hint"),count,text("≥95% trong 1.000 ms","range-hint")),column(3,text("Quá hạn / lỗi","range-hint"),late,issues));
            getChildren().addAll(row(8,text(title,"range-direction-title"),space(),rate),counts,confirmed,new Separator(),rtt);setMinWidth(0);
        }
        void update(JsonNode p,JsonNode run,boolean server){
            if(!p.has("planned")){count.setText("—");late.setText("—");rate.setText("Chưa có mẫu");confirmed.setText("Chưa có kết quả");issues.setText("");rtt.setText("Trung vị: — ms    p95: — ms");return;}
            int errors=p.path("send_failed").asInt()+p.path("disconnected").asInt()+p.path("cancelled").asInt();
            int known=p.path("confirmed").asInt();boolean missing=server&&!run.path("active").asBoolean()&&!run.path("server_summary_received").asBoolean();
            count.setText((missing&&known==0?"—":p.path("on_time").asText("0"))+" / "+p.path("planned").asInt());
            late.setText(missing&&known==0?"—":Integer.toString(p.path("timeout").asInt()+errors));
            rate.setText(missing&&known==0?"Chưa xác nhận":String.format(Locale.ROOT,"%.1f%%",p.path("on_time_rate").asDouble()*100));
            confirmed.setText("Đã có kết quả: "+known+" / "+p.path("planned").asInt()+(missing?" · Chưa nhận đủ kết quả server.":""));
            issues.setText(errors+" lỗi · "+p.path("unconfirmed").asInt()+" chưa xác nhận");
            rtt.setText("Trung vị: "+number(p,"median")+" ms    p95: "+number(p,"p95")+" ms");
            rtt.setTooltip(new Tooltip("RTT min / trung vị / p95 / max (ms): "+number(p,"min")+" / "+number(p,"median")+" / "+number(p,"p95")+" / "+number(p,"max")));
        }
    }
    private static Throwable root(Throwable e){while(e instanceof CompletionException&&e.getCause()!=null)e=e.getCause();return e;}
    private static void showError(Label label,String message){label.setText(message);label.setManaged(true);label.setVisible(true);}
    private static void clearError(Label label){label.setText("");label.setManaged(false);label.setVisible(false);}
}
