package vn.edu.hutech.nqb.admin;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.edu.hutech.nqb.client.*;
import java.util.*;
import java.util.concurrent.*;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Shared LAN selector for guest JOIN and manager LOGIN/REGISTER. No editable addresses. */
final class LanEntryPane extends VBox implements AutoCloseable {
    record Target(Endpoint endpoint,String roomCode) { }
    private static final ThreadPoolExecutor SCANS=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),Thread.ofPlatform().daemon().name("lan-search-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    private final boolean student;
    private final TextField code=new TextField();
    private final ListView<LanDirectory.Room> rooms=new ListView<>();
    private final ComboBox<LanDirectory.Server> servers=new ComboBox<>();
    private final Label status=text("Đang tìm trong LAN…","muted");
    private final Button refresh;
    private final Timeline timer;
    private List<LanDirectory.Server> verified=List.of();
    private CompletableFuture<Void> scanFinished=CompletableFuture.completedFuture(null);
    private Future<?> scan;
    private boolean codeMode,closed,busy;
    LanEntryPane(boolean student) {
        super(10);this.student=student;setId("lan-entry");
        refresh=button("Tìm lại","quiet",this::refresh);refresh.setId("lan-refresh");refresh.setMinWidth(Region.USE_PREF_SIZE);status.setId("lan-scan-status");
        code.setId("room-code-input");code.setPromptText("Mã 6 ký tự từ giảng viên");code.setAccessibleText("Mã phòng");
        code.setTextFormatter(new TextFormatter<String>(change->{change.setText(change.getText().toUpperCase(Locale.ROOT));return change.getControlNewText().matches("[A-Z0-9]{0,6}")?change:null;}));
        rooms.setId("lan-rooms");rooms.setPrefHeight(164);rooms.setMinHeight(110);rooms.setAccessibleText("Phòng thi trong LAN");
        rooms.sceneProperty().addListener((o,before,scene)->{
            rooms.prefHeightProperty().unbind();
            if(scene!=null)rooms.prefHeightProperty().bind(javafx.beans.binding.Bindings.when(scene.heightProperty().lessThan(720)).then(112).otherwise(164));
        });
        rooms.setPlaceholder(text("Chưa có phòng đang mở.","muted"));
        rooms.setCellFactory(list->new ListCell<>() {
            @Override protected void updateItem(LanDirectory.Room room,boolean empty) {
                super.updateItem(room,empty);setText(null);setGraphic(null);
                if(!empty&&room!=null) {
                    String state=room.joinable()?"Đang chờ":room.phase().equals("WAITING")?"Đã đủ người":room.phase().equals("FINISHED")?"Đã kết thúc":"Đang thi";
                    setGraphic(column(4,text(room.name(),"subtitle"),text(room.teacher()+" · "+room.players()+"/"+room.maxPlayers()+" người · "+state,"caption")));
                    setAccessibleText(room.name()+", "+room.teacher()+", "+state);setOpacity(room.joinable()?1:.65);
                }
            }
        });
        servers.setId("lan-servers");servers.setMaxWidth(Double.MAX_VALUE);servers.setPromptText("Chọn máy chủ trong LAN");
        if(student) {
            var group=new ToggleGroup();var browse=new ToggleButton("Phòng trong LAN");var byCode=new ToggleButton("Nhập mã phòng");
            browse.setToggleGroup(group);byCode.setToggleGroup(group);browse.setSelected(true);browse.getStyleClass().add("lan-mode");byCode.getStyleClass().add("lan-mode");
            browse.setId("lan-browse-mode");byCode.setId("lan-code-mode");
            var codeBox=field("Mã phòng",code);codeBox.setVisible(false);codeBox.setManaged(false);
            group.selectedToggleProperty().addListener((o,old,selected)->{
                if(selected==null){group.selectToggle(old);return;}codeMode=selected==byCode;
                rooms.setVisible(!codeMode);rooms.setManaged(!codeMode);codeBox.setVisible(codeMode);codeBox.setManaged(codeMode);
                if(codeMode)code.requestFocus();
            });
            getChildren().addAll(row(8,browse,byCode),rooms,codeBox);
        } else getChildren().add(field("Máy chủ trong LAN",servers));
        getChildren().add(row(12,status,space(),refresh));
        timer=new Timeline(new KeyFrame(Duration.seconds(4),e->{if(!busy)refresh();}));timer.setCycleCount(Animation.INDEFINITE);timer.play();refresh();
    }
    static void shutdownSearches(){SCANS.shutdownNow();}
    private void refresh() {
        if(closed||busy)return;busy=true;refresh.setDisable(true);status.setText("Đang tìm trong LAN…");scanFinished=new CompletableFuture<>();
        var pending=scanFinished;
        try {scan=SCANS.submit(()->{
            List<LanDirectory.Server> found=List.of();Throwable error=null;
            try {found=LanDirectory.scan(ClientConnectionProfile.load());}catch(Exception failure){error=failure;}
            var result=found;var failure=error;
            Platform.runLater(()->{
                if(closed){pending.completeExceptionally(new CancellationException());return;}
                busy=false;refresh.setDisable(false);verified=result;
                var selectedRoom=rooms.getSelectionModel().getSelectedItem();var selectedServer=servers.getValue();
                var updated=result.stream().flatMap(server->server.rooms().stream()).toList();
                if(!rooms.getItems().equals(updated))rooms.setItems(FXCollections.observableArrayList(updated));
                if(selectedRoom!=null)updated.stream().filter(room->room.serverId().equals(selectedRoom.serverId())&&room.code().equals(selectedRoom.code())).findFirst().ifPresent(room->rooms.getSelectionModel().select(room));
                if(!servers.getItems().equals(result)) {
                    servers.setValue(null);servers.setItems(FXCollections.observableArrayList(result));
                    if(selectedServer!=null)result.stream().filter(server->server.id().equals(selectedServer.id())).findFirst().ifPresent(servers::setValue);
                    if(servers.getValue()==null&&result.size()==1)servers.setValue(result.getFirst());
                }
                status.setText(failure instanceof IllegalStateException?failure.getMessage():result.isEmpty()?"Chưa tìm thấy máy chủ. Kiểm tra Server đã bật và cùng mạng.":student?(updated.isEmpty()?"Chờ giảng viên mở phòng.":updated.size()+" phòng trong LAN"):result.size()+" máy chủ sẵn sàng");
                pending.complete(null);
            });
        });}catch(RejectedExecutionException full){busy=false;refresh.setDisable(false);status.setText("Đang bận tìm kiếm. Thử lại sau một chút.");pending.completeExceptionally(new IllegalStateException(status.getText()));}
    }
    CompletableFuture<Target> resolveTarget() {
        if(closed)return CompletableFuture.failedFuture(new CancellationException());
        if(!busy&&verified.isEmpty())refresh();
        return scanFinished.thenApply(ignored->{
            if(closed)throw new CancellationException();
            if(!student) {
                var chosen=servers.getValue();if(chosen==null)throw new IllegalStateException(verified.isEmpty()?"Chưa tìm thấy máy chủ. Bật Server rồi nhấn Tìm lại.":"Chọn máy chủ để tiếp tục.");
                return new Target(chosen.endpoint(),"");
            }
            LanDirectory.Room room;
            if(codeMode) {
                String value=code.getText().strip();if(!value.matches("[A-Z2-9]{6}"))throw new IllegalStateException("Nhập đúng mã phòng gồm 6 ký tự.");
                var matches=verified.stream().flatMap(server->server.rooms().stream()).filter(r->r.code().equals(value)).toList();
                if(matches.isEmpty())throw new IllegalStateException("Không tìm thấy mã phòng. Kiểm tra mã hoặc nhấn Tìm lại.");
                if(matches.size()>1)throw new IllegalStateException("Có nhiều phòng cùng mã. Chọn đúng phòng trong danh sách LAN.");
                room=matches.getFirst();
            } else {room=rooms.getSelectionModel().getSelectedItem();if(room==null)throw new IllegalStateException("Chọn một phòng trong LAN hoặc chuyển sang Nhập mã phòng.");}
            if(!room.joinable())throw new IllegalStateException("Phòng đã đủ người hoặc đang thi. Hãy chọn phòng đang chờ.");
            return new Target(room.endpoint(),room.code());
        });
    }
    @Override public void close(){closed=true;timer.stop();if(scan!=null)scan.cancel(true);scanFinished.completeExceptionally(new CancellationException());}
}
