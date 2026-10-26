package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import java.time.Instant;
import java.util.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** A schematic of authoritative room members, never a floor plan or a simulated range scan. */
final class RoomTopologyView extends VBox {
    private final Label roomName=text("","subtitle"),summary=text("","status");
    private final Label code=text("","topology-room-code"),port=text("","topology-room-port");
    private final Orbit diagram=new Orbit();
    RoomTopologyView(JsonNode room,boolean live){
        super(12);setId("room-topology");getStyleClass().add("room-topology");setMinWidth(0);
        code.setId("topology-room-code");port.setId("topology-room-port");summary.setId("topology-summary");
        var heading=column(3,text("KẾT NỐI ĐẾN PHÒNG","caption"),roomName);
        HBox.setHgrow(heading,Priority.ALWAYS);
        var top=new BorderPane(heading);top.setRight(summary);BorderPane.setAlignment(summary,Pos.CENTER_RIGHT);
        var legend=new FlowPane(16,6,legend("Sẵn sàng","ready"),legend("Đang chuẩn bị","preparing"),legend("Mất kết nối","offline"));
        var note=text("Vị trí chỉ minh họa liên kết, không theo tỉ lệ khoảng cách. Mét cần số đo Wi-Fi RTT từ phần cứng.","muted");
        getChildren().addAll(top,diagram,legend,note);update(room,live);
    }
    void update(JsonNode room,boolean live){
        var participants=RoomTopologyModel.participants(room,live,Instant.now());
        roomName.setText(room.path("room_name").asText().isBlank()?"Chưa tạo phòng":room.path("room_name").asText());
        code.setText(room.path("room_code").asText().isBlank()?"— — —":room.path("room_code").asText());
        port.setText(room.path("server_port").asInt()>0?"TCP "+room.path("server_port").asInt()+" · TLS":"Máy chủ · TLS");
        long online=participants.stream().filter(p->!p.stateStyle().equals("offline")&&!p.stateStyle().equals("unknown")).count();
        long ready=participants.stream().filter(p->p.stateStyle().equals("ready")).count();
        String capacity=room.has("max_players")?"/"+room.path("max_players").asInt():"";
        summary.setText(live?online+capacity+" kết nối · "+ready+" sẵn sàng":"Dữ liệu lần cuối · chưa xác nhận");
        diagram.update(participants);
    }
    private static HBox legend(String name,String state){
        var dot=new Circle(4);dot.getStyleClass().addAll("topology-dot",state);
        return row(6,dot,text(name,"caption"));
    }
    private void details(RoomTopologyModel.Participant p){
        var dialog=new Alert(Alert.AlertType.INFORMATION);dialog.initOwner(getScene().getWindow());styleDialog(dialog);
        dialog.setTitle("Kết nối đến phòng");dialog.setHeaderText(p.nickname());dialog.getDialogPane().setId("topology-client-details");
        var rows=column(12,text(p.state(),"subtitle"),text("Khoảng cách: "+p.distance(),"brand"),
            text(p.ip().isBlank()?"IP chưa được cung cấp cho vai trò hiện tại.":"IP được Server ghi nhận: "+p.ip(),"muted"),
            text("Mét cần kết quả Wi-Fi RTT/FTM từ phần cứng hỗ trợ. Ping TCP/TLS chỉ là thời gian phản hồi.","muted"),
            text("Nhãn AP là khoảng cách tới điểm phát, không phải khoảng cách tới máy Server.","caption"));
        dialog.getDialogPane().setContent(rows);dialog.show();
    }
    private final class Orbit extends Pane {
        private final Ellipse outer=new Ellipse(),inner=new Ellipse();
        private final StackPane hub=new StackPane();
        private final Label empty=text("Chưa có client tham gia.\nChia sẻ mã phòng để kết nối.","muted");
        private List<RoomTopologyModel.Participant> participants=List.of();
        private final List<Button> clients=new ArrayList<>();
        private final List<Line> links=new ArrayList<>();
        Orbit(){
            outer.getStyleClass().add("topology-orbit");inner.getStyleClass().add("topology-orbit");
            outer.setMouseTransparent(true);inner.setMouseTransparent(true);
            var circle=new Circle(69);circle.getStyleClass().add("topology-hub-circle");
            var caption=text("PHÒNG","topology-hub-caption");
            var content=column(3,caption,code,port);content.setAlignment(Pos.CENTER);
            hub.getChildren().addAll(circle,content);hub.setMinSize(140,140);hub.setPrefSize(140,140);hub.setMaxSize(140,140);
            empty.setAlignment(Pos.CENTER);empty.setMouseTransparent(true);
            setMinWidth(600);getStyleClass().add("topology-orbit-area");
        }
        void update(List<RoomTopologyModel.Participant> next){
            participants=next;clients.clear();links.clear();getChildren().clear();getChildren().addAll(outer,inner);
            for(var p:next){
                var line=new Line();line.getStyleClass().addAll("topology-link",p.stateStyle());line.setMouseTransparent(true);links.add(line);getChildren().add(line);
            }
            getChildren().add(hub);
            for(var p:next){
                var frame=new StackPane(QuizlingArt.avatar(p.avatar(),44));
                var dot=new Circle(4.5);dot.getStyleClass().addAll("topology-dot",p.stateStyle());StackPane.setAlignment(dot,Pos.BOTTOM_RIGHT);
                frame.getChildren().add(dot);frame.setMaxSize(44,44);
                var name=text(p.nickname(),"topology-nickname");name.setWrapText(false);name.setMaxWidth(120);name.setTooltip(new Tooltip(p.nickname()));
                var state=text(p.state(),"topology-state",p.stateStyle());state.setWrapText(false);
                var metres=text(p.distance(),"topology-distance");metres.setWrapText(false);metres.setMaxWidth(126);
                var content=column(1,frame,name,state,metres);content.setAlignment(Pos.CENTER);content.setMouseTransparent(true);
                var button=button("","topology-client",()->details(p));button.setGraphic(content);
                button.setMinSize(132,108);button.setPrefSize(132,108);button.setMaxSize(132,108);
                button.setAccessibleText(p.nickname()+" · "+p.state()+" · "+p.distance());
                button.setTooltip(new Tooltip(button.getAccessibleText()+(p.ip().isBlank()?"":"\nIP: "+p.ip())+"\nBấm để xem chi tiết."));
                clients.add(button);getChildren().add(button);
            }
            if(next.isEmpty())getChildren().add(empty);
            double height=RoomTopologyModel.preferredHeight(next.size());setMinHeight(height);setPrefHeight(height);setMaxHeight(height);
            requestLayout();
        }
        @Override protected void layoutChildren(){
            double w=getWidth(),h=getHeight(),cx=w/2,cy=h/2,rx=Math.max(0,cx-80),ry=Math.max(0,cy-68);
            outer.setCenterX(cx);outer.setCenterY(cy);outer.setRadiusX(rx*.9);outer.setRadiusY(ry*.9);
            inner.setCenterX(cx);inner.setCenterY(cy);inner.setRadiusX(rx*.66);inner.setRadiusY(ry*.66);
            hub.resizeRelocate(cx-70,cy-70,140,140);
            var positions=RoomTopologyModel.positions(w,h,participants.size());
            for(int i=0;i<clients.size();i++){
                var point=positions.get(i);clients.get(i).resizeRelocate(point.x(),point.y(),132,108);
                var line=links.get(i);line.setStartX(cx);line.setStartY(cy);line.setEndX(point.x()+66);line.setEndY(point.y()+54);
            }
            if(participants.isEmpty())empty.resizeRelocate(Math.max(0,cx-230),cy+95,460,55);
        }
    }
}
