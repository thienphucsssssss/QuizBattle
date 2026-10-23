package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.*;
import javafx.application.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.*;
import javafx.util.Duration;
import vn.edu.hutech.nqb.client.*;
import vn.edu.hutech.nqb.shared.AvatarCatalog;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** JavaFX arcade workspace. All real actions cross QuizGateway; no local scoring. */
public final class ArcadeApplication extends Application {
    private Stage stage, projection;
    private Scene scene;
    private final BorderPane root = new BorderPane(), workspace = new BorderPane();
    private final Label feedback = text("", "feedback");
    private final Label connectionLabel = text("Chưa kết nối", "muted");
    private final SfxSlots sfx = new SfxSlots();
    private final MusicSlots music = new MusicSlots();
    private final Preferences prefs = Preferences.userNodeForPackage(ArcadeApplication.class);
    private QuizGateway gateway;
    private LanEntryPane lanEntry;
    private record OpenedConnection(LanEntryPane.Target target,TlsQuizGateway gateway) { }
    private JsonNode user = object(), room = object(), question = object(), result = object();
    private String role = "", route = "", session = "", nickname = "";
    private volatile int connectionGeneration;
    private int bankPage = 1, userPage = 1, historyPage = 1;
    private String bankKeyword = "", bankCategory = "Tất cả chủ đề", bankStatus = "Tất cả trạng thái", bankSort = "Mới cập nhật";
    private String userKeyword = "", userStatus = "Tất cả trạng thái";
    private AnswerState answer;
    private Timeline pulse;
    private long phaseDeadline;
    private boolean disconnected, reducedMotion;
    private String celebratedClientSession="",celebratedProjectionSession="";
    private FadeTransition transition;
    private Label timerLabel, countdownLabel, ackLabel, progressLabel, projectionTimer;
    private ProgressBar timerBar;
    private final List<Button> answerButtons = new ArrayList<>();
    private Button readyButton;
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private boolean qa;
    private boolean passwordRequired;
    private static volatile Throwable qaFailure;
    static Consumer<ArcadeApplication> qaDriver;
    private JsonNode previousRanking = object().putArray("ranking"), currentRanking = object().putArray("ranking");
    private long rankedQuestion = -1;
    private Dialog<ButtonType> avatarDialog;
    private FlowPane avatarGrid;
    private Label avatarError;
    private boolean avatarPending, chooseAvatarAfterJoin;

    public static void launchFor(String entry, String[] arguments) {
        var values = new ArrayList<String>(); values.add("--entry=" + entry); values.addAll(List.of(arguments));
        Application.launch(ArcadeApplication.class, values.toArray(String[]::new));
        if (qaFailure != null) throw new IllegalStateException("JavaFX UI QA failed", qaFailure);
    }
    @Override public void start(Stage stage) {
        this.stage = stage; loadFonts();
        reducedMotion = prefs.getBoolean("reducedMotion", false);
        var args = getParameters().getNamed(); qa = qaDriver != null;
        sfx.configure(args.getOrDefault("entry", "MANAGE").equals("STUDENT") ? "STUDENT" : "TEACHER", qa);
        music.configure(args.getOrDefault("entry", "MANAGE").equals("STUDENT") ? "STUDENT" : "TEACHER", qa);
        double width = Double.parseDouble(args.getOrDefault("width", "1366"));
        double height = Double.parseDouble(args.getOrDefault("height", "768"));
        scene = new Scene(root, width, height); scene.getStylesheets().add(CSS);
        scene.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (e.getTarget() instanceof Button button && !button.getStyleClass().contains("answer")) sfx.play(SfxSlots.Cue.UI_PRESS);
            else if (e.getTarget() instanceof ComboBox<?> || e.getTarget() instanceof CheckBox) sfx.play(SfxSlots.Cue.UI_SELECT);
        });
        stage.setScene(scene); stage.setTitle("Network Quiz Battle"); stage.setMinWidth(1024); stage.setMinHeight(640);
        stage.setOnCloseRequest(event -> {
            if (isRunning() && !confirm("Rời ứng dụng?", "Bạn sẽ mất kết nối. Ván trên Server không tự dừng.")) event.consume();
        });
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11 && !role.equals("STUDENT") && gateway != null) { showProjection(); event.consume(); return; }
            if (scene.getFocusOwner() instanceof TextInputControl) return;
            if (role.equals("STUDENT") && route.equals("GAME") && stage.isFocused()) {
                String key = event.getCode().getName().toUpperCase(Locale.ROOT);
                if (key.matches("[ABCD]")) { submitAnswer(key); event.consume(); }
            }
        });
        welcome(); stage.show();
        if (qaDriver != null) qaDriver.accept(this);
    }
    @Override public void stop() { if(lanEntry!=null)lanEntry.close(); LanEntryPane.shutdownSearches(); closeGateway(); sfx.close(); music.close(); if (pulse != null) pulse.stop(); if (projection != null) projection.close(); }
    private void closeGateway() { music.stop(); connectionGeneration++; if (avatarDialog != null) avatarDialog.close(); avatarPending = false; if (gateway != null) { gateway.close(); gateway = null; } }
    private void welcome() {
        if (pulse != null) pulse.stop();
        if (projection != null) projection.close();
        closeGateway(); user = object(); room = object(); question = object(); result = object();
        answer = null; session = ""; nickname = ""; role = ""; route = ""; passwordRequired = false;
        disconnected = false; chooseAvatarAfterJoin = false;
        bankPage = userPage = historyPage = 1; bankKeyword = userKeyword = "";
        bankCategory = "Tất cả chủ đề"; bankStatus = userStatus = "Tất cả trạng thái"; bankSort = "Mới cập nhật";
        previousRanking = object().putArray("ranking"); currentRanking = object().putArray("ranking"); rankedQuestion = -1;
        connectionLabel.setText("Chưa kết nối"); connectionLabel.setTooltip(null); navButtons.clear();
        auth("");
    }
    private void auth(String selectedRole) { auth(selectedRole, false); }
    private void auth(String selectedRole, boolean registering) {
        if(lanEntry!=null){lanEntry.close();lanEntry=null;}
        closeGateway(); if (pulse != null) pulse.stop(); if (projection != null) projection.close(); room = object(); answer = null; passwordRequired = false;
        user = object();
        role = selectedRole; route = "Đăng nhập"; connectionLabel.setText("Chưa kết nối"); sfx.configure(role, qa); music.configure(role, qa); session = ""; question = object(); result = object(); root.setLeft(null); root.setTop(row(16, brand(), space(), button("← Vai trò", "quiet", this::welcome))); ((HBox)root.getTop()).setPadding(new Insets(24, 36, 16, 36));
        root.setBottom(null); root.getStyleClass().removeAll("game", "welcome");
        var who = new ComboBox<String>(FXCollections.observableArrayList("Học sinh", "Giảng viên", "Admin"));
        who.setId("login-role"); who.setPromptText("Chọn vai trò của bạn"); who.setMaxWidth(Double.MAX_VALUE);
        if (!selectedRole.isEmpty()) who.setValue(selectedRole.equals("STUDENT") ? "Học sinh" : selectedRole.equals("ADMIN") ? "Admin" : "Giảng viên");
        who.setOnAction(e -> auth(who.getValue().equals("Học sinh") ? "STUDENT" : who.getValue().equals("Admin") ? "ADMIN" : "TEACHER"));
        if(!selectedRole.isEmpty())lanEntry=new LanEntryPane(selectedRole.equals("STUDENT"));
        TextField identity = new TextField(); identity.setPromptText(selectedRole.equals("STUDENT") ? "Biệt danh của bạn" : "Tên đăng nhập");
        identity.setId("identity");
        PasswordField password = new PasswordField(); TextField visible = new TextField(); visible.textProperty().bindBidirectional(password.textProperty());
        password.setId("login-password");
        visible.setVisible(false); visible.setManaged(false); CheckBox show = new CheckBox("Hiện mật khẩu"); show.setOnAction(e -> { visible.setVisible(show.isSelected()); visible.setManaged(show.isSelected()); password.setVisible(!show.isSelected()); password.setManaged(!show.isSelected()); });
        TextField displayName = new TextField(); displayName.setId("register-name");
        PasswordField confirmation = new PasswordField(); confirmation.setId("register-confirm");
        Label nameError = authFieldError(), identityError = authFieldError(), passwordError = authFieldError(), confirmationError = authFieldError();
        var validators = Map.<TextInputControl, Runnable>of(
            displayName, () -> authFieldError(nameError, displayName.getText().isBlank() || displayName.getText().strip().length() > 80 ? "Nhập tên hiển thị từ 1–80 ký tự." : ""),
            identity, () -> authFieldError(identityError, !identity.getText().strip().matches("(?i)[a-z0-9_.-]{3,40}") ? "Dùng 3–40 ký tự: a–z, 0–9, _, . hoặc -." : ""),
            password, () -> authFieldError(passwordError, password.getText().length() < 8 || password.getText().length() > 128 ? "Mật khẩu cần 8–128 ký tự." : ""),
            confirmation, () -> authFieldError(confirmationError, !confirmation.getText().equals(password.getText()) ? "Mật khẩu nhập lại chưa khớp." : ""));
        if (registering) validators.forEach((control, validate) -> control.focusedProperty().addListener((o, before, focused) -> { if (!focused && !control.getText().isEmpty()) validate.run(); }));
        Label error = text("", "feedback", "error"); error.setVisible(false); error.setManaged(false);
        var input = column(8, password, visible, show);
        var form = column(16, text(registering ? "Tạo tài khoản giảng viên" : selectedRole.equals("STUDENT") ? "Vào phòng thi" : selectedRole.equals("ADMIN") ? "Đăng nhập Admin" : selectedRole.equals("TEACHER") ? "Đăng nhập giảng viên" : "Đăng nhập Quiz Battle", "title"));
        if (!registering) form.getChildren().add(field("Bạn là ai?", who));
        if(lanEntry!=null)form.getChildren().add(lanEntry);
        if (registering) form.getChildren().add(column(4, field("Tên hiển thị", displayName), nameError));
        if (!selectedRole.isEmpty()) form.getChildren().add(column(4, field(selectedRole.equals("STUDENT") ? "Biệt danh" : "Tên đăng nhập", identity), identityError));
        if (!selectedRole.isEmpty()&&!selectedRole.equals("STUDENT")) form.getChildren().add(column(7, text("Mật khẩu", "caption"), input, passwordError));
        if (registering) form.getChildren().add(column(4, field("Nhập lại mật khẩu", confirmation), confirmationError));
        form.getChildren().add(error);
        var connect = new Button(registering ? "Tạo tài khoản   →" : selectedRole.equals("STUDENT") ? "Tham gia phòng   →" : "Đăng nhập   →"); connect.getStyleClass().add("ink-button"); connect.setMaxWidth(Double.MAX_VALUE); connect.setDefaultButton(true);
        connect.setId("login-submit"); connect.setDisable(selectedRole.isEmpty());
        connect.setOnAction(e -> {
            if (registering) {
                validators.values().forEach(Runnable::run);
                if (nameError.isVisible()) { displayName.requestFocus(); return; }
                if (identityError.isVisible()) { identity.requestFocus(); return; }
                if (passwordError.isVisible()) { password.requestFocus(); return; }
                if (confirmationError.isVisible()) { confirmation.requestFocus(); return; }
            }
            if (identity.getText().isBlank() || (!selectedRole.equals("STUDENT") && password.getText().isEmpty())) { showError(error, "Điền đầy đủ thông tin trước khi kết nối."); return; }
            String loginSecret = password.getText(); String username = identity.getText().strip(), name = displayName.getText().strip();
            error.setVisible(false); error.setManaged(false); connect.setDisable(true); connect.setText("Đang kết nối…");
            int token = ++connectionGeneration;
            lanEntry.resolveTarget().thenCompose(target->CompletableFuture.supplyAsync(() -> {
                char[] secret = null;
                try {
                    var profile = ClientConnectionProfile.load();
                    if (!java.nio.file.Files.isRegularFile(profile.truststore())) throw new IllegalStateException("Bộ cài chưa có chứng chỉ của máy chủ. Nhận bộ cài Client từ Admin.");
                    secret = profile.truststorePassword().toCharArray();
                    return new OpenedConnection(target,TlsQuizGateway.connect(target.endpoint(), profile.truststore(), secret));
                }
                catch (Exception failure) { throw new CompletionException(failure); }
                finally { if (secret != null) Arrays.fill(secret, '\0'); }
            })).whenComplete((opened, failure) -> Platform.runLater(() -> {
                var newGateway=opened==null?null:opened.gateway();
                if (token != connectionGeneration) { if (newGateway != null) newGateway.close(); return; }
                if (failure != null) { connect.setDisable(false); connect.setText(selectedRole.equals("STUDENT")?"Tham gia phòng   →":"Thử kết nối lại"); showError(error, unwrap(failure) instanceof IllegalStateException ? message(failure) : "Không kết nối được máy chủ. Kiểm tra Server đã bật, cùng mạng và dùng đúng bộ cài Client."); return; }
                gateway = newGateway; attachEvents(); disconnected = false;
                ObjectNode payload = object(); if (selectedRole.equals("STUDENT")) payload.put("nickname", username).put("room_code", opened.target().roomCode()); else payload.put("username", username).put("password", loginSecret);
                var accountCreated = new java.util.concurrent.atomic.AtomicBoolean();
                var registration = registering ? newGateway.request("REGISTER", payload.deepCopy().put("display_name", name)).thenApply(registered -> { accountCreated.set(true); return registered; }) : CompletableFuture.<JsonNode>completedFuture(object());
                registration.thenCompose(registered -> {
                    if (token != connectionGeneration) return CompletableFuture.<JsonNode>failedFuture(new CancellationException());
                    return newGateway.request(selectedRole.equals("STUDENT") ? "JOIN" : "LOGIN", payload);
                }).whenComplete((response, loginFailure) -> Platform.runLater(() -> {
                    payload.remove("password");
                    if (token != connectionGeneration) return;
                    if (loginFailure != null) {
                        closeGateway(); connect.setDisable(false);
                        if (accountCreated.get()) {
                            password.clear(); confirmation.clear(); showError(error, "Đã tạo tài khoản. Hãy quay lại đăng nhập để tiếp tục.");
                            connect.setText("Đăng nhập   →"); connect.setOnAction(retry -> auth("TEACHER"));
                        } else { showError(error, message(loginFailure)); connect.setText("Thử lại"); }
                        return;
                    }
                    password.clear(); confirmation.clear();
                    role = selectedRole.equals("STUDENT") ? "STUDENT" : response.path("role").asText();
                    if (!List.of("STUDENT", "TEACHER", "ADMIN").contains(role) || !role.equals(selectedRole)) { closeGateway(); showError(error, "Tài khoản không thuộc vai trò đã chọn. Hãy chọn đúng vai trò để đăng nhập."); connect.setDisable(false); connect.setText("Đăng nhập   →"); return; }
                    user = response; nickname = selectedRole.equals("STUDENT") ? username : nickname;
                    sfx.configure(role, qa); music.configure(role, qa); sfx.play(SfxSlots.Cue.CONNECTION_UP);
                    chooseAvatarAfterJoin = selectedRole.equals("STUDENT");
                    passwordRequired = response.path("must_change_password").asBoolean();
                    connectionLabel.setText("Đã kết nối an toàn"); shell();
                    if (response.path("must_change_password").asBoolean()) passwordChange(true); else roomView();
                }));
            }));
        });
        form.getChildren().add(connect);
        if (registering) form.getChildren().add(button("Đã có tài khoản? Đăng nhập", "quiet", () -> auth("TEACHER")));
        else if (selectedRole.isEmpty() || selectedRole.equals("TEACHER")) {
            var register = button("Tạo tài khoản giảng viên", "quiet", () -> auth("TEACHER", true)); register.setId("register-open"); form.getChildren().add(register);
        }
        form.getChildren().add(button("Tùy chọn", "quiet", this::settings));
        form.setMaxWidth(540); form.setPadding(new Insets(24)); var centered = new StackPane(form); centered.setPadding(new Insets(16, 24, 36, 24)); root.setCenter(scroll(centered));
    }
    private static void showError(Label label, String value) { label.setText(value); label.setVisible(true); label.setManaged(true); }
    private static Label authFieldError() { var label = text("", "caption", "error"); label.setVisible(false); label.setManaged(false); return label; }
    private static void authFieldError(Label label, String message) { label.setText(message); label.setVisible(!message.isEmpty()); label.setManaged(!message.isEmpty()); }
    private void attachEvents() {
        int token = connectionGeneration;
        gateway.onEvent(event -> Platform.runLater(() -> { if (token == connectionGeneration) event(event); }));
    }
    private void shell() {
        if (gateway == null || user.isEmpty()) { welcome(); return; }
        if(lanEntry!=null){lanEntry.close();lanEntry=null;}
        root.setCenter(workspace); root.setBottom(null); root.setLeft(null); root.getStyleClass().removeAll("game", "welcome");
        feedback.setVisible(false); feedback.setManaged(false);
        var top = column(0);
        var toolbar = row(16, text(role.equals("STUDENT") ? nickname : role.equals("ADMIN") ? "Quản trị" : "Giảng viên", "brand"), space(), connectionLabel, button("Tùy chọn", "quiet", this::settings));
        toolbar.setPadding(new Insets(14, 24, 14, 24)); top.getChildren().addAll(toolbar, feedback); root.setTop(top);
        workspace.setPadding(new Insets(20, 28, 20, 28));
        if (!role.equals("STUDENT") && !passwordRequired) {
            var sidebar = column(10, brand(), text("QUẢN LÝ LỚP HỌC", "caption")); sidebar.getStyleClass().add("sidebar"); sidebar.setPrefWidth(208); navButtons.clear();
            for (String label : role.equals("ADMIN") ? List.of("Phòng thi", "Ngân hàng", "Lịch sử", "Người dùng") : List.of("Phòng thi", "Ngân hàng", "Lịch sử")) {
                var b = button(label, "nav", () -> { if (label.equals("Phòng thi")) roomView(); else if (label.equals("Ngân hàng")) bankView(); else if (label.equals("Lịch sử")) historyView(); else usersView(); });
                b.setMaxWidth(Double.MAX_VALUE); navButtons.put(label, b); sidebar.getChildren().add(b);
            }
            sidebar.getChildren().addAll(space(), text(user.path("display_name").asText("Giảng viên"), "subtitle"), button("Đổi mật khẩu", "nav", () -> passwordChange(false)), button("Đăng xuất", "nav", this::logout)); root.setLeft(sidebar);
        }
    }
    private void navigate(String page, Node content) {
        if (!route.equals(page)) sfx.play(SfxSlots.Cue.UI_TRANSITION);
        route = page;
        navButtons.forEach((name, button) -> { button.getStyleClass().remove("selected"); if (name.equals(page) || (page.equals("GAME") && name.equals("Phòng thi"))) button.getStyleClass().add("selected"); });
        workspace.setCenter(content);
        if (transition != null) transition.stop();
        content.setOpacity(1);
        if (!reducedMotion && !qa) { transition = new FadeTransition(Duration.millis(180), content); transition.setFromValue(.65); transition.setToValue(1); transition.play(); }
    }
    void call(String type, ObjectNode payload, Consumer<JsonNode> success) { call(type, payload, success, error -> notice(message(error), true)); }
    void call(String type, ObjectNode payload, Consumer<JsonNode> success, Consumer<Throwable> failure) {
        if (gateway == null || disconnected) { failure.accept(new GatewayException("DISCONNECTED", "Chưa kết nối. Hãy kết nối lại.", object())); return; }
        int token = connectionGeneration;
        gateway.request(type, payload).whenComplete((value, error) -> Platform.runLater(() -> { if (token != connectionGeneration) return; if (error != null) failure.accept(unwrap(error)); else success.accept(value); }));
    }
    private static Throwable unwrap(Throwable error) { while (error instanceof CompletionException && error.getCause() != null) error = error.getCause(); return error; }
    static String message(Throwable error) { error = unwrap(error); return error.getMessage() == null ? "Không hoàn thành yêu cầu. Hãy thử lại." : error.getMessage(); }
    void notice(String value, boolean error) {
        feedback.setText(value); feedback.getStyleClass().remove("error"); if (error) feedback.getStyleClass().add("error"); feedback.setManaged(true); feedback.setVisible(true);
        sfx.event(error ? SfxSlots.Cue.UI_ERROR : SfxSlots.Cue.UI_SUCCESS, "notice", value.hashCode());
    }
    private boolean confirm(String title, String body) {
        sfx.play(SfxSlots.Cue.UI_WARNING);
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.CANCEL, ButtonType.OK); dialog.initOwner(stage); dialog.setTitle(title); dialog.setHeaderText(title); styleDialog(dialog);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Xác nhận"); ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Hủy");
        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void logout() {
        if (isRunning() && !confirm("Đăng xuất?", "Ván vẫn chạy trên Server. Bạn cần đăng nhập lại để điều khiển.")) return;
        if (gateway != null) gateway.request(role.equals("STUDENT") ? "LEAVE" : "LOGOUT", object());
        if (projection != null) projection.close(); welcome();
    }
    private boolean isRunning() { return List.of("COUNTDOWN", "QUESTION_ACTIVE", "SHOW_RESULT", "SHOW_RANKING").contains(room.path("phase").asText()); }
    private boolean canEdit() { return gateway != null && !user.isEmpty() && !passwordRequired && room.path("phase").asText("WAITING").equals("WAITING") && !disconnected; }

    void bankView() { new ManagementViews(this).bank(bankKeyword, bankCategory, bankStatus, bankSort, bankPage); }
    void usersView() { new ManagementViews(this).users(userKeyword, userStatus, userPage); }
    void historyView() { new ManagementViews(this).history(historyPage); }
    void setHistoryPage(int page) { historyPage = page; }
    void setBankFilter(String keyword, String category, String status, String sort, int page) { bankKeyword = keyword; bankCategory = category; bankStatus = status; bankSort = sort; bankPage = page; }
    void setUserFilter(String keyword, String status, int page) { userKeyword = keyword; userStatus = status; userPage = page; }
    boolean editsAllowed() { return canEdit(); }
    Stage ownerStage() { return stage; }
    void showManagement(String route, Node content) { root.getStyleClass().remove("game"); navigate(route, content); }
    void passwordChange(boolean required) { new ManagementViews(this).password(required); }
    void passwordChanged() { if (projection != null) projection.close(); welcome(); }

    void roomView() {
        route = "Phòng thi";
        call("ROOM_STATE", object(), value -> {
            if (!route.equals("Phòng thi")) return;
            room = value;
            if ((isRunning() || room.path("phase").asText().equals("FINISHED")) && (role.equals("STUDENT") || room.path("is_owner").asBoolean())) {
                session = room.path("session_id").asText(session);
                question = room.path("question").isObject() ? room.path("question") : object();
                result = room.path("result").isObject() ? room.path("result") : object();
                phaseDeadline = System.nanoTime() + room.path("remaining_ms").asLong() * 1_000_000L;
                if (room.path("phase").asText().equals("QUESTION_ACTIVE") && (answer == null || answer.questionId() != question.path("question_id").asLong())) answer = new AnswerState(question.path("question_id").asLong(), room.path("remaining_ms").asLong(), System.nanoTime());
                if (result.has("ranking")) currentRanking = result.path("ranking").deepCopy();
                gameView(); if (isRunning()) startPulse();
            } else { lobby(); if (chooseAvatarAfterJoin) { chooseAvatarAfterJoin = false; avatarPicker(); } }
        });
    }
    private void lobby() {
        music.stop();
        root.getStyleClass().remove("game");
        boolean student = role.equals("STUDENT");
        if (student) root.getStyleClass().add("game");
        var header = row(12, text(student ? "Phòng chờ" : "Phòng thi", "title"), space());
        if (!student) header.getChildren().add(button("Trình chiếu ↗", "quiet", this::showProjection));
        else header.getChildren().add(button("Rời phòng", "quiet", this::logout));
        var roomCode = text(room.path("room_code").asText().isBlank() ? "— — —" : room.path("room_code").asText(), "room-code");
        var codeArea = column(4, text("MÃ PHÒNG", "caption"), roomCode, text(room.path("room_name").asText("Chưa có phòng"), "subtitle"));
        var configSummary = column(16, text(room.path("question_count").asInt(3) + " câu   ·   " + room.path("duration_seconds").asInt(15) + " giây/câu", "subtitle"));
        if (!student) {
            var configure = button(room.path("room_code").asText().isBlank() ? "Tạo phòng" : "Cấu hình phòng", "quiet", this::roomConfig); configure.setDisable(disconnected || (!room.path("room_code").asText().isBlank() && !room.path("is_owner").asBoolean()));
            configSummary.getChildren().add(row(12, configure, button("Sao chép mã", "quiet", () -> { ClipboardContent clipboard = new ClipboardContent(); clipboard.putString(room.path("room_code").asText()); Clipboard.getSystemClipboard().setContent(clipboard); notice("Đã sao chép mã phòng.", false); })));
        }
        var board = new GridPane(); board.setHgap(40); board.add(codeArea, 0, 0); board.add(configSummary, 1, 0); GridPane.setHgrow(configSummary, Priority.ALWAYS); board.getStyleClass().add("room-banner");
        var people = column(10, text("NGƯỜI CHƠI  ·  " + room.path("ready_count").asInt() + "/" + room.path("connected_player_count").asInt() + " sẵn sàng", "caption"));
        var tiles = new FlowPane(12, 12);
        for (JsonNode player : room.path("players")) {
            String n = player.path("nickname").asText();
            var entry = column(4, QuizlingArt.avatar(player.path("avatar_id").asText(), 108), text(n, "subtitle"), text(player.path("ready").asBoolean() ? "✓ Sẵn sàng" : "Đang chuẩn bị", "muted"));
            entry.setAlignment(Pos.CENTER); entry.setPrefWidth(185); entry.getStyleClass().add("player-seat"); tiles.getChildren().add(entry);
        }
        people.getChildren().add(tiles);
        var body = column(24, header, board, people); body.setPadding(new Insets(0, 0, 16, 0));
        var actions = row(12);
        if (student) {
            boolean ready = false;
            for (JsonNode player : room.path("players")) if (player.path("player_id").asText().equals(user.path("player_id").asText())) ready = player.path("ready").asBoolean();
            final boolean wasReady = ready;
            readyButton = button(ready ? "Đã sẵn sàng ✓" : "Sẵn sàng   →", "primary", () -> call("READY_SET", object().put("ready", !wasReady), value -> { room = value; if (!wasReady) sfx.play(SfxSlots.Cue.PLAYER_READY); lobby(); }));
            var changeAvatar = button("Đổi linh vật", "quiet", this::avatarPicker); changeAvatar.setDisable(ready || disconnected);
            actions.getChildren().addAll(readyButton, changeAvatar);
        } else {
            boolean owner = room.path("is_owner").asBoolean();
            var start = button("Bắt đầu ván thi   →", "ink-button", () -> call("START_GAME", object(), ignored -> { }));
            start.setDisable(!owner || room.path("connected_player_count").asInt() < 2 || room.path("ready_count").asInt() != room.path("connected_player_count").asInt() || !room.path("phase").asText().equals("WAITING"));
            var reset = button("Reset phòng", "quiet", () -> { if (confirm("Reset phòng?", "Điểm và trạng thái sẵn sàng sẽ được xóa.")) call("ROOM_RESET", object(), value -> { room = value; lobby(); }); }); reset.setDisable(!owner || disconnected);
            var close = button("Đóng phòng", "danger", () -> { if (confirm("Đóng phòng?", "Người chơi sẽ rời phòng; giảng viên khác có thể tổ chức.")) call("ROOM_CLOSE", object(), value -> { room = value; lobby(); }); }); close.setDisable(!owner || disconnected);
            actions.getChildren().addAll(start, reset, close);
            if (!owner || room.path("ready_count").asInt() < room.path("connected_player_count").asInt()) body.getChildren().add(text(!owner ? "Phòng đang do giảng viên khác tổ chức." : "Chờ mọi người sẵn sàng.", "muted"));
        }
        var layout = new BorderPane(scroll(body)); layout.setBottom(actions); navigate("Phòng thi", layout); updateProjection();
    }
    private void avatarPicker() {
        if (!role.equals("STUDENT") || !room.path("phase").asText().equals("WAITING") || disconnected) return;
        avatarDialog = new Dialog<>(); avatarDialog.initOwner(stage); styleDialog(avatarDialog);
        avatarDialog.setTitle("Biệt đội Quiz"); avatarDialog.setHeaderText("Chọn linh vật của bạn");
        avatarGrid = new FlowPane(10, 10); avatarGrid.setPrefWrapLength(740);
        avatarError = text("", "feedback", "error"); avatarError.setManaged(false); avatarError.setVisible(false);
        var choices = scroll(avatarGrid); choices.setPrefViewportHeight(370); choices.setPrefViewportWidth(750);
        avatarDialog.getDialogPane().setContent(column(12, text("Mỗi người một linh vật. Bạn có thể đổi trước khi sẵn sàng.", "muted"), choices, avatarError));
        avatarDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button)avatarDialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Xong");
        refreshAvatars(); avatarDialog.show();
    }
    private void refreshAvatars() {
        if (avatarGrid == null) return;
        String self = user.path("player_id").asText();
        Map<String, JsonNode> owners = new HashMap<>();
        for (JsonNode player : room.path("players")) owners.put(player.path("avatar_id").asText(), player);
        String focused = avatarGrid.getScene() != null && avatarGrid.getScene().getFocusOwner() != null ? avatarGrid.getScene().getFocusOwner().getId() : null;
        avatarGrid.getChildren().clear();
        for (var avatar : AvatarCatalog.ALL) {
            JsonNode owner = owners.get(avatar.id()); boolean mine = owner != null && owner.path("player_id").asText().equals(self), locked = owner != null && !mine;
            var b = new Button(); b.setId(avatar.id()); b.getStyleClass().add("avatar-choice"); if (mine) b.getStyleClass().add("avatar-selected");
            String status = mine ? "✓ Của bạn" : locked ? "Đã chọn" : avatar.name();
            var picture = QuizlingArt.avatar(avatar.id(), 68); if (locked) picture.setOpacity(.4); var contents = column(3, picture, text(status, "caption")); contents.setAlignment(Pos.CENTER); b.setGraphic(contents);
            b.setPrefSize(112, 106); b.setAccessibleText(avatar.name() + ", " + (locked ? "đã được " + owner.path("nickname").asText() + " chọn" : mine ? "đang chọn" : "còn trống"));
            b.setTooltip(new Tooltip(b.getAccessibleText())); b.setDisable(locked || avatarPending || disconnected);
            b.setOnAction(e -> {
                if (mine) return;
                avatarPending = true; refreshAvatars();
                call("AVATAR_SET", object().put("avatar_id", avatar.id()).put("room_code", room.path("room_code").asText()), value -> {
                    avatarPending = false;
                    if (!room.path("phase").asText().equals("WAITING")) return;
                    room = value; avatarError.setVisible(false); avatarError.setManaged(false); refreshAvatars(); if (route.equals("Phòng thi")) lobby();
                }, failure -> {
                    avatarPending = false;
                    if (failure instanceof GatewayException ge && ge.details().has("players")) room = ge.details();
                    showError(avatarError, message(failure)); refreshAvatars();
                    call("ROOM_STATE", object(), value -> { room = value; refreshAvatars(); });
                });
            }); avatarGrid.getChildren().add(b);
        }
        if (focused != null) avatarGrid.getChildren().stream().filter(n -> focused.equals(n.getId()) && !n.isDisabled()).findFirst().ifPresent(Node::requestFocus);
    }
    private void roomConfig() {
        TextField name = new TextField(room.path("room_name").asText("Mạng máy tính"));
        Spinner<Integer> count = new Spinner<>(1, 20, room.path("question_count").asInt(3)); Spinner<Integer> seconds = new Spinner<>(5, 120, room.path("duration_seconds").asInt(15));
        var dialog = new Dialog<ButtonType>(); dialog.initOwner(stage); dialog.setHeaderText("Cấu hình phòng thi"); styleDialog(dialog);
        var error = text("", "feedback", "error"); error.setVisible(false); error.setManaged(false);
        dialog.getDialogPane().setContent(column(16, field("Tên phòng", name), field("Số câu", count), field("Giây mỗi câu", seconds), text("Server chỉ chọn câu đang bật từ ngân hàng của bạn.", "muted"), error));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Button save = (Button)dialog.getDialogPane().lookupButton(ButtonType.OK); save.setText("Lưu cấu hình"); save.getStyleClass().add("primary");
        save.addEventFilter(javafx.event.ActionEvent.ACTION, e -> { e.consume(); save.setDisable(true); call("ROOM_CONFIGURE", object().put("room_name", name.getText().strip()).put("question_count", count.getValue()).put("duration_seconds", seconds.getValue()), value -> { dialog.close(); room = value; lobby(); }, failure -> { save.setDisable(false); showError(error, message(failure)); }); });
        dialog.show();
    }
    private void event(JsonNode event) {
        JsonNode p = event.path("payload");
        switch (event.path("type").asText()) {
            case "DISCONNECTED":
                music.stop();
                disconnected = true; if (pulse != null) pulse.stop(); answerButtons.forEach(b -> b.setDisable(true));
                if (avatarDialog != null) avatarDialog.close();
                connectionLabel.setText("Mất kết nối"); feedback.setText(role.equals("STUDENT") ? "Mất kết nối. Bạn có thể tham gia lại ở ván sau." : "Mất kết nối. Đăng nhập lại để tiếp tục quản lý phòng."); feedback.setManaged(true); feedback.setVisible(true); sfx.play(SfxSlots.Cue.CONNECTION_DOWN);
                root.setBottom(button("Kết nối lại", "quiet", () -> auth(role)));
                if (projection != null) projection.close(); break;
            case "ROOM_CLOSED":
                welcome(); root.setBottom(text(p.path("message").asText("Phòng thi đã đóng."), "subtitle")); sfx.play(SfxSlots.Cue.CONNECTION_DOWN); break;
            case "ROOM_STATE":
                if (p.path("phase").asText().equals("WAITING") && room.path("phase").asText().equals("WAITING")) {
                    if (p.path("players").size() > room.path("players").size()) sfx.play(SfxSlots.Cue.PLAYER_JOIN);
                    else if (p.path("ready_count").asInt() > room.path("ready_count").asInt()) sfx.play(SfxSlots.Cue.PLAYER_READY);
                }
                room = p;
                if (avatarDialog != null && avatarDialog.isShowing()) { if (!room.path("phase").asText().equals("WAITING")) avatarDialog.close(); else refreshAvatars(); }
                if ((route.equals("Phòng thi") || route.equals("GAME")) && room.path("phase").asText().equals("WAITING")) { if (pulse != null) pulse.stop(); answer = null; question = object(); result = object(); session = ""; lobby(); }
                else if (progressLabel != null) progressLabel.setText(p.path("answered_count").asInt() + "/" + p.path("connected_player_count").asInt() + " đã nộp");
                syncMusic();
                break;
            case "GAME_START":
                currentRanking = object().putArray("ranking"); previousRanking = currentRanking; rankedQuestion = -1;
                if (avatarDialog != null) avatarDialog.close();
                session = p.path("session_id").asText(); room = ((ObjectNode)room).put("phase", "COUNTDOWN"); phaseDeadline = System.nanoTime() + p.path("remaining_ms").asLong(3000) * 1_000_000L; question = object(); result = object(); answer = null; gameView(); startPulse(); break;
            case "QUESTION":
                if (!session.isEmpty() && !session.equals(p.path("session_id").asText(session))) return;
                if (question.path("question_id").asLong(-1) == p.path("question_id").asLong() && question.path("session_id").asText().equals(session)) return;
                session = p.path("session_id").asText(session); question = p; result = object(); room = ((ObjectNode)room).put("phase", "QUESTION_ACTIVE");
                answer = new AnswerState(p.path("question_id").asLong(), p.path("remaining_ms").asLong(), System.nanoTime()); gameView(); startPulse(); sfx.event(SfxSlots.Cue.ROUND_OPEN, session, answer.questionId()); break;
            case "QUESTION_RESULT":
                if (!p.path("session_id").asText(session).equals(session) || p.path("question_id").asLong() != question.path("question_id").asLong()) return;
                if (!room.path("phase").asText().equals("QUESTION_ACTIVE")) return;
                result = p; room = ((ObjectNode)room).put("phase", "SHOW_RESULT"); phaseDeadline = System.nanoTime() + p.path("remaining_ms").asLong(3000) * 1_000_000L;
                gameView(); startPulse(); sfx.event(role.equals("STUDENT") ? (p.path("correct").asBoolean() ? SfxSlots.Cue.ANSWER_CORRECT : SfxSlots.Cue.ANSWER_WRONG) : SfxSlots.Cue.ROUND_CLOSE, session, p.path("question_id").asLong()); break;
            case "ROUND_RANKING":
                if (!p.path("session_id").asText().equals(session) || p.path("question_id").asLong() != question.path("question_id").asLong() || rankedQuestion == p.path("question_id").asLong()) return;
                previousRanking = currentRanking; currentRanking = p.path("ranking").deepCopy(); rankedQuestion = p.path("question_id").asLong();
                ((ObjectNode)result).set("ranking", currentRanking); room = ((ObjectNode)room).put("phase", "SHOW_RANKING");
                phaseDeadline = System.nanoTime() + p.path("remaining_ms").asLong(4500) * 1_000_000L;
                gameView(); startPulse(); break;
            case "GAME_OVER":
                if (!p.path("session_id").asText(session).equals(session)) return;
                result = p; room = ((ObjectNode)room).put("phase", "FINISHED"); if (pulse != null) pulse.stop(); sfx.stopPlaying(); gameView(); if (!music.enabled()) sfx.event(SfxSlots.Cue.MATCH_FINISH, session, 0); break;
            case "GAME_ABORTED":
                if (!p.path("session_id").asText(session).equals(session)) return;
                result = object().put("aborted", true); room = ((ObjectNode)room).put("phase", "FINISHED"); if (pulse != null) pulse.stop(); gameView(); break;
            default: break;
        }
    }
    private void startPulse() {
        if (pulse != null) pulse.stop(); pulse = new Timeline(new KeyFrame(Duration.millis(100), e -> tick())); pulse.setCycleCount(Animation.INDEFINITE); pulse.play(); tick();
    }
    private void tick() {
        long now = System.nanoTime();
        if (answer != null) answer.expire(now);
        long remaining = room.path("phase").asText().equals("QUESTION_ACTIVE") && answer != null ? answer.remainingMillis(now) : Math.max(0, (phaseDeadline - now) / 1_000_000L);
        String seconds = String.format(Locale.ROOT, "%02d", (remaining + 999) / 1000);
        if (timerLabel != null) timerLabel.setText(seconds);
        if (projectionTimer != null) projectionTimer.setText(seconds);
        if (countdownLabel != null) countdownLabel.setText(remaining > 0 ? String.valueOf((remaining + 999) / 1000) : "Chờ máy chủ…");
        if (room.path("phase").asText().equals("COUNTDOWN") && remaining > 0 && remaining <= 3000) sfx.event(SfxSlots.Cue.COUNTDOWN_TICK, session, (remaining + 999) / 1000);
        if (timerBar != null && answer != null) timerBar.setProgress(Math.min(1, (double)remaining / Math.max(1, question.path("remaining_ms").asLong())));
        if (ackLabel != null && answer != null) {
            ackLabel.setText(switch (answer.status()) { case OPEN -> "Chọn đáp án · Phím A–D"; case SENDING -> "Đang gửi…"; case RECORDED -> "✓ Đã nhận đáp án " + answer.answer(); case EXPIRED -> "Hết giờ!"; case REJECTED -> "Chưa ghi nhận được đáp án"; });
            answerButtons.forEach(b -> b.setDisable(answer.status() != AnswerState.Status.OPEN || disconnected));
        }
    }
    private void submitAnswer(String choice) {
        if (answer == null || disconnected || !answer.submit(choice, System.nanoTime())) return;
        long id = answer.questionId(); String capturedSession = session;
        for (int i = 0; i < answerButtons.size(); i++) {
            Button button = answerButtons.get(i); button.setDisable(true);
            if ("ABCD".substring(i, i + 1).equals(choice)) {
                button.getStyleClass().add("chosen");
                if (!reducedMotion && !qa) { var press = new ScaleTransition(Duration.millis(100), button); press.setFromX(1); press.setFromY(1); press.setToX(1.01); press.setToY(1.01); press.setAutoReverse(true); press.setCycleCount(2); press.play(); }
            }
        }
        tick();
        call("ANSWER", object().put("session_id", session).put("question_id", id).put("answer", choice).put("room_code", room.path("room_code").asText()), response -> {
            if (answer != null && answer.questionId() == id && session.equals(capturedSession)) { answer.acknowledge(response.path("question_id").asLong(id), response.path("accepted").asBoolean()); tick(); if (answer.status() == AnswerState.Status.RECORDED) sfx.event(SfxSlots.Cue.ANSWER_ACK, session, id); }
        }, error -> { if (answer != null && answer.questionId() == id && session.equals(capturedSession)) { answer.acknowledge(id, false); tick(); notice("Chưa xác nhận được đáp án. Không tự gửi lại một lựa chọn khác.", true); } });
    }
    void gameView() {
        syncMusic();
        root.getStyleClass().remove("game"); root.getStyleClass().add("game");
        timerLabel = null; countdownLabel = null; ackLabel = null; progressLabel = null; timerBar = null; answerButtons.clear();
        var layout = deck(false); navigate("GAME", layout); updateProjection(); tick();
    }
    private void syncMusic() {
        if (gateway == null || disconnected || (!role.equals("STUDENT") && !room.path("is_owner").asBoolean())) { music.stop(); return; }
        music.update(session, room.path("phase").asText(), result.path("aborted").asBoolean() || room.path("result").path("aborted").asBoolean());
    }
    private Node deck(boolean publicView) {
        String phase = room.path("phase").asText();
        var heading = row(16, text("QUIZ BATTLE", "brand"), text(room.path("room_code").asText(), "status"), space(), text(phase.equals("FINISHED") ? "CHUNG CUỘC" : "CÂU " + question.path("number").asInt(result.path("number").asInt()) + "/" + room.path("question_count").asInt(3), "caption"));
        if (!publicView && !role.equals("STUDENT") && !phase.equals("FINISHED")) heading.getChildren().add(button("Hủy ván", "danger", () -> { if (confirm("Hủy ván?", "Ván bị hủy không tạo kết quả hoàn thành.")) call("ABORT_GAME", object(), ignored -> { }); }));
        var body = column(20, heading); body.setPadding(new Insets(publicView ? 36 : 12));
        if (phase.equals("COUNTDOWN")) {
            var number = text("3", "score"); number.setStyle("-fx-font-size: 112px;");
            if (publicView) projectionTimer = number; else countdownLabel = number;
            var center = column(16, QuizlingArt.avatar("quizling-1", 120), text("Sẵn sàng chưa?", "game-title"), number); center.setAlignment(Pos.CENTER); center.setPadding(new Insets(24, 16, 24, 16)); body.getChildren().add(center);
        } else if (phase.equals("QUESTION_ACTIVE")) {
            var timer = text("15", "timer"); if (publicView) projectionTimer = timer; else timerLabel = timer;
            boolean compactGame = !publicView && scene.getHeight() < 720;
            var title = text(question.path("content").asText("Chờ câu hỏi từ máy chủ…"), "game-title"); title.setStyle("-fx-font-size: " + (publicView ? 42 : compactGame ? 26 : 30) + "px;");
            var questionHeader = row(16, text(question.path("category").asText(), "caption"), space(), timer);
            var fixedHeader = column(compactGame ? 8 : 16, heading, questionHeader); body.getChildren().remove(heading); body.getChildren().add(title);
            if (compactGame) body.setSpacing(12);
            var grid = new GridPane(); grid.setHgap(16); grid.setVgap(16);
            var widths = List.of(new ColumnConstraints(), new ColumnConstraints()); widths.forEach(c -> { c.setPercentWidth(50); c.setHgrow(Priority.ALWAYS); }); grid.getColumnConstraints().addAll(widths);
            List<Node> tiles = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String letter = "ABCD".substring(i, i + 1);
                Label option = text(question.path("option_" + letter.toLowerCase(Locale.ROOT)).asText(), "subtitle"); option.setStyle("-fx-font-size: " + (publicView ? 26 : 19) + "px;"); option.setMaxWidth(Double.MAX_VALUE);
                var contents = row(16, text(letter, "answer-letter"), option); HBox.setHgrow(option, Priority.ALWAYS);
                if (!publicView && role.equals("STUDENT")) {
                    var b = button("", "answer", () -> submitAnswer(letter)); b.getStyleClass().add(letter.toLowerCase(Locale.ROOT)); b.setGraphic(contents); b.setAccessibleText("Đáp án " + letter + ": " + option.getText()); b.setMaxWidth(Double.MAX_VALUE); b.setMaxHeight(Double.MAX_VALUE);
                    if (compactGame) b.setStyle("-fx-min-height: 88; -fx-padding: 16;");
                    if (answer != null && answer.answer().equals(letter)) b.getStyleClass().add("chosen"); answerButtons.add(b); tiles.add(b);
                } else { var tile = column(0, contents); tile.getStyleClass().addAll("answer", letter.toLowerCase(Locale.ROOT)); tiles.add(tile); }
            }
            boolean[] lastNarrow = {false}, flowed = {false};
            Runnable reflow = () -> {
                double viewportWidth = publicView && projection != null ? projection.getScene().getWidth() : scene.getWidth();
                boolean narrow = viewportWidth < 950 || question.path("content").asText().length() > 200 || tiles.stream().anyMatch(n -> question.path("option_" + "abcd".charAt(tiles.indexOf(n))).asText().length() > 100);
                if (flowed[0] && lastNarrow[0] == narrow) return;
                flowed[0] = true; lastNarrow[0] = narrow;
                grid.getChildren().clear(); grid.getColumnConstraints().forEach(c -> c.setPercentWidth(narrow ? (c == grid.getColumnConstraints().getFirst() ? 100 : 0) : 50));
                for (int i = 0; i < tiles.size(); i++) grid.add(tiles.get(i), narrow ? 0 : i % 2, narrow ? i : i / 2);
            }; reflow.run();
            grid.widthProperty().addListener((o, old, size) -> { if (size.doubleValue() > 0) reflow.run(); });
            body.getChildren().add(grid);
            var fixedBottom = column(12);
            if (!publicView) {
                timerBar = new ProgressBar(1); timerBar.setMaxWidth(Double.MAX_VALUE); timerBar.setPrefHeight(8); fixedBottom.getChildren().add(timerBar);
                if (role.equals("STUDENT")) { ackLabel = text("", "feedback"); fixedBottom.getChildren().add(ackLabel); }
                else { progressLabel = text(room.path("answered_count").asInt() + "/" + room.path("connected_player_count").asInt() + " đã nộp", "muted"); fixedBottom.getChildren().add(progressLabel); }
            }
            // Timer and ACK remain visible while long content scrolls; answers never clip.
            var activeLayout = new BorderPane(scroll(body)); activeLayout.setTop(fixedHeader); activeLayout.setBottom(fixedBottom); activeLayout.setPadding(new Insets(publicView ? 36 : 12));
            body.setPadding(new Insets(12, 0, 12, 0)); return activeLayout;
        } else if (phase.equals("SHOW_RESULT")) {
            String correct = result.path("correct_answer").asText();
            var reveal = column(16); reveal.setAlignment(Pos.CENTER); reveal.setPadding(new Insets(20));
            if (!publicView && role.equals("STUDENT")) {
                String ownAvatar = "quizling-1"; for (JsonNode p : result.path("ranking")) if (p.path("player_id").asText().equals(user.path("player_id").asText())) ownAvatar = p.path("avatar_id").asText();
                reveal.getChildren().addAll(QuizlingArt.avatar(ownAvatar, 120), text(result.path("correct").asBoolean() ? "Chính xác!" : "Tiếc quá!", "game-title"), text("+" + result.path("points").asInt() + " điểm", "score"));
            }
            reveal.getChildren().addAll(text("Đáp án " + correct, "caption"), text(question.path("option_" + correct.toLowerCase(Locale.ROOT)).asText(correct), "subtitle"));
            body.getChildren().add(reveal);
        } else if (phase.equals("SHOW_RANKING")) {
            var timer = text("05", "timer"); if (publicView) projectionTimer = timer; else timerLabel = timer;
            body.getChildren().add(row(16, column(6, text("SAU CÂU " + question.path("number").asInt(), "eyebrow"), text("Bảng xếp hạng", "game-title")), space(), timer));
            body.getChildren().add(ranking(currentRanking, publicView));
            body.getChildren().add(text(question.path("number").asInt() >= room.path("question_count").asInt() ? "Tiếp theo: kết quả chung cuộc" : "Sẵn sàng cho câu tiếp theo", "muted"));
        } else if (phase.equals("FINISHED")) {
            var finalTitle=text(result.path("aborted").asBoolean() ? "Ván thi đã hủy" : "Về đích!", "game-title");finalTitle.setId("final-title");finalTitle.setFocusTraversable(true);body.getChildren().add(finalTitle);
            if (!result.path("aborted").asBoolean()) {
                boolean first=!session.equals(publicView?celebratedProjectionSession:celebratedClientSession);
                if(first&&!publicView)Platform.runLater(()->{if(finalTitle.getScene()!=null)finalTitle.requestFocus();});
                if(publicView)celebratedProjectionSession=session;else celebratedClientSession=session;
                var podium=new VictoryPodium(result.path("ranking"),!publicView&&scene.getHeight()<720,reducedMotion||!first);
                var stage=new StackPane(podium);stage.setAlignment(Pos.BOTTOM_CENTER);body.getChildren().add(stage);
            }
            if (!publicView) {
                if (!result.path("result_saved").asBoolean() && !result.path("aborted").asBoolean()) body.getChildren().add(text("Chưa lưu được kết quả. Hãy báo giảng viên.", "feedback", "error"));
                if (!role.equals("STUDENT")) body.getChildren().add(row(12, button("Chơi ván mới", "primary", () -> call("ROOM_RESET", object(), value -> { room = value; lobby(); })), button("Đóng phòng", "quiet", () -> call("ROOM_CLOSE", object(), value -> { room = value; lobby(); }))));
                else body.getChildren().add(button("Rời phòng", "primary", this::logout));
            }
            if(!result.path("aborted").asBoolean())body.getChildren().addAll(text("Bảng xếp hạng chung cuộc","subtitle"),ranking(result.path("ranking"),publicView));
        } else { body.getChildren().addAll(text("CÙNG THAM GIA", "caption"), text(room.path("room_code").asText("— — —"), "room-code"), text(room.path("room_name").asText("Chưa có phòng"), "game-title")); for (JsonNode player : room.path("players")) body.getChildren().add(text(player.path("nickname").asText() + "  ·  " + (player.path("ready").asBoolean() ? "Sẵn sàng" : "Đang chuẩn bị"), "subtitle")); }
        if (List.of("SHOW_RANKING", "FINISHED").contains(phase)) { body.setMaxWidth(1060); var centered = new StackPane(body); centered.setAlignment(Pos.TOP_CENTER); return scroll(centered); }
        return scroll(body);
    }
    private Node ranking(JsonNode values, boolean publicView) {
        boolean reorder = room.path("phase").asText().equals("SHOW_RANKING");
        return new RankBoard(reorder ? previousRanking : values, values, !publicView && role.equals("STUDENT") ? user.path("player_id").asText() : "", reducedMotion || !reorder);
    }
    private static String initials(String text) { String[] parts = text.strip().split("\\s+"); return Arrays.stream(parts).filter(s -> !s.isEmpty()).skip(Math.max(0, parts.length - 2)).map(s -> s.substring(0, 1)).reduce("", String::concat).toUpperCase(Locale.ROOT); }
    private void showProjection() {
        if (projection != null && projection.isShowing()) { projection.setFullScreen(!projection.isFullScreen()); return; }
        Screen target = Screen.getPrimary();
        if (Screen.getScreens().size() > 1 && !qa) {
            var choice = new ChoiceDialog<>(1, java.util.stream.IntStream.rangeClosed(1, Screen.getScreens().size()).boxed().toList()); choice.initOwner(stage); styleDialog(choice); choice.setHeaderText("Chọn màn hình trình chiếu");
            Optional<Integer> selected = choice.showAndWait(); if (selected.isEmpty()) return; target = Screen.getScreens().get(selected.get() - 1);
        }
        projection = new Stage(); projection.setTitle("Quiz Battle · Trình chiếu"); projection.initOwner(stage);
        projection.setX(target.getBounds().getMinX()); projection.setY(target.getBounds().getMinY());
        var root = new StackPane(); root.getStyleClass().add("game"); var scene = new Scene(root, 1280, 720); scene.getStylesheets().add(CSS); projection.setScene(scene);
        projection.setFullScreenExitHint("Esc để thoát toàn màn hình. Ván thi vẫn tiếp tục."); projection.show(); updateProjection(); if (!qa) projection.setFullScreen(true);
    }
    private void updateProjection() { if (projection != null && projection.isShowing()) { projectionTimer = null; ((StackPane)projection.getScene().getRoot()).getChildren().setAll(deck(true)); } }
    private void settings() {
        var reduced = new CheckBox("Giảm chuyển động"); reduced.setSelected(reducedMotion);
        var sound = new CheckBox("Âm thanh giao diện"); sound.setDisable(!sfx.assetsAvailable()); sound.setSelected(!sfx.muted());
        var volume = new Slider(0, 100, sfx.volume() * 100); volume.setAccessibleText("Âm lượng hiệu ứng"); volume.setShowTickLabels(true); volume.setMajorTickUnit(25); volume.setBlockIncrement(5);
        volume.disableProperty().bind(sound.selectedProperty().not());
        var audioStatus = text(sfx.status(), "muted");
        sound.setOnAction(e -> sfx.mute(!sound.isSelected())); volume.valueProperty().addListener((o, before, after) -> sfx.volume(after.doubleValue() / 100));
        var testSound = button("Nghe thử", "quiet", () -> { sfx.play(SfxSlots.Cue.UI_SELECT); audioStatus.setText(sfx.status()); }); testSound.disableProperty().bind(sound.selectedProperty().not());
        var background = new CheckBox("Nhạc nền ván đấu và chung cuộc"); background.setId("music-enabled"); background.setDisable(!music.assetsAvailable()); background.setSelected(!music.muted());
        var musicVolume = new Slider(0, 100, music.volume() * 100); musicVolume.setId("music-volume"); musicVolume.setAccessibleText("Âm lượng nhạc nền"); musicVolume.setShowTickLabels(true); musicVolume.setMajorTickUnit(25); musicVolume.setBlockIncrement(5); musicVolume.disableProperty().bind(background.selectedProperty().not());
        var musicStatus = text(music.status(), "muted"); musicStatus.setId("music-status");
        background.setOnAction(e -> music.mute(!background.isSelected())); musicVolume.valueProperty().addListener((o, before, after) -> music.volume(after.doubleValue() / 100));
        var content = column(16, reduced, new Separator(), sound, field("Âm lượng hiệu ứng", volume), row(12, testSound, audioStatus), new Separator(), background, field("Âm lượng nhạc nền", musicVolume), musicStatus);
        var scroller = scroll(content); scroller.setPrefViewportWidth(460); scroller.setPrefViewportHeight(Math.min(410, Math.max(240, stage.getHeight() - 240)));
        var dialog = new Dialog<ButtonType>(); dialog.initOwner(stage); styleDialog(dialog); dialog.setHeaderText("Tùy chọn"); dialog.setResizable(true);
        dialog.getDialogPane().setContent(scroller);
        var refreshStatus = new Timeline(new KeyFrame(Duration.millis(500), e -> { audioStatus.setText(sfx.status()); musicStatus.setText(music.status()); })); refreshStatus.setCycleCount(Animation.INDEFINITE); dialog.setOnShown(e -> refreshStatus.play()); dialog.setOnHidden(e -> refreshStatus.stop());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); reduced.setOnAction(e -> { reducedMotion = reduced.isSelected(); prefs.putBoolean("reducedMotion", reducedMotion); if (transition != null) transition.stop(); if (workspace.getCenter() != null) workspace.getCenter().setOpacity(1); if (reducedMotion) { settleRanks(root); if (projection != null) settleRanks(projection.getScene().getRoot()); } }); dialog.show();
    }
    private void settleRanks(Node node) { if (node instanceof RankBoard board) board.settle(); if(node instanceof VictoryPodium podium)podium.settle(); if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(this::settleRanks); }

    // Real TLS UI test hooks: observations and normal user actions only.
    Scene qaScene() { return scene; }
    void qaSettings() { settings(); }
    String qaPhase() { return room.path("phase").asText(); }
    MusicSlots.Track qaMusicTrack() { return music.desiredTrack(); }
    JsonNode qaResult() { return result.deepCopy(); }
    boolean qaConnected() { return gateway != null && !disconnected && !user.isEmpty(); }
    void qaAnswer(String letter) { submitAnswer(letter); }
    static void qaFailed(Throwable failure) { qaFailure = failure; }
    void qaAuth() { auth("ADMIN"); }
    void qaStudentAuth() { auth("STUDENT"); }
    void qaTeacherAuth() { auth("TEACHER"); }
    void qaWelcome() { welcome(); }
    void qaLogout() { logout(); }
    void qaClick(String label) { ((Button)scene.getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals(label)).findFirst().orElseThrow()).fire(); }
    void qaAssertRanksSettled() { assertRanksSettled(root); }
    private void assertRanksSettled(Node node) {
        if (node instanceof RankBoard board && !board.settled()) throw new AssertionError("Ranking did not settle");
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(this::assertRanksSettled);
    }
    void qaCloseAuxiliary() { for (Window window : new ArrayList<>(Window.getWindows())) if (window != stage && window.isShowing()) window.hide(); }
}
