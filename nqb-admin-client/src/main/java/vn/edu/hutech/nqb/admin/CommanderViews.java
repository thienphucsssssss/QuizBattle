package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
    private TextField search;
    private ComboBox<String> filter;
    private Button start, abort, projection;
    private Label progressCaption, connections, round, actionHint, visibleCount, detailName, detailMeta, detailStats;
    private ProgressBar completion;
    private FlowPane leaders;
    private VBox leaderArea;
    private ScrollPane leaderScroll;
    private HBox detailIdentity;
    private final Map<CommanderModel.Filter, ToggleButton> quickButtons = new EnumMap<>(CommanderModel.Filter.class);
    private CommanderModel.Filter quickFilter = CommanderModel.Filter.ALL;
    private String leadersKey = "";
    private boolean updatingPlayers;
    private long deadline;

    CommanderViews(ArcadeApplication app) { this.app = app; }

    void open(String page) {
        this.page = page; players = null; search = null; filter = null; start = abort = projection = null;
        completion = null; leaders = null; detailIdentity = null; quickButtons.clear(); quickFilter = CommanderModel.Filter.ALL; leadersKey = "";
        layout = new BorderPane(); layout.setId("commander-workspace");
        if (page.equals("Giám sát thi đấu") || page.equals("Đấu trường thí sinh")) layout.getStyleClass().add("commander-live");
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
            case "Đấu trường thí sinh" -> arena();
            case "Cài đặt" -> settings();
            default -> throw new IllegalArgumentException("Unknown management page");
        }
        app.showManagement(page, layout); update(app.roomSnapshot()); load();
    }
    private static String description(String page) {
        return switch (page) {
            case "Tổng quan" -> "Ngân hàng của bạn và tình hình phòng thi trong mạng LAN.";
            case "Giám sát thi đấu" -> "Theo dõi kết nối, tiến độ nộp bài và điều khiển ván thi.";
            case "Đấu trường thí sinh" -> "Danh sách thí sinh, trạng thái tham gia và bảng điểm của phòng hiện tại.";
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
        players.getColumns().addAll(List.of(identityColumn(true), badgeColumn("Kết nối", p -> !app.managementConnected() ? "Chưa xác định" : p.path("connected").asBoolean() ? "Đang kết nối" : "Mất kết nối", 135),
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
    private void arena() {
        search = new TextField(); search.setId("arena-search"); search.setPromptText("Tìm thí sinh theo biệt danh…"); search.setAccessibleText("Tìm thí sinh");
        filter = new ComboBox<>(FXCollections.observableArrayList("Tất cả thí sinh","Đang kết nối","Mất kết nối","Sẵn sàng")); filter.setValue("Tất cả thí sinh"); filter.setAccessibleText("Lọc trạng thái thí sinh");
        summary.setId("arena-summary");
        filter.setId("arena-filter");
        var searchLabel = text("Tìm thí sinh", "caption"); searchLabel.setLabelFor(search);
        var statusLabel = text("Trạng thái", "caption"); statusLabel.setLabelFor(filter);
        var searchField = column(3, searchLabel, search); var statusField = column(3, statusLabel, filter); filter.setMaxWidth(Double.MAX_VALUE);
        var tools = row(12,searchField,statusField); HBox.setHgrow(searchField,Priority.ALWAYS); statusField.setMinWidth(170);
        tools.getStyleClass().add("commander-tools");
        leaders = new FlowPane(12,8); leaders.setId("arena-leaders"); leaders.getStyleClass().add("commander-leaders");
        leaderScroll = scroll(leaders); leaderScroll.setId("arena-leaders-scroll"); leaderScroll.setPrefHeight(64); leaderScroll.setMinHeight(0); leaderScroll.setMaxHeight(64);
        leaderArea = column(6,text("Đang dẫn đầu", "commander-section-title"),leaderScroll); leaderArea.setVisible(false); leaderArea.setManaged(false);
        players = table("arena-players");
        var rankColumn = numberColumn("Hạng", this::rank, 62); rankColumn.getStyleClass().add("commander-rank-cell");
        players.getColumns().addAll(List.of(rankColumn, identityColumn(false), badgeColumn("Trạng thái",this::playerStatus,150),
            numberColumn("Điểm",p -> p.path("score").asText("—"),85),numberColumn("Câu đúng",p -> p.path("correct_count").asText("—"),85)));
        players.getSelectionModel().selectedItemProperty().addListener((o,before,after) -> { if (!updatingPlayers) updateDetails(); });
        var content = new BorderPane(players); content.setTop(column(8,leaderArea,tools,summary)); BorderPane.setMargin(players,new Insets(12,0,0,0));layout.setCenter(content);
        detailName = text("Chọn thí sinh để xem chi tiết", "commander-player-name");
        detailName.setWrapText(false); detailName.setMaxWidth(Double.MAX_VALUE);
        detailMeta = text("Địa chỉ IP và kết nối được cung cấp theo quyền xem phòng.", "muted"); detailMeta.setId("arena-player-details");
        detailStats = text("", "muted"); detailStats.setId("arena-player-stats");
        var detailText = column(3, detailName, detailMeta); HBox.setHgrow(detailText, Priority.ALWAYS);
        detailStats.setMaxWidth(220); detailStats.setAlignment(Pos.CENTER_RIGHT);
        detailIdentity = row(16, detailText, detailStats); detailIdentity.getStyleClass().add("commander-player-detail");
        var note = text("Điểm và hạng cập nhật sau mỗi câu được chấm. Bộ lọc chỉ thay đổi danh sách đang xem.","muted");
        var footer = column(8, detailIdentity, note); footer.getStyleClass().add("commander-footer");layout.setBottom(footer);
        search.textProperty().addListener((o,before,after) -> updatePlayers()); filter.setOnAction(e -> updatePlayers());
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
        } else if (page.equals("Đấu trường thí sinh")) { updateLeaders(room); updatePlayers(); }
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
        var selectedFilter = filter == null ? quickFilter : switch (filter.getValue()) {
            case "Đang kết nối" -> CommanderModel.Filter.ONLINE;
            case "Mất kết nối" -> CommanderModel.Filter.OFFLINE;
            case "Sẵn sàng" -> CommanderModel.Filter.READY;
            default -> CommanderModel.Filter.ALL;
        };
        var values = CommanderModel.players(room, selectedFilter, search == null ? "" : search.getText(), page.equals("Đấu trường thí sinh"));
        String selectedId=players.getSelectionModel().getSelectedItem()==null?"":players.getSelectionModel().getSelectedItem().path("player_id").asText();
        updatingPlayers = true;
        if (!players.getItems().equals(values)) players.setItems(FXCollections.observableArrayList(values));
        // Phase and connection state can change while a player's JSON stays equal.
        // Recompute visible cell values that depend on the surrounding room state.
        players.refresh();
        values.stream().filter(p->p.path("player_id").asText().equals(selectedId)).findFirst().ifPresent(p->players.getSelectionModel().select(p));
        if (!selectedId.isEmpty() && values.stream().noneMatch(p -> p.path("player_id").asText().equals(selectedId))) players.getSelectionModel().clearSelection();
        updatingPlayers = false;
        players.setPlaceholder(text(room.path("players").isEmpty()?"Chưa có thí sinh tham gia. Mở Phòng thi để mời thí sinh.":"Không có thí sinh phù hợp. Thử bộ lọc hoặc biệt danh khác.","muted"));
        if(page.equals("Đấu trường thí sinh")) {
            summary.setText(values.size()+"/"+room.path("players").size()+" thí sinh · "+(app.managementConnected()?room.path("connected_player_count").asInt()+" đang kết nối":"Mất kết nối máy chủ · dữ liệu lần cập nhật cuối"));
            updateDetails();
        } else visibleCount.setText(values.size() + "/" + room.path("players").size() + " thí sinh");
    }
    private String playerStatus(JsonNode player) {
        return CommanderModel.status(app.roomSnapshot(), player, app.managementConnected());
    }
    private void updateLeaders(JsonNode room) {
        var entries = CommanderModel.leaders(room);
        String key = entries.toString();
        leaderArea.setVisible(!entries.isEmpty()); leaderArea.setManaged(!entries.isEmpty());
        leaderArea.setOpacity(app.managementConnected() ? 1 : .65);
        if (key.equals(leadersKey)) return;
        leadersKey = key; leaders.getChildren().clear();
        for (var entry : entries) {
            var name = text(entry.path("nickname").asText(), "commander-player-name"); name.setWrapText(false); name.setTooltip(new Tooltip(name.getText())); name.setMaxWidth(135);
            var identity = column(3, name, text(entry.path("score").asText("—") + " điểm", "commander-leader-score")); HBox.setHgrow(identity, Priority.ALWAYS);
            var place = text("#" + entry.path("rank").asInt(), "commander-leader-rank"); place.setWrapText(false); place.setMinWidth(34);
            var item = row(8, place, QuizlingArt.avatar(entry.path("avatar_id").asText(), 42), identity);
            item.getStyleClass().add("commander-leader"); item.setPrefWidth(212); item.setMaxWidth(212);
            item.setAccessibleText("Hạng " + entry.path("rank").asInt() + ", " + name.getText() + ", " + entry.path("score").asText("—") + " điểm");
            leaders.getChildren().add(item);
        }
        leaderScroll.setVvalue(0);
    }
    private void updateDetails() {
        if (detailIdentity == null) return;
        var player = players.getSelectionModel().getSelectedItem();
        detailStats.setVisible(player != null); detailStats.setManaged(player != null);
        if (player == null) {
            detailName.setText("Chọn thí sinh để xem chi tiết"); detailMeta.setText("Địa chỉ IP và kết nối được cung cấp theo quyền xem phòng.");
            return;
        }
        detailName.setText(player.path("nickname").asText()); detailName.setTooltip(new Tooltip(detailName.getText()));
        detailMeta.setText("IP: " + player.path("ip_address").asText("không được cung cấp") + " · " + playerStatus(player));
        detailStats.setText("Hạng " + rank(player) + " · " + player.path("score").asText("—") + " điểm · " + player.path("correct_count").asText("—") + " câu đúng" + (app.managementConnected() ? "" : " · dữ liệu lần cập nhật cuối"));
    }
    private String rank(JsonNode player) { int rank=CommanderModel.rank(app.roomSnapshot(), player);return rank==Integer.MAX_VALUE?"—":"#"+rank; }
    private TableColumn<JsonNode, JsonNode> identityColumn(boolean showIp) {
        var column = new TableColumn<JsonNode, JsonNode>(showIp ? "Thí sinh / Địa chỉ IP" : "Thí sinh");
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue())); column.setPrefWidth(260); column.setMinWidth(180); column.setSortable(false);
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(JsonNode player, boolean empty) {
                super.updateItem(player, empty); setText(null); setGraphic(null); setTooltip(null);
                if (empty || player == null) return;
                var name = text(player.path("nickname").asText(), "commander-player-name"); name.setWrapText(false); name.setMaxWidth(Double.MAX_VALUE);
                var identity = column(3, name); HBox.setHgrow(identity, Priority.ALWAYS);
                if (showIp) identity.getChildren().add(text(player.path("ip_address").asText("IP không được cung cấp"), "commander-ip"));
                var avatar = QuizlingArt.avatar(player.path("avatar_id").asText(), 40);
                setGraphic(row(10, avatar, identity)); setTooltip(new Tooltip(name.getText() + (showIp ? "\n" + player.path("ip_address").asText("IP không được cung cấp") : "")));
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
