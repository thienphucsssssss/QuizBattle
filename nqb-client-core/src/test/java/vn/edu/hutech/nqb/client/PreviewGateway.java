package vn.edu.hutech.nqb.client;

import static vn.edu.hutech.nqb.client.QuizGateway.object;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.edu.hutech.nqb.shared.AvatarRoster;

/** Test-only fixture; never packaged in a Client or used for authentication. */
public final class PreviewGateway implements QuizGateway {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "quiz-preview"); thread.setDaemon(true); return thread;
    });
    private volatile Consumer<JsonNode> listener = ignored -> { };
    private final List<ObjectNode> questions = new ArrayList<>();
    private final List<ObjectNode> users = new ArrayList<>();
    private final String role;
    private String phase = "WAITING", name = "Mạng máy tính", code = "QZ7K2", nickname = "Minh Anh";
    private int questionCount = 3, seconds = 15, index, score, correctCount;
    private boolean roomExists = true, ready;
    private volatile boolean closed;
    private String selected = "", session = "preview-1";
    private long deadline;
    private int generation;
    private JsonNode current = object();
    private final AvatarRoster avatars = new AvatarRoster();

    public PreviewGateway(String role) {
        this.role = role;
        avatars.join("p1"); avatars.join("p2"); avatars.join("p3");
        seed();
    }
    @Override public synchronized void onEvent(Consumer<JsonNode> listener) { this.listener = listener; }
    @Override public boolean isPreview() { return true; }
    @Override public CompletableFuture<JsonNode> request(String type, ObjectNode payload) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Preview closed"));
        ObjectNode copy = payload.deepCopy();
        try { return CompletableFuture.supplyAsync(() -> handle(type, copy), worker); }
        catch (RejectedExecutionException stopped) { return CompletableFuture.failedFuture(new IllegalStateException("Preview closed")); }
    }
    private void needManager() {
        if (role.equals("STUDENT")) throw error("FORBIDDEN", "Học sinh không có quyền quản lý.");
    }
    private void needAdmin() {
        if (!role.equals("ADMIN")) throw error("FORBIDDEN", "Chỉ Admin được quản lý tài khoản.");
    }
    private void needWaiting() {
        if (!phase.equals("WAITING")) throw error("INVALID_STATE", "Chỉ chỉnh ngân hàng khi phòng đang chờ.");
    }
    private GatewayException error(String code, String message) { return new GatewayException(code, message, object()); }
    private JsonNode handle(String type, ObjectNode p) {
        if (closed) throw error("DISCONNECTED", "Đã đóng bản xem thử.");
        switch (type) {
            case "LOGIN": return object().put("user_id", 2).put("display_name", "Phúc Thiện").put("username", "giangvien.demo").put("role", role).put("must_change_password", false);
            case "JOIN":
                needWaiting();
                if (!roomExists || !p.path("room_code").asText().equals(code)) throw error("WRONG_ROOM", "Phòng không tồn tại. Mã phòng mẫu là QZ7K2.");
                if (p.path("nickname").asText().isBlank()) throw error("INVALID_NICKNAME", "Nhập biệt danh để tham gia.");
                avatars.join("p1");
                nickname = p.path("nickname").asText().strip();
                return object().put("player_id", "p1").put("nickname", nickname).put("room_code", code).put("avatar_id", avatars.snapshot().get("p1"));
            case "ROOM_STATE": return state();
            case "AVATAR_SET":
                if (!role.equals("STUDENT")) throw error("FORBIDDEN", "Chỉ người chơi được chọn linh vật.");
                needWaiting();
                if (ready) throw error("ALREADY_READY", "Bỏ sẵn sàng để đổi linh vật.");
                try { avatars.select("p1", p.path("avatar_id").asText()); }
                catch (IllegalArgumentException | IllegalStateException invalid) {
                    throw new GatewayException(invalid.getMessage(), invalid.getMessage().equals("AVATAR_TAKEN") ? "Linh vật vừa có người chọn. Chọn bạn khác nhé." : "Không thể chọn linh vật này.", state());
                }
                emit("ROOM_STATE", state()); return state();
            case "QUESTION_CATEGORIES": {
                needManager(); var values = object().putArray("categories");
                questions.stream().map(q -> q.path("category").asText()).distinct().sorted().forEach(values::add);
                return object().set("categories", values);
            }
            case "QUESTION_LIST": {
                needManager(); String keyword = TextKey.of(p.path("keyword").asText());
                List<ObjectNode> found = questions.stream().filter(q -> TextKey.of(q.path("content").asText()).contains(keyword))
                    .filter(q -> p.path("category").isNull() || !p.has("category") || p.path("category").asText().equals(q.path("category").asText()))
                    .filter(q -> p.path("is_active").isNull() || !p.has("is_active") || p.path("is_active").asBoolean() == q.path("is_active").asBoolean())
                    .sorted(p.path("sort").asText().equals("ID_ASC") ? Comparator.comparingInt(q -> q.path("question_id").asInt()) : Comparator.comparingInt((ObjectNode q) -> q.path("question_id").asInt()).reversed()).toList();
                return page(found, p, true);
            }
            case "QUESTION_GET": needManager(); return question(p.path("question_id").asInt()).deepCopy();
            case "QUESTION_CHECK_DUPLICATE": {
                needManager(); var candidates = duplicates(p); return object().put("has_duplicate", !candidates.isEmpty()).put("total_matches", candidates.size()).set("candidates", array(candidates.stream().limit(5).toList()));
            }
            case "QUESTION_CREATE", "QUESTION_UPDATE": {
                needManager(); needWaiting();
                ObjectNode existing = type.equals("QUESTION_UPDATE") ? question(p.path("question_id").asInt()) : null;
                validate(p);
                ObjectNode duplicateInput = p.deepCopy().put("exclude_question_id", existing == null ? -1 : existing.path("question_id").asInt());
                var duplicates = duplicates(duplicateInput);
                if (!duplicates.isEmpty() && !p.path("allow_duplicate").asBoolean()) {
                    var details = object().put("total_matches", duplicates.size()); details.set("candidates", array(duplicates.stream().limit(5).toList()));
                    throw new GatewayException("DUPLICATE_QUESTION", "Câu hỏi đã tồn tại trong ngân hàng của bạn. Chưa lưu.", details);
                }
                int id = existing == null ? questions.stream().mapToInt(q -> q.path("question_id").asInt()).max().orElse(0) + 1 : existing.path("question_id").asInt();
                ObjectNode value = p.deepCopy().put("question_id", id).put("updated_at_utc", Instant.now().toString());
                value.remove(List.of("allow_duplicate", "exclude_question_id"));
                if (existing != null) questions.remove(existing);
                questions.add(value); return value.deepCopy();
            }
            case "QUESTION_DELETE": needManager(); needWaiting(); questions.remove(question(p.path("question_id").asInt())); return object();
            case "QUESTION_SET_ACTIVE": needManager(); needWaiting(); question(p.path("question_id").asInt()).put("is_active", p.path("is_active").asBoolean()); return object();
            case "USER_LIST": {
                needAdmin(); String keyword = TextKey.of(p.path("keyword").asText());
                var found = users.stream().filter(u -> TextKey.of(u.path("username").asText() + " " + u.path("display_name").asText()).contains(keyword))
                    .filter(u -> !p.hasNonNull("is_active") || p.path("is_active").asBoolean() == u.path("is_active").asBoolean()).toList();
                return page(found, p, false);
            }
            case "USER_CREATE": {
                needAdmin(); String username = p.path("username").asText().strip();
                if (username.isBlank() || p.path("display_name").asText().isBlank() || p.path("temporary_password").asText().length() < 8) throw error("INVALID_INPUT", "Điền tên, username và mật khẩu tạm ít nhất 8 ký tự.");
                if (users.stream().anyMatch(u -> u.path("username").asText().equalsIgnoreCase(username))) throw error("USERNAME_TAKEN", "Username đã được sử dụng.");
                var user = object().put("user_id", users.size() + 10).put("username", username).put("display_name", p.path("display_name").asText()).put("role", "TEACHER").put("is_active", true).put("must_change_password", true);
                users.add(user); return user.deepCopy();
            }
            case "USER_UPDATE", "USER_SET_ACTIVE", "USER_RESET_PASSWORD": {
                needAdmin(); ObjectNode user = users.stream().filter(u -> u.path("user_id").asInt() == p.path("user_id").asInt()).findFirst().orElseThrow(() -> error("NOT_FOUND", "Không tìm thấy tài khoản."));
                if (!user.path("role").asText().equals("TEACHER")) throw error("FORBIDDEN", "Không thay đổi Admin qua chức năng này.");
                if (type.equals("USER_UPDATE")) {
                    if (p.path("display_name").asText().isBlank()) throw error("INVALID_INPUT", "Tên hiển thị không được trống.");
                    user.put("display_name", p.path("display_name").asText());
                } else if (type.equals("USER_SET_ACTIVE")) user.put("is_active", p.path("is_active").asBoolean());
                else {
                    if (p.path("temporary_password").asText().length() < 8) throw error("INVALID_INPUT", "Mật khẩu tạm cần ít nhất 8 ký tự.");
                    user.put("must_change_password", true);
                }
                return user.deepCopy();
            }
            case "PASSWORD_CHANGE":
                if (p.path("new_password").asText().length() < 8) throw error("INVALID_INPUT", "Mật khẩu mới cần ít nhất 8 ký tự.");
                return object(); // Preview deliberately stores no password or password hash.
            case "ROOM_CONFIGURE":
                needManager(); if (!phase.equals("WAITING")) throw error("INVALID_STATE", "Chưa thể cấu hình phòng khi đang thi.");
                String newName = p.path("room_name").asText(name); int newCount = p.path("question_count").asInt(3), newSeconds = p.path("duration_seconds").asInt(15);
                if (newName.isBlank() || newCount < 1 || newCount > 20 || newSeconds < 5 || newSeconds > 120) throw error("INVALID_CONFIG", "Chọn 1–20 câu và 5–120 giây/câu.");
                name = newName; questionCount = newCount; seconds = newSeconds;
                if (!roomExists) { avatars.join("p1"); avatars.join("p2"); avatars.join("p3"); }
                roomExists = true; emit("ROOM_STATE", state()); return state();
            case "READY_SET": needWaiting(); if (!avatars.snapshot().containsKey("p1")) throw error("NOT_JOINED", "Hãy vào phòng trước."); ready = p.path("ready").asBoolean(); emit("ROOM_STATE", state()); return state();
            case "START_GAME": needManager(); startGame(); return object();
            case "PREVIEW_START": startGame(); return object();
            case "ANSWER":
                if (!phase.equals("QUESTION_ACTIVE") || System.nanoTime() >= deadline) return object().put("question_id", p.path("question_id").asInt()).put("accepted", false).put("reason", "TIME_EXPIRED");
                if (!session.equals(p.path("session_id").asText()) || current.path("question_id").asInt() != p.path("question_id").asInt()) return object().put("question_id", p.path("question_id").asInt()).put("accepted", false).put("reason", "WRONG_QUESTION");
                if (!selected.isEmpty()) return object().put("question_id", p.path("question_id").asInt()).put("accepted", false).put("reason", "DUPLICATE_ANSWER");
                if (!p.path("answer").asText().matches("[ABCD]")) throw error("INVALID_ANSWER", "Đáp án phải là A–D.");
                selected = p.path("answer").asText(); emit("ROOM_STATE", state());
                return object().put("question_id", current.path("question_id").asInt()).put("accepted", true);
            case "ABORT_GAME": needManager(); generation++; phase = "FINISHED"; emit("GAME_ABORTED", object().put("message", "Ván mẫu đã được hủy.")); emit("ROOM_STATE", state()); return state();
            case "ROOM_RESET", "ROOM_CLOSE":
                needManager(); if (!phase.equals("WAITING") && !phase.equals("FINISHED")) throw error("INVALID_STATE", "Hãy kết thúc ván trước khi reset/đóng phòng.");
                generation++; phase = "WAITING"; score = 0; roomExists = !type.equals("ROOM_CLOSE"); ready = false; if (!roomExists) avatars.clear(); emit("ROOM_STATE", state()); return state();
            case "HISTORY_LIST": {
                needManager(); var items = array(List.of(object().put("session_id", "sample-history").put("room_name", "Ôn tập TCP/IP").put("ended_at_utc", "2026-10-14T03:30:00Z").put("question_count", 10).put("player_count", 3)));
                return object().put("total", 1).set("items", items);
            }
            case "HISTORY_GET": needManager(); return object().put("room_name", "Ôn tập TCP/IP").set("ranking", ranking());
            case "LEAVE": avatars.leave("p1"); ready = false; emit("ROOM_STATE", state()); return object();
            case "PING", "LOGOUT": return object();
            default: throw error("UNKNOWN_TYPE", "Chức năng chưa có trong bản xem thử.");
        }
    }
    private ObjectNode question(int id) { return questions.stream().filter(q -> q.path("question_id").asInt() == id).findFirst().orElseThrow(() -> error("NOT_FOUND", "Không tìm thấy câu hỏi.")); }
    private List<ObjectNode> duplicates(JsonNode p) {
        return questions.stream().filter(q -> q.path("question_id").asInt() != p.path("exclude_question_id").asInt(-1))
            .filter(q -> TextKey.of(q.path("content").asText()).equals(TextKey.of(p.path("content").asText()))).map(this::summary).toList();
    }
    private void validate(JsonNode p) {
        if (p.path("content").asText().isBlank() || p.path("content").asText().length() > 2000) throw error("INVALID_INPUT", "Nội dung cần 1–2000 ký tự.");
        for (String letter : List.of("a", "b", "c", "d")) if (p.path("option_" + letter).asText().isBlank() || p.path("option_" + letter).asText().length() > 500) throw error("INVALID_INPUT", "Mỗi lựa chọn cần 1–500 ký tự.");
        if (!p.path("correct_answer").asText().matches("[ABCD]") || p.path("category").asText().isBlank() || p.path("category").asText().length() > 80) throw error("INVALID_INPUT", "Chọn một đáp án đúng và chủ đề tối đa 80 ký tự.");
    }
    private ObjectNode summary(ObjectNode q) {
        String content = q.path("content").asText();
        return object().put("question_id", q.path("question_id").asInt()).put("content_preview", content.length() > 110 ? content.substring(0, 110) + "…" : content)
            .put("category", q.path("category").asText()).put("is_active", q.path("is_active").asBoolean()).put("updated_at_utc", q.path("updated_at_utc").asText());
    }
    private JsonNode page(List<ObjectNode> values, JsonNode p, boolean question) {
        int size = p.path("page_size").asInt(10), page = p.path("page").asInt(1);
        if (size < 1 || size > 20 || page < 1) throw error("INVALID_PAGE", "Trang hoặc kích thước trang không hợp lệ.");
        var items = object().putArray("items");
        values.stream().skip((long)(page - 1) * size).limit(size).forEach(v -> items.add(question ? summary(v) : v.deepCopy()));
        return object().put("total", values.size()).put("page", page).put("page_size", size).put("has_more", (long)page * size < values.size()).set("items", items);
    }
    private ArrayNode array(List<? extends JsonNode> values) { var a = JsonNodeFactory.instance.arrayNode(); values.forEach(v -> a.add(v.deepCopy())); return a; }
    private void emit(String type, JsonNode payload) { if (!closed) listener.accept(object().put("type", type).set("payload", payload.deepCopy())); }
    private JsonNode state() {
        var players = array(List.of(object().put("player_id", "p1").put("nickname", nickname).put("ready", role.equals("STUDENT") ? ready : true).put("connected", true),
            object().put("player_id", "p2").put("nickname", "Quốc Bảo").put("ready", true).put("connected", true),
            object().put("player_id", "p3").put("nickname", "Thu Hà").put("ready", true).put("connected", true)));
        Map<String, String> claims = avatars.snapshot();
        for (JsonNode p : players) ((ObjectNode)p).put("avatar_id", claims.getOrDefault(p.path("player_id").asText(), ""));
        for (int i = players.size() - 1; i >= 0; i--) if (!claims.containsKey(players.get(i).path("player_id").asText())) players.remove(i);
        return object().put("room_code", roomExists ? code : "").put("room_name", name).put("phase", phase).put("is_owner", !role.equals("STUDENT"))
            .put("question_count", questionCount).put("duration_seconds", seconds).put("connected_player_count", players.size()).put("ready_count", java.util.stream.StreamSupport.stream(players.spliterator(), false).filter(p -> p.path("ready").asBoolean()).count())
            .put("answered_count", selected.isEmpty() ? 2 : 3).set("players", players);
    }
    private void startGame() {
        if (!roomExists || !phase.equals("WAITING")) throw error("INVALID_STATE", "Phòng chưa sẵn sàng.");
        if (role.equals("STUDENT") && !ready) throw error("NOT_READY", "Bấm Sẵn sàng trước khi chạy ván mẫu.");
        if (questions.stream().filter(q -> q.path("is_active").asBoolean()).count() < questionCount) throw error("NOT_ENOUGH_QUESTIONS", "Ngân hàng chưa đủ câu đang bật.");
        generation++; int token = generation; index = 0; score = 0; correctCount = 0; phase = "COUNTDOWN";
        session = "preview-" + generation;
        emit("GAME_START", object().put("session_id", session).put("remaining_ms", 3000)); emit("ROOM_STATE", state());
        worker.schedule(() -> openQuestion(token), 3, TimeUnit.SECONDS);
    }
    private void openQuestion(int token) {
        if (closed || token != generation) return;
        phase = "QUESTION_ACTIVE"; selected = "";
        current = questions.stream().filter(q -> q.path("is_active").asBoolean()).toList().get(index);
        ObjectNode publicQuestion = (ObjectNode) current.deepCopy(); publicQuestion.remove("correct_answer");
        publicQuestion.put("session_id", session).put("number", index + 1).put("total", questionCount).put("remaining_ms", seconds * 1000).put("score", score);
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        emit("QUESTION", publicQuestion); emit("ROOM_STATE", state());
        worker.schedule(() -> result(token), seconds, TimeUnit.SECONDS);
    }
    private ArrayNode ranking() {
        List<ObjectNode> ranks = new ArrayList<>(List.of(object().put("player_id", "p1").put("nickname", nickname).put("score", score).put("connected", true),
            object().put("player_id", "p2").put("nickname", "Quốc Bảo").put("score", Math.max(0, index * 100 - (index >= 2 ? 100 : 0))).put("connected", true),
            object().put("player_id", "p3").put("nickname", "Thu Hà").put("score", index >= 2 ? 200 : 0).put("connected", true)));
        ranks.sort(Comparator.comparingInt((ObjectNode r) -> r.path("score").asInt()).reversed());
        for (ObjectNode r : ranks) r.put("avatar_id", avatars.snapshot().getOrDefault(r.path("player_id").asText(), ""));
        int rank = 0, previous = -1;
        for (int i = 0; i < ranks.size(); i++) { int points = ranks.get(i).path("score").asInt(); if (points != previous) rank = i + 1; ranks.get(i).put("rank", rank); previous = points; }
        return array(ranks);
    }
    private void result(int token) {
        if (closed || token != generation) return;
        phase = "SHOW_RESULT"; boolean correct = selected.equals(current.path("correct_answer").asText());
        if (correct) { score += 100; correctCount++; } index++;
        var result = object().put("session_id", session).put("question_id", current.path("question_id").asInt()).put("correct_answer", current.path("correct_answer").asText())
            .put("your_answer", selected).put("correct", correct).put("score", score).put("points", correct ? 100 : 0).put("number", index).put("total", questionCount).put("remaining_ms", 3000);
        result.set("ranking", ranking()); result.set("answer_counts", object().put("A", 0).put("B", 2).put("C", 1).put("D", 0));
        emit("QUESTION_RESULT", result); emit("ROOM_STATE", state());
        worker.schedule(() -> {
            if (closed || token != generation) return;
            phase = "SHOW_RANKING";
            emit("ROUND_RANKING", object().put("session_id", session).put("question_id", current.path("question_id").asInt())
                .put("number", index).put("total", questionCount).put("remaining_ms", 4500).set("ranking", ranking()));
            emit("ROOM_STATE", state());
        }, 3, TimeUnit.SECONDS);
        worker.schedule(() -> {
            if (closed || token != generation) return;
            if (index < questionCount) openQuestion(token);
            else { phase = "FINISHED"; var end = object().put("session_id", session).put("score", score).put("correct_count", correctCount).put("total", questionCount).put("result_saved", false).put("preview", true); end.set("ranking", ranking()); emit("GAME_OVER", end); emit("ROOM_STATE", state()); }
        }, 7500, TimeUnit.MILLISECONDS);
    }
    private void seed() {
        users.add(object().put("user_id", 1).put("username", "admin").put("display_name", "Admin hệ thống").put("role", "ADMIN").put("is_active", true).put("must_change_password", false));
        users.add(object().put("user_id", 2).put("username", "giangvien.demo").put("display_name", "Phúc Thiện").put("role", "TEACHER").put("is_active", true).put("must_change_password", false));
        users.add(object().put("user_id", 3).put("username", "giangvien.b").put("display_name", "Viết Mỹ").put("role", "TEACHER").put("is_active", false).put("must_change_password", true));
        String[][] seed = {
            {"Giao thức nào đảm bảo truyền dữ liệu tin cậy?", "UDP", "TCP", "IP", "ICMP", "B", "TCP/IP"},
            {"Trong Java, lớp nào lắng nghe kết nối TCP?", "Socket", "ServerSocket", "DatagramPacket", "URL", "B", "Java Socket"},
            {"TCP truyền dữ liệu theo mô hình nào?", "Byte stream", "Từng message riêng", "Từng file riêng", "Ảnh nhị phân", "A", "TCP/IP"},
            {"Địa chỉ 127.0.0.1 dùng để làm gì?", "Broadcast LAN", "Gateway", "Loopback", "DNS công cộng", "C", "TCP/IP"},
            {"Phương thức nào gửi dữ liệu ra stream?", "read()", "write()", "accept()", "bind()", "B", "Java Socket"},
            {"TLS bảo vệ phần nào của kết nối?", "Nhiệt độ CPU", "Dung lượng DB", "Dữ liệu trên đường truyền", "Tốc độ ổ cứng", "C", "Bảo mật"},
            {"Mật khẩu nên lưu dưới dạng nào?", "Văn bản rõ", "MD5 đơn thuần", "Base64", "Hash chậm có salt", "D", "Bảo mật"},
            {"Ý nghĩa của port trong TCP là gì?", "Định danh điểm cuối dịch vụ", "Tên máy", "Subnet mask", "Độ dài dây mạng", "A", "TCP/IP"},
            {"UTF-8 giúp ứng dụng xử lý điều gì?", "Tiếng Việt và Unicode", "Tăng điện áp", "Tự mở firewall", "Nén TCP", "A", "Giao thức"},
            {"Một lần read() có luôn nhận đủ một JSON không?", "Có", "Không", "Chỉ trên LAN", "Chỉ khi dùng Java", "B", "Java Socket"},
            {"Vai trò của request_id là gì?", "Chấm điểm", "Mã hóa mật khẩu", "Đối chiếu phản hồi với yêu cầu", "Thay IP", "C", "Giao thức"},
            {"Ai quyết định deadline câu hỏi?", "Mỗi Client", "Server", "Wireshark", "Router", "B", "Giao thức"},
            {"PreparedStatement giúp xử lý vấn đề nào?", "Tham số SQL an toàn", "Mất điện", "Màn hình tối", "Sai SAN", "A", "Bảo mật"},
            {"Heartbeat dùng để làm gì?", "Phát nhạc", "Nén ảnh", "Đổi cổng", "Theo dõi tình trạng kết nối", "D", "Giao thức"},
            {"Lệnh accept() trả về đối tượng nào?", "Socket", "String", "File", "Thread", "A", "Java Socket"},
            {"Socket timeout giúp tránh điều gì?", "Địa chỉ trùng", "Chờ kết nối vô hạn", "Unicode", "Xếp hạng", "B", "Java Socket"},
            {"Ai được đọc file SQLite của ứng dụng?", "Mọi học sinh", "Mọi máy LAN", "Chỉ Server", "Wi-Fi", "C", "Bảo mật"},
            {"LAN có bắt buộc kết nối Internet không?", "Có", "Không", "Chỉ khi dùng TLS", "Chỉ khi dùng JavaFX", "B", "TCP/IP"},
            {"ACK đáp án xác nhận điều gì?", "Đáp án chắc chắn đúng", "Đã ghi nhận đáp án", "Ván đã kết thúc", "Được thưởng tốc độ", "B", "Giao thức"},
            {"Server kiểm tra quyền sở hữu câu hỏi dựa vào đâu?", "Role Client tự gửi", "Màu avatar", "Tên tab UI", "Tài khoản đã xác thực", "D", "Bảo mật"}
        };
        for (int i = 0; i < seed.length; i++) {
            var s = seed[i]; questions.add(object().put("question_id", i + 1).put("content", s[0]).put("option_a", s[1]).put("option_b", s[2]).put("option_c", s[3]).put("option_d", s[4])
                .put("correct_answer", s[5]).put("category", s[6]).put("is_active", i != 17).put("updated_at_utc", "2026-10-14T03:00:00Z"));
        }
    }
    @Override public void close() { closed = true; generation++; worker.shutdownNow(); }
}
