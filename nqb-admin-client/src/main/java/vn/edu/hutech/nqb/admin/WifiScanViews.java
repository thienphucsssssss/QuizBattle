package vn.edu.hutech.nqb.admin;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import vn.edu.hutech.nqb.client.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Offline-capable hardware check before collecting survey points. */
final class WifiScanViews {
    private WifiScanViews() { }
    static void open(ArcadeApplication app,ProximityClientService service){
        var dialog=new Dialog<ButtonType>();dialog.initOwner(app.ownerStage());dialog.setTitle("Chẩn đoán quét Wi-Fi");dialog.setHeaderText("Quét các BSSID xung quanh");styleDialog(dialog);dialog.setResizable(true);
        dialog.getDialogPane().setId("wifi-scan-dialog");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var adapters=new ComboBox<WifiScanProvider.Adapter>();adapters.setId("wifi-scan-adapter");adapters.setMaxWidth(Double.MAX_VALUE);
        var ssid=new TextField();ssid.setId("wifi-scan-ssid");ssid.setPromptText("HUTECH E1 · để trống để xem tất cả");
        var status=text("Đang đọc danh sách card Wi-Fi…","muted");status.setId("wifi-scan-status");
        var table=new TableView<WifiObservation>();table.setId("wifi-scan-table");table.setPrefHeight(210);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(text("Nhấn Quét để kiểm tra phần cứng.","muted"));
        column(table,"SSID",WifiObservation::ssid,125);column(table,"BSSID",WifiObservation::bssid,170);
        column(table,"RSSI",v->v.rssiDbm()+" dBm",100);
        column(table,"MHz / kênh",v->(v.frequencyKhz()/1000)+" / "+v.channel(),110);
        column(table,"Dữ liệu",v->v.fresh()?"Mới":v.ageMillis()<0?"Chưa rõ":"Cũ · "+(v.ageMillis()/1000)+"s",105);
        column(table,"Đang dùng",v->v.associated()?"Có":"—",95);
        var scan=new Button("Quét Wi-Fi");scan.setId("wifi-scan-start");scan.getStyleClass().add("quiet");scan.setDisable(true);
        var cancel=new Button("Hủy quét");cancel.setDisable(true);cancel.getStyleClass().add("quiet");
        var export=new Button("Xuất JSON");export.setId("wifi-scan-export");export.setDisable(true);export.getStyleClass().add("quiet");
        var state=new Object(){WifiScanSnapshot latest;CompletableFuture<WifiScanSnapshot> pending;};
        scan.setOnAction(event->{
            var adapter=adapters.getValue();if(adapter==null){status.setText("Chọn card Wi-Fi.");return;}
            scan.setDisable(true);cancel.setDisable(false);adapters.setDisable(true);ssid.setDisable(true);export.setDisable(true);table.getItems().clear();state.latest=null;
            status.setText("Đang chờ Windows quét… (tối đa 5 giây)");
            state.pending=service.scanAsync(adapter.id(),ssid.getText());
            state.pending.whenComplete((value,error)->Platform.runLater(()->{
                if(!dialog.isShowing())return;
                scan.setDisable(false);cancel.setDisable(true);adapters.setDisable(false);ssid.setDisable(false);
                if(error!=null){status.setText(error instanceof CancellationException?"Đã hủy quét.":ArcadeApplication.message(error));return;}
                state.latest=value;table.setItems(FXCollections.observableArrayList(value.observations()));export.setDisable(false);
                status.setText(value.freshCount()+" / "+value.observations().size()+" BSSID có dữ liệu mới · "+(value.completedAtMillis()-value.startedAtMillis())+" ms\n"+value.diagnostic()
                    +(value.omittedCount()>0?" Bỏ bớt "+value.omittedCount()+" mục do giới hạn 64.":"")
                    +(value.freshCount()>0?" Danh mục BSSID đã được lưu trên máy.":""));
            }));
        });
        cancel.setOnAction(e->{if(state.pending!=null)state.pending.cancel(false);});
        export.setOnAction(e->{
            if(state.latest==null)return;var chooser=new FileChooser();chooser.setTitle("Xuất chẩn đoán Wi-Fi");chooser.setInitialFileName("wifi-scan.json");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON","*.json"));
            var file=chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());if(file==null)return;var snapshot=state.latest;export.setDisable(true);
            CompletableFuture.runAsync(()->{try{WifiObservationCatalog.export(file.toPath(),snapshot);}catch(Exception failure){throw new CompletionException(failure);}})
                .whenComplete((v,error)->Platform.runLater(()->{if(dialog.isShowing()){export.setDisable(false);status.setText(error==null?"Đã xuất chẩn đoán: "+file.getName():ArcadeApplication.message(error));}}));
        });
        var note=text("Số BSSID không phải số AP vật lý.\nĐây là dữ liệu chẩn đoán, chưa phải mẫu khảo sát có tọa độ.","caption");note.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);note.setMaxWidth(Double.MAX_VALUE);
        status.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        var body=FxKit.column(12,field("Card Wi-Fi",adapters),field("Tên Wi-Fi cần khảo sát (SSID)",ssid),row(10,scan,cancel,export),status,table,note);
        var scroller=scroll(body);scroller.setPrefViewportWidth(760);scroller.setPrefViewportHeight(Math.max(280,Math.min(490,app.ownerStage().getHeight()-160)));
        dialog.getDialogPane().setContent(scroller);((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");app.trackProximityDialog(dialog);
        dialog.setOnHidden(e->{if(state.pending!=null)state.pending.cancel(false);});dialog.show();
        service.scanAdaptersAsync().whenComplete((values,error)->Platform.runLater(()->{
            if(!dialog.isShowing())return;
            if(error!=null){status.setText(ArcadeApplication.message(error));return;}
            adapters.setItems(FXCollections.observableArrayList(values));
            values.stream().filter(WifiScanProvider.Adapter::connected).findFirst().or(()->values.stream().findFirst()).ifPresent(adapters::setValue);
            scan.setDisable(values.isEmpty());status.setText(values.isEmpty()?"Không thấy card Wi-Fi hỗ trợ. Kiểm tra driver và WLAN AutoConfig.":"Sẵn sàng. Mỗi lần quét cách nhau ít nhất 5 giây.");
        }));
    }
    private static void column(TableView<WifiObservation> table,String name,Function<WifiObservation,String> display,int width){
        var col=new TableColumn<WifiObservation,String>(name);col.setCellValueFactory(v->new ReadOnlyStringWrapper(display.apply(v.getValue())));col.setMinWidth(width);col.setPrefWidth(width);table.getColumns().add(col);
        col.setCellFactory(c->new TableCell<>(){protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setText(empty?null:value);setTooltip(empty?null:new Tooltip(value));}});
    }
}
