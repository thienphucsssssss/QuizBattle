package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Function;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Management workspaces; values come from the authenticated server session. */
final class CommanderViews {
    private final ArcadeApplication app;
    private BorderPane layout;
    private String page = "";
    private Label phase, roomInfo, summary, submitted, questionCount, matches, timer;
    private TableView<JsonNode> players;
    private Button start, abort, projection;
    private Label progressCaption, connections, round, actionHint, visibleCount;
    private ProgressBar completion;
    private final Map<CommanderModel.Filter, ToggleButton> quickButtons = new EnumMap<>(CommanderModel.Filter.class);
    private CommanderModel.Filter quickFilter = CommanderModel.Filter.ALL;
    private long deadline;

    CommanderViews(ArcadeApplication app) { this.app = app; }

    void open(String page) {
        if (!List.of("Tổng quan", "Giám sát thi đấu", "Cài đặt").contains(page)) throw new IllegalArgumentException("Unknown management page");
        this.page = page; players = null; start = abort = projection = null;
        completion = null; quickButtons.clear(); quickFilter = CommanderModel.Filter.ALL;
        layout = new BorderPane(); layout.setId("commander-workspace");
        if (page.equals("Giám sát thi đấu")) layout.getStyleClass().add("commander-live");
        phase = text("Đang tải phòng…", "status"); roomInfo = text("", "muted"); summary = text("", "muted");
        submitted = text("", "subtitle"); timer = text("—", "subtitle");
        var refresh = button("Làm mới", "quiet", this::load);
        var heading = column(6, text(page, "title"), text(description(page), "muted"));
        HBox.setHgrow(heading, Priority.ALWAYS);
        var header = column(18, row(12, heading, space(), refresh), row(12, phase, roomInfo));
        header.setPadding(new Insets(0,0,20,0)); layout.setTop(header);
        switch (page) {
            case "Tổng quan" -> overview();
            case "Giám sát thi đấu" -> monitor();
            case "Cài đặt" -> settings();
            default -> throw new IllegalArgumentException("Unknown management page");
        }
        app.showManagement(page, layout); update(app.roomSnapshot()); load();
    }
    private static String description(String page) {
        return switch (page) {
            case "Tổng quan" -> "Ngân hàng của bạn và tình hình phòng thi trong mạng LAN.";
            case "Giám sát thi đấu" -> "Theo dõi kết nối, tiến độ nộp bài và điều khiển ván thi.";
            default -> "Quản lý tài khoản và tùy chọn trải nghiệm trên máy này.";
        };
    }
    private void overview() {
        questionCount = text("Đang tải…", "counter"); questionCount.setId("overview-questions");
        matches = text("Đang tải…", "counter"); matches.setId("overview-matches");
        var stats = new GridPane(); stats.setHgap(36); stats.setVgap(12); stats.getStyleClass().add("panel");
        stats.add(column(6, text("CÂU HỎI CỦA BẠN", "caption"), questionCount),0,0);
        stats.add(column(6, text("VÁN ĐÃ LƯU", "caption"), matches),1,0);
        stats.add(column(6, text("THÍ SINH TRONG PHÒNG", "caption"), submitted),2,0);
        var actions = new FlowPane(12,12, button("Quản lý phòng thi", "ink-button", app::roomView), button("Mở ngân hàng", "quiet", app::bankView), button("Theo dõi ván thi", "quiet", () -> open("Giám sát thi đấu")), button("Xem lịch sử & bảng điểm", "quiet", app::historyView));
        var body = column(24, stats, text("Phòng thi hiện tại", "subtitle"), summary, actions,
            text("Bắt đầu một buổi thi", "subtitle"), text("1. Chuẩn bị câu hỏi trong ngân hàng của bạn.\n2. Tạo phòng và gửi mã cho học sinh trong cùng mạng LAN.\n3. Cần ít nhất một thí sinh; chờ tất cả sẵn sàng rồi bắt đầu.", "muted"));
        layout.setCenter(scroll(body));
    }
    private void monitor() {
        players = table("monitor-players");
        players.getColumns().addAll(List.of(identityColumn(), badgeColumn("Kết nối", p -> !app.managementConnected() ? "Chưa xác định" : p.path("connected").asBoolean() ? "Đang kết nối" : "Mất kết nối", 135),
            badgeColumn("Tiến độ", this::playerStatus, 160), numberColumn("Điểm", p -> p.path("score").asText("—"), 80)));
        submitted.setId("monitor-submitted");
        submitted.getStyleClass().add("commander-value");
        timer.setId("monitor-timer"); timer.getStyleClass().add("commander-value");
        progressCaption = text("", "caption"); connections = text("", "muted"); connections.setId("monitor-connections");
        round = text("", "commander-round"); round.setId("monitor-round");
        completion = new ProgressBar(0); completion.setId("monitor-progress"); completion.setMaxWidth(Double.MAX_VALUE);
        completion.setAccessibleText("Tiến độ thí sinh trong phòng");
        var progressBody = column(4, progressCaption, submitted, completion, connections); HBox.setHgrow(progressBody, Priority.ALWAYS);
        var clock = column(3, text("Thời gian còn lại", "caption"), timer); clock.setAlignment(Pos.CENTER_RIGHT);
        var progress = row(24, progressBody, clock); progress.getStyleClass().add("commander-progress-panel");
        var filters = new FlowPane(8, 8); filters.getStyleClass().add("commander-quick-filters");
        var group = new ToggleGroup();
        for (var value : List.of(CommanderModel.Filter.ALL, CommanderModel.Filter.PENDING, CommanderModel.Filter.OFFLINE)) {
            var toggle = new ToggleButton(); toggle.getStyleClass().add("commander-filter"); toggle.setToggleGroup(group);
            toggle.setId("monitor-filter-" + value.name().toLowerCase(Locale.ROOT)); toggle.setSelected(value == quickFilter);
            toggle.setOnAction(e -> { quickFilter = value; toggle.setSelected(true); updatePlayers(); });
            quickButtons.put(value, toggle); filters.getChildren().add(toggle);
        }
        visibleCount = text("", "muted"); visibleCount.setId("monitor-visible-count"); filters.getChildren().add(visibleCount);
        filters.setAlignment(Pos.CENTER_LEFT);
        var top = column(12, row(12, round, space(), summary), progress, filters);
        var content = new BorderPane(players); content.setTop(top); BorderPane.setMargin(players,new Insets(12,0,0,0)); layout.setCenter(content);
        start = button("Bắt đầu ván thi", "ink-button", app::startManagedGame);
        start.setId("monitor-start");
        projection = button("Trình chiếu ↗", "quiet", app::openProjection);
        projection.setId("monitor-projection");
        abort = button("Hủy ván thi", "danger", app::abortManagedGame);
        abort.setId("monitor-abort");
        actionHint = text("", "muted"); actionHint.setId("monitor-action-hint");
        var primaryActions = new FlowPane(8,8,start,projection,button("Mở phòng thi", "quiet", app::roomView));
        var actions = new BorderPane(primaryActions); actions.setRight(abort); BorderPane.setMargin(abort,new Insets(0,0,0,20));
        var footer = column(8, actionHint, actions); footer.getStyleClass().add("commander-footer"); layout.setBottom(footer);
    }
    private void settings() {
        var form = column(18, text(app.displayName(),"subtitle"),text(app.accountRole().equals("ADMIN") ? "Quản trị viên" : "Giảng viên","muted"),new Separator(),
            text("Tài khoản", "subtitle"),text("Đổi mật khẩu đăng nhập. Bạn sẽ đăng nhập lại sau khi đổi thành công.","muted"),button("Đổi mật khẩu tài khoản","quiet",() -> app.passwordChange(false)),
            new Separator(),text("Trải nghiệm", "subtitle"),text("Điều chỉnh âm thanh, nhạc nền, âm lượng và chế độ giảm chuyển động.","muted"),button("Âm thanh & giao diện","ink-button",app::openSettings));
        form.setId("management-settings");form.setMaxWidth(640);layout.setCenter(scroll(form));
    }
    private void load() {
        BorderPane requested = layout;
        app.refreshManagedRoom(() -> { if (active(requested)) update(app.roomSnapshot()); });
        if (page.equals("Tổng quan")) {
            app.call("QUESTION_LIST",object().put("page",1).put("page_size",1),r -> {if(active(requested))questionCount.setText(r.path("total").asText("0"));},e -> {if(active(requested))questionCount.setText("Chưa tải được");});
            app.call("HISTORY_LIST",object().put("page",1).put("page_size",1),r -> {if(active(requested))matches.setText(r.path("total").asText("0"));},e -> {if(active(requested))matches.setText("Chưa tải được");});
        }
    }
    private boolean active(BorderPane requested) { return requested == layout && requested.getScene() != null; }
    void update(JsonNode room) {
        if (layout == null || layout.getScene() == null) return;
        String state = room.path("phase").asText("WAITING");
        phase.setText(!app.managementConnected() ? "Mất kết nối" : room.path("room_code").asText().isBlank() ? "Chưa có phòng thi" : phaseLabel(state));
        roomInfo.setText(room.path("room_code").asText().isBlank() ? "" : room.path("room_name").asText() + " · Mã " + room.path("room_code").asText());
        int online = room.path("connected_player_count").asInt(), ready = room.path("ready_count").asInt();
        deadline = System.nanoTime() + room.path("remaining_ms").asLong() * 1_000_000L;
        if (page.equals("Tổng quan")) {
            submitted.setText(online + " đang kết nối");
            summary.setText(room.path("room_code").asText().isBlank() ? "Chưa mở phòng. Tạo phòng để học sinh tham gia bằng mã hoặc danh sách LAN." : room.path("room_name").asText() + " · " + phaseLabel(state) + " · " + ready + "/" + online + " sẵn sàng");
        } else if (page.equals("Giám sát thi đấu")) {
            var counts = CommanderModel.counts(room); boolean waiting = CommanderModel.waiting(room), connected = app.managementConnected();
            boolean hasRoom = !room.path("room_code").asText().isBlank();
            progressCaption.setText(waiting ? "Thí sinh sẵn sàng" : "Bài nộp câu hiện tại");
            submitted.setText(!hasRoom || !connected ? "—" : (waiting ? counts.ready() : counts.answered()) + "/" + counts.total() + (waiting ? " sẵn sàng" : " đã nộp"));
            completion.setProgress(connected ? counts.progress(waiting) : 0);
            completion.setAccessibleText(submitted.getText());
            round.setText(!hasRoom ? "Chuẩn bị ván thi" : waiting ? "Phòng chờ · " + room.path("question_count").asInt() + " câu" : state.equals("COUNTDOWN") ? "Chuẩn bị bắt đầu" : "Câu " + room.path("question_number").asInt() + "/" + room.path("question_count").asInt());
            connections.setText(!connected ? "Mất kết nối máy chủ · đang giữ dữ liệu lần cập nhật cuối" : counts.online() + " đang kết nối · " + counts.offline() + " mất kết nối");
            summary.setText(!hasRoom ? "Chưa mở phòng. Mở Phòng thi để tạo phòng mới." : room.path("is_owner").asBoolean() ? "Bạn đang điều khiển phòng." : "Chỉ theo dõi · phòng do giảng viên khác tổ chức.");
            var controls = CommanderModel.controls(room, app.accountRole(), connected);
            start.setDisable(!controls.start()); abort.setDisable(!controls.abort()); projection.setDisable(!controls.projection());
            String reason = !connected ? "Kết nối lại máy chủ để điều khiển ván thi." : !hasRoom ? "Tạo phòng và mời thí sinh tham gia để bắt đầu." : !room.path("is_owner").asBoolean() ? "Chỉ chủ phòng được bắt đầu, hủy và trình chiếu ván thi." : !waiting ? state.equals("FINISHED") ? "Ván đã kết thúc. Mở Phòng thi để chuẩn bị ván mới." : "Ván đang diễn ra · bài nộp được cập nhật tự động." : counts.total() == 0 ? "Đang chờ thí sinh tham gia phòng." : counts.offline() > 0 ? "Chờ " + counts.offline() + " thí sinh kết nối lại trước khi bắt đầu." : counts.ready() < counts.total() ? "Chờ " + (counts.total() - counts.ready()) + " thí sinh sẵn sàng trước khi bắt đầu." : "Tất cả thí sinh đã sẵn sàng. Có thể bắt đầu ván thi.";
            actionHint.setText(reason); start.setTooltip(new Tooltip(reason));
            quickButtons.get(CommanderModel.Filter.ALL).setText("Tất cả · " + counts.total());
            quickButtons.get(CommanderModel.Filter.PENDING).setText((waiting ? "Chưa sẵn sàng" : "Chưa nộp") + " · " + (connected ? Integer.toString(waiting ? counts.total() - counts.ready() : counts.pending()) : "—"));
            quickButtons.get(CommanderModel.Filter.OFFLINE).setText("Mất kết nối · " + (connected ? Integer.toString(counts.offline()) : "—"));
            quickButtons.forEach((kind, toggle) -> toggle.setDisable(kind != CommanderModel.Filter.ALL && (!connected || !hasRoom || state.equals("COUNTDOWN"))));
            tick(); updatePlayers();
        }
    }
    void tick() {
        if(layout==null || layout.getScene()==null || !page.equals("Giám sát thi đấu"))return;
        String state=app.roomSnapshot().path("phase").asText();
        long seconds = (Math.max(0,deadline-System.nanoTime())+999_999_999L)/1_000_000_000L;
        timer.setText(!app.managementConnected() || !CommanderModel.running(state) ? "—" : seconds + " giây");
        timer.getStyleClass().remove("commander-timer-urgent");
        if (app.managementConnected() && state.equals("QUESTION_ACTIVE") && seconds <= 5) timer.getStyleClass().add("commander-timer-urgent");
    }
    private void updatePlayers() {
        if (players == null || layout.getScene() == null) return;
        JsonNode room = app.roomSnapshot();
        var values = CommanderModel.players(room, quickFilter);
        String selectedId=players.getSelectionModel().getSelectedItem()==null?"":players.getSelectionModel().getSelectedItem().path("player_id").asText();
        if (!players.getItems().equals(values)) players.setItems(FXCollections.observableArrayList(values));
        // Phase and connection state can change while a player's JSON stays equal.
        // Recompute visible cell values that depend on the surrounding room state.
        players.refresh();
        values.stream().filter(p->p.path("player_id").asText().equals(selectedId)).findFirst().ifPresent(p->players.getSelectionModel().select(p));
        if (!selectedId.isEmpty() && values.stream().noneMatch(p -> p.path("player_id").asText().equals(selectedId))) players.getSelectionModel().clearSelection();
        players.setPlaceholder(text(room.path("players").isEmpty()?"Chưa có thí sinh tham gia. Mở Phòng thi để mời thí sinh.":"Không có thí sinh phù hợp. Thử bộ lọc khác.","muted"));
        visibleCount.setText(values.size() + "/" + room.path("players").size() + " thí sinh");
    }
    private String playerStatus(JsonNode player) {
        return CommanderModel.status(app.roomSnapshot(), player, app.managementConnected());
    }
    private TableColumn<JsonNode, JsonNode> identityColumn() {
        var column = new TableColumn<JsonNode, JsonNode>("Thí sinh / Địa chỉ IP");
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue())); column.setPrefWidth(260); column.setMinWidth(180); column.setSortable(false);
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(JsonNode player, boolean empty) {
                super.updateItem(player, empty); setText(null); setGraphic(null); setTooltip(null);
                if (empty || player == null) return;
                var name = text(player.path("nickname").asText(), "commander-player-name"); name.setWrapText(false); name.setMaxWidth(Double.MAX_VALUE);
                var identity = column(3, name); HBox.setHgrow(identity, Priority.ALWAYS);
                identity.getChildren().add(text(player.path("ip_address").asText("IP không được cung cấp"), "commander-ip"));
                var avatar = QuizlingArt.avatar(player.path("avatar_id").asText(), 40);
                setGraphic(row(10, avatar, identity)); setTooltip(new Tooltip(name.getText() + "\n" + player.path("ip_address").asText("IP không được cung cấp")));
            }
        });
        return column;
    }
    private TableColumn<JsonNode, String> badgeColumn(String title, Function<JsonNode,String> value, double width) {
        var column = col(title, value, width); column.setMinWidth(105);
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(null);
                if (empty || value == null) return;
                String tone = !app.managementConnected() ? "unknown" : value.equals("Mất kết nối") ? "offline" : List.of("Sẵn sàng","Đã nộp bài","Đang kết nối").contains(value) ? "success" : List.of("Đang chuẩn bị","Đang trả lời","Không nộp câu này").contains(value) ? "pending" : "unknown";
                setGraphic(text(value, "commander-badge", tone));
            }
        });
        return column;
    }
    private static TableColumn<JsonNode,String> numberColumn(String title, Function<JsonNode,String> value, double width) {
        var column = col(title, value, width); column.setMinWidth(60); column.getStyleClass().add("commander-number-cell"); return column;
    }
    private static String phaseLabel(String phase) { return switch(phase) { case "COUNTDOWN" -> "Sắp bắt đầu";case "QUESTION_ACTIVE" -> "Đang thi";case "SHOW_RESULT" -> "Đang chấm câu";case "SHOW_RANKING" -> "Bảng xếp hạng";case "FINISHED" -> "Đã kết thúc";default -> "Phòng chờ";}; }
    private static TableView<JsonNode> table(String id) { var table=new TableView<JsonNode>();table.setId(id);table.getStyleClass().add("commander-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setMinHeight(120);return table; }
    private static TableColumn<JsonNode,String> col(String title, Function<JsonNode,String> value,double width) { var col=new TableColumn<JsonNode,String>(title);col.setPrefWidth(width);col.setSortable(false);col.setCellValueFactory(c->new ReadOnlyStringWrapper(value.apply(c.getValue())));return col; }
}
