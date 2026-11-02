package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.edu.hutech.nqb.client.SurveySync;
import vn.edu.hutech.nqb.shared.fingerprint.*;
import vn.edu.hutech.nqb.shared.WifiSurvey;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;
import static vn.edu.hutech.nqb.shared.WifiSurvey.*;

/** Shared map panel, embedded in room monitoring or opened alone for offline/student use. */
final class FloorMapViews implements AutoCloseable {
    private record Choice(String id,String name){@Override public String toString(){return name;}}
    private final ArcadeApplication app;
    private final Stage stage;
    private final boolean embedded;
    private final ScrollPane view;
    private final TitledPane settingsPane;
    private Consumer<String> selectionChanged=id->{};
    private Runnable modeChanged=()->{};
    private String selectedLiveClient="";
    private final CorridorMapView map=new CorridorMapView();
    private final ComboBox<Choice> clients=new ComboBox<>();
    private final ComboBox<String> serverRoom=new ComboBox<>();
    private final Spinner<Double> roomWidth=new Spinner<>(3.0,20.0,8.0,1.0),roomDepth=new Spinner<>(3.0,20.0,8.0,1.0),hallWidth=new Spinner<>(1.0,8.0,3.0,.5);
    private final TextField areaName=new TextField("Hành lang HUTECH E1"),ssid=new TextField("HUTECH E1");
    private final TextField floorNumber=new TextField();
    private final Slider motion=new Slider(0,100,0);
    private final CheckBox smooth=new CheckBox("Chuyển động mượt");
    private final Label origin=text("","caption"),distance=text("","subtitle"),status=text("","muted"),detail=text("","muted");
    private final Label apDistances=text("","muted");
    private final Label signalDetails=text("","muted");
    private final Label positionStatus=text("","muted");
    private final TitledPane signalPane=new TitledPane();
    private final ToggleButton demo=new ToggleButton("Mô phỏng tại nhà"),live=new ToggleButton("Theo dõi Wi-Fi");
    private final Button play,activate,disable,load;
    private final Timeline playback;
    private final VBox demoControls,liveControls;
    private CorridorPlan plan=CorridorPlan.DEFAULT;
    private final double[] progress={0,55};
    private Bundle survey;
    private JsonNode snapshot=object();
    private volatile boolean closed;
    private boolean busy,selecting;
    private long snapshotAt;
    private final Timeline ageTimer=new Timeline(new KeyFrame(Duration.seconds(1),e->{if(!demo.isSelected())render();}));

    FloorMapViews(ArcadeApplication app){
        this(app,new Stage(),false);
    }
    FloorMapViews(ArcadeApplication app,Stage owner){this(app,owner,true);}
    private FloorMapViews(ArcadeApplication app,Stage owner,boolean embedded){
        this.app=app;this.stage=owner;this.embedded=embedded;
        if(!embedded){stage.initOwner(app.ownerStage());stage.setTitle("Bản đồ Client — Server");stage.setMinWidth(760);stage.setMinHeight(540);
            stage.setWidth(Math.min(1100,app.ownerStage().getWidth()));stage.setHeight(Math.min(760,app.ownerStage().getHeight()));}
        ageTimer.setCycleCount(Animation.INDEFINITE);runWhenShowing(apDistances,ageTimer);
        map.setId("floor-map");origin.setId("floor-origin");distance.setId("floor-distance");status.setId("floor-status");detail.setId("floor-detail");
        distance.setStyle("-fx-font-size: 24px;");
        signalPane.setText("Chi tiết tín hiệu");signalPane.setContent(column(8,detail,signalDetails));signalPane.setExpanded(false);signalPane.setId("floor-signal-details");
        status.managedProperty().bind(status.textProperty().isNotEmpty());status.visibleProperty().bind(status.managedProperty());
        positionStatus.managedProperty().bind(positionStatus.textProperty().isNotEmpty());positionStatus.visibleProperty().bind(positionStatus.managedProperty());
        clients.setId("floor-client");clients.setPrefWidth(210);clients.setAccessibleText("Client đang xem");
        var group=new ToggleGroup();demo.setToggleGroup(group);live.setToggleGroup(group);demo.setId("floor-demo");live.setId("floor-live");
        if(embedded||!app.floorRoomCode().isBlank())live.setSelected(true);else demo.setSelected(true);
        clients.setPromptText("Chọn Client");
        smooth.setSelected(!app.reducedMotion());smooth.setId("floor-smooth");smooth.setOnAction(e->render());
        for(int i=0;i<plan.roomsPerSide()*2;i++)serverRoom.getItems().add(plan.roomName(i));serverRoom.getSelectionModel().select(1);serverRoom.setId("floor-server-room");
        serverRoom.valueProperty().addListener((o,b,a)->{if(a!=null&&!selecting){progress[0]=0;progress[1]=55;render();}});
        motion.setId("floor-motion");motion.setAccessibleText("Di chuyển Client mô phỏng");motion.setShowTickLabels(true);motion.setMajorTickUnit(25);
        motion.valueProperty().addListener((o,b,a)->{if(selecting||!demo.isSelected())return;progress[demoIndex()]=a.doubleValue();render();});
        playback=new Timeline(new KeyFrame(Duration.millis(100),e->{int i=demoIndex();motion.setValue(Math.min(100,progress[i]+.35));if(progress[i]>=100)stopPlayback();}));playback.setCycleCount(Animation.INDEFINITE);
        play=button("Chạy mô phỏng","primary",this::togglePlayback);play.setId("floor-play");
        demoControls=column(8,new FlowPane(10,8,field("Phòng đặt Server",serverRoom),play,button("Về đầu","quiet",()->{stopPlayback();motion.setValue(0);})),field("Di chuyển Client",motion));
        motion.setTooltip(new Tooltip("Từ phòng ra hành lang, rồi đi dọc dãy phòng."));
        load=button("Mở khảo sát","quiet",()->{var chooser=new FileChooser();chooser.setTitle("Khảo sát Wi-Fi đã thu");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Khảo sát JSON","*.json"));var file=chooser.showOpenDialog(stage);if(file!=null)loadSurvey(file.toPath());});load.setId("floor-load");
        activate=button("Bật theo dõi","primary",this::activate);activate.setId("floor-activate");
        disable=button("Dừng theo dõi","quiet",()->request("FLOOR_DISABLE",object(),this::accept));disable.setId("floor-disable");
        liveControls=column(8,new FlowPane(10,8,load,activate,disable));
        var template=button("Tạo mốc khảo sát","quiet",()->{
            try{var area=plan.survey(areaName.getText().strip(),ssid.getText(),serverRoom.getSelectionModel().getSelectedIndex());app.openSurvey(area);}
            catch(Exception error){status.setText(ArcadeApplication.message(error));}
        });template.setId("floor-template");
        floorNumber.setId("floor-number");floorNumber.setPromptText("Chưa chọn");floorNumber.setPrefColumnCount(6);
        floorNumber.setTooltip(new Tooltip("Để trống khi chưa biết tầng; tên phòng sẽ dùng E1.X.01, E1.X.02…"));
        var planError=text("","feedback","error");planError.setId("floor-plan-error");planError.managedProperty().bind(planError.textProperty().isNotEmpty());planError.visibleProperty().bind(planError.managedProperty());
        var apply=button("Áp dụng sơ đồ","quiet",()->{
            try{installPlan(new CorridorPlan(plan.roomsPerSide(),roomWidth.getValue(),roomDepth.getValue(),hallWidth.getValue(),floorNumber.getText()));planError.setText("");render();}
            catch(IllegalArgumentException error){planError.setText(error.getMessage());}
        });apply.setId("floor-apply-size");
        var dimensions=new FlowPane(12,8,field("Tầng",floorNumber),field("Ngang phòng (m)",roomWidth),field("Sâu phòng (m)",roomDepth),field("Rộng hành lang (m)",hallWidth),apply);
        var setup=new TitledPane("Sơ đồ & mốc khảo sát",column(10,text("Tên phòng E1.X.Y: X là tầng, Y là số phòng. Chưa chọn tầng thì giữ X.","caption"),dimensions,planError,text("6 phòng mỗi bên, kích thước giả định.","caption"),new FlowPane(12,8,field("Tên khu",areaName),field("Tên Wi-Fi",ssid)),template));setup.setExpanded(false);setup.setId("floor-setup");
        settingsPane=new TitledPane("Thiết lập bản đồ",column(12,liveControls,setup));settingsPane.setExpanded(false);settingsPane.setId("floor-settings");
        var body=column(12,origin,new FlowPane(12,8,demo,live,field("Client",clients),smooth),distance,positionStatus,apDistances,map,
            text("Client: chấm xanh   ·   Server: ô vuông   ·   Đường nối: khoảng cách thẳng","caption"),demoControls,status,signalPane,settingsPane);
        body.setPadding(new Insets(embedded?12:20));view=scroll(body);view.setId("floor-scroll");
        if(!embedded){var scene=new Scene(view);scene.getStylesheets().add(CSS);stage.setScene(scene);stage.setOnHidden(e->close());}
        map.prefHeightProperty().bind(stage.heightProperty().multiply(embedded?.36:.42));
        map.onSelect(id->{clients.getItems().stream().filter(c->c.id().equals(id)).findFirst().ifPresent(clients::setValue);});
        map.onDistance(value->distance.setText(clients.getValue()==null?"Chọn Client để xem khoảng cách":clients.getValue().name()+" → Server: "+(value==null?"chưa xác định vị trí":String.format(Locale.ROOT,"≈ %.1f m",value))));
        clients.valueProperty().addListener((o,b,a)->{if(selecting)return;if(live.isSelected()){selectedLiveClient=a==null?"":a.id();selectionChanged.accept(selectedLiveClient);}render();});
        group.selectedToggleProperty().addListener((o,b,a)->{if(a==null){live.setSelected(true);return;}stopPlayback();refreshClients();render();modeChanged.run();if(a==live)refresh();});
        refreshClients();render();if(!embedded){stage.show();if(live.isSelected())refresh();}
    }
    private int demoIndex(){return clients.getValue()!=null&&clients.getValue().id().equals("demo-2")?1:0;}
    private void togglePlayback(){if(playback.getStatus()==Animation.Status.RUNNING)stopPlayback();else{if(motion.getValue()>=100)motion.setValue(0);playback.play();play.setText("Tạm dừng");}}
    private void stopPlayback(){playback.stop();play.setText("Chạy mô phỏng");}
    private void refreshClients(){
        selecting=true;String before=demo.isSelected()?(clients.getValue()==null?"":clients.getValue().id()):selectedLiveClient;var choices=new ArrayList<Choice>();
        if(demo.isSelected())choices.addAll(List.of(new Choice("demo-1","Client 01"),new Choice("demo-2","Client 02")));
        else {var roster=new LinkedHashMap<String,Choice>();for(var p:snapshot.path("players"))roster.put(p.path("player_id").asText(),new Choice(p.path("player_id").asText(),p.path("nickname").asText("Client")));
            if(embedded)for(var p:app.latestProximitySnapshot().path("players"))roster.put(p.path("player_id").asText(),new Choice(p.path("player_id").asText(),p.path("nickname").asText("Client")));
            choices.addAll(roster.values());}
        if(!clients.getItems().equals(choices))clients.getItems().setAll(choices);
        clients.getItems().stream().filter(c->c.id().equals(before)).findFirst().ifPresentOrElse(clients::setValue,()->{if(demo.isSelected())clients.getSelectionModel().selectFirst();else clients.getSelectionModel().clearSelection();});selecting=false;
    }
    private void render(){
        if(closed)return;boolean simulated=demo.isSelected();demoControls.setVisible(simulated);demoControls.setManaged(simulated);liveControls.setVisible(!simulated);liveControls.setManaged(!simulated);
        boolean selected=clients.getValue()!=null;
        apDistances.setManaged(!simulated&&(!embedded||selected));apDistances.setVisible(apDistances.isManaged());
        signalPane.setManaged(!simulated&&(!embedded||selected));signalPane.setVisible(signalPane.isManaged());
        updateApReadings();
        var values=new ArrayList<CorridorMapView.Client>();CorridorPlan visiblePlan=plan;var server=plan.roomCenter(Math.max(0,serverRoom.getSelectionModel().getSelectedIndex()));
        if(simulated){
            positionStatus.setText("");
            origin.setText("MÔ PHỎNG · Sơ đồ giả định");
            for(int i=0;i<2;i++)values.add(new CorridorMapView.Client("demo-"+(i+1),"Client 0"+(i+1),plan.demoPosition(serverRoom.getSelectionModel().getSelectedIndex(),progress[i]/100),"DEMO"));
            selecting=true;motion.setValue(progress[demoIndex()]);selecting=false;detail.setText("Server cố định tại phòng "+serverRoom.getValue()+". Khoảng cách tính theo tỷ lệ sơ đồ quy ước.");
        }else{
            origin.setText("THEO DÕI WI-FI · "+(snapshot.path("enabled").asBoolean()?snapshot.path("area").path("name").asText():"Chưa bật bản đồ cho phòng thi"));
            if(snapshot.path("enabled").asBoolean()){
                visiblePlan=decode(snapshot.path("plan"),CorridorPlan.class);var area=snapshot.path("area");server=new CorridorPlan.Position(area.path("serverX").asDouble(),area.path("serverY").asDouble());
                for(var p:snapshot.path("players"))values.add(new CorridorMapView.Client(p.path("player_id").asText(),p.path("nickname").asText(),located(p)?new CorridorPlan.Position(p.path("x").asDouble(),p.path("y").asDouble()):null,p.path("status").asText()));
                var chosen=clients.getValue();var node=chosen==null?null:findPlayer(chosen.id());
                String locationStatus=node==null?"":node.path("located").asBoolean()&&!located(node)?"STALE":node.path("status").asText();
                positionStatus.setText(node==null?(clients.getItems().isEmpty()?"Chưa có Client trong phòng.":""):located(node)?"":statusName(locationStatus));
                detail.setText(node==null?"":statusName(locationStatus)+" · BSSID: "+node.path("bssid").asText("—")+" · tuổi mẫu: "+(node.path("age_ms").asLong()+snapshotAge())/1000+" giây");
            }else {positionStatus.setText("Chủ phòng mở khảo sát và bật theo dõi.");detail.setText("");}
        }
        map.update(visiblePlan,server,values,clients.getValue()==null?"":clients.getValue().id(),smooth.isSelected());
        activate.setDisable(busy||survey==null||!app.canConfigureFloor());disable.setDisable(busy||!app.canConfigureFloor()||!snapshot.path("enabled").asBoolean());load.setDisable(busy);
    }
    private JsonNode findPlayer(String id){for(var p:snapshot.path("players"))if(p.path("player_id").asText().equals(id))return p;return null;}
    private long snapshotAge(){return snapshotAt==0?0:(System.nanoTime()-snapshotAt)/1_000_000;}
    private boolean located(JsonNode p){return p.path("located").asBoolean()&&p.path("age_ms").asLong(-1)>=0&&p.path("age_ms").asLong()+snapshotAge()<=20000;}
    private void updateApReadings(){if(closed||demo.isSelected())return;var chosen=clients.getValue();var player=chosen==null?null:findPlayer(chosen.id());long age=snapshotAge();
        if(embedded&&chosen!=null)for(var p:app.latestProximitySnapshot().path("players"))if(p.path("player_id").asText().equals(chosen.id())){player=p;age=app.measurementSnapshotAgeMs();break;}
        String client=player==null?"":ApDistanceViews.compact(chosen.name(),player.path("ap_distance"),age);
        if(embedded&&player!=null){long pingAge=player.path("ping_age_ms").asLong(-1);String ping=player.path("connected").asBoolean()&&player.has("ping_ms")&&pingAge>=0&&pingAge+age<=15000?player.path("ping_ms").asText()+" ms":"— ms";
            String apName=player.path("access_point").path("ap_name").asText();
            client+="\nAP: "+(!AccessPointViews.fresh(player,age)?"Đang chờ dữ liệu":apName.isBlank()?"Chưa đặt tên":apName)+" · Ping: "+ping;
            if(player.has("connected")&&!player.path("connected").asBoolean())client+=player.path("reconnecting").asBoolean()?" · Đang giữ chỗ":" · Mất kết nối";}
        apDistances.setText(client+(embedded?"":(client.isEmpty()?"":"\n")+ApDistanceViews.compact("Server",snapshot.path("server_ap_distance"),snapshotAge())));
        signalDetails.setText((player==null?"":ApDistanceViews.summary(chosen.name(),player.path("ap_distance"),age)+"\n")+(embedded?"":ApDistanceViews.summary("Server",snapshot.path("server_ap_distance"),snapshotAge())+"\n")+"Khoảng cách tới AP được ước lượng từ RSSI, có thể sai lệch do tường và thiết bị.");}
    static String statusName(String value){return switch(value){case "ESTIMATED"->"Vị trí ước lượng theo khảo sát";case "WAITING"->"Đang chờ scan từ Client";case "STALE"->"Mẫu đã cũ";case "DISCONNECTED"->"Client mất kết nối";default->{try{yield FingerprintViews.statusName(FingerprintModel.Status.valueOf(value));}catch(Exception e){yield "Chưa có vị trí";}}};}
    void accept(JsonNode value){if(closed)return;snapshot=value.deepCopy();snapshotAt=System.nanoTime();if(live.isSelected()){refreshClients();render();}}
    void roomChanged(JsonNode config){
        if(closed)return;
        if(config.path("enabled").asBoolean()!=snapshot.path("enabled").asBoolean()||!snapshot.path("epoch").asText().equals(config.path("epoch").asText())){snapshot=config.deepCopy();if(live.isSelected())refreshClients();}
        render();
    }
    private void installPlan(CorridorPlan value){
        stopPlayback();int previous=serverRoom.getSelectionModel().getSelectedIndex();selecting=true;plan=value;serverRoom.getItems().clear();
        for(int i=0;i<plan.roomsPerSide()*2;i++)serverRoom.getItems().add(plan.roomName(i));serverRoom.getSelectionModel().select(Math.max(0,Math.min(previous,serverRoom.getItems().size()-1)));selecting=false;
        roomWidth.getValueFactory().setValue(plan.roomWidth());roomDepth.getValueFactory().setValue(plan.roomDepth());hallWidth.getValueFactory().setValue(plan.corridorWidth());
        floorNumber.setText(plan.floor().equals("X")?"":plan.floor());
    }
    void disconnected(){snapshot=object();if(live.isSelected()){refreshClients();render();status.setText("Mất kết nối Server. Vị trí sẽ cập nhật lại sau khi nối lại.");}}
    void loadSurvey(java.nio.file.Path file){
        if(busy||closed)return;busy=true;render();status.setText("Đang kiểm tra khảo sát…");
        CompletableFuture.supplyAsync(()->{try{var data=FingerprintEvaluationFiles.readSurvey(file);new FingerprintModel(data.areas().getFirst(),data.runs(),FingerprintModel.Parameters.baseline(2));CorridorPlan.fromSurvey(data.areas().getFirst());return data;}catch(Exception e){throw new CompletionException(e);}})
            .whenComplete((data,error)->Platform.runLater(()->{if(closed)return;busy=false;if(error!=null){survey=null;status.setText(ArcadeApplication.message(error));}else{survey=data;CorridorPlan.fromSurvey(data.areas().getFirst()).ifPresent(this::installPlan);status.setText("Đã mở khảo sát: "+data.areas().getFirst().name());}render();}));
    }
    private void activate(){
        var gateway=app.floorGateway();if(gateway==null||survey==null||busy||!app.canConfigureFloor())return;var data=survey;var chosenPlan=plan;String roomCode=app.floorRoomCode();busy=true;render();status.setText("Đang đồng bộ khảo sát lên Server…");
        SurveySync.upload(gateway,data,null).thenCompose(count->{if(closed)return CompletableFuture.<JsonNode>failedFuture(new CancellationException());var area=data.areas().getFirst();var p=object().put("area_id",area.id()).put("revision",area.revision()).put("room_code",roomCode);p.set("plan",JSON.valueToTree(chosenPlan));return gateway.request("FLOOR_CONFIGURE",p);})
            .whenComplete((value,error)->Platform.runLater(()->{if(closed)return;busy=false;if(error==null){accept(value);status.setText("Đang theo dõi vị trí.");}else status.setText(ArcadeApplication.message(error));render();}));
    }
    private void request(String type,com.fasterxml.jackson.databind.node.ObjectNode payload,Consumer<JsonNode> success){
        var gateway=app.floorGateway();if(gateway==null){status.setText("Kết nối và tham gia phòng thi để xem dữ liệu thật.");return;}
        gateway.request(type,payload).whenComplete((value,error)->Platform.runLater(()->{if(closed)return;if(error==null)success.accept(value);else status.setText(ArcadeApplication.message(error));}));
    }
    void refresh(){if(embedded&&!app.managedRoomSnapshot().path("is_owner").asBoolean())return;request("FLOOR_GET",object(),this::accept);}
    Node content(){return view;}
    boolean simulated(){return demo.isSelected();}
    void onSelect(Consumer<String> action){selectionChanged=action;}
    void onModeChanged(Runnable action){modeChanged=action;}
    void showLive(){live.setSelected(true);}
    void selectPlayer(String id){boolean changed=!selectedLiveClient.equals(id);selectedLiveClient=id;if(!demo.isSelected()){refreshClients();if(changed)render();else updateApReadings();}}
    void measurementsChanged(){if(!demo.isSelected()){var before=clients.getValue();refreshClients();if(!Objects.equals(before,clients.getValue()))render();else updateApReadings();}}
    void openSettings(){settingsPane.setExpanded(true);view.setVvalue(1);}
    void suspend(){stopPlayback();}
    boolean showing(){return !closed&&stage.isShowing();}
    void focus(){stage.toFront();}
    @Override public void close(){if(closed)return;closed=true;playback.stop();ageTimer.stop();map.close();if(!embedded)stage.close();}
}
