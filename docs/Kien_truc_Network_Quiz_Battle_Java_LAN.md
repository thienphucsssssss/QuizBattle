# Kiến trúc Network Quiz Battle bằng Java trong LAN

Phiên bản 4.2 — ngày 14/10/2026. Bổ sung phạm vi quản lý giảng viên, lọc/phân trang và cảnh báo câu hỏi trùng; vẫn Java/LAN/TLS, không hosting. Đọc `Boi_canh_va_yeu_cau_Java_LAN.md` và `Ke_hoach_trien_khai_Java_LAN.md` cho yêu cầu/plan mới. Đây là thiết kế mục tiêu, chưa phải chức năng Java đã triển khai; đề cương Word chưa sửa trong đợt chuẩn bị plan này.

Cập nhật 16/10/2026: đã có Server nghiệp vụ TLS, SQLite, auth worker, game worker và JavaFX kết nối thật; SFX Java Sound cục bộ. Runtime vẫn Java/LAN/5 module/Server-only DB. Server chỉ phụ thuộc shared/Jackson/SQLite; dependency client-core trong Server và Server trong admin-client đều là **test scope**, không nằm trong bản runtime tương ứng. Setup hiện ghim chứng chỉ Server tự ký trong truststore, thay cho phương án CA riêng được mô tả trong thiết kế cũ bên dưới. Xem `Chay_LAN_va_kiem_thu.md` và `Nghiem_thu_Server_LAN.md`.

## 1 Phạm vi

Server và Client đều viết bằng Java 21. Một Server console trong LAN; hai ứng dụng JavaFX là Client quản lý (giảng viên/Admin) và Client học sinh (Player). TCP Socket qua TLS là kênh truyền trực tiếp, không thay bằng HTTP, WebSocket, Discord hay nền tảng quiz có sẵn.

Nhiều tài khoản ADMIN/TEACHER, nhưng chỉ một phòng có một chủ phòng đã đăng nhập tại một thời điểm; tối đa 10 Player theo cấu hình ban đầu. Học sinh nhập nickname/mã phòng, không đăng ký tài khoản. Start cần ít nhất một Player đang kết nối và tất cả ready; nghiệm thu dùng ít nhất ba Player trên ít nhất hai máy vật lý.

Ngân hàng có câu hỏi bốn lựa chọn, một đáp án đúng; tài khoản quản lý chỉ CRUD/tìm kiếm ngân hàng và lịch sử chung cuộc của mình. Admin cấp/khóa/reset tài khoản giảng viên. Không làm tài khoản học sinh, tự đăng ký tài khoản quản trị, nhiều phòng, đồng bộ giữa các Server, khôi phục phiên Player, web/mobile, chat, lưu từng đáp án hay chống gian lận nâng cao. Nhập CSV/bộ cài là mở rộng sau MVP.

## 2 Sơ đồ triển khai

```text
Giảng viên/Admin JavaFX ─┐
Học sinh 1 ──────────────┼── Switch/Wi-Fi LAN ── Java Server ── SQLite
Học sinh 2..10 ──────────┘     TCP/TLS 5050         console       local
```

Ví dụ Server có IP `192.168.1.10`, Client nhập `192.168.1.10:5050`. Đây chỉ là địa chỉ minh họa; lấy IP thật từ adapter mạng của máy Server. Khi thử trên một máy, dùng `127.0.0.1:5050`.

Server có thể bind `0.0.0.0` để nhận kết nối từ các adapter hoặc bind IP LAN cụ thể. Bind wildcard không phải kiểm soát truy cập: chỉ dùng mạng tin cậy và giới hạn firewall theo mạng nội bộ. Không forward port router, không dùng domain/public IP. Không tự thay đổi firewall hoặc cấu hình mạng của người dùng.

Client quản lý có thể chạy cùng máy Server hoặc máy khác trong LAN, nhưng luôn gọi API qua Socket. Chỉ Server sở hữu SQLite. Đóng Client không dừng Server; dừng Server ngắt mọi Client và bỏ ván đang thi. Tài khoản/ngân hàng giữ trên Server này, không tự có trên Server khác.

## 3 Công nghệ

| Vai trò | Lựa chọn |
| --- | --- |
| Runtime và build | JDK 21, Maven 3.9.x, UTF-8 cho source/resources/report |
| Giao diện | JavaFX 21, CSS và controls chuẩn; chỉ module desktop cần JavaFX |
| Kết nối | TCP qua JSSE `SSLServerSocket`/`SSLSocket`, InputStream/OutputStream |
| JSON | Jackson, DTO/JsonNode rõ kiểu, không bật default typing |
| Đồng thời | Thread/ExecutorService, BlockingQueue có giới hạn |
| Thời gian | `System.nanoTime()` đo deadline; Instant cho thời điểm lịch sử |
| Dữ liệu | SQLite qua Xerial SQLite JDBC, PreparedStatement và transaction |
| Xác thực | PBKDF2WithHmacSHA256, SecureRandom salt, tài khoản SQLite, role/owner theo connection |
| Kiểm thử | JUnit 5, socket localhost, JavaFX manual smoke, Wireshark LAN |

Version dependency cụ thể được khóa tại `pom.xml`, không dùng bản EA. Không đưa JavaFX vào Server hoặc client-core. Cần kiểm thử lại SQLite native library và JavaFX runtime trên hệ điều hành dùng demo trước khi đóng gói.

## 4 Module và luồng phụ thuộc

```text
nqb-server ────────────> nqb-shared
nqb-admin-client ──────> nqb-client-core ───> nqb-shared
nqb-player-client ──┬─> nqb-admin-client (presentation dùng chung)
                   └─> nqb-client-core
```

`nqb-shared`: envelope/message types, framing, model và giới hạn protocol. Không biết SQLite hoặc JavaFX.

`nqb-server`: networking/TcpServer, connection, dispatch/auth, game/GameWorker, scoring, repository/SQLite, config/log. Game Worker là chủ sở hữu duy nhất của trạng thái ván và JDBC connection.

`nqb-client-core`: TCP/TLS client, truststore, read/write workers, bounded queues, heartbeat, request correlation và listener. Không chạm control JavaFX hoặc chứa quy tắc chấm điểm.

`nqb-admin-client`: Client quản lý chung cho TEACHER/ADMIN, bootstrap JavaFX, controllers/views/CSS, chọn vai trò, kết nối/đăng nhập, Phòng thi, Ngân hàng riêng, Lịch sử, Đổi mật khẩu. ADMIN có thêm tab Tài khoản. Giữ tên module hiện tại.

`nqb-player-client`: Client học sinh, bootstrap JavaFX, chọn vai trò, kết nối/join bằng nickname/mã phòng, lobby/ready, quiz và kết quả. Không tạo tài khoản học sinh. Màn hình chọn vai trò có cùng bố cục ở hai launcher; phần JavaFX vẫn chỉ nằm trong hai module desktop, không thêm module thứ sáu. UI quyết định trang theo sự kiện Server.

Hiện presentation dùng chung được đặt trong admin-client; player-client phụ thuộc module đó để tái sử dụng view, không có hai bản controller trùng. Quyền không đến từ dependency/JAR hay vai trò UI: Server vẫn phải kiểm tra connection/user/owner. client-core không import JavaFX; CSS/font trong resources chỉ là tài nguyên. PreviewGateway trong core mô phỏng game để xem UI, không được dùng làm Server hoặc cơ chế auth/scoring của kết nối thật.

Mỗi module dùng source/test/resource Maven chuẩn. Ban đầu dùng classpath, không thêm JPMS để giảm lỗi phản chiếu Jackson/JavaFX. JAR hiện tại là JAR mỏng; đóng gói runtime bằng jpackage là việc sau, không cần cho giai đoạn xây chức năng.

## 5 TCP và đồng thời

Accept thread nhận SSLSocket từ SSLServerSocket rồi chuyển handshake tới worker có queue hữu hạn, timeout 5 giây; không handshake trong accept loop. Giới hạn connection tính cả kết nối đang handshake. Sau TLS hợp lệ, mỗi connection có reader/writer riêng; reader ghép byte/parse và gửi event, writer duy nhất ghi OutputStream từ outbound queue để không trộn byte.

Game Worker tuần tự lấy event, kiểm tra quyền/trạng thái, điều phối ván và phát message bằng thao tác `offer` không chặn. Worker này không ghi socket trực tiếp. Outbound queue đầy thì đóng connection chậm và gửi event disconnect; không làm Game Worker chờ một Client. Một ván có tối đa 10 Player nên thread-per-connection dễ giải thích và đủ cho mục tiêu đồ án.

PBKDF2 không chạy trên Game Worker/JavaFX thread. Game Worker đọc user, chuyển snapshot hash/tham số sang auth executor (dự kiến 2 worker, queue 16), nhận AuthResult event rồi kiểm tra connection generation, is_active và credential_version trước khi gắn danh tính. Auth executor không dùng JDBC connection của Game Worker; login/đổi/reset mật khẩu đều theo luồng này. Queue đầy trả SERVER_BUSY; auth result tới sau Logout hoặc đóng connection phải bị bỏ qua.

Giới hạn dự kiến: 32 connection tổng, queue gửi 100 message/connection, queue sự kiện 1024 event. Khi event queue đầy, từ chối/đóng connection gây tải; cần test và log rõ. Chỉ reader đưa message đã có envelope hợp lệ vào queue; payload và quyền vẫn được kiểm tra tại Server.

Game Worker kiểm tra `System.nanoTime()` tại lúc xử lý ANSWER so với deadline; message chờ quá hạn trong queue vẫn bị từ chối. Reader có thể ghi thời điểm nhận cho log, nhưng không dùng timestamp Client làm căn cứ. Quy tắc này đơn giản và nhất quán; cần đo thời gian xử lý queue ở tải 10 Player. Không gửi giá trị nanoTime tuyệt đối sang Client để so sánh giữa hai máy; QUESTION gửi thời lượng/remaining_ms để hiển thị. Xử lý đúng tại hoặc sau deadline bị từ chối.

JavaFX cập nhật qua `Platform.runLater`; connect/read/write/query dài không chạy trên JavaFX Application Thread. Bảo vệ kết nối mới khỏi callback của connection cũ bằng connection generation hoặc session token cục bộ.

## 6 Protocol và quyền

TCP là byte stream, không bảo toàn ranh giới message. JSON UTF-8 gọn một dòng, kết thúc byte LF `0x0A`; nội dung xuống dòng trong câu hỏi được JSON escape. Frame tối đa 64 KiB không tính LF. Receiver giữ buffer byte qua nhiều lần read, chỉ decode UTF-8/parse khi có LF; xử lý được nhiều frame trong một read. Không dùng Java object serialization hoặc `writeUTF` làm framing.

Envelope có `protocol_version`, `type`, `request_id`, `room_code`, `session_id`, `payload`. ID yêu cầu phục vụ đối chiếu, `session_id` là ID ván. Trường không áp dụng có thể bỏ qua theo schema; `payload` luôn object. Unknown type, JSON/UTF-8 sai, thiếu trường, quá lớn, sai phiên/câu hoặc sai quyền bị từ chối với mã lỗi; framing hỏng nghiêm trọng đóng riêng connection.

LOGIN nhận username/password qua TLS; Server lấy role từ users và gắn user_id/role vào connection sau xác thực. Chọn vai trò ở UI hoặc tự gửi role/owner không cấp quyền. Học sinh JOIN rồi nhận player_id gắn Socket. Một connection không đồng thời là user quản lý và Player; đổi luồng cần thoát/kết nối mới. Không có API tải DB.

Mọi API câu hỏi/lịch sử lọc owner từ user_id của connection, gồm list/search/get theo ID. UPDATE/DELETE dùng `WHERE id = ? AND owner_user_id = ?`, PreparedStatement; CREATE gán owner tại Server. ADMIN quản lý tài khoản nhưng chỉ dùng ngân hàng của mình, không mặc nhiên đọc/sửa ngân hàng khác. API không trả hash/salt. QUESTION cho Player không chứa correct_answer trước khi đóng câu; Client quản lý chỉ xem đáp án trong ngân hàng mình.

PBKDF2 bảo vệ mật khẩu lưu; TLS bảo vệ đường truyền. TLS không mã hóa SQLite và không tự chống gian lận ở máy học sinh. Chi tiết tài khoản/chứng chỉ ở mục 13.

## 7 Game Worker và scoring

```text
WAITING → COUNTDOWN → QUESTION_ACTIVE → SHOW_RESULT → SHOW_RANKING
                                                      ├→ QUESTION_ACTIVE
                                                      └→ FINISHED → WAITING khi reset
```

ROOM_CONFIGURE khi phòng chưa có owner sẽ nhận chủ phòng từ user_id đã xác thực; xử lý tuần tự bởi Game Worker. Owner khác thì trả ROOM_BUSY. Chỉ owner được Configure/Start/Abort/Reset/Close; ADMIN cũng không giành phòng của giảng viên khác. ROOM_CLOSE ở WAITING/FINISHED dọn lobby/trạng thái/mã cũ và giải phóng owner; ROOM_RESET vẫn giữ owner để tổ chức tiếp.

Start kiểm tra owner, ít nhất một Player, tất cả ready và đủ câu đang bật của owner. Snapshot câu hỏi/người chơi kèm avatar_id; mặc định 10 câu, 15 giây/câu, countdown/kết quả 3 giây, bảng hạng từng câu 4,5 giây. JOIN và CRUD bị từ chối ngoài WAITING; không đổi bộ câu giữa ván. Xem ngân hàng/lịch sử không sửa vẫn được phép theo quyền.

Mỗi câu: Server đặt deadline trước broadcast QUESTION. ANSWER cần request_id, session_id, question_id và A/B/C/D. Chấp nhận duy nhất đáp án hợp lệ đầu tiên của Player cho câu đó; ANSWER_ACK mới là xác nhận đã ghi nhận, gửi thành công ở Client không phải ACK. Đáp án trễ/trùng/sai câu không đổi điểm.

Chờ hết deadline kể cả khi tất cả đã trả lời. Theo cập nhật ngày 24/10/2026, đúng trong hạn nhận `50 + round(50 × thời_gian_còn_lại / thời_lượng_câu)` điểm (50–100); sai/bỏ trống/trễ +0. Mốc mở câu và thời gian nhận đáp án hợp lệ đầu tiên do Server ghi bằng `System.nanoTime()`; không tin thời gian Client. Server chấm đúng một lần, lưu điểm câu vừa chấm để `QUESTION_RESULT.points`, tổng điểm, ranking và lịch sử thống nhất. Xóa thời gian/điểm riêng của câu khi mở câu mới, START_GAME và ROOM_RESET. Đồng điểm đồng hạng kiểu 1,1,3; nickname/player_id chỉ ổn định thứ tự hiển thị. Chi tiết ở `Cham_diem_theo_toc_do.md`.

Sau câu cuối vẫn công bố ROUND_RANKING, sau đó lưu transaction rồi gửi GAME_OVER, gồm `result_saved` để báo lỗi ghi DB. ROOM_RESET ở trạng thái hợp lệ đưa phòng về WAITING, xóa điểm/ready/đáp án và sinh mã phòng mới, giữ avatar người còn kết nối. ABORT_GAME hủy rõ ràng, không tạo kết quả hoàn thành giả.

UI v2 thêm AvatarCatalog/AvatarRoster (Java thuần ở shared để dùng chung với fixture, không import JavaFX); một roster thuộc một phòng, Server/Game Worker mới là chủ sở hữu quyền cấp avatar. JOIN gán avatar trống, học sinh đổi qua AVATAR_SET trước ready, check-and-swap nguyên tử. Chỉ UI/Preview và unit test đã triển khai, chưa có handler Server. Danh mục 24 linh vật không nâng mục tiêu tải 10 Player. Xem bổ sung giao thức về khóa/giải phóng và SHOW_RANKING; các guard đang thi phải bao gồm trạng thái mới này.

## 8 Kết nối và lỗi

PING mỗi 5 giây, PONG đối chiếu request_id, timeout 20 giây không nhận phản hồi hợp lệ. Thời hạn đăng nhập/JOIN ban đầu 10 giây. Socket connect dùng timeout hữu hạn; shutdown đóng socket để giải phóng reader đang block, dừng writer, đưa disconnect event đúng một lần.

Player rời: giữ điểm/đáp án, đánh dấu disconnected; người khác tiếp tục. Không khôi phục phiên Player hoặc nhận người mới giữa ván. Chủ phòng Logout/rớt mạng không hủy ván hoặc giải phóng owner; cùng tài khoản login lại mới nhận điều khiển. Tối đa một connection quản lý/tài khoản; login lần hai trả ALREADY_LOGGED_IN đến khi connection cũ đóng/timeout. Hết Player kết nối thì hủy ván; owner giữ quyền reset/close.

Restart Server bỏ trận dang dở, phòng về WAITING; ngân hàng và lịch sử đã commit còn nguyên. Mất điện/ghi DB lỗi phải được ghi log và báo Admin, không báo “đã lưu” giả. Không log mật khẩu hoặc toàn bộ message xác thực.

## 9 SQLite

`users`: id, username UNIQUE, display_name, role (ADMIN/TEACHER), password_hash, password_salt, kdf_algorithm, kdf_iterations, credential_version, is_active, must_change_password, created_at_utc. STUDENT chỉ là vai trò connection trong MVP.

`questions`: id, owner_user_id NOT NULL FK users, content, normalized_content, option_a..d, correct_answer, category, normalized_category, is_active, created_at_utc, updated_at_utc. Index `(owner_user_id, normalized_content)` non-unique phục vụ cảnh báo trùng có xác nhận.

`matches`: session_id, host_user_id NOT NULL FK users, room_name, started_at_utc, ended_at_utc, question_count, duration_seconds, status.

`match_results`: session_id, player_id, nickname, score, correct_count, rank, connected_at_end; khóa chính ghép session_id/player_id, foreign key về matches.

Ready, room owner, connection, deadline, snapshot/đáp án ở RAM. Game Worker sở hữu JDBC connection; `PRAGMA foreign_keys=ON`, PreparedStatement, transaction. Không xóa user vật lý, chỉ khóa để giữ khóa ngoại. Seed ít nhất 20 câu cho tài khoản demo được chỉ định, không dùng ngân hàng chung không owner; seed một lần, không thêm lại câu đã xóa. Backup khi Server dừng ghi hoặc dùng cơ chế backup SQLite phù hợp. Quyền đọc DB/backup dành cho người vận hành Server; SQLite chưa được mã hóa.

## 10 Định hướng UI

JavaFX dùng CSS chung, nền sáng, font Segoe UI, màu nhấn xanh, focus keyboard rõ. Trạng thái có chữ và biểu tượng, không dựa chỉ vào màu. Không cần tải font/ảnh từ Internet.

Màn hình đầu hỏi “Bạn là ai?” với lựa chọn Giảng viên/Học sinh và lối vào Admin hệ thống. Đây là điều hướng, không phải xác thực. Giảng viên tới login, học sinh tới JOIN. Trang quản lý hiện tên/role từ LOGIN_OK, Ngân hàng của tôi, Lịch sử của tôi, Phòng thi, Đổi mật khẩu; tab Tài khoản chỉ ADMIN. Ẩn nút chỉ hỗ trợ UX, quyền thật kiểm tra tại Server.

Start/Reset/Abort/Close điều khiển phòng, không điều khiển tiến trình Server. Player có bốn đáp án lớn, wrap/scroll nội dung, countdown và trạng thái gửi/ACK/lỗi. Khóa nút khi gửi, có ACK hoặc hết hạn; lỗi gửi phản hồi rõ.

Màn hình nhỏ vẫn dùng được, không clip câu hỏi. UI không chấm điểm cục bộ hoặc tự mở câu kế tiếp. Các hiệu ứng chỉ hỗ trợ phản hồi, không làm chậm thao tác.

## 11 Kiểm thử và nghiệm thu

Unit test protocol/framing/UTF-8, scoring/rank/state, quyền và repository. Integration test nhiều socket localhost, đáp án gần đồng thời, disconnect, timeout, bad JSON, queue đầy và restart dữ liệu. Test JavaFX trên máy thực, xem UI không treo khi Server ngắt.

Demo chính dùng ít nhất hai máy vật lý cùng LAN và ba Player, chơi trọn ván, đối chiếu score/ranking/history và reset. Thử tăng đến 10 Player, ghi số liệu thực tế, không suy diễn từ build thành công. Kiểm tra firewall, sai IP/cổng, Wi-Fi isolation và ngắt một Player.

Test auth/owner: chọn role không nâng quyền; A/B không thấy/tìm/sửa/xóa câu hoặc lịch sử nhau; cùng password có salt/hash khác; dữ liệu giữ qua restart; khóa/đổi/reset thu hồi phiên; TEACHER không gọi API user; auth load không chặn deadline; auth result cũ không gắn connection mới; chỉ owner điều khiển phòng.

TLS test: sai CA, hết hạn, sai SAN/IP/hostname và raw TCP bị từ chối, không gửi LOGIN hoặc fallback plaintext. Wireshark lọc `tcp.port == 5050`, minh họa TCP/TLS handshake và dữ liệu mã hóa. Follow TCP Stream không cho JSON rõ; giải thích JSON LF bằng mẫu không nhạy cảm hoặc unit test decoder sau giải mã, không tắt TLS để thu mật khẩu.

## 12 Phân công và tiến độ

Thiện (2380610421) chủ trì F01 F02 F04 F05 F12: TCP/TLS, PBKDF2/login, role/owner, phòng, lỗi và tích hợp. Mỹ (2380601382) chủ trì F03 F09 F11: UI quản lý/login/tài khoản, JDBC users/owner, CRUD, scoring/history. Trường Thi (2380602092) chủ trì F06 F07 F08 F10: UI chọn vai trò/học sinh, client-core/TLS, ready/câu/đáp án/bảng hạng. Mỹ hỗ trợ UI F02, Trường Thi hỗ trợ client F01; Thiện vẫn chủ trì hai mã này. Cả nhóm test LAN và dữ liệu hai tài khoản.

Tuần 1 đề cương; tuần 2 protocol/TLS/schema users-owner và thử hai máy; tuần 3 console/login/PBKDF2/quyền/phòng; tuần 4 CRUD riêng/UI quản lý/tài khoản; tuần 5 vòng thi/học sinh; tuần 6 heartbeat/lỗi/TLS/auth/owner/test LAN; tuần 7 UI/báo cáo; tuần 8 tổng duyệt và bàn giao.

## 13 Tài khoản và bảo vệ mật khẩu

### Cấp tài khoản và phiên

Admin đầu tiên tạo cục bộ tại Server trước khi mở listener; nhận mật khẩu từ prompt che ký tự hoặc quy trình bootstrap ngoài repository, không hardcode. Admin chỉ cấp TEACHER qua UI; không tự đăng ký role quản trị hoặc reset bằng email trong MVP. User có mật khẩu tạm phải đổi sau login; khi must_change_password=true chỉ cho PASSWORD_CHANGE/LOGOUT/PING, chưa được CRUD hoặc tổ chức thi.

Phạm vi quản lý user đã được người dùng chọn ngày 14/10: Admin và giảng viên có tài khoản, học sinh guest nickname/mã phòng. Admin tìm/lọc/phân trang danh sách, tạo TEACHER, sửa display_name bằng USER_UPDATE, khóa/mở và reset mật khẩu. Không sửa role/username hoặc xóa user vật lý trong MVP; không âm thầm thêm tài khoản học sinh.

USER_LIST chỉ trả id/username/display_name/role/is_active/must_change_password, không trả hash/salt. Admin không khóa chính mình hoặc Admin duy nhất. Khóa/reset/đổi password tăng credential_version; Game Worker thu hồi các connection cũ và bỏ auth result chờ. PASSWORD_CHANGE xác minh mật khẩu hiện tại, lưu hash mới rồi đóng phiên; Client login lại.

Đổi/reset/khóa chủ phòng bị từ chối khi COUNTDOWN/QUESTION_ACTIVE/SHOW_RESULT/SHOW_RANKING. Nếu thao tác ở WAITING/FINISHED, thu hồi connection, đóng phòng user đó, dọn lobby và giải phóng owner. Logout/đứt mạng thường không giải phóng owner để chủ phòng trở lại; restart Server đưa phòng về WAITING không owner. Người vận hành máy Server vẫn đọc được file, không coi role ứng dụng là cách bảo vệ trước người chiếm máy Server.

### Băm mật khẩu

PBKDF2WithHmacSHA256 qua SecretKeyFactory/PBEKeySpec có sẵn trong Java, không cần thư viện native bổ sung. Ban đầu 600.000 vòng, salt SecureRandom riêng tối thiểu 16 byte và hash 32 byte; lưu thuật toán/vòng/version để nâng cấp. Đây là lựa chọn triển khai đồ án, không khẳng định PBKDF2 tốt hơn Argon2id. Tham khảo work factor PBKDF2 tại OWASP.

So sánh byte hash bằng MessageDigest.isEqual, clearPassword/xóa mảng nhạy cảm khi có thể. Không lưu password rõ, mã hóa giải ngược, MD5 hoặc SHA-256 đơn thuần. Username chuẩn hóa nhất quán, password không tự trim; chặn password quá dài trước PBKDF2. Không log password/hash/salt hoặc payload LOGIN/USER_CREATE/PASSWORD_CHANGE/USER_RESET_PASSWORD.

Giới hạn login theo IP và username với cửa sổ hữu hạn, dự kiến 5 lần thất bại/username/phút và thêm giới hạn IP. Không khóa vĩnh viễn vì thử sai. Login thất bại thông báo chung, không tiết lộ username có tồn tại. Queue auth hữu hạn, timeout ban đầu 10 giây; đo tham số tải trên máy demo, không suy ra hiệu năng từ số cấu hình.

### TLS trong LAN

Tất cả connection dùng TLS 1.3 hoặc 1.2 trước NDJSON. Server keystore chứa private key/chứng chỉ; Client truststore chứa CA/chứng chỉ công khai được phân phối và đối chiếu fingerprint trực tiếp. CA nội bộ của nhóm không cần Internet/hosting; khóa CA không đặt trong Client hoặc repository.

Chứng chỉ Server có SAN đúng IP/hostname kết nối; nên cố định IP demo hoặc cấp lại khi IP đổi. Thử loopback cần SAN 127.0.0.1 nếu kết nối bằng địa chỉ đó. Client kiểm tra chain/thời hạn/SAN bằng SSLParameters endpoint identification; không trust-all, bỏ hostname verification hoặc fallback plaintext. Không yêu cầu client certificate; giảng viên login mật khẩu bên trong TLS, học sinh JOIN.

TLS không mã hóa SQLite, không bảo vệ Server bị chiếm hoặc chống chụp màn hình/trao đổi đáp án. Dùng tài khoản demo, firewall LAN và backup kiểm soát; role/owner vẫn kiểm tra tại Server sau TLS.

## 14 Lọc và cảnh báo câu hỏi trùng

QUESTION_LIST kết hợp keyword/category/is_active theo AND và owner, phân trang mặc định 10/tối đa 20, sort theo allowlist và id làm khóa phụ. Trả summary có preview; QUESTION_GET trả detail đầy đủ; QUESTION_CATEGORIES lấy chủ đề ngân hàng riêng. COUNT và categories cũng phải kiểm tra owner. PreparedStatement, escape LIKE wildcard của keyword văn bản thường và giới hạn response encode dưới 64 KiB; không dựa vào SQLite NOCASE cho Unicode tiếng Việt đầy đủ.

Java tạo normalized_content/normalized_category bằng NFC, khoảng trắng Unicode, trim và Locale.ROOT lower case; giữ dấu/chữ số/ký hiệu có nghĩa, giữ content gốc để hiển thị. Server dùng nội dung chuẩn hóa để cảnh báo trùng trong cùng ngân hàng, cả câu đang bật/đã tắt và khác category/options. Update loại chính ID, kiểm tra owner ID đó trước. Đây là trùng văn bản chuẩn hóa, chưa phải so khớp ngữ nghĩa bằng AI.

QUESTION_CHECK_DUPLICATE chỉ xem trước; CREATE/UPDATE kiểm tra lại cùng lượt ghi trong Game Worker. Chính sách đề xuất: nếu trùng và allow_duplicate=false thì DUPLICATE_QUESTION, chưa ghi, trả tối đa 5 candidate và total_matches của cùng owner. UI cho mở câu cũ/sửa/hủy hoặc xác nhận lưu với allow_duplicate=true và request_id mới. Nếu giảng viên yêu cầu cấm tuyệt đối, bỏ nhánh xác nhận trước code. Không dùng UNIQUE normalized_content khi cho phép lưu có xác nhận; retry cùng request không được tạo thêm bản ghi ngoài ý muốn.

Test bổ sung TC24–TC38 ở plan: users/filter/Unicode/trùng inactive/self/cross-owner/concurrency/retry/stale response/frame size/Internet tắt. Test và UI đều còn cần lập trình, không chỉ thêm popup demo.

## Tài liệu kỹ thuật

- [Java ServerSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html).
- [Java BlockingQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html).
- [OpenJFX](https://openjfx.io/openjfx-docs/).
- [Jackson databind](https://github.com/FasterXML/jackson-databind).
- [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc).
- [Wireshark User Guide](https://www.wireshark.org/docs/wsug_html_chunked/).
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
- [Java JSSE Reference Guide](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html).
