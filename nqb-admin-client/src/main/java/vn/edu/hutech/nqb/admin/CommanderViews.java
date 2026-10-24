package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
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
    private long deadline;

    CommanderViews(ArcadeApplication app) { this.app = app; }

    void open(String page) {
        this.page = page; players = null; search = null; filter = null; start = abort = projection = null;
        layout = new BorderPane(); layout.setId("commander-workspace");
        phase = text("Đang tải phòng…", "status"); roomInfo = text("", "muted"); summary = text("", "muted");
        submitted = text("", "subtitle"); timer = text("—", "subtitle");
        var refresh = button("Làm mới", "quiet", this::load);
        var heading = column(6, text(page, "title"), text(description(page), "muted"));
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
            text("Bắt đầu một buổi thi", "subtitle"), text("1. Chuẩn bị câu hỏi trong ngân hàng của bạn.\n2. Tạo phòng và gửi mã cho học sinh trong cùng mạng LAN.\n3. Chờ ít nhất hai thí sinh sẵn sàng rồi bắt đầu.", "muted"));
        layout.setCenter(scroll(body));
    }
    private void monitor() {
        players = table("monitor-players");
        players.getColumns().addAll(List.of(col("Thí sinh", p -> p.path("nickname").asText(), 210), col("Kết nối", p -> !app.managementConnected() ? "Chưa xác định" : p.path("connected").asBoolean() ? "Đang kết nối" : "Mất kết nối", 135),
            col("Tiến độ", this::playerStatus, 170), col("Điểm", p -> p.path("score").asText("—"), 90)));
        submitted.setId("monitor-submitted");
        var progress = row(20, column(4,text("TIẾN ĐỘ", "caption"),submitted), space(), column(4,text("THỜI GIAN CÒN LẠI", "caption"),timer));
        progress.getStyleClass().add("panel");
        var content = new BorderPane(players); content.setTop(column(14, progress, summary)); BorderPane.setMargin(players,new Insets(16,0,0,0)); layout.setCenter(content);
        start = button("Bắt đầu ván thi", "ink-button", app::startManagedGame);
        projection = button("Trình chiếu ↗", "quiet", app::openProjection);
        abort = button("Hủy ván thi", "danger", app::abortManagedGame);
        var actions = new FlowPane(12,12,start,projection,button("Mở phòng thi", "quiet", app::roomView),abort); actions.setPadding(new Insets(16,0,0,0)); layout.setBottom(actions);
    }
    private void arena() {
        search = new TextField(); search.setId("arena-search"); search.setPromptText("Tìm thí sinh theo biệt danh…"); search.setAccessibleText("Tìm thí sinh");
        filter = new ComboBox<>(FXCollections.observableArrayList("Tất cả thí sinh","Đang kết nối","Mất kết nối","Sẵn sàng")); filter.setValue("Tất cả thí sinh"); filter.setAccessibleText("Lọc trạng thái thí sinh");
        summary.setId("arena-summary");
        var tools = row(12,search,filter); HBox.setHgrow(search,Priority.ALWAYS);
        players = table("arena-players"); players.getColumns().addAll(List.of(col("Hạng", this::rank,65), col("Thí sinh", p -> p.path("nickname").asText(),220),
            col("Trạng thái",this::playerStatus,160),col("Điểm",p -> p.path("score").asText("—"),85),col("Câu đúng",p -> p.path("correct_count").asText("—"),85)));
        var content = new BorderPane(players); content.setTop(column(12,tools,summary)); BorderPane.setMargin(players,new Insets(16,0,0,0));layout.setCenter(content);
        var note = text("Điểm và thứ hạng cập nhật sau khi Server chấm câu hỏi. Lọc danh sách không ảnh hưởng thí sinh đang thi.","muted");note.setPadding(new Insets(16,0,0,0));layout.setBottom(note);
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
            submitted.setText(state.equals("WAITING") ? ready + "/" + online + " sẵn sàng" : room.path("answered_count").asInt() + "/" + online + " đã nộp");
            summary.setText(room.path("room_code").asText().isBlank() ? "Chưa mở phòng. Mở Phòng thi để tạo phòng mới." : "Câu " + room.path("question_number").asInt() + "/" + room.path("question_count").asInt() + " · " + (room.path("is_owner").asBoolean() ? "Bạn đang điều khiển phòng." : "Chỉ theo dõi · phòng do giảng viên khác tổ chức."));
            boolean owner = room.path("is_owner").asBoolean() && app.managementConnected();
            start.setDisable(!owner || !state.equals("WAITING") || online < 2 || ready != online || online != room.path("players").size());
            abort.setDisable(!owner || !running(state)); projection.setDisable(!owner);
            tick(); updatePlayers();
        } else if (page.equals("Đấu trường thí sinh")) updatePlayers();
    }
    void tick() {
        if(layout==null || layout.getScene()==null || !page.equals("Giám sát thi đấu"))return;
        String state=app.roomSnapshot().path("phase").asText();
        timer.setText(!app.managementConnected() || !running(state) ? "—" : ((Math.max(0,deadline-System.nanoTime())+999_999_999L)/1_000_000_000L)+" giây");
    }
    private void updatePlayers() {
        if (players == null || layout.getScene() == null) return;
        JsonNode room = app.roomSnapshot(); var values = new ArrayList<JsonNode>();
        String keyword=search==null?"":vn.edu.hutech.nqb.client.TextKey.of(search.getText());
        for (JsonNode player : room.path("players")) {
            if(!keyword.isEmpty()&&!vn.edu.hutech.nqb.client.TextKey.of(player.path("nickname").asText()).contains(keyword))continue;
            String selected=filter==null?"Tất cả thí sinh":filter.getValue();
            boolean online=player.path("connected").asBoolean();
            if(selected.equals("Đang kết nối")&&!online || selected.equals("Mất kết nối")&&online || selected.equals("Sẵn sàng")&&(!online||!player.path("ready").asBoolean()||!room.path("phase").asText().equals("WAITING")))continue;
            values.add(player);
        }
        String selectedId=players.getSelectionModel().getSelectedItem()==null?"":players.getSelectionModel().getSelectedItem().path("player_id").asText();
        if(page.equals("Đấu trường thí sinh"))values.sort(Comparator.comparingInt((JsonNode p)->rankValue(p)).thenComparing(p->p.path("nickname").asText()));
        players.setItems(FXCollections.observableArrayList(values));
        // Phase and connection state can change while a player's JSON stays equal.
        // Recompute visible cell values that depend on the surrounding room state.
        players.refresh();
        values.stream().filter(p->p.path("player_id").asText().equals(selectedId)).findFirst().ifPresent(p->players.getSelectionModel().select(p));
        players.setPlaceholder(text(room.path("players").isEmpty()?"Chưa có thí sinh tham gia phòng.":"Không tìm thấy thí sinh phù hợp.","muted"));
        if(page.equals("Đấu trường thí sinh"))summary.setText(values.size()+"/"+room.path("players").size()+" thí sinh · "+(app.managementConnected()?room.path("connected_player_count").asInt()+" đang kết nối":"Mất kết nối máy chủ · dữ liệu lần cập nhật cuối"));
    }
    private String playerStatus(JsonNode player) {
        if(!app.managementConnected())return "Chưa xác định · mất kết nối";
        if(!player.path("connected").asBoolean())return "Mất kết nối";
        return switch(app.roomSnapshot().path("phase").asText("WAITING")) {
            case "WAITING" -> player.path("ready").asBoolean()?"Sẵn sàng":"Đang chuẩn bị";
            case "COUNTDOWN" -> "Sắp bắt đầu";
            case "QUESTION_ACTIVE" -> player.path("answered").asBoolean()?"Đã nộp bài":"Đang trả lời";
            case "FINISHED" -> "Đã kết thúc";
            default -> player.path("answered").asBoolean()?"Đã nộp bài":"Không nộp câu này";
        };
    }
    private int rankValue(JsonNode player) {
        for(JsonNode r:app.roomSnapshot().path("ranking"))if(r.path("player_id").asText().equals(player.path("player_id").asText()))return r.path("rank").asInt(Integer.MAX_VALUE);
        return Integer.MAX_VALUE;
    }
    private String rank(JsonNode player) { int rank=rankValue(player);return rank==Integer.MAX_VALUE?"—":"#"+rank; }
    private static boolean running(String phase) { return List.of("COUNTDOWN","QUESTION_ACTIVE","SHOW_RESULT","SHOW_RANKING").contains(phase); }
    private static String phaseLabel(String phase) { return switch(phase) { case "COUNTDOWN" -> "Sắp bắt đầu";case "QUESTION_ACTIVE" -> "Đang thi";case "SHOW_RESULT" -> "Đang chấm câu";case "SHOW_RANKING" -> "Bảng xếp hạng";case "FINISHED" -> "Đã kết thúc";default -> "Phòng chờ";}; }
    private static TableView<JsonNode> table(String id) { var table=new TableView<JsonNode>();table.setId(id);table.getStyleClass().add("commander-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setMinHeight(120);return table; }
    private static TableColumn<JsonNode,String> col(String title, Function<JsonNode,String> value,double width) { var col=new TableColumn<JsonNode,String>(title);col.setPrefWidth(width);col.setSortable(false);col.setCellValueFactory(c->new ReadOnlyStringWrapper(value.apply(c.getValue())));return col; }
}
