# Bối cảnh và yêu cầu Network Quiz Battle Java LAN

Ngày cập nhật: 14/10/2026. Tài liệu này chuẩn bị ngữ cảnh cho kế hoạch và các phiên lập trình tiếp theo; không khẳng định chức năng mới đã được triển khai.

Cập nhật 16/10/2026: đã triển khai Server TLS, SQLite, PBKDF2, quyền sở hữu, vòng thi và nối JavaFX thật; tích hợp 17 SFX. Xem `Chay_LAN_va_kiem_thu.md` và `Nghiem_thu_Server_LAN.md` cho cách chạy/minh chứng. Các baseline và checklist bên dưới là ngữ cảnh lập kế hoạch 14/10, không phải trạng thái mới nhất. Chưa nghiệm thu nhiều máy LAN vật lý.

## 1 Phạm vi đã xác nhận

Network Quiz Battle là đồ án môn Lập trình mạng của nhóm VUA, lớp 23DTHA3. Một máy trong LAN chạy Server; các Client desktop JavaFX kết nối tới Server bằng TCP Socket qua TLS để quản lý câu hỏi và thi đấu trắc nghiệm thời gian thực.

Sáu yêu cầu mới được người dùng truyền đạt từ giảng viên:

| Mã | Yêu cầu | Diễn giải cho sản phẩm | Chức năng liên quan |
| --- | --- | --- | --- |
| R01 | Viết bằng Java 100% | Server, Client, logic nghiệp vụ, giao thức và test sản phẩm viết bằng Java; không có backend hoặc dịch vụ Python/JavaScript | F01–F12 |
| R02 | Quản lý người dùng | Admin quản lý tài khoản giảng viên; có đăng nhập, danh sách, tìm/lọc, tạo, sửa tên hiển thị, khóa/mở và đặt lại mật khẩu | F02, F12 |
| R03 | Quản lý câu hỏi và lọc | Ngân hàng riêng: xem/thêm/sửa/xóa/bật tắt; lọc kết hợp từ khóa/chủ đề/trạng thái, phân trang và sắp xếp tại Server | F03 |
| R04 | Phát hiện và thông báo câu hỏi trùng | Server kiểm tra nội dung chuẩn hóa khi thêm/sửa; UI hiển thị câu đã tồn tại trước khi lưu trùng | F03, F12 |
| R05 | LAN 100% | Không hosting/cloud hoặc dịch vụ Internet; Server/Client trong cùng LAN, hoạt động khi Internet tắt | F01, F12 |
| R06 | Mã hóa dữ liệu đăng nhập | Kết nối dùng TLS đã xác minh chứng chỉ; mật khẩu lưu bằng PBKDF2 có salt | F01, F02, F12 |

Ngày 14/10 người dùng chọn giữ tài khoản chỉ cho Admin/giảng viên. Học sinh dùng nickname và mã phòng; không đăng ký, không có mật khẩu hoặc lịch sử cá nhân theo tài khoản. “Quản lý người dùng” trong plan được giới hạn như R02, không âm thầm mở rộng thành tài khoản học sinh. Cần trình bày rõ giới hạn này khi xác nhận lại yêu cầu với giảng viên.

Các chức năng thi đấu đã đăng ký vẫn thuộc MVP, không bị thay thế bởi sáu yêu cầu quản lý: lobby/ready, đồng bộ câu hỏi, deadline Server, nhận đáp án/ACK, chấm điểm, bảng hạng và lịch sử chung cuộc của người tổ chức.

## 2 Thứ tự đọc và nguồn quyết định

1. Yêu cầu trực tiếp mới nhất của người dùng và giảng viên do người dùng truyền đạt.
2. Tài liệu này: phạm vi, quyết định, khác biệt với bản đề cương trước.
3. `Ke_hoach_trien_khai_Java_LAN.md`: các mốc, đầu việc, phụ thuộc và tiêu chí hoàn thành.
4. `Kien_truc_Network_Quiz_Battle_Java_LAN.md`: kiến trúc hiện hành.
5. `Giao_thuc_Java_LAN.md`: danh mục và hợp đồng message.
6. Mã thực tế và `pom.xml`: bằng chứng phần đã lập trình, không suy ra từ mô tả thiết kế.

Đề cương Word hiện tại trong `output/documents/De_cuong_Network_Quiz_Battle_Java_LAN_Nop_Giang_Vien.docx` là hồ sơ ngày 08/10 đã bổ sung tài khoản/TLS. Đợt chuẩn bị plan này không sửa Word. Sau khi thống nhất plan, cần cập nhật phần lọc/cảnh báo trùng và minh chứng mới vào đề cương nếu giảng viên yêu cầu nộp lại.

Hai đặc tả Markdown gốc và `archive/python-prototype/` là tài liệu/mã lịch sử. Chỉ tham khảo ý tưởng, không phục hồi Python hoặc hosting làm quyết định triển khai. Các helper Python ở `tools/` dùng xử lý tài liệu, không thuộc sản phẩm Java; không đưa chúng hoặc `.venv/` vào gói mã nguồn sản phẩm nộp môn học. Không xóa các bản lịch sử trong giai đoạn lên plan.

## 3 Trạng thái thực tế ngày 14 tháng 10

Đã kiểm tra trực tiếp mã nguồn và chạy `mvn -o -B -ntp verify` ngày 14/10/2026: BUILD SUCCESS, 8 test chạy, 0 failure/error/skip. Chạy offline thành công chứng minh dependency phục vụ build hiện tại đã có trên máy này, không chứng minh một máy mới tự build offline được.

| Phần | Thực tế hiện có | Chưa có |
| --- | --- | --- |
| Maven | 5 module, Java release 21, khóa dependency, UTF-8 | Gói runtime/bộ cài để demo trên máy mới |
| nqb-shared | Protocol constants, GamePhase, NdjsonDecoder, JsonMessages | DTO/payload validation, danh mục type/lỗi đầy đủ |
| nqb-client-core | Endpoint và 2 test endpoint | TLS client, reader/writer, heartbeat, request correlation |
| nqb-server | ServerMain in thông báo rồi kết thúc | Listener, TLS, SQLite, auth, quản lý users/câu hỏi, Game Worker |
| Hai Client | AdminMain/PlayerMain in thông báo rồi kết thúc | JavaFX UI hoạt động, login, lọc, cảnh báo trùng và quiz |
| Test | 6 test framing/envelope và 2 test endpoint | Test mạng thật, TLS/auth/owner/filter/duplicate/game/GUI/LAN |
| Cấu hình | server.example.properties là bản mẫu | Bootstrap đọc cấu hình; TLS keystore/truststore config |

Máy hiện có Temurin JDK 21.0.10 và Maven 3.9.12. Dependency trong POM: JavaFX 21.0.12, Jackson 2.22.3, SQLite JDBC 3.53.4.0, JUnit 5.14.4. Đây là phiên bản đang khóa, không phải khẳng định bản mới nhất. Chưa có Git repository ở thư mục dự án; không tự khởi tạo hoặc push trong bước chuẩn bị ngữ cảnh.

## 4 Quyết định và giới hạn

| Mã | Quyết định | Trạng thái |
| --- | --- | --- |
| D01 | Java 21, JavaFX, Maven 5 module; không Python backend | Đã xác nhận |
| D02 | Server chỉ chạy LAN, kết nối TCP/TLS, không hosting/Internet | Đã xác nhận |
| D03 | ADMIN/TEACHER có tài khoản, học sinh nickname/mã phòng | Người dùng xác nhận ngày 14/10 |
| D04 | Ngân hàng/lịch sử riêng theo owner; Server kiểm tra quyền | Giữ thiết kế ngày 08/10 |
| D05 | Một phòng có một chủ phòng, tối đa 10 học sinh ban đầu | Giữ thiết kế ngày 08/10 |
| D06 | SQLite JDBC, chỉ Server truy cập DB | Giữ thiết kế; không tự đổi database |
| D07 | Lọc từ khóa/chủ đề/trạng thái, có phân trang | Thiết kế đề xuất để đáp ứng yêu cầu mới |
| D08 | Trùng nội dung sau chuẩn hóa; cảnh báo, mặc định chưa ghi; có xác nhận lưu dù trùng | Chính sách đề xuất, không phải yêu cầu giảng viên đã xác nhận |
| D09 | Không AI/fuzzy search để đoán câu cùng nghĩa trong MVP | Giới hạn đề xuất để bảo đảm khả thi |

R01 được hiểu là mã ứng dụng/nghiệp vụ/test do nhóm viết hoàn toàn Java. CSS, cấu hình XML/properties, SQL và script gọi Java là tài nguyên/công cụ hỗ trợ, không thay logic Java. Thư viện GUI/DB có thể dùng thành phần native; không tuyên bố mọi dependency đều được viết bằng Java. Nếu giảng viên yêu cầu cả engine DB phải thuần Java, cần xác nhận riêng trước khi thay SQLite bằng lựa chọn JDBC khác.

Không đưa vào MVP: tài khoản học sinh, đăng ký quản trị công khai, reset email, nhiều phòng đồng thời, cloud sync, web/mobile, chat/voice, khôi phục trận sau restart, chống gian lận nâng cao, phát hiện trùng ngữ nghĩa bằng AI. Chấm theo tốc độ được bổ sung theo yêu cầu người dùng ngày 24/10/2026; xem `Cham_diem_theo_toc_do.md`.

## 5 Vai trò và quản lý người dùng

| Thao tác | ADMIN | TEACHER | Học sinh guest |
| --- | --- | --- | --- |
| Đăng nhập/đổi mật khẩu quản lý | Có | Có | Không áp dụng |
| Tạo/tìm/lọc/sửa/khóa/reset tài khoản giảng viên | Có | Không | Không |
| CRUD/lọc/check trùng ngân hàng của mình | Có | Có | Không |
| Xem/sửa ngân hàng của tài khoản khác | Không mặc nhiên | Không | Không |
| Tạo và điều khiển phòng của mình | Có | Có | Không |
| JOIN/ready/trả lời | Không trên cùng connection quản lý | Không trên cùng connection quản lý | Có |
| Xem lịch sử các ván mình tổ chức | Có | Có | Không có lịch sử tài khoản |

Admin đầu tiên bootstrap tại máy Server trước khi mở listener, không hardcode password. UI chỉ cấp TEACHER; không tạo thêm Admin qua API trong MVP. Khóa thay cho xóa tài khoản vật lý để giữ ngân hàng/lịch sử và khóa ngoại. Cho sửa display_name; username/role là định danh ổn định, không cho sửa tùy ý trong MVP. Không được khóa/reset chính Admin bằng API quản lý giảng viên.

USER_LIST tìm username/display_name, lọc role/trạng thái, phân trang; USER_UPDATE sửa tên hiển thị, USER_SET_ACTIVE khóa/mở, USER_RESET_PASSWORD cấp mật khẩu tạm. User phải đổi mật khẩu tạm trước quản lý ngân hàng/phòng. Khóa/reset/đổi password tăng credential_version, thu hồi connection cũ; vẫn giữ dữ liệu.

Màn hình “Bạn là ai?” chỉ điều hướng: Giảng viên tới login, Học sinh tới JOIN, Admin tới login quản trị. Server lấy role từ DB, không nhận role tự cấp từ Client. Học sinh có player_id gắn connection, không có bản ghi users.

## 6 Kiến trúc để triển khai

```text
Client quản lý JavaFX ─┐
Client học sinh JavaFX ├─ LAN TCP/TLS ─ Server Java ─ SQLite local
                      ┘       NDJSON       │
                               │           ├─ auth executor có giới hạn
                         reader/writer     └─ Game Worker sở hữu trạng thái/DB
```

Giữ module và luồng phụ thuộc hiện tại. `nqb-shared` không phụ thuộc JavaFX/DB; `nqb-client-core` không chạm control UI; Server không phụ thuộc JavaFX. `nqb-admin-client` phục vụ cả giảng viên và Admin, không đổi tên module chỉ để đổi nhãn màn hình.

TCP là byte stream, message JSON UTF-8 một dòng kết thúc LF, frame tối đa 64 KiB. TLS hoàn tất và xác minh endpoint trước khi gửi LOGIN/JOIN. Một connection có một writer; reader/event queue và outbound queue có giới hạn. Mỗi response đối chiếu request_id; session_id chỉ xác định ván, không phải phiên login.

LAN là phạm vi triển khai, không suy ra chỉ từ việc IP có dạng 192.168.x.x. Khi demo chọn adapter/IP LAN của Server, firewall giới hạn nguồn mạng nội bộ và không forward router/public port. Không tự đổi firewall. Không tải font/CDN, gọi login cloud, cập nhật online hoặc dịch vụ ngoài trong luồng sử dụng; dependency/runtime phải chuẩn bị trước. Minh chứng R05 là chơi được trên hai máy khi ngắt Internet nhưng giữ LAN.

Một Game Worker điều phối room/scoring/JDBC tuần tự. PBKDF2 và TLS handshake chạy trên executor giới hạn, không chặn Game Worker hoặc JavaFX Application Thread. UI cập nhật qua Platform.runLater; callback connection cũ không tác động connection mới. Request CRUD/khóa/reset phải được kiểm tra tại Server kể cả Client ẩn nút.

## 7 Bảo vệ dữ liệu đăng nhập

Hai lớp bảo vệ khác nhau:

- Truyền: JSSE SSLSocket/SSLServerSocket, TLS 1.3 hoặc 1.2, truststore CA nội bộ, kiểm tra chain/thời hạn/SAN khớp IP hoặc hostname. Raw SSLSocket cần bật kiểm tra endpoint, không chỉ có chứng chỉ tin cậy là đủ. Không trust-all/fallback plaintext. CA nội bộ không cần hosting hoặc Internet. Theo [hướng dẫn JSSE của Oracle](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html).
- Lưu: PBKDF2WithHmacSHA256, salt SecureRandom riêng tối thiểu 16 byte, hash 32 byte và 600.000 vòng ban đầu; lưu thuật toán/tham số để nâng cấp. Mật khẩu không lưu rõ hoặc mã hóa có thể giải ngược. Theo [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html); chọn PBKDF2 vì có sẵn trong Java, không khẳng định tốt hơn Argon2id.

Không tự viết AES hoặc gửi hash như “mật khẩu thay thế”. Không log password/hash/salt/private key hoặc payload auth. Client không lưu password để tự đăng nhập trong MVP. UI chỉ dùng che ký tự để hỗ trợ riêng tư, không coi đó là mã hóa.

Giới hạn thử login, timeout/queue hữu hạn; so sánh hash an toàn; auth result cũ phải kiểm tra credential_version. Lỗi auth chung, không tiết lộ username tồn tại. Private key/CA key/keystore password không đưa vào repository; Client chỉ nhận truststore chứa chứng chỉ công khai. Không cấu hình xác thực TLS phụ thuộc dịch vụ Internet khi demo offline.

SQLite/backup chưa được mã hóa tự động; hạn chế quyền file và dùng tài khoản demo. TLS không bảo vệ máy Server đã bị chiếm hoặc chống trao đổi đáp án ngoài phần mềm.

## 8 Quản lý câu hỏi và bộ lọc

Mỗi câu có content, bốn lựa chọn A–D, một correct_answer, category, is_active và owner_user_id. Giữ nguyên câu gốc để hiển thị, dùng trường chuẩn hóa riêng để tìm/so sánh. Đề xuất giới hạn content 2.000 ký tự, mỗi lựa chọn 500 ký tự, category 80 ký tự; chốt validation thống nhất ở shared/Server trước UI.

QUESTION_LIST có keyword, category, is_active, page, page_size và sort. Các bộ lọc kết hợp AND; trường không chọn là null/không lọc. Keyword tìm theo nội dung đã chuẩn hóa; category lấy từ danh sách chủ đề của chính owner. page bắt đầu 1, page_size mặc định 10, tối đa 20. Sort enum được Server allowlist, có id làm khóa thứ tự phụ ổn định.

QUESTION_LIST chỉ trả id/content_preview/category/is_active/updated_at, total/page/page_size/has_more. QUESTION_GET trả nội dung/đáp án đầy đủ cho owner. QUESTION_CATEGORIES trả chủ đề từ ngân hàng riêng. Đo kích thước JSON encode, không gửi frame quá 64 KiB; không cắt nửa response hoặc rút số hàng mà vẫn nhảy offset theo page_size gây mất dữ liệu.

Mọi truy vấn, kể cả COUNT/category/duplicate, có điều kiện owner. Dùng PreparedStatement; escape ký tự `%`/`_` nếu keyword là tìm văn bản thường, không coi input như SQL wildcard tùy ý. Không dựa vào SQLite NOCASE để xử lý đầy đủ tiếng Việt: chuẩn hóa ở Java và lưu trường tương ứng. Xem [SQLite SQL expressions](https://www.sqlite.org/lang_expr.html).

UI cần hiển thị số kết quả/trang hiện tại, trạng thái loading/error/empty và nút xóa bộ lọc. Response cũ của bộ lọc trước không được ghi đè kết quả của bộ lọc mới. Không lọc chỉ các hàng đã tải rồi gọi đó là kết quả toàn ngân hàng.

## 9 Phát hiện câu hỏi trùng

### Định nghĩa MVP

Hai câu bị cảnh báo khi nội dung sau chuẩn hóa giống nhau trong cùng ngân hàng. Chuẩn hóa bằng Java: NFC, đổi khoảng trắng Unicode thành space/gộp liên tiếp, trim, lower case với Locale.ROOT và NFC lại nếu cần. Giữ dấu tiếng Việt, chữ số và dấu/ký hiệu có ý nghĩa; không strip accent hoặc bỏ dấu câu. Java cung cấp [Normalizer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.html).

Khác chủ đề, khác lựa chọn hoặc khác trạng thái không bỏ qua cảnh báo nếu nội dung trùng. Tìm cả câu đang bật và đã tắt. Sửa câu phải loại chính question_id đang sửa; hai tài khoản có cùng câu vẫn được phép mà không tiết lộ ngân hàng của nhau. Chỉ phát hiện trùng chuẩn hóa, không đảm bảo tìm câu diễn đạt khác, sai chính tả hoặc cùng nghĩa. Hoa/thường trong mã hoặc công thức có thể mang nghĩa khác nên đây là cảnh báo, không chứng minh hai bài toán tương đương.

### Luồng lưu đề xuất

1. Client có thể gọi QUESTION_CHECK_DUPLICATE để xem trước; không được coi kết quả này là bảo đảm khi lưu sau đó.
2. QUESTION_CREATE/UPDATE luôn kiểm tra lại tại Server cùng thao tác ghi, sau kiểm tra user/owner/validation/trạng thái phòng. Tắt nút UI không thay thế bước này.
3. Nếu trùng và allow_duplicate=false, trả ERROR code DUPLICATE_QUESTION, tối đa 5 candidate gồm id/preview/category/is_active và total_matches; chưa ghi DB.
4. UI hiện “Câu hỏi đã tồn tại trong ngân hàng của bạn”, cho mở câu cũ, quay lại sửa hoặc xác nhận “Lưu dù trùng”. Không tự lưu hoặc tự xóa câu cũ.
5. Nếu giảng viên chủ động xác nhận, gửi lại request với allow_duplicate=true, request_id mới; Server vẫn kiểm tra owner/validation và ghi nhận quyết định, không log toàn bộ câu/password. Nếu giảng viên yêu cầu cấm tuyệt đối câu trùng, bỏ nhánh xác nhận này trước khi code UI.

Game Worker xử lý check/ghi tuần tự; hai request tạo gần đồng thời không cùng vượt kiểm tra mặc định. Index `(owner_user_id, normalized_content)` là index tìm kiếm, không UNIQUE vì chính sách đề xuất cho phép lưu có xác nhận. Retry do timeout cần request correlation/idempotency để không vô tình tạo thêm bản ghi; mỗi lần xác nhận lưu chủ động dùng request mới.

## 10 Dữ liệu mục tiêu

| Bảng | Trường và ràng buộc chính |
| --- | --- |
| users | id, username UNIQUE, display_name, role ADMIN/TEACHER, hash/salt/KDF params, credential_version, is_active, must_change_password, created_at_utc |
| questions | id, owner_user_id FK users, content, normalized_content, option_a..d, correct_answer, category, normalized_category, is_active, created_at_utc, updated_at_utc |
| matches | session_id PK, host_user_id FK users, room/config, started_at_utc/ended_at_utc, status |
| match_results | session_id FK matches, player_id, nickname, score, correct_count, rank, connected_at_end; PK ghép session_id/player_id |

Schema version/migration và seed bằng Java/JDBC, không chạy backend Python. Chỉ Server đọc file DB; PreparedStatement và transaction; foreign_keys=ON. Seed chỉ khi khởi tạo, gắn owner rõ, không tự phục hồi câu đã xóa. Fixture đề xuất: 1 Admin, 2 giảng viên A/B, tối thiểu 20 câu thuộc A và một tập riêng thuộc B; mọi credential demo được cấp bằng quy trình riêng, không hardcode trong nguồn.

Không có DB và schema Java thật được xác minh ở baseline hiện tại; migration cần backup nếu phát hiện data/quiz.db do người dùng tạo, không ghi đè tự động. Dữ liệu quiz đang chạy ở RAM; restart không khôi phục ván nhưng ngân hàng/lịch sử đã commit phải còn.

## 11 Luật thi giữ nguyên

- Một phòng, một owner. Configure/Start/Abort/Reset/Close chỉ owner, kể cả tài khoản ADMIN khác cũng không tự giành phòng.
- Start cần ít nhất hai Player kết nối, tất cả ready và đủ câu đang bật của owner; nghiệm thu dùng ba Player. Mặc định 10 câu, 15 giây/câu, countdown/result 3 giây.
- WAITING → COUNTDOWN → QUESTION_ACTIVE → SHOW_RESULT → SHOW_RANKING → câu tiếp hoặc FINISHED. JOIN/CRUD câu hỏi chỉ khi WAITING; không sửa snapshot giữa ván. SHOW_RESULT 3 giây, SHOW_RANKING 4,5 giây, kể cả câu cuối; bảng hạng và deadline do Server quyết định.

- Bổ sung UI v2: guest chọn một trong 24 linh vật vector (không tài khoản học sinh). JOIN gán một avatar trống, AVATAR_SET cho đổi trong WAITING trước ready; mỗi avatar chỉ thuộc một người trong phòng. Server kiểm tra và giữ chỗ nguyên tử, không tin player_id Client tự khai. UI khóa avatar đã có chủ, cập nhật bằng ROOM_STATE; lỗi giữ avatar cũ. Giới hạn tải mục tiêu vẫn 10 Player. Hiện đã có UI/Preview và unit test, chưa có handler LAN.
- Deadline bằng System.nanoTime tại Server; không tin timestamp/điểm Client. Chỉ ANSWER hợp lệ đầu tiên được nhận; Server ACK, khóa/chấm đúng một lần khi hết hạn.
- Đúng trong hạn: 50–100 điểm, gồm 50 điểm đúng và thưởng tốc độ `round(50 × thời_gian_còn_lại / thời_lượng_câu)` theo thời gian Server ghi nhận. Sai/bỏ trống/trễ +0. Đồng điểm đồng hạng kiểu 1, 1, 3. Chưa gửi correct_answer cho học sinh trước khi đóng câu.
- Player mất mạng giữ điểm đã nhận, không khôi phục phiên. Chủ phòng mất mạng ván vẫn tự chạy; cùng tài khoản login lại mới nhận quyền. Hết Player thì hủy ván rõ ràng.
- GAME_OVER báo result_saved; lỗi lưu không báo thành công giả. ROOM_RESET giữ owner và xóa điểm/ready; ROOM_CLOSE giải phóng owner.

## 12 Các điểm còn cần đối chiếu

Không cần trì hoãn chuẩn bị plan vì các mục này; không tự suy ra quyết định đã được giảng viên duyệt:

- “Quản lý người dùng” giới hạn Admin/giảng viên có phù hợp cách giảng viên chấm không; đây là lựa chọn của người dùng, không phải xác nhận trực tiếp từ giảng viên.
- Có cho xác nhận lưu câu trùng hay phải cấm trùng; plan hiện đề xuất cảnh báo có xác nhận.
- Hạn nộp và thời gian làm thực tế chưa được cung cấp; mốc trong plan theo phụ thuộc, không gán ngày deadline.
- Máy demo/IP LAN và khả năng dùng hai máy thật chưa xác minh; không tự thay firewall/router, không dùng localhost thay minh chứng LAN.
- Yêu cầu nộp lại đề cương Word sau khi bổ sung lọc/trùng chưa có; bước này chỉ cập nhật Markdown phục vụ plan.

## 13 Ngữ cảnh cho phiên code đầu tiên

Đọc các tài liệu theo mục 2, kiểm tra source trước khi sửa và bắt đầu đúng milestone đã chọn. Giữ Java/LAN/5 module/guest học sinh; không dựng Python backend, API web, hosting hoặc tài khoản học sinh. Không viết toàn bộ game/UI trong một lượt trước khi có transport/auth/repository test. Mọi đầu việc kết thúc bằng test theo gate; không ghi “hoàn tất” từ một placeholder hoặc build thành công.

Phiên đầu nên chốt DTO/schema/validation/normalizer cùng hợp đồng lọc/trùng, sau đó tạo transport TCP/TLS nhỏ có test localhost và hướng dẫn thử hai máy. Đây là chỉ dẫn phạm vi kỹ thuật để thực hiện plan, không phải quyền tự triển khai ngoài yêu cầu của người dùng.

Tham khảo về SQLite JDBC và thành phần native: [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc). Không dùng đặc tính thư viện này để diễn giải sai rằng sản phẩm có Python backend.
