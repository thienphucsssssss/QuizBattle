# LAN Range Measurement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Hai sub-skill trên chưa có trong danh sách skill của phiên lập kế hoạch. Không giả định đã tải/chạy chúng; phiên triển khai dùng workflow trực tiếp, tuần tự và TDD hiện có. Không sử dụng subagent. Checklist chi tiết dưới đây giữ nội dung kế hoạch ban đầu; kết quả thực thi thực tế nằm ở cuối tài liệu.

**Goal:** Thêm công cụ kiểm tra tầm kết nối LAN thực nghiệm theo mốc mét người vận hành đo thật, kiểm tra hai phía chủ động gửi và lưu bằng chứng.

**Architecture:** Dùng kết nối TCP/TLS sẵn có và mở rộng nhóm LAN_RANGE_* trong protocol version 1; mỗi phía dùng đồng hồ monotonic của chính mình để đo RTT. Domain/nhật ký dùng chung, coordinator client độc lập JavaFX, session server chạy qua game worker không chặn; mỗi đầu lưu journal riêng. Dialog truy cập từ Cài đặt/Tùy chọn hiện có, giữ toàn bộ phong cách và luồng thi đấu.

**Tech Stack:** Java 21, JavaFX 21, Jackson, TCP/TLS, java.nio, Maven/JUnit 5; không thêm dependency hoặc database schema.

**Spec:** [Đặc tả cùng ngày](../specs/2026-10-25-lan-range-measurement.md). Đọc đặc tả trước khi làm task.

## Global Constraints

- distance_m: số hữu hạn từ 0 đến 500, tối đa một chữ số thập phân.
- duration_seconds: chỉ 30 hoặc 60, mặc định 30.
- interval_ms = 1000 và deadline_ms = 1000.
- Mỗi peer tối đa một lượt, server tối đa bốn lượt đồng thời.
- Một lượt đạt cần COMPLETED, đủ mẫu hai hướng, mỗi hướng >=95% đúng hạn, không lỗi gửi/ngắt/hủy/lưu.
- Giữ Java 21, JavaFX 21, protocol version 1, TCP 5050 và discovery UDP 5051.
- READY timeout 10 giây; COMPLETE timeout duration_seconds + 5 giây từ READY; client chờ FINISHED tối đa duration_seconds + 6 giây từ READY_OK, quá hạn là CONTROL_TIMEOUT.
- Không suy ra mét từ IP/ping/RSSI; không tự tạo IP, dữ liệu đo hoặc kết quả còn thiếu.
- IP lấy từ socket; timestamp UTC để lưu, monotonic riêng từng máy để tính RTT.
- Giữ PING/PONG, heartbeat, điểm số, TLS, database, tài khoản, chứng chỉ và giao diện hiện tại.
- Lưu journal ở cả hai đầu; mẫu server client chưa nhận phải ghi “chưa xác nhận”.
- Tôn trọng AGENTS.md và .cursor/rules/preserve-existing-ui-ux.mdc; chưa đụng dist/bộ cài/Server đang chạy.

## Review Focus

- Phản hồi trễ/trùng/sai sequence/run_id hoặc socket cũ không được tăng số mẫu thành công: Task 1, 2, 3.
- Mất sóng sau mẫu cuối nhưng trước FINISHED không được biến kết quả chiều server thành đủ/đạt: Task 4.
- Đĩa đầy, thư mục không ghi được hoặc JSONL cuối bị cắt vẫn giữ phần dữ liệu có thật và báo lỗi lưu: Task 1, 4.
- Game bắt đầu/reset/đóng phòng, logout hoặc đóng dialog phải hủy scheduler và giữ lượt một phần: Task 3, 4, 5.
- Hai đầu cùng máy, đổi tuyến/AP và form bị cắt ở cửa sổ nhỏ không được tạo kết luận thực địa sai hoặc che nút Dừng: Task 4, 5, 6.

## File map

Đường dẫn dưới đây tính từ gốc QuizBattle-git; không tách lại các màn hình đang có.

- Create nqb-shared/src/main/java/vn/edu/hutech/nqb/shared/LanRangeProtocol.java: constants, options, Sample/Direction/Status/EndReason và validation.
- Create nqb-shared/src/main/java/vn/edu/hutech/nqb/shared/LanRangeStatistics.java: tổng hợp mẫu, percentile, tiêu chí đạt và chọn mốc trong cùng survey.
- Create nqb-shared/src/main/java/vn/edu/hutech/nqb/shared/LanRangeJournal.java: JSONL append/replay có giới hạn hàng đợi; không chứa JavaFX.
- Modify nqb-client-core/src/main/java/vn/edu/hutech/nqb/client/TlsQuizGateway.java: timeout probe riêng, responder nội bộ.
- Create nqb-client-core/src/main/java/vn/edu/hutech/nqb/client/LanRangeClient.java: scheduler CLIENT_TO_SERVER, ghép kết quả được server báo, lifecycle.
- Create nqb-client-core/src/main/java/vn/edu/hutech/nqb/client/LanRangeReport.java: replay lịch sử, CSV tổng hợp/chi tiết và ghi xuất nguyên tử.
- Create nqb-server/src/main/java/vn/edu/hutech/nqb/server/LanRangeSession.java: scheduler/state SERVER_TO_CLIENT và journal server.
- Modify nqb-server/src/main/java/vn/edu/hutech/nqb/server/QuizServer.java: dispatch, xác thực, giới hạn, hook tick/lifecycle.
- Create nqb-admin-client/src/main/java/vn/edu/hutech/nqb/admin/LanRangeView.java: dialog, form, kết quả/lịch sử, file chooser.
- Modify nqb-admin-client/src/main/java/vn/edu/hutech/nqb/admin/ArcadeApplication.java: ownership coordinator, event fan-out, entry point và cleanup.
- Modify nqb-admin-client/src/main/java/vn/edu/hutech/nqb/admin/CommanderViews.java: nút công cụ trong Cài đặt.
- Tests tương ứng ở src/test/java cùng package; thêm LanRangeUiCheck và mở rộng LiveLanUiCheck.
- Create docs/Do_tam_ket_noi_LAN.md; update docs/Giao_thuc_Java_LAN.md, README.md và design-system/network-quiz-battle/MASTER.md khi thực thi.

## Task 1: Domain, công thức và nhật ký bền qua mất kết nối

**Files:** Create ba file shared trong file map; create LanRangeProtocolTest.java, LanRangeStatisticsTest.java, LanRangeJournalTest.java cùng package test.

**Interfaces:**

- LanRangeProtocol.Options(BigDecimal distanceMeters, int durationSeconds, String surveyId, String surveyName, String route, Topology topology, DistanceBasis distanceBasis, String notes).
- LanRangeProtocol.parseOptions(JsonNode) -> Options; optionsPayload(Options) -> ObjectNode; constants INTERVAL_MS=1000, DEADLINE_MS=1000, MAX_ACTIVE_RUNS=4.
- LanRangeProtocol.Sample(Direction direction, int sequence, Status status, Long rttNanos); rttNanos chỉ có khi ON_TIME. Options và enum theo spec mục 3–4.
- LanRangeStatistics.summarize(Direction, int planned, Collection<Sample>) -> ObjectNode; keys planned, confirmed, on_time, timeout, send_failed, disconnected, cancelled, unconfirmed, on_time_rate, rtt_min_ms, rtt_median_ms, rtt_p95_ms, rtt_max_ms.
- LanRangeStatistics.passes(JsonNode clientSummary, JsonNode serverSummary, EndReason, boolean journalsHealthy) -> boolean. Hàm chỉ true khi đủ điều kiện spec; phép chọn max mốc ở Task 4 còn loại loopback và nhóm survey.
- new LanRangeJournal(Path directory, String origin, String runId); append(ObjectNode) -> CompletableFuture<Void>; closeAsync() -> CompletableFuture<Void>; replay(Path) -> List<JsonNode>. Queue 256; lỗi phải complete exceptionally.

- [ ] **1. Viết test:** options chấp nhận 0/30/31.5/500 và 30/60 giây; từ chối âm/500.1/31.55/duration=31/survey rỗng. summarize dùng mẫu cố định 1..30, 29 ON_TIME + 1 TIMEOUT => 29/30 và đạt; 28/30 không đạt; 57/60 đạt. Median của 10,20,30,40 ms =25; p95=40; không RTT => null; mẫu trùng không tăng confirmed; sequence ngoài phạm vi bị từ chối.
- [ ] **2. Viết test journal:** append ba record rồi đọc đúng thứ tự/schema; thư mục không ghi được, queue full và writer lỗi đều trả lỗi, không báo thành công; file bị cắt cuối giữ dòng hoàn chỉnh; closeAsync flush các record đã nhận.
- [ ] **3. Chạy để thấy FAIL:** mvn -B -ntp -pl nqb-shared "-Dtest=LanRange*Test" "-Dsurefire.failIfNoSpecifiedTests=false" test. Expected: thiếu classes/hành vi mới, không phải lỗi môi trường.
- [ ] **4. Triển khai đúng các interface trên:** options không chứa IP do client tự khai; dùng Jackson có sẵn, percentile nearest rank; journal executor I/O riêng, không im lặng bỏ mẫu.
- [ ] **5. Chạy lại cùng lệnh:** PASS các test mới và chạy toàn bộ test nqb-shared; review thay đổi rồi commit riêng với message feat: add LAN range domain and journals.

Assertion mẫu cần có trong LanRangeStatisticsTest; dữ liệu dưới đây chỉ là fixture kiểm thử:

```java
var samples = java.util.stream.IntStream.rangeClosed(1, 30)
    .mapToObj(i -> new LanRangeProtocol.Sample(
        LanRangeProtocol.Direction.CLIENT_TO_SERVER, i,
        i == 30 ? LanRangeProtocol.Status.TIMEOUT : LanRangeProtocol.Status.ON_TIME,
        i == 30 ? null : 10_000_000L)).toList();
var summary = LanRangeStatistics.summarize(
    LanRangeProtocol.Direction.CLIENT_TO_SERVER, 30, samples);
assertEquals(29, summary.path("on_time").asInt());
assertEquals(1, summary.path("timeout").asInt());
assertEquals(0, summary.path("unconfirmed").asInt());
assertEquals(10.0, summary.path("rtt_p95_ms").asDouble());
assertTrue(LanRangeStatistics.passes(summary, summary,
    LanRangeProtocol.EndReason.COMPLETED, true));
```

Trong passes(), so sánh tỷ lệ bằng số nguyên on_time * 100 >= 95 * planned; không làm tròn phần trăm hiển thị trước khi quyết định đạt.

## Task 2: Gateway trả lời challenge ngoài JavaFX

**Files:** Modify TlsQuizGateway.java; extend TlsQuizGatewayTest.java. Không đổi public QuizGateway interface hoặc cách dùng của các màn hình khác.

**Consumes:** LanRangeProtocol và JsonMessages hiện có.

**Produces:** request("LAN_RANGE_PROBE", payload) timeout 1000 ms; START_OK gắn active run; CHALLENGE được trả lời một chiều qua outbound queue; FINISHED/close giải phóng responder.

- [ ] **1. Thêm TLS tests:** peer gửi START_OK rồi challenge ngay lập tức, nhận CHALLENGE_REPLY cùng request_id/run_id/sequence khi onEvent bị giữ hoặc chưa được đăng ký. Thử challenge giả dùng request_id trùng một RPC khác: không complete/đánh cắp future đó.
- [ ] **2. Thêm tests:** duplicate/stale/out-of-range challenge chỉ nhận tối đa một reply hợp lệ; trước START_OK và sau FINISHED không reply; probe timeout dọn pending, phản hồi trễ không trở thành một mẫu mới; PING/RPC thường giữ cấu hình timeout cũ.
- [ ] **3. Chạy FAIL:** mvn -B -ntp -pl nqb-client-core -am "-Dtest=TlsQuizGatewayTest" "-Dsurefire.failIfNoSpecifiedTests=false" test.
- [ ] **4. Triển khai private handleRangeControl(JsonNode) -> boolean trước pending.remove trong read():** bind START_OK trước future.complete; validate active run và sequence; encode CHALLENGE_REPLY qua outbound, không gọi request() và không chờ FX. Giữ DISCONNECTED duy nhất và cleanup hiện có.
- [ ] **5. Chạy lại test:** PASS cả kiểm tra TLS/SAN/correlation cũ; review rồi commit feat: respond to server LAN range probes in gateway.

## Task 3: Session server, quyền và giới hạn

**Files:** Create LanRangeSession.java, LanRangeSessionTest.java trong server; modify QuizServer.java và ServerIntegrationTest.java.

**Consumes:** Task 1; gateway Task 2 dùng cho kiểm thử TLS.

**Interfaces:**

- new LanRangeSession(String runId, Options options, Consumer<ObjectNode> send, LanRangeJournal journal, LongSupplier nanoClock, Clock wallClock).
- start() -> void; clientProbe(int sequence) -> ObjectNode; challengeReply(String requestId, int sequence) -> void; clientComplete() -> void; tick() -> void; finish(EndReason) -> void; finished() -> boolean. start() ứng với READY, clientComplete() ứng với COMPLETE; cả hai idempotent.
- Session giới hạn sequence/correlation, giữ map kết quả đã chốt, không giữ lịch sử không giới hạn. QuizServer.Peer sở hữu session và cấu hình/cặp địa chỉ socket của lượt đó; không dùng map tra tùy ý run_id của peer khác.
- Server dùng journal path config.database().toAbsolutePath().getParent().resolve("lan-range"). QuizServer ghi metadata RUN_STARTED từ Options và socket trước START_OK; READY ghi RUN_READY và enqueue READY_OK trước start() phát challenge. Các completion I/O đưa lại game worker, không block worker.

- [ ] **1. Viết unit test với fake nanoClock:** N=30/60; trước READY không phát mẫu; READY lặp không reset; advance clock từng giây tạo đúng N challenge; RTT theo cùng clock; deadline/trễ/trùng/sai id không chốt lại; tick trễ không phát dồn; chiều server xong trước COMPLETE chưa phát FINISHED; CONTROL_TIMEOUT đúng hạn; finish STOPPED/GAME_STARTED chỉ một lần.
- [ ] **2. Thêm real TLS integration tests:** LOGIN quản lý không có phòng vẫn START được; STUDENT đã JOIN được START; chưa auth/mustChange/running bị từ chối; lượt thứ hai cùng peer và lượt thứ năm toàn server bị BUSY. IP tự khai không thay socket IP trả về; peer khác không PROBE/STOP được lượt đang đo.
- [ ] **3. Thêm lifecycle tests:** START_GAME hủy đo nhưng ván vẫn bắt đầu; ROOM_RESET/CLOSE, LEAVE/LOGOUT, socket đứt, server close kết thúc session và flush journal. Hook phải chạy trước early-return trong detach() cả với manager không có playerId. Không đưa dữ liệu đo vào ROOM_STATE/discovery/ranking.
- [ ] **4. Chạy FAIL:** mvn -B -ntp -pl nqb-server -am "-Dtest=LanRangeSessionTest,ServerIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test.
- [ ] **5. Triển khai:** dispatch nhóm LAN_RANGE_* sau auth/mustChange và trước nhóm game; challenge reply dùng request_id đã phát, không phản hồi RPC thừa. START chỉ chuẩn bị; READY mới chạy, COMPLETE chỉ xác nhận chiều client xong. Tick session từ game worker 50 ms hiện có, kiểm tra READY/COMPLETE timeout. Xử lý completion I/O bằng enqueue worker và kiểm tra peer/session còn đúng; giữ tối đa bốn lượt gồm cả lượt đang chuẩn bị lưu. FINISHED có đủ trạng thái lưu chỉ gửi sau RUN_FINISHED đã flush hoặc biết lỗi ghi.
- [ ] **6. Chạy lại test:** PASS cả gameplay, IP tracking và quyền cũ; journal server chỉ ghi RTT SERVER_TO_CLIENT tự đo, FINISHED chứa kết quả thật và trạng thái lưu. Review rồi commit feat: add server initiated LAN range sessions.

Giữ một bài integration hoàn tất đủ 30 giây bằng TLS thật; các trường hợp quyền/lifecycle dừng sớm, còn biên thời gian 30/60 giây dùng fake clock của LanRangeSessionTest để tránh lặp nhiều bài chờ thực.

## Task 4: Coordinator client, khôi phục và CSV

**Files:** Create LanRangeClient.java, LanRangeReport.java cùng test trong client-core.

**Consumes:** Tasks 1–3 và QuizGateway.request/onEvent hiện có.

**Interfaces:**

- new LanRangeClient(QuizGateway gateway, Path journalDirectory, Consumer<JsonNode> onSnapshot).
- start(Options options) -> CompletableFuture<JsonNode>; onEvent(JsonNode) -> void; stop(EndReason) -> CompletableFuture<Void>; snapshot() -> JsonNode; close() -> void.
- start() thực hiện START -> lưu RUN_STARTED client -> READY; future hoàn thành khi nhận READY_OK và scheduler bắt đầu. Chốt mọi mẫu client rồi gửi COMPLETE; FINISHED server không thay thế bước này. Callback snapshot là immutable deep copy, chưa chuyển sang FX.
- LanRangeReport.load(Path directory) -> List<JsonNode>; farthestPassing(List<JsonNode>, String surveyId) -> Optional<BigDecimal>; exportSummary(Path target, List<JsonNode>) -> void; exportSamples(Path target, List<JsonNode>) -> void.
- Snapshot gồm options/run_id/IP, client_summary, server_summary, server_summary_received, server_journal_healthy, client_journal_healthy, end_reason, loopback, saved và sample arrays. Khi đang chạy end_reason=null; journal có metadata và cùng record schema của spec.

- [ ] **1. Viết coordinator tests:** đúng 30/60 probe, mỗi future chỉ chốt một sample; lấy nanoTime lúc request/complete trên thread nền, không sau Platform.runLater; stop/disconnect dừng schedule; late future từ run cũ không sửa run mới.
- [ ] **2. Viết completeness tests:** ghi journal chậm chưa phát READY/probe; đủ C2S nhưng không nhận SERVER_SAMPLE/FINISHED => chiều server chưa xác nhận và không đạt; gửi COMPLETE đúng một lần sau mẫu cuối, client watchdog hết hạn thành CONTROL_TIMEOUT. FINISHED bất thường tới trước deadline mẫu client cuối không được đạt; FINISHED báo lỗi journal không đạt; callback DISCONNECTED sau khi hai đầu đã tổng kết và journal client flush thành công không sửa kết quả đã chốt.
- [ ] **3. Viết replay/export tests:** restart đọc lại mẫu thật, lượt thiếu FINISHED thành APP_CLOSED; sample server thiếu vẫn unconfirmed; max mốc tách survey và loại loopback/incomplete. CSV giữ tiếng Việt, quote/newline/null; text mở đầu =,+,-,@ không thành formula; hủy lưu không tạo file; lỗi ghi không thay file đích đang có.
- [ ] **4. Chạy FAIL:** mvn -B -ntp -pl nqb-client-core -am "-Dtest=LanRangeClientTest,LanRangeReportTest" "-Dsurefire.failIfNoSpecifiedTests=false" test.
- [ ] **5. Triển khai:** scheduler và I/O tuần tự ngoài FX; onEvent chỉ enqueue việc ngắn, không block socket reader; mỗi run tự lưu metadata/RUN_READY/mẫu/kết thúc. Nếu client journal preflight lỗi thì không START; server START thành công nhưng journal client lỗi thì gửi STOP, báo STORAGE_ERROR. Chỉ công bố kết quả đạt sau journal client RUN_FINISHED flush thành công. File server chưa nhận không được tự đồng bộ giả; UI giữ server_summary_received=false.
- [ ] **6. Chạy lại test:** PASS; test logic dùng fake clock/gateway, transport thật đã được kiểm tra tại Task 2–3; review rồi commit feat: record LAN range runs and export reports.

## Task 5: Dialog tích hợp vào UI hiện có

**Files:** Create LanRangeView.java, LanRangeUiCheck.java, LanRangeViewModelTest.java; modify ArcadeApplication.java, CommanderViews.java, LiveLanUiCheck.java. Chỉ thêm CSS có scope lan-range nếu FxKit chưa đủ.

**Consumes:** Options, LanRangeClient, LanRangeReport và snapshot Task 4.

**Interfaces:**

- new LanRangeView(Stage owner, Supplier<LanRangeClient> currentClient, Path journalDirectory, BooleanSupplier canStart); show() -> void; update(JsonNode snapshot) -> void; connectionChanged() -> void. Predicate gắn danh tính/pha thi/kết nối thực tế của ứng dụng, khóa Bắt đầu ngay khi mất kết nối.
- ArcadeApplication.openLanRange() package-private; giữ một dialog và một coordinator gắn gateway hiện hành. Lịch sử lấy từ journal, không nằm trong đối tượng gateway.
- static LanRangeView.parseDistance(String) -> BigDecimal: hỗ trợ 30/30.5/30,5; mọi lỗi hiện cạnh trường. Không chứa I/O.
- attachEvents fan-out LAN_RANGE_* và DISCONNECTED tới coordinator đã capture của connectionGeneration trước Platform.runLater; UI vẫn dùng generation guard. Close/logout ngừng coordinator cũ; callbacks/journal không gắn lại vào gateway mới.

- [ ] **1. Viết tests form/model:** input hợp lệ/âm/rỗng/sai thập phân; đang đo khóa cấu hình/Bắt đầu, còn Dừng; incomplete/loopback không hiện mốc thực địa; sau logout/mustChange/game running không START; trạng thái saved không hiện như live.
- [ ] **2. Mở rộng LiveLanUiCheck trước khi thêm nút:** từ Cài đặt mở dialog, START trên Server TLS thật, thấy IP/tiến độ hai hướng; disconnect và mở lại vẫn thấy lượt đã lưu. UNKNOWN_TYPE hiện thông báo Server cũ và không tạo kết quả.
- [ ] **3. Chạy FAIL:** targeted JUnit bằng Maven; mvn -B -ntp install rồi mvn -B -ntp -pl nqb-admin-client exec:java "-Dexec.mainClass=vn.edu.hutech.nqb.admin.LiveLanUiCheck" "-Dexec.classpathScope=test". Expected: thiếu hành động/control mới; lưu log để phân biệt lỗi setup với hành vi chưa làm.
- [ ] **4. Triển khai form/control:** thêm nhóm “Mạng LAN” trong CommanderViews.settings(); thêm nút cùng công cụ vào Tùy chọn học sinh khi đã JOIN. Dialog có input nhãn rõ, bảng/lịch sử native, giải thích khoảng cách nhập tay, tiến độ từng hướng, lý do dừng, đường dẫn journal và footer cố định. Không đổi sidebar.
- [ ] **5. Triển khai vòng đời:** đóng dialog gọi stop(STOPPED), giữ row; GAME_STARTED/ROOM_CHANGED/APP_CLOSED đi đúng EndReason; DISCONNECTED khóa Bắt đầu nhưng vẫn xuất dữ liệu; cấu hình đợt thay đổi tạo survey_id mới; mở dialog lại không tạo scheduler mới.
- [ ] **6. Chạy JUnit và UI check:** mvn -B -ntp -pl nqb-admin-client -am "-Dtest=LanRangeViewModelTest" "-Dsurefire.failIfNoSpecifiedTests=false" test; chạy LanRangeUiCheck bằng exec:java test classpath, tạo snapshot ở 1366x768 và 1024x640 trong tmp/lan-range-ui/. Kiểm tra keyboard/tab/error, tên dài, footer/Dừng luôn tới được; xem PNG và sửa lỗi trước khi commit feat: add LAN range measurement dialog.

## Task 6: Tài liệu, hồi quy và nghiệm thu thực địa

**Files:** Create docs/Do_tam_ket_noi_LAN.md; modify docs/Giao_thuc_Java_LAN.md, README.md, design-system/network-quiz-battle/MASTER.md. Update chính plan này bằng kết quả thực thi, không đánh dấu thay cho test chưa chạy.

**Consumes:** Tính năng đã triển khai Tasks 1–5.

**Produces:** hướng dẫn người dùng, hợp đồng giao thức chính thức, kết quả regression và checklist thực địa còn trống đến khi thực sự đi đo.

- [ ] **1. Viết hướng dẫn:** hai máy vật lý, giữ AP/server và tuyến đo, phân biệt đường thẳng/hành lang, đo thước rồi nhập mét; 30/60 giây, 95%/1000 ms, RTT khứ hồi theo phía khởi tạo; journal hai đầu và cách đối chiếu run_id. Nêu rõ thiếu summary server là chưa xác nhận, phải đo lại để kết luận mốc.
- [ ] **2. Cập nhật giao thức:** các LAN_RANGE_* payload/errors/giới hạn, timeout riêng, auth/lifecycle, không có dữ liệu đo trong discovery/ROOM_STATE. Ghi server cũ cần cập nhật cả hai đầu.
- [ ] **3. Kiểm tra regression:** mvn -B -ntp verify; sau khi pass chạy mvn -B -ntp install để live QA dùng đúng module mới; chạy LiveLanUiCheck và LanRangeUiCheck từ test classpath. Không dùng kết quả cũ làm bằng chứng cho source mới.
- [ ] **4. Review diff:** chỉ feature/tài liệu đã định; không thay chứng chỉ, config cá nhân, database, bộ cài, firewall. Kiểm tra không có số mét được bịa trong runtime hoặc báo cáo. Commit docs: document LAN range measurement and acceptance.
- [ ] **5. Nghiệm thu trên hai máy khi có thiết bị/người vận hành:** đặt router cạnh server hoặc server hotspot; client ở mốc đo bằng thước; thử 5/10/15... m và từng mét gần biên, ít nhất ba lượt/mốc gần biên; kiểm tra hai hướng, rớt sóng/quay về, app restart, CSV/journal hai đầu. Không giả định trước mốc 30/31 m.
- [ ] **6. Ghi kết quả:** ảnh bố trí, cách đo khoảng cách/vật cản/Wi-Fi, thời điểm, IP thực tế, file bằng chứng, mốc đạt/không đạt. Nếu chưa đi đo, kết luận chỉ “đã kiểm tra phần mềm trên loopback/TLS”, để nghiệm thu vật lý ở trạng thái chưa hoàn thành.

## Trình tự và phạm vi bàn giao

Task 1 -> Task 2 -> Task 3 -> Task 4 -> Task 5 -> Task 6. Các interface cần nhất quán nên ưu tiên triển khai tuần tự trong cùng chat. Commit ở mỗi task là checkpoint khi thực thi; bản kế hoạch không tự stage/commit/push hay tạo PR.

Tách trạng thái hoàn thành phần mềm khỏi nghiệm thu thực địa. Build/test trên máy hiện tại không thể tạo bằng chứng về số mét của tầng học. Việc đóng gói/cập nhật Server đang dùng chỉ làm sau khi source/QA đạt và người dùng yêu cầu triển khai bản chạy.

## Kết quả thực thi 25/10/2026

- [x] Triển khai domain/options/thống kê và journal JSONL hai đầu.
- [x] Gateway tự trả lời challenge trên reader, timeout riêng cho probe và giữ TLS/PING cũ.
- [x] Server quản lý phiên theo socket, quyền truy cập, giới hạn bốn lượt, vòng đời phòng/thi và lấy IP socket thực.
- [x] Client đo RTT bằng nanoTime, giữ dữ liệu một phần, đọc lại journal, xuất CSV và loại lượt cục bộ khỏi mốc xa nhất.
- [x] Dialog trong Cài đặt/Tùy chọn, nhập số theo tiếng Việt, lịch sử, footer cố định và khóa Bắt đầu ngay khi mất kết nối.
- [x] Hướng dẫn người dùng, README, giao thức và quy ước thiết kế đã cập nhật.
- [x] Maven verify: 167 tests, failures=0, errors=0, skipped=0; cả sáu reactor projects BUILD SUCCESS. Có lượt thực hiện đủ 30 mẫu TLS thật mỗi hướng trên cùng máy/IP.
- [x] Maven install sau verify (skip chạy lại tests) để live QA nạp đúng module mới; JUnit form được chạy lại cùng lượt kiểm tra UI cuối.
- [x] LAN_RANGE_UI_PASS: TLS thật hai chiều, nhãn cục bộ, dữ liệu nhập sai, dừng/mở lại, CSV, mất kết nối và kích thước 1366×768/1024×640. Ảnh được mở kiểm tra trực quan trong tmp/lan-range-ui/.
- [x] LiveLanUiCheck hồi quy: COMMANDER_UI_PASS, QUESTION_IMPORT_UI_PASS, SERVER_COMPATIBILITY_UI_PASS và LIVE_LAN_UI_PASS. Ảnh trong tmp/lan-range-regression/.
- [ ] Nghiệm thu khoảng cách trên hai máy vật lý trong một tầng: chưa thực hiện vì người dùng hiện có một máy. Không có số mét thực địa được công bố.

Test fail trước implementation đã được ghi nhận cho domain thiếu class, gateway chưa trả challenge/timeout, server chưa hỗ trợ LAN_RANGE_* và UI chưa có điểm mở. Các bài test mới dùng địa chỉ loopback thật trong TLS cục bộ; không tạo địa chỉ hoặc RTT giả trong runtime. Fixture chỉ nằm trên test classpath.

Phạm vi bàn giao là mã nguồn/tài liệu và JAR build. Chưa tạo commit/push/PR, chưa thay bộ cài hoặc restart Server đang dùng. Các thay đổi script launcher/tài liệu khác xuất hiện song song trong workspace được giữ nguyên, không gộp vào phạm vi tính năng này.
