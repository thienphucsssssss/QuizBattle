package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.util.Duration;
import java.util.*;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Logical Wi-Fi topology. Coordinates never pretend to locate physical laptops. */
final class LanTopologyView extends VBox {
    private final Map<String,PlayerNode> nodes=new LinkedHashMap<>();
    private final Label summary=text("","muted"),ssid=text("Chưa chọn điểm phát","topology-ssid"),refreshSummary=text("","topology-legend");
    private final Graph graph=new Graph();
    private final Consumer<String> select;
    private long receivedAt;
    private final Timeline ageTimer=new Timeline(new KeyFrame(Duration.seconds(1),e->updateAges()));
    private static final class PlayerNode {
        final Button button=new Button();final Line line=new Line();final Label metrics=text("— ms · — m","topology-metrics"),status=text("","topology-status");
        JsonNode data;String avatar="",name="";
    }
    LanTopologyView(boolean publicView,Consumer<String> select){
        super(12);this.select=publicView?null:select;setId(publicView?"projected-topology":"lan-topology");getStyleClass().add("topology-panel");
        getChildren().addAll(row(12,text("Kết nối lớp học","subtitle"),space(),summary),graph,text("Ping: với máy chủ · Khoảng cách: đến điểm phát","topology-legend"),refreshSummary);
        ageTimer.setCycleCount(Timeline.INDEFINITE);runWhenShowing(this,ageTimer);
    }
    void update(JsonNode room,JsonNode snapshot){
        receivedAt=System.nanoTime();
        String network=snapshot.path("anchor_ssid").asText(room.path("proximity").path("anchor_ssid").asText());ssid.setText(network.isBlank()?"Chưa chọn điểm phát":network);
        var data=new LinkedHashMap<String,JsonNode>();for(var p:snapshot.path("players"))data.put(p.path("player_id").asText(),p);
        var roster=room.path("players").isArray()?room.path("players"):snapshot.path("players");
        Set<String> keep=new LinkedHashSet<>();int ready=0;
        for(var member:roster){String id=member.path("player_id").asText();keep.add(id);if(member.path("ready").asBoolean())ready++;
            var value=member.deepCopy();if(value instanceof com.fasterxml.jackson.databind.node.ObjectNode obj&&data.containsKey(id))obj.setAll((com.fasterxml.jackson.databind.node.ObjectNode)data.get(id));
            var node=nodes.computeIfAbsent(id,key->{var n=new PlayerNode();n.line.getStyleClass().add("topology-link");n.line.setMouseTransparent(true);n.button.setId("topology-player-"+key);n.button.getStyleClass().add("topology-player");n.button.setOnAction(e->{if(this.select!=null)this.select.accept(key);});n.button.setFocusTraversable(this.select!=null);graph.getChildren().add(0,n.line);graph.getChildren().add(n.button);return n;});
            node.data=value;
            String avatar=value.path("avatar_id").asText("quizling-1"),name=value.path("nickname").asText();
            if(!avatar.equals(node.avatar)||!name.equals(node.name)){node.avatar=avatar;node.name=name;var title=text(name,"topology-name");title.setWrapText(false);title.setMaxWidth(126);title.setTooltip(new Tooltip(name));var content=column(2,QuizlingArt.avatar(avatar,48),title,node.metrics,node.status);content.setAlignment(Pos.CENTER);node.button.setGraphic(content);}
        }
        for(var id:List.copyOf(nodes.keySet()))if(!keep.contains(id)){var n=nodes.remove(id);graph.getChildren().removeAll(n.line,n.button);}
        double graphHeight=keep.size()<=6?360:keep.size()<=8?440:500;graph.setMinHeight(graphHeight);graph.setPrefHeight(graphHeight);graph.setMaxHeight(graphHeight);
        summary.setText(ready+"/"+keep.size()+" sẵn sàng");updateAges();graph.requestLayout();
        var refresh=snapshot.path("refresh");String state=refresh.path("state").asText();
        String last="";for(var p:snapshot.path("players")){String stamp=p.path("refreshed_at_utc").asText();if(stamp.compareTo(last)>0)last=stamp;}
        String time="";try{time=java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(last));}catch(java.time.format.DateTimeParseException ignored){}
        refreshSummary.setText(state.equals("RUNNING")?"Đang thu mẫu mới · "+refresh.path("completed").asInt()+"/"+refresh.path("total").asInt()+" máy đã phản hồi":state.equals("CANCELLED")?"Lượt đo đã hủy khi chuyển phiên":refresh.path("updated").asInt()>0?"Đã đo mới "+refresh.path("updated").asInt()+"/"+refresh.path("total").asInt()+" máy · "+time:refresh.path("total").asInt()>0?"Chưa nhận được số đo mới của các máy":"");
        refreshSummary.setManaged(!refreshSummary.getText().isEmpty());refreshSummary.setVisible(refreshSummary.isManaged());
    }
    private void updateAges(){long extra=receivedAt==0?0:(System.nanoTime()-receivedAt)/1_000_000;
        for(var node:nodes.values()){
            var p=node.data;boolean live=p.path("connected").asBoolean();long age=p.path("measurement_age_ms").asLong(-1);boolean fresh=age>=0&&age+extra<15000;
            long pingAge=p.path("ping_age_ms").asLong(-1);String ping=live&&p.has("ping_ms")&&pingAge>=0&&pingAge+extra<15000?(p.path("ping_ms").asLong()==0?"<1":p.path("ping_ms").asText())+" ms":"— ms";
            String distance=live&&fresh&&p.has("estimated_meters")?"≈ "+String.format(Locale.forLanguageTag("vi-VN"),"%.1f",p.path("estimated_meters").asDouble())+" m":"— m";
            boolean pending=p.path("refresh_status").asText().equals("PENDING");boolean failed=Set.of("FAILED","TIMEOUT").contains(p.path("refresh_status").asText());
            try{if(java.time.Instant.parse(p.path("updated_at_utc").asText()).isAfter(java.time.Instant.parse(p.path("refreshed_at_utc").asText())))failed=false;}catch(java.time.format.DateTimeParseException ignored){}
            node.metrics.setText(ping+" · "+distance);node.metrics.setOpacity(pending||failed?.55:1);
            String state=!live?(p.path("reconnecting").asBoolean()?"Đang giữ chỗ…":"Ngắt kết nối"):pending?"Đang đo…":p.path("refresh_status").asText().equals("UNSUPPORTED")?"Cần cập nhật Client":failed?reason(p.path("refresh_reason").asText()):!fresh&&p.has("estimated_meters")?"Số đo đã cũ":p.path("ready").asBoolean()?"Sẵn sàng":p.path("status_label").asText("Đang chuẩn bị");
            node.status.setText(state);node.button.setAccessibleText(node.name+", "+node.metrics.getText()+", "+state);
            node.line.getStyleClass().removeAll("lost","warning","missing");if(!live)node.line.getStyleClass().add("lost");else if(p.path("over_threshold").asBoolean()||p.path("status").asText().equals("FLUCTUATING"))node.line.getStyleClass().add("warning");else if(!fresh||!p.has("estimated_meters"))node.line.getStyleClass().add("missing");
        }
    }
    static String reason(String value){return switch(value){case "CALIBRATING"->"Đang hiệu chuẩn";case "PERMISSION_DENIED"->"Chưa cấp quyền";case "WIFI_DISABLED"->"Wi-Fi đang tắt";case "NO_ADAPTER"->"Không có Wi-Fi";case "DISCONNECTED"->"Mất Wi-Fi";case "API_ERROR"->"Chưa đọc được RSSI";case "CANCELLED"->"Đã hủy lượt đo";default->value.isBlank()?"Chưa có số đo":value;};}
    private final class Graph extends Pane {
        private final VBox anchor=column(4,deviceMark(true),text("Điểm phát Wi-Fi","topology-name"),ssid);
        private final VBox server=column(2,deviceMark(false),text("Máy chủ","topology-status"));private final Line serverLink=new Line();
        Graph(){setMinHeight(500);setPrefHeight(500);setMaxHeight(500);setMinWidth(0);anchor.setAlignment(Pos.CENTER);anchor.getStyleClass().add("topology-anchor");ssid.setMaxWidth(160);ssid.setWrapText(false);server.setAlignment(Pos.CENTER);server.getStyleClass().add("topology-server");serverLink.getStyleClass().add("topology-link");getChildren().addAll(serverLink,anchor,server);}
        @Override protected void layoutChildren(){double w=getWidth(),cx=w/2,cy=getHeight()/2,rx=Math.max(130,(w-160)/2),ry=getHeight()/2-60;
            anchor.resizeRelocate(cx-95,cy-48,190,96);server.resizeRelocate(cx+103,cy-24,90,48);serverLink.setStartX(cx+65);serverLink.setStartY(cy);serverLink.setEndX(cx+115);serverLink.setEndY(cy);
            int i=0,n=nodes.size();for(var node:nodes.values()){double angle=-Math.PI/2+2*Math.PI*i++/Math.max(1,n),x=cx+rx*Math.cos(angle),y=cy+ry*Math.sin(angle),dx=x-cx,dy=y-cy;double edge=Math.min(70/Math.max(.001,Math.abs(dx)),56/Math.max(.001,Math.abs(dy)));node.button.resizeRelocate(x-70,y-56,140,112);node.line.setStartX(cx);node.line.setStartY(cy);node.line.setEndX(x-dx*edge);node.line.setEndY(y-dy*edge);}
        }
    }
    private static Pane deviceMark(boolean wifi){
        var pane=new Pane();pane.setMinSize(40,30);pane.setPrefSize(40,30);pane.setMaxSize(40,30);
        if(wifi){var outer=new Arc(20,28,18,18,40,100);var inner=new Arc(20,28,10,10,40,100);var dot=new Circle(20,26,2);for(var shape:List.of(outer,inner,dot)){shape.getStyleClass().add("topology-device-shape");pane.getChildren().add(shape);}}
        else{var screen=new Rectangle(6,2,28,17);screen.setArcHeight(4);screen.setArcWidth(4);for(var shape:List.of(screen,new Line(20,19,20,26),new Line(13,27,27,27))){shape.getStyleClass().add("topology-device-shape");pane.getChildren().add(shape);}}
        return pane;
    }
}
