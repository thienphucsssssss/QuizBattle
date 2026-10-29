package vn.edu.hutech.nqb.admin;

import javafx.animation.*;
import javafx.scene.control.*;
import javafx.util.Duration;

final class FreshMeasurementButton extends Button {
    FreshMeasurementButton(ArcadeApplication app){
        super("Làm mới số đo");setDisable(true);getStyleClass().add("quiet");setId("proximity-fresh-refresh");setTooltip(new Tooltip("Thu 5 mẫu Wi-Fi mới tại các máy học sinh"));
        setOnAction(e->app.refreshProximitySamples());
        var timer=new Timeline(new KeyFrame(Duration.millis(250),e->{var snap=app.latestProximitySnapshot();var refresh=snap.path("refresh");boolean busy=refresh.path("state").asText().equals("RUNNING");long cooldown=refresh.path("retry_after_ms").asLong()-app.measurementSnapshotAgeMs();
            boolean oldServer=snap.has("players")&&!snap.path("topology_supported").asBoolean();
            setText(oldServer?"Cần cập nhật Server":busy?"Đang đo "+refresh.path("completed").asInt()+"/"+refresh.path("total").asInt():"Làm mới số đo");
            setDisable(!app.canRefreshMeasurements()||busy||cooldown>0);
            setAccessibleText(getText()+(cooldown>0&&!busy?", chờ "+(cooldown/1000+1)+" giây":""));
        }));timer.setCycleCount(Timeline.INDEFINITE);FxKit.runWhenShowing(this,timer);
    }
}
