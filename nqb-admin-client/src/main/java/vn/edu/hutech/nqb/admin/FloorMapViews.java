package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
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

/** A schematic view alongside the existing exam workspace. Demo never transmits samples. */
final class FloorMapViews implements AutoCloseable {
    private record Choice(String id,String name){@Override public String toString(){return name;}}
    private final ArcadeApplication app;
    private final Stage stage=new Stage();
    private final CorridorMapView map=new CorridorMapView();
    private final ComboBox<Choice> clients=new ComboBox<>();
    private final ComboBox<String> serverRoom=new ComboBox<>();
    private final Spinner<Double> roomWidth=new Spinner<>(3.0,20.0,8.0,1.0),roomDepth=new Spinner<>(3.0,20.0,8.0,1.0),hallWidth=new Spinner<>(1.0,8.0,3.0,.5);
    private final TextField areaName=new TextField("Hành lang HUTECH E1"),ssid=new TextField("HUTECH E1");
    private final Slider motion=new Slider(0,100,0);
    private final CheckBox smooth=new CheckBox("Chuyển động mượt");
    private final Label origin=text("","caption"),distance=text("","subtitle"),status=text("","muted"),detail=text("","muted");
    private final Label apDistances=text("","muted");
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
    private final Timeline ageTimer=new Timeline(new KeyFrame(Duration.seconds(1),e->updateApReadings()));

    FloorMapViews(ArcadeApplication app){
        this.app=app;stage.initOwner(app.ownerStage());stage.setTitle("Bản đồ Client — Server");stage.setMinWidth(760);stage.setMinHeight(540);
        stage.setWidth(Math.min(1100,app.ownerStage().getWidth()));stage.setHeight(Math.min(760,app.ownerStage().getHeight()));
        ageTimer.setCycleCount(Animation.INDEFINITE);runWhenShowing(apDistances,ageTimer);
        map.setId("floor-map");origin.setId("floor-origin");distance.setId("floor-distance");status.setId("floor-status");detail.setId("floor-detail");
        clients.setId("floor-client");clients.setPrefWidth(210);clients.setAccessibleText("Client đang xem");
        var group=new ToggleGroup();demo.setToggleGroup(group);live.setToggleGroup(group);demo.setSelected(true);demo.setId("floor-demo");live.setId("floor-live");
        smooth.setSelected(!app.reducedMotion());smooth.setId("floor-smooth");smooth.setOnAction(e->render());
        for(int i=0;i<12;i++)serverRoom.getItems().add(plan.roomName(i));serverRoom.getSelectionModel().select(1);serverRoom.setId("floor-server-room");
        serverRoom.valueProperty().addListener((o,b,a)->{if(a!=null&&!selecting){progress[0]=0;progress[1]=55;render();}});
        motion.setId("floor-motion");motion.setAccessibleText("Di chuyển Client mô phỏng");motion.setShowTickLabels(true);motion.setMajorTickUnit(25);
        motion.valueProperty().addListener((o,b,a)->{if(selecting||!demo.isSelected())return;progress[demoIndex()]=a.doubleValue();render();});
        playback=new Timeline(new KeyFrame(Duration.millis(100),e->{int i=demoIndex();motion.setValue(Math.min(100,progress[i]+.35));if(progress[i]>=100)stopPlayback();}));playback.setCycleCount(Animation.INDEFINITE);
        play=button("Chạy mô phỏng","primary",this::togglePlayback);play.setId("floor-play");
        demoControls=column(8,new FlowPane(10,8,field("Phòng đặt Server",serverRoom),play,button("Về vị trí đầu","quiet",()->{stopPlayback();motion.setValue(0);})),field("Lộ trình: từ phòng ra hành lang rồi đi dọc dãy phòng",motion));
        load=button("Mở khảo sát JSON","quiet",()->{var chooser=new FileChooser();chooser.setTitle("Khảo sát Wi-Fi đã thu");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Khảo sát JSON","*.json"));var file=chooser.showOpenDialog(stage);if(file!=null)loadSurvey(file.toPath());});load.setId("floor-load");
        activate=button("Dùng khảo sát cho phòng thi","primary",this::activate);activate.setId("floor-activate");
        disable=button("Dừng theo dõi phòng","quiet",()->request("FLOOR_DISABLE",object(),this::accept));disable.setId("floor-disable");
        liveControls=column(8,new FlowPane(10,8,load,activate,disable),text("Client trong phòng tự gửi scan mới khi chủ phòng bật theo dõi. Chu kỳ gồm thời gian quét và 5 giây chờ. Mất mẫu: ẩn vị trí, giữ phiên thi.","caption"));
        var template=button("Tạo mốc để khảo sát Wi-Fi","quiet",()->{
            try{var area=plan.survey(areaName.getText().strip(),ssid.getText(),serverRoom.getSelectionModel().getSelectedIndex());app.openSurvey(area);}
            catch(Exception error){status.setText(ArcadeApplication.message(error));}
        });template.setId("floor-template");
        var apply=button("Áp dụng kích thước","quiet",()->{installPlan(new CorridorPlan(plan.roomsPerSide(),roomWidth.getValue(),roomDepth.getValue(),hallWidth.getValue()));render();});apply.setId("floor-apply-size");
        var dimensions=new FlowPane(12,8,field("Ngang phòng (m)",roomWidth),field("Sâu phòng (m)",roomDepth),field("Rộng hành lang (m)",hallWidth),apply);
        var setup=new TitledPane("Sơ đồ quy ước và mốc khảo sát",column(10,text("6 phòng mỗi bên. Kích thước giả định để demo; tên A/B là nhãn quy ước. Tọa độ khảo sát và sơ đồ cần dùng cùng quy ước.","caption"),dimensions,new FlowPane(12,8,field("Tên khu",areaName),field("SSID khảo sát",ssid)),template));setup.setExpanded(false);setup.setId("floor-setup");
        var body=column(12,origin,new FlowPane(12,8,demo,live,field("Client đang xem",clients),smooth),distance,map,
            text("Chấm xanh: Client · Ô vuông: Server · Nét đứt: khoảng cách đường thẳng xuyên tường","caption"),detail,apDistances,demoControls,liveControls,setup,status);
        body.setPadding(new Insets(20));var scroller=scroll(body);scroller.setId("floor-scroll");var scene=new Scene(scroller);scene.getStylesheets().add(CSS);stage.setScene(scene);stage.setOnHidden(e->close());
        map.prefHeightProperty().bind(stage.heightProperty().multiply(.42));
        map.onSelect(id->{clients.getItems().stream().filter(c->c.id().equals(id)).findFirst().ifPresent(clients::setValue);});
        map.onDistance(value->distance.setText((clients.getValue()==null?"Client":clients.getValue().name())+" → Server: "+(value==null?"chưa định vị được":String.format(Locale.ROOT,"≈ %.1f m",value))));
        clients.valueProperty().addListener((o,b,a)->{if(!selecting)render();});
        group.selectedToggleProperty().addListener((o,b,a)->{if(a==null){demo.setSelected(true);return;}stopPlayback();refreshClients();render();if(a==live)refresh();});
        refreshClients();render();stage.show();
    }
    private int demoIndex(){return clients.getValue()!=null&&clients.getValue().id().equals("demo-2")?1:0;}
    private void togglePlayback(){if(playback.getStatus()==Animation.Status.RUNNING)stopPlayback();else{if(motion.getValue()>=100)motion.setValue(0);playback.play();play.setText("Tạm dừng");}}
    private void stopPlayback(){playback.stop();play.setText("Chạy mô phỏng");}
    private void refreshClients(){
        selecting=true;String before=clients.getValue()==null?"":clients.getValue().id();clients.getItems().clear();
        if(demo.isSelected())clients.getItems().addAll(new Choice("demo-1","Client 01"),new Choice("demo-2","Client 02"));
        else for(var p:snapshot.path("players"))clients.getItems().add(new Choice(p.path("player_id").asText(),p.path("nickname").asText("Client")));
        clients.getItems().stream().filter(c->c.id().equals(before)).findFirst().ifPresentOrElse(clients::setValue,()->clients.getSelectionModel().selectFirst());selecting=false;
    }
    private void render(){
        if(closed)return;boolean simulated=demo.isSelected();demoControls.setVisible(simulated);demoControls.setManaged(simulated);liveControls.setVisible(!simulated);liveControls.setManaged(!simulated);
        apDistances.setManaged(!simulated);apDistances.setVisible(!simulated);
        updateApReadings();
        var values=new ArrayList<CorridorMapView.Client>();CorridorPlan visiblePlan=plan;var server=plan.roomCenter(Math.max(0,serverRoom.getSelectionModel().getSelectedIndex()));
        if(simulated){
            origin.setText("MÔ PHỎNG · kích thước và chuyển động giả lập · chưa phải số đo thực tế");
            for(int i=0;i<2;i++)values.add(new CorridorMapView.Client("demo-"+(i+1),"Client 0"+(i+1),plan.demoPosition(serverRoom.getSelectionModel().getSelectedIndex(),progress[i]/100),"DEMO"));
            selecting=true;motion.setValue(progress[demoIndex()]);selecting=false;detail.setText("Server cố định tại phòng "+serverRoom.getValue()+". Khoảng cách tính theo tỷ lệ sơ đồ quy ước.");
        }else{
            origin.setText("THEO DÕI WI-FI · "+(snapshot.path("enabled").asBoolean()?snapshot.path("area").path("name").asText():"Chưa bật bản đồ cho phòng thi"));
            if(snapshot.path("enabled").asBoolean()){
                visiblePlan=decode(snapshot.path("plan"),CorridorPlan.class);var area=snapshot.path("area");server=new CorridorPlan.Position(area.path("serverX").asDouble(),area.path("serverY").asDouble());
                for(var p:snapshot.path("players"))values.add(new CorridorMapView.Client(p.path("player_id").asText(),p.path("nickname").asText(),p.path("located").asBoolean()?new CorridorPlan.Position(p.path("x").asDouble(),p.path("y").asDouble()):null,p.path("status").asText()));
                var chosen=clients.getValue();var node=chosen==null?null:findPlayer(chosen.id());detail.setText(node==null?"Chưa có Client trong phòng.":statusName(node.path("status").asText())+" · BSSID: "+node.path("bssid").asText("—")+" · tuổi mẫu: "+node.path("age_ms").asLong()/1000+" giây");
            }else detail.setText("Chủ phòng mở khảo sát đã thu và nhấn Dùng khảo sát cho phòng thi. Học sinh xem vị trí của mình.");
        }
        map.update(visiblePlan,server,values,clients.getValue()==null?"":clients.getValue().id(),smooth.isSelected());
        activate.setDisable(busy||survey==null||!app.canConfigureFloor());disable.setDisable(busy||!app.canConfigureFloor()||!snapshot.path("enabled").asBoolean());load.setDisable(busy);
    }
    private JsonNode findPlayer(String id){for(var p:snapshot.path("players"))if(p.path("player_id").asText().equals(id))return p;return null;}
    private void updateApReadings(){if(closed||demo.isSelected())return;var chosen=clients.getValue();var player=chosen==null?null:findPlayer(chosen.id());long age=snapshotAt==0?0:(System.nanoTime()-snapshotAt)/1_000_000;
        apDistances.setText((player==null?"":ApDistanceViews.summary(chosen.name(),player.path("ap_distance"),age)+"\n")+ApDistanceViews.summary("Server",snapshot.path("server_ap_distance"),age));}
    static String statusName(String value){return switch(value){case "ESTIMATED"->"Vị trí ước lượng theo khảo sát";case "WAITING"->"Đang chờ scan từ Client";case "STALE"->"Mẫu đã cũ";case "DISCONNECTED"->"Client mất kết nối";default->{try{yield FingerprintViews.statusName(FingerprintModel.Status.valueOf(value));}catch(Exception e){yield "Chưa có vị trí";}}};}
    void accept(JsonNode value){if(closed)return;snapshot=value.deepCopy();snapshotAt=System.nanoTime();if(live.isSelected()){refreshClients();render();}}
    void roomChanged(JsonNode config){
        if(closed)return;
        if(!config.path("enabled").asBoolean()||!snapshot.path("epoch").asText().equals(config.path("epoch").asText())){snapshot=config.deepCopy();if(live.isSelected())refreshClients();}
        render();
    }
    private void installPlan(CorridorPlan value){
        stopPlayback();int previous=serverRoom.getSelectionModel().getSelectedIndex();selecting=true;plan=value;serverRoom.getItems().clear();
        for(int i=0;i<plan.roomsPerSide()*2;i++)serverRoom.getItems().add(plan.roomName(i));serverRoom.getSelectionModel().select(Math.max(0,Math.min(previous,serverRoom.getItems().size()-1)));selecting=false;
        roomWidth.getValueFactory().setValue(plan.roomWidth());roomDepth.getValueFactory().setValue(plan.roomDepth());hallWidth.getValueFactory().setValue(plan.corridorWidth());
    }
    void disconnected(){snapshot=object();if(live.isSelected()){refreshClients();render();status.setText("Mất kết nối Server. Vị trí sẽ cập nhật lại sau khi nối lại.");}}
    void loadSurvey(java.nio.file.Path file){
        if(busy||closed)return;busy=true;render();status.setText("Đang kiểm tra khảo sát…");
        CompletableFuture.supplyAsync(()->{try{var data=FingerprintEvaluationFiles.readSurvey(file);new FingerprintModel(data.areas().getFirst(),data.runs(),FingerprintModel.Parameters.baseline(2));CorridorPlan.fromSurvey(data.areas().getFirst());return data;}catch(Exception e){throw new CompletionException(e);}})
            .whenComplete((data,error)->Platform.runLater(()->{if(closed)return;busy=false;if(error!=null){survey=null;status.setText(ArcadeApplication.message(error));}else{survey=data;CorridorPlan.fromSurvey(data.areas().getFirst()).ifPresent(this::installPlan);status.setText("Đã mở "+data.areas().getFirst().name()+" · Server và các mốc giữ tọa độ trong khảo sát. Chỉ kích hoạt sau khi đã tạo phòng thi.");}render();}));
    }
    private void activate(){
        var gateway=app.floorGateway();if(gateway==null||survey==null||busy||!app.canConfigureFloor())return;var data=survey;var chosenPlan=plan;String roomCode=app.floorRoomCode();busy=true;render();status.setText("Đang đồng bộ khảo sát lên Server…");
        SurveySync.upload(gateway,data,null).thenCompose(count->{if(closed)return CompletableFuture.<JsonNode>failedFuture(new CancellationException());var area=data.areas().getFirst();var p=object().put("area_id",area.id()).put("revision",area.revision()).put("room_code",roomCode);p.set("plan",JSON.valueToTree(chosenPlan));return gateway.request("FLOOR_CONFIGURE",p);})
            .whenComplete((value,error)->Platform.runLater(()->{if(closed)return;busy=false;if(error==null){accept(value);status.setText("Đã bật định vị cho phòng. Khoảng cách phụ thuộc tọa độ và mẫu khảo sát.");}else status.setText(ArcadeApplication.message(error));render();}));
    }
    private void request(String type,com.fasterxml.jackson.databind.node.ObjectNode payload,Consumer<JsonNode> success){
        var gateway=app.floorGateway();if(gateway==null){status.setText("Kết nối và tham gia phòng thi để xem dữ liệu thật.");return;}
        gateway.request(type,payload).whenComplete((value,error)->Platform.runLater(()->{if(closed)return;if(error==null)success.accept(value);else status.setText(ArcadeApplication.message(error));}));
    }
    private void refresh(){request("FLOOR_GET",object(),this::accept);}
    boolean showing(){return !closed&&stage.isShowing();}
    void focus(){stage.toFront();}
    @Override public void close(){if(closed)return;closed=true;playback.stop();ageTimer.stop();map.close();stage.close();}
}
