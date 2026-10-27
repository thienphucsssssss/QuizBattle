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
    private final ComboBox<JsonNode> target=new ComboBox<>();
    private final VBox targetField;
    private final Label targetHint=text("Chọn client cần đo trong danh sách.","range-hint");
    private final Label guideAction=text("Giảng viên bấm bắt đầu; Client đứng yên đến hết lượt.","range-hint");
    private final Label error=text("","error"),distanceError=text("","error"),surveyError=text("","error"),routeError=text("","error");
    private final Label state=text("Sẵn sàng kiểm tra kết nối","range-section-title"),evidence=text("","range-hint");
    private final Label progressCaption=text("Chưa có mẫu đo","range-hint"),verdict=text("Chưa bắt đầu","range-badge");
    private final Label farthest=text("Chưa có mốc đạt","range-farthest-value"),location=text("","range-hint");
    private final ProgressBar progress=new ProgressBar(0);
    private final TableView<JsonNode> history=new TableView<>();
    private final List<JsonNode> runs=new ArrayList<>();
    private final DirectionPanel clientPanel=new DirectionPanel("Client","Client gửi · Client → Server","lan-range-client-stats");
    private final DirectionPanel serverPanel=new DirectionPanel("Server","Server gửi · Server → Client","lan-range-server-stats");
    private final Label serverAddress=text("IP Server: đang chờ xác nhận","range-peer-address");
    private final Label clientConnections=text("Chưa có client kết nối vào phòng.","range-hint");
    private final Label connectionBadge=text("Chờ kết nối","range-badge");
    private final Label runServerAddress=text("","range-peer-address"),runClientAddress=text("","range-peer-address");
    private final Label runScope=text("","range-hint");
    private final Label resultContext=text("","range-badge");
    private final Label emptyResults=text("Chưa có lượt đo. Đứng đúng mốc, nhập số mét và bấm Bắt đầu đo.","range-hint");
    private final VBox measurementDetails;
    private final ScrollPane peerScroll;
    private final TitledPane peerList,historySection;
    private final Button start,stop,summary,detail,closeHistory;
    private final TitledPane setup;
    private final Timeline availability;
    private JsonNode current=object();
    private JsonNode connectionState=object(),displayedRun=object();
    private boolean connectionLive;
    private String surveyId=UUID.randomUUID().toString(),surveyKey="";
    private boolean exporting,disposed,viewingHistory,syncingHistory;
    private int reloadGeneration;

    LanRangeView(Stage owner,Supplier<LanRangeClient> currentClient,Path journalDirectory,BooleanSupplier canStart,Runnable leavePage){
        this.owner=owner;client=currentClient;directory=journalDirectory;allowed=canStart;this.leavePage=leavePage;
        page.setId("lan-range-page");page.getStyleClass().add("lan-range-page");
        serverPanel.setId("lan-range-server-panel");clientPanel.setId("lan-range-client-panel");
        serverAddress.setId("lan-range-server-ip");clientConnections.setId("lan-range-client-connections");
        runServerAddress.setId("lan-range-run-server-ip");runClientAddress.setId("lan-range-run-client-ip");runScope.setId("lan-range-run-scope");
        serverPanel.getChildren().add(2,runServerAddress);clientPanel.getChildren().add(2,runClientAddress);
        survey.setId("lan-range-survey");route.setId("lan-range-route");distance.setId("lan-range-distance");
        survey.setPromptText("Đặt tên đợt đo");route.setPromptText("Tầng / hành lang đang thử");
        distance.setPromptText("Ví dụ: 30,0");distance.setAccessibleText("Mốc đã đo bằng thước, đơn vị mét");
        duration.setValue(30);duration.setId("lan-range-duration");duration.setConverter(converter(s->s+" giây"));
        topology.setValue(Topology.SHARED_WIFI);basis.setValue(DistanceBasis.CORRIDOR_ROUTE);
        topology.setConverter(converter(t->switch(t){case DEDICATED_AP->"Router đặt cạnh Server";case SERVER_HOTSPOT->"Server phát Wi-Fi";case SHARED_WIFI->"Wi-Fi trường / dùng chung";}));
        basis.setConverter(converter(b->b==DistanceBasis.STRAIGHT_LINE?"Khoảng cách đường thẳng":"Khoảng cách dọc hành lang"));
        notes.setWrapText(true);notes.setPrefRowCount(2);notes.setPromptText("Vật cản, điểm phát nếu biết…");
        for(var label:List.of(error,distanceError,surveyError,routeError)){label.setVisible(false);label.setManaged(false);}
        var names=equalRow(column(4,field("Tên đợt đo",survey),surveyError),column(4,field("Tầng / tuyến đo",route),routeError));
        var arrangement=equalRow(field("Bố trí mạng",topology),field("Cách đo khoảng cách",basis));
        var extra=new TitledPane("Bố trí Wi-Fi và ghi chú (không bắt buộc)",column(12,arrangement,field("Ghi chú",notes)));
        extra.setExpanded(false);extra.setId("lan-range-extra");
        var heading=column(5,text("Đo tầm kết nối LAN","title"),text("Đi đến mốc đã đo bằng thước để kiểm tra kết nối với Server.","muted"));
        heading.getStyleClass().add("range-heading");page.setTop(heading);
        connectionBadge.setId("lan-range-live-status");
        var serverLive=column(5,text("Server · máy cố định","range-direction-title"),serverAddress,text("Giữ Server ở vị trí gốc trong suốt đợt đo.","range-hint"));
        peerScroll=scroll(clientConnections);peerScroll.setPrefViewportHeight(44);peerScroll.setMaxHeight(106);
        target.setId("lan-range-target");target.setPromptText("Chọn client cần đo");
        target.setConverter(converter(value->value.path("nickname").asText("Client")+" · "+value.path("ip_address").asText()));
        targetField=field("Client cần đo",target);
        peerList=new TitledPane("Client trong phòng",column(8,peerScroll,targetField,targetHint));peerList.setExpanded(true);peerList.setId("lan-range-peers");
        var liveConnections=card(column(12,row(10,text("1. Kiểm tra kết nối","range-section-title"),space(),connectionBadge),equalRow(serverLive,peerList)));
        var inputLine=row(8,distance,text("m","range-unit"));inputLine.getStyleClass().add("range-distance-input");HBox.setHgrow(distance,Priority.ALWAYS);
        var distanceLabel=text("Mốc đã đo bằng thước","caption");distanceLabel.setLabelFor(distance);
        var input=column(6,distanceLabel,inputLine,distanceError,text("Tính từ vị trí Server · 0–500 m","range-hint"));
        var markerFields=equalRow(input,field("Thời gian kiểm tra",duration));
        var guide=column(7,text("Cách thử tại mỗi mốc","range-section-title"),
            text("Đo bằng thước từ Server đến chỗ đặt Client.","range-hint"),
            text("Đặt Client tại mốc rồi nhập số mét ở bên trái.","range-hint"),guideAction);
        guide.getStyleClass().add("range-guide");
        setup=new TitledPane("2. Chuẩn bị lượt đo",column(14,names,equalRow(markerFields,guide),extra));
        setup.setExpanded(true);setup.setId("lan-range-setup");setup.getStyleClass().add("range-preparation");
        state.setId("lan-range-state");evidence.setId("lan-range-evidence");
        progress.setId("lan-range-progress");progress.setMaxWidth(Double.MAX_VALUE);progressCaption.setId("lan-range-progress-caption");
        var progressLine=new FlowPane(8,6,state,verdict,progressCaption);progressLine.setAlignment(Pos.CENTER_LEFT);
        resultContext.setId("lan-range-result-context");
        measurementDetails=column(10,resultContext,runScope,progressLine,progress,equalRow(serverPanel,clientPanel),
            text("Đạt khi hoàn tất lượt đo và cả hai chiều có ít nhất 95% phản hồi trong 1 giây.","range-hint"),evidence);
        measurementDetails.setManaged(false);measurementDetails.setVisible(false);verdict.setId("lan-range-verdict");
        closeHistory=button("Đóng kết quả lịch sử","quiet",this::closeHistoryResult);closeHistory.setId("lan-range-close-history");closeHistory.setVisible(false);closeHistory.setManaged(false);
        var measurement=card(column(12,row(12,text("3. Kết quả lượt đo","range-section-title"),space(),closeHistory),emptyResults,measurementDetails));
        farthest.setId("lan-range-farthest");
        var farthestRow=row(16,column(2,text("Mốc xa nhất đạt trong đợt đang xem","caption"),farthest),space(),text("Chỉ tính lượt thực địa đạt cả hai chiều.","range-hint"));
        farthestRow.getStyleClass().add("range-farthest");
        history.setId("lan-range-history");history.setMinHeight(150);history.setPrefHeight(175);history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        history.setPlaceholder(text("Chưa có lượt đo. Nhập mốc thực tế và bắt đầu kiểm tra.","muted"));
        addColumn("Mốc đo",r->r.path("distance_m").asText()+" m",80);
        addColumn("Client",r->r.path("nickname").asText(r.path("client_ip_observed").asText("Chưa ghi tên")),155);
        addColumn("Client → Server",r->count(r.path("client_summary")),125);
        addColumn("Server → Client",r->count(r.path("server_summary")),125);
        addColumn("Trạng thái",LanRangeView::resultLabel,190);
        addColumn("Nguồn / ghi chú",r->r.path("loopback").asBoolean()?"Kiểm thử cục bộ":topologyLabel(r.path("topology").asText()),165);
        history.getSelectionModel().selectedItemProperty().addListener((o,before,after)->{if(after!=null&&!syncingHistory&&!liveActive()){viewingHistory=true;setup.setExpanded(false);display(after);}});
        historySection=new TitledPane("Lịch sử trên máy này",column(10,history,text("Chọn lượt để xem tên/IP client và kết quả ở phần 3. Mỗi lượt giữ đúng client đã đo.","range-hint"),location));
        historySection.setId("lan-range-history-section");historySection.setExpanded(false);
        var record=card(column(10,farthestRow,historySection));
        var body=column(14,liveConnections,setup,error,measurement,record);body.setPadding(new Insets(0,4,12,0));
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
        target.valueProperty().addListener((o,before,after)->{refreshButtons();});
        availability=new Timeline(new KeyFrame(Duration.millis(300),e->refreshButtons()));availability.setCycleCount(Animation.INDEFINITE);
        page.sceneProperty().addListener((o,before,after)->{if(after==null)availability.stop();else if(!disposed)availability.play();});
        refreshButtons();updateMarker();
    }
    Parent content(){return page;}
    void activate(){reload();var c=client.get();if(c!=null&&resumesCurrent(c.snapshot()))update(c.snapshot());location.setText("Nhật ký tự lưu: "+directory.toAbsolutePath());refreshButtons();}
    private boolean resumesCurrent(JsonNode snapshot){return snapshot.path("active").asBoolean()||(snapshot.has("run_id")&&snapshot.path("run_id").asText().equals(current.path("run_id").asText()));}
    private void closeHistoryResult(){
        if(liveActive())return;
        viewingHistory=false;history.getSelectionModel().clearSelection();current=object();display(current);setup.setExpanded(true);refreshButtons();
    }
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
        boolean controlled=managerMode();var selected=target.getValue();
        if(controlled&&(selected==null||!connectionState.path("is_owner").asBoolean())){showError(error,"Chủ phòng cần chọn một client đang kết nối để đo.");return;}
        clearError(error);clearError(distanceError);clearError(surveyError);clearError(routeError);
        BigDecimal metres;try{metres=parseDistance(distance.getText());}catch(IllegalArgumentException invalid){showError(distanceError,invalid.getMessage());distance.requestFocus();return;}
        if(survey.getText().isBlank()){setup.setExpanded(true);showError(surveyError,"Nhập tên đợt đo.");survey.requestFocus();return;}
        if(route.getText().isBlank()){setup.setExpanded(true);showError(routeError,"Nhập tầng hoặc tuyến đo.");route.requestFocus();return;}
        try{
            String key=survey.getText().strip()+"\u0000"+route.getText().strip()+"\u0000"+topology.getValue()+"\u0000"+basis.getValue()+"\u0000"+notes.getText().strip()+"\u0000"+(controlled?selected.path("player_id").asText():connectionState.path("connection_info").path("player_id").asText());
            if(!key.equals(surveyKey)){surveyId=UUID.randomUUID().toString();surveyKey=key;}
            var options=new Options(metres,duration.getValue(),surveyId,survey.getText(),route.getText(),topology.getValue(),basis.getValue(),notes.getText());
            start.setDisable(true);setup.setExpanded(false);
            (controlled?c.startFor(selected.path("player_id").asText(),options):c.start(options)).whenComplete((v,failure)->Platform.runLater(()->{if(disposed)return;if(failure!=null&&!(root(failure) instanceof CancellationException))showError(error,ArcadeApplication.message(root(failure)));refreshButtons();}));
        }catch(IllegalArgumentException invalid){setup.setExpanded(true);showError(error,invalid.getMessage());}
    }
    void update(JsonNode snapshot){
        if(disposed)return;current=snapshot.deepCopy();String id=current.path("run_id").asText();
        syncingHistory=true;
        try{
        if(!id.isEmpty()){
            int at=-1;for(int i=0;i<runs.size();i++)if(id.equals(runs.get(i).path("run_id").asText())){at=i;break;}
            String selected=history.getSelectionModel().getSelectedItem()==null?"":history.getSelectionModel().getSelectedItem().path("run_id").asText();
            if(at<0){runs.addFirst(current);history.getItems().addFirst(current);}else{runs.set(at,current);history.getItems().set(at,current);}
            if(selected.isEmpty()||selected.equals(id)||current.path("active").asBoolean())history.getSelectionModel().select(current);
        }
        }finally{syncingHistory=false;}
        if(current.path("active").asBoolean()||history.getSelectionModel().getSelectedItem()==null||history.getSelectionModel().getSelectedItem().path("run_id").asText().equals(id)){
            if(current.path("active").asBoolean()||!displayedRun.path("run_id").asText().equals(id))viewingHistory=false;
            display(current);
        }
        refreshButtons();
    }
    private void display(JsonNode row){
        displayedRun=row.deepCopy();renderConnections();
        boolean hasRun=row.has("run_id");
        measurementDetails.setVisible(hasRun);measurementDetails.setManaged(hasRun);emptyResults.setVisible(!hasRun);emptyResults.setManaged(!hasRun);
        emptyResults.setText(row.path("active").asBoolean()?"Đang chuẩn bị lượt đo…":"Chưa có lượt đo hiện tại. Chọn client để bắt đầu, hoặc mở Lịch sử để xem lượt cũ.");
        closeHistory.setVisible(hasRun&&viewingHistory);closeHistory.setManaged(hasRun&&viewingHistory);
        resultContext.setText(!hasRun?"":viewingHistory?"Lịch sử đã lưu · không phải trạng thái kết nối hiện tại":row.path("active").asBoolean()?"Lượt đo hiện tại":"Lượt đo đã kết thúc · IP ghi nhận lúc đo");
        historySection.setText("Lịch sử trên máy này ("+runs.size()+" lượt)");
        String distanceText=row.path("distance_m").asText().replace('.',',');
        runScope.setText(!hasRun?"":"Client: "+row.path("nickname").asText(row.path("client_ip_observed").asText("Chưa xác nhận"))+" · Đợt: "+row.path("survey_name").asText("Chưa có tên")+" · Tuyến: "+row.path("route").asText("Chưa ghi")
            +"\n"+(row.path("active").asBoolean()?"Đang đo kết nối Server ↔ Client":"Kết quả đã ghi nhận")+" · Mốc "+distanceText+" m · "+row.path("duration_seconds").asInt(30)+" giây");
        String ipLabel=viewingHistory?"IP đã lưu: ":row.path("active").asBoolean()?"IP lượt đo: ":"IP ghi nhận lúc đo: ";
        runServerAddress.setText(!hasRun?"":ipLabel+(row.path("server_ip").asText().isBlank()?"Chưa xác nhận":endpoint(row.path("server_ip").asText(),row.path("server_port").asInt())));
        runClientAddress.setText(!hasRun?"":ipLabel+(row.path("client_ip_observed").asText().isBlank()?"Chưa xác nhận":row.path("client_ip_observed").asText()));
        clearError(error);var far=LanRangeReport.farthestPassing(runs,row.path("survey_id").asText(surveyId));
        farthest.setText(far.map(m->m.stripTrailingZeros().toPlainString().replace('.',',')+" m").orElse("Chưa có mốc đạt"));
        boolean active=row.path("active").asBoolean();
        if(active)setup.setExpanded(false);
        setup.setText(active?"2. Đang kiểm tra mốc "+distanceText+" m":"2. Chuẩn bị "+(hasRun?"lượt tiếp theo":"lượt đo"));
        state.setText(active?switch(row.path("phase").asText()){case "MEASURING"->"Đang kiểm tra tại mốc "+row.path("distance_m").asText()+" m";case "SAVING"->"Đang lưu kết quả…";default->"Đang chuẩn bị kết nối…";}:row.has("distance_m")?"Kết quả tại mốc "+row.path("distance_m").asText()+" m":"Sẵn sàng kiểm tra kết nối");
        verdict.setText(resultLabel(row)+(row.path("saved").asBoolean()?" · Đã lưu":""));
        verdict.getStyleClass().removeAll("range-pass","range-warning");if(row.path("passed").asBoolean())verdict.getStyleClass().add("range-pass");else if(!active&&row.hasNonNull("end_reason"))verdict.getStyleClass().add("range-warning");
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
    private boolean managerMode(){return connectionState.path("connection_info").path("player_id").asText().isBlank();}
    private void refreshButtons(){
        var c=client.get();boolean active=liveActive();
        boolean manager=managerMode();
        start.setDisable(active||c==null||!allowed.getAsBoolean()||(manager&&(target.getValue()==null||!connectionState.path("is_owner").asBoolean())));stop.setDisable(!active);distance.setDisable(active);duration.setDisable(active);setup.setDisable(active);history.setDisable(active);
        target.setDisable(active||!connectionLive||!connectionState.path("is_owner").asBoolean());
        summary.setDisable(exporting||runs.isEmpty());detail.setDisable(exporting||runs.isEmpty());
    }
    void connectionChanged(){refreshButtons();}
    void updateConnections(JsonNode room,boolean connected){
        if(disposed)return;
        connectionState=room.deepCopy();connectionLive=connected;renderConnections();refreshButtons();
    }
    private void renderConnections(){
        var info=connectionState.path("connection_info");
        connectionBadge.setText(connectionLive?"Đã kết nối":"Mất kết nối");
        connectionBadge.getStyleClass().removeAll("range-pass","range-warning");connectionBadge.getStyleClass().add(connectionLive?"range-pass":"range-warning");
        var peers=new ArrayList<String>();
        var candidates=new ArrayList<JsonNode>();
        boolean hiddenIp=false;int connectedCount=0;
        if(connectionLive)for(var player:connectionState.path("players")){
            if(!player.path("connected").asBoolean())continue;
            connectedCount++;
            String ip=player.path("ip_address").asText();
            if(ip.isBlank()){hiddenIp=true;continue;}
            candidates.add(player.deepCopy());
            peers.add(player.path("nickname").asText("Client")+" · IP: "+ip+" · Đang kết nối");
        }
        String serverIp=info.path("server_ip").asText(),clientIp=info.path("client_ip_observed").asText();
        serverAddress.setText(!serverIp.isBlank()&&connectionLive
            ?"IP Server: "+endpoint(serverIp,info.path("server_port").asInt())
            :connectionLive?"Server chưa cung cấp địa chỉ IP.":"IP Server: chưa xác nhận · mất kết nối");
        boolean ownStudent=!info.path("player_id").asText().isBlank();
        targetField.setVisible(!ownStudent);targetField.setManaged(!ownStudent);targetHint.setVisible(!ownStudent);targetHint.setManaged(!ownStudent);
        guideAction.setText(ownStudent?"Bấm bắt đầu hoặc chờ giảng viên gửi lệnh đo; đứng yên đến hết lượt.":"Giảng viên chọn Client và bấm bắt đầu; Client đứng yên đến hết lượt.");
        targetHint.setText(!connectionState.path("is_owner").asBoolean()?"Tạo phòng hoặc dùng tài khoản chủ phòng để điều khiển đo.":candidates.isEmpty()?"Chờ client tham gia phòng để bắt đầu đo.":"Đo kết nối Server với đúng client được chọn.");
        if(!liveActive()&&!target.getItems().equals(candidates)){
            String chosen=target.getValue()==null?"":target.getValue().path("player_id").asText();target.getItems().setAll(candidates);
            target.setValue(candidates.stream().filter(v->v.path("player_id").asText().equals(chosen)).findFirst().orElse(null));
        }
        if(ownStudent){
            peers.clear();
            if(connectionLive){
                String name="Client";
                for(var player:connectionState.path("players"))if(player.path("player_id").asText().equals(info.path("player_id").asText())){name=player.path("nickname").asText("Client");break;}
                peers.add(name+" · IP: "+(clientIp.isBlank()?"Chưa xác nhận":clientIp)+" · Đang kết nối");
            }
        }
        peerList.setText(!connectionLive?"Client trong phòng · chưa xác nhận":ownStudent?"Client kết nối tới Server":"Client trong phòng ("+connectedCount+") · tên và IP");
        peerScroll.setPrefViewportHeight(Math.min(96,Math.max(44,peers.size()*42)));
        clientConnections.setText(!connectionLive?"Mất kết nối Server · chưa xác nhận danh sách client."
            :!peers.isEmpty()?String.join("\n",peers)
            :hiddenIp?"Server không cung cấp quyền xem IP các client khác.":"Chưa có client kết nối vào phòng.");
    }
    private static String endpoint(String ip,int port){return (ip.contains(":")?"["+ip+"]":ip)+(port>0?":"+port:"");}
    private void reload(){
        int generation=++reloadGeneration;
        CompletableFuture.supplyAsync(()->{try{return LanRangeReport.load(directory);}catch(Exception failure){throw new CompletionException(failure);}})
            .whenComplete((loaded,failure)->Platform.runLater(()->{
                if(disposed||generation!=reloadGeneration)return;
                if(failure!=null){showError(error,"Không đọc được lịch sử đo: "+root(failure).getMessage());return;}
                String selected=history.getSelectionModel().getSelectedItem()==null?"":history.getSelectionModel().getSelectedItem().path("run_id").asText();
                boolean historyMode=viewingHistory;
                var c=client.get();var live=c==null?null:c.snapshot();
                syncingHistory=true;try{runs.clear();runs.addAll(loaded);history.setItems(FXCollections.observableArrayList(runs));}finally{syncingHistory=false;}
                boolean resumed=live!=null&&resumesCurrent(live),restored=false;
                if(resumed)update(live);
                if(!liveActive()&&!selected.isEmpty())for(var row:runs)if(selected.equals(row.path("run_id").asText())){
                    syncingHistory=true;try{history.getSelectionModel().select(row);}finally{syncingHistory=false;}
                    viewingHistory=historyMode;display(row);restored=true;break;
                }
                if(!resumed&&!restored){viewingHistory=false;display(object());}
                refreshButtons();
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
        DirectionPanel(String title,String direction,String id){
            super(7);getStyleClass().add("range-direction");confirmed.setId(id);
            var counts=equalRow(column(3,text("Phản hồi trong 1 giây","range-hint"),count),column(3,text("Trễ / lỗi","range-hint"),late,issues));
            getChildren().addAll(row(8,text(title,"range-direction-title"),space(),rate),text(direction,"range-hint"),counts,confirmed,new Separator(),rtt);setMinWidth(0);
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
