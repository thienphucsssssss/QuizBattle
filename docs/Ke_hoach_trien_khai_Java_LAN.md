# Kế hoạch triển khai Network Quiz Battle Java LAN

Ngày lập: 14/10/2026. Phạm vi: triển khai MVP theo sáu yêu cầu giảng viên, giữ trò chơi quiz thời gian thực và đề cương hiện hành. Đây là kế hoạch; chưa bắt đầu code chức năng mới trong đợt chuẩn bị này.

Cập nhật thực thi 16/10/2026: phần Server/SQLite/auth/game và tích hợp JavaFX/SFX đã có mã chạy, test socket thật và UI thật. Chi tiết: `Nghiem_thu_Server_LAN.md`, hướng dẫn `Chay_LAN_va_kiem_thu.md`. Giữ checklist gốc để đối chiếu; không đánh dấu tất cả gate hoàn tất chỉ từ test loopback. LAN nhiều máy vật lý, loa và máy chiếu vẫn cần nghiệm thu.

Đọc `Boi_canh_va_yeu_cau_Java_LAN.md` trước; quyết định học sinh guest đã được người dùng xác nhận. Chính sách lưu câu trùng có xác nhận là đề xuất, có thể đổi thành cấm trùng trước khi triển khai UI nếu giảng viên yêu cầu.

## 1 Điểm xuất phát và thứ tự

- [x] Rà source/POM/config/docs ngày 14/10: sản phẩm mới là khung 5 module, không có socket/UI/auth/CRUD/game thật.
- [x] Kiểm tra `mvn -o -B -ntp verify`: 8 test nền tảng, 0 failure/error/skip.
- [x] Ghi sáu yêu cầu, scope Admin/giảng viên/guest, thiết kế bộ lọc/trùng và các gate trong bộ ngữ cảnh.
- [ ] Triển khai các mốc P1–P8 bên dưới và thu minh chứng thực tế.

```text
P1 Hợp đồng và model
        ↓
P2 TCP/TLS ──────┐
                ├→ P3 Auth và users → P4 Ngân hàng lọc/trùng
P3a SQLite ─────┘                         ↓
                                 P5 Phòng và vòng thi
                                          ↓
                                    P6 JavaFX tích hợp
                                          ↓
                                  P7 Kiểm thử LAN và lỗi
                                          ↓
                                  P8 Đóng gói và báo cáo
```

Các nhánh là phân công kỹ thuật cho nhóm, không yêu cầu tạo thêm agent hoặc chat. Có thể phác UI theo hợp đồng sớm nhưng chỉ ghi hoàn thành khi UI dùng API thật và test end-to-end đạt. Không có deadline trong dữ liệu hiện tại nên không tự gán ngày lịch; ưu tiên gate thay vì ngày ước đoán.

## 2 P1 Hợp đồng ứng dụng và model

Chủ trì: Thiện; Mỹ chốt dữ liệu/lọc/trùng; Trường Thi chốt client event. Kế thừa code framing hiện có, không thay toàn bộ shared bằng thư viện framework.

- [ ] Chốt message type/error/DTO, request_id bắt buộc cho request/response, validator payload theo lệnh.
- [ ] Chốt Role ADMIN/TEACHER và guest connection, public User DTO không hash/salt.
- [ ] Chốt QuestionSummary/QuestionDetail, filter/category/page/sort và response dưới 64 KiB.
- [ ] Chốt normalizer Java: NFC, khoảng trắng Unicode, case Locale.ROOT, giữ dấu/ký hiệu; unit test tiếng Việt và nội dung mã/công thức.
- [ ] Chốt ERROR DUPLICATE_QUESTION, candidate/total_matches, allow_duplicate=false mặc định và nhánh xác nhận riêng.
- [ ] Chốt schema users/questions/matches/results, schema_version, quyền owner và index non-unique cho trùng.
- [ ] Chuẩn hóa timeout: connect/handshake TLS/auth/JOIN/heartbeat là các thời hạn riêng, không dùng một thuộc tính mơ hồ cho mọi bước.

Gate: test DTO/envelope/filter/page/normalizer đạt; malformed/unknown type/field sai không được đi tới handler; schema không mâu thuẫn với quyền owner hoặc chính sách trùng. Chưa gọi P1 là mạng LAN hoạt động.

## 3 P2 Transport TCP qua TLS

Chủ trì: Thiện; Trường Thi làm client-core. Module: nqb-server, nqb-client-core, nqb-shared.

- [ ] ServerConfig/ClientTlsConfig đọc properties và secret ngoài nguồn; validate IP/port/keystore/truststore trước khởi chạy.
- [ ] SSLContext/SSLServerSocket/SSLSocket; cert SAN cho loopback và IP demo, truststore CA công khai; bật endpoint identification trên Client.
- [ ] Accept → bounded handshake executor → reader/writer; một writer/socket, queue có giới hạn và đóng tài nguyên đúng một lần.
- [ ] Tích hợp NdjsonDecoder/JsonMessages, correlation request_id, timeout connect/response, bỏ callback cũ bằng generation.
- [ ] PING/PONG và lỗi JSON/UTF-8/frame lớn, Client chậm, server shutdown; không gửi payload auth trước handshake.
- [ ] Java integration test TLS localhost: round-trip, split/coalesced UTF-8, sai CA/SAN/hết hạn, raw TCP và queue đầy.
- [ ] Thử transport trên hai máy LAN sớm, không tự chỉnh firewall; hướng dẫn người dùng thao tác nếu bị chặn.

Gate: hai Client nhận/gửi frame đúng qua TLS; không trust-all/plaintext fallback; một Client chậm không chặn Client khác; GUI chưa cần có. Chỉ dùng localhost cho automated test, biên bản hai máy là minh chứng riêng.

## 4 P3 SQLite xác thực và quản lý users

Chủ trì: Thiện F02; Mỹ repository và UI quản lý user; Trường Thi tích hợp Client login. Nhánh schema/repository có thể chuẩn bị cùng P2 nhưng auth end-to-end phụ thuộc TLS.

- [ ] Database bootstrap/migration/seed bằng Java/JDBC; backup trước migration dữ liệu đã có; foreign_keys/transaction/PreparedStatement.
- [ ] Bootstrap Admin bằng prompt/local setup không hardcode; seed owner rõ, không tự thêm lại câu đã xóa.
- [ ] PasswordHasher PBKDF2/salt/KDF params và password length limit, so sánh an toàn; không log bí mật.
- [ ] Auth executor giới hạn; Game Worker gắn identity sau kiểm tra user active/credential_version/generation, không chia sẻ JDBC sang auth thread.
- [ ] LOGIN/LOGOUT/PASSWORD_CHANGE, bắt buộc đổi password tạm, một connection quản lý/user, rate limit và error chung.
- [ ] USER_LIST tìm/lọc/phân trang; USER_CREATE TEACHER; USER_UPDATE display_name; USER_SET_ACTIVE; USER_RESET_PASSWORD.
- [ ] Thu hồi phiên khi khóa/reset/đổi; chặn thao tác thay credential chủ phòng đang thi; không xóa users vật lý.
- [ ] PermissionService tại Server; guest/teacher không gọi API Admin; role tự khai bị từ chối.

Gate: Admin tạo/khóa/mở/reset giảng viên thật qua TLS; password cũ/phiên cũ không dùng được; dữ liệu users giữ sau restart; API không lộ hash/salt. Học sinh vẫn JOIN guest, không có trang đăng ký tài khoản.

## 5 P4 Ngân hàng câu hỏi lọc và trùng

Chủ trì: Mỹ F03; Thiện kiểm tra quyền/dispatch; Trường Thi phối hợp request/response. Đây là một mốc bắt buộc theo yêu cầu giảng viên, không đẩy ra “hướng mở rộng”.

- [ ] QuestionRepository/QuestionService và CRUD/GET/SET_ACTIVE trong ngân hàng của connection user.
- [ ] Lưu normalized_content/normalized_category, created_at/updated_at và index owner; validate bốn lựa chọn/một đáp án.
- [ ] QUESTION_LIST lọc AND keyword/category/is_active, page 1-based và sort allowlist; COUNT áp dụng cùng owner/filters.
- [ ] QUESTION_CATEGORIES thuộc đúng owner; summary gọn, QUESTION_GET đầy đủ; encode kiểm tra frame size.
- [ ] QUESTION_CHECK_DUPLICATE xem trước; create/update kiểm tra lại cùng lượt ghi, không tin check ở Client.
- [ ] Khi trùng mặc định không ghi, trả candidate; update loại chính ID; inactive/category/options khác vẫn cảnh báo; cross-owner không rò dữ liệu.
- [ ] Nếu giữ D08, allow_duplicate=true chỉ từ thao tác xác nhận chủ động; test log metadata an toàn và request idempotency.
- [ ] Hai create gần đồng thời, timeout/retry, restart và lỗi SQL không ghi lặp/sửa nhầm owner.

Gate: tài khoản A/B dùng ngân hàng riêng; các tổ hợp filter đúng cả items/total; hiện cảnh báo trùng trên luồng lưu thật, không chỉ mock popup; dữ liệu giữ sau restart. Thử keyword có dấu, `%`, `_`, quote và SQL-like text. Không có AI semantic dedupe.

## 6 P5 Phòng vòng thi điểm và lịch sử

Thiện chủ trì room/join F04 F05; Trường Thi ready/question/answer/rank F06 F07 F08 F10; Mỹ scoring/history F09 F11. Giữ một chủ trì cho mỗi mã chức năng.

- [ ] Room owner từ tài khoản, một phòng; Configure/Start/Abort/Reset/Close đúng owner và trạng thái.
- [ ] JOIN nickname/mã phòng/giới hạn/trùng tên, lobby, READY_SET và điều kiện Start.
- [ ] Snapshot câu hỏi đang bật của owner/người chơi; Game Worker/state machine và clock có thể kiểm thử.
- [ ] QUESTION/deadline Server, ANSWER đầu tiên hợp lệ, ACK, session/question/deadline validation.
- [ ] Đóng/chấm một lần, đúng 50–100 điểm theo tốc độ do Server ghi nhận (cập nhật 24/10/2026), sai/bỏ trống/trễ +0, đồng điểm đồng hạng; QUESTION_RESULT/GAME_OVER không lộ đáp án sớm.
- [ ] Lưu matches/match_results theo host bằng transaction; result_saved=false khi lỗi; HISTORY_LIST owner/pagination.
- [ ] Disconnect: giữ điểm guest rời, chủ phòng login lại, hết guest thì abort; restart không phục hồi ván.
- [ ] Reset giữ owner, Close giải phóng owner; dữ liệu CRUD không thay snapshot đang chạy.

Gate: console/integration test ba guest chơi trọn ván trước khi dựa vào UI; score/ranking/history khớp. Đáp án trễ/lặp/sai ván và tài khoản khác điều khiển phòng không làm sai điểm/trạng thái.

## 7 P6 Client JavaFX tích hợp

Mỹ chủ trì Client quản lý, Trường Thi Client học sinh; Thiện hỗ trợ trạng thái/lỗi. Giữ nqb-admin-client/nqb-player-client và CSS/resource local, không chuyển sang web frontend.

- [ ] Màn hình Bạn là ai; nhập IP/cổng/truststore, kiểm tra kết nối; Admin/giảng viên login, học sinh JOIN.
- [ ] Trang password tạm/đổi password/Logout, hiện user/role do Server trả.
- [ ] Tab Tài khoản ADMIN: danh sách/tìm/lọc, form tạo/sửa, xác nhận khóa/reset; xử lý permission/error thật.
- [ ] Tab Ngân hàng riêng: bảng/phân trang, bộ lọc, detail/form CRUD; empty/loading/error rõ.
- [ ] Dialog trùng: candidate, mở câu cũ/sửa/hủy; nếu D08 được giữ có xác nhận Lưu dù trùng. Không tự bỏ qua cảnh báo.
- [ ] Tab Phòng/Lịch sử owner; lobby/ready, countdown, bốn đáp án, pending/ACK/result/ranking.
- [ ] Task mạng/DB không chạy trên JavaFX thread; Platform.runLater, bỏ response stale khi đổi filter/connection.
- [ ] Nội dung tiếng Việt/dài, resize/keyboard, trạng thái không chỉ dùng màu; không giả lập dữ liệu cho demo nghiệm thu.

Gate: toàn bộ thao tác UI gửi API thật; guest không thấy hoặc gọi được API quản trị; lỗi mạng không treo cửa sổ. UI không chấm điểm cục bộ hoặc quyết định câu tiếp theo.

## 8 P7 Hardening và nghiệm thu LAN

Thiện F12 chủ trì; cả nhóm kiểm thử chéo. Nghiệm thu không thay bằng video một máy/localhost.

- [ ] Chạy ít nhất hai máy vật lý cùng LAN với ba guest; tối thiểu 20 câu mẫu, hai tài khoản giảng viên.
- [ ] Disconnect/heartbeat/Client chậm/queue đầy/lỗi save/reset/restart; ghi dữ liệu và kết quả thực tế.
- [ ] Test 10 Player, login sai liên tục khi thi và truy vấn phân trang; đo tác động lên deadline/queue, không khẳng định tải trước đo.
- [ ] Loại dữ liệu nhạy cảm khỏi log/exception/capture; kiểm tra API không trả hash/salt.
- [ ] Kiểm tra sai CA/SAN/hết hạn/raw TCP không gửi LOGIN và không fallback.
- [ ] Ngắt Internet nhưng giữ LAN và chơi trọn ván; không phụ thuộc cloud/font/CDN/login Internet.
- [ ] Wireshark TCP/TLS handshake/application data mã hóa; giải thích NDJSON bằng mẫu/unit test sau giải mã, không thu password rõ.
- [ ] Lập biên bản TC với expected/actual/Pass-Fail/ngày/máy/người chạy/minh chứng.

Gate: cả sáu R01–R06 và chức năng quiz có minh chứng; không ghi Pass từ build/test framing. Nếu máy LAN/firewall chưa sẵn sàng, ghi Chưa chạy, không giả kết quả.

## 9 P8 Đóng gói và hồ sơ nộp

Thiện chủ trì tích hợp; cả nhóm giải thích được Socket, TLS, auth, quyền, lọc/trùng và game.

- [ ] Gói chạy gồm Server và hai Client, Java runtime/dependency local; không yêu cầu Python, Node hoặc Maven tải Internet khi demo.
- [ ] Chuẩn bị certificates/truststore/SAN trên máy demo bằng quy trình local; private key/password không đưa vào gói source công khai.
- [ ] README chạy LAN và troubleshooting, schema/data demo Java, protocol/kiến trúc, biên bản test và kịch bản demo.
- [ ] Cập nhật Word khi plan thống nhất/nếu giảng viên cần: F02 quản lý user rõ; F03 lọc/trùng; TC mới; giữ thông tin và mã phân công.
- [ ] Gói mã sản phẩm chỉ 5 module Java/POM/resource/test và tài liệu cần thiết; loại archive/Python helpers/.venv/cache/credential thật khỏi gói nộp, không xóa bản gốc.
- [ ] Máy sạch thử bộ chạy khi Internet tắt; backup DB và runbook restart, không mất câu hỏi/lịch sử đã commit.

Gate: chạy được bằng hướng dẫn trên máy demo, hồ sơ phản ánh đúng tính năng và test thực tế; không còn dependency vào prototype Python/hosting.

## 10 Ma trận yêu cầu và minh chứng

| Yêu cầu | Gate chính | Minh chứng bắt buộc |
| --- | --- | --- |
| R01 Java 100% | P1–P8 | Source/test Java, build Maven; chạy sản phẩm không Python/Node |
| R02 Users | P3/P6/P7 | Admin tạo/tìm/lọc/sửa/khóa/mở/reset giảng viên; teacher/guest bị từ chối |
| R03 Ngân hàng và lọc | P4/P6/P7 | CRUD/bật tắt thật, A/B riêng, restart giữ dữ liệu; keyword/category/status và items/total/page đúng, không stale |
| R04 Câu trùng | P4/P6/P7 | Cảnh báo create/update, Unicode/space/case, inactive/self/concurrency đúng |
| R05 LAN 100% | P2/P7/P8 | Hai máy LAN khi Internet tắt; không cloud/hosting/font/CDN hoặc tải runtime khi demo |
| R06 Mã hóa login | P2/P3/P7 | TLS không plaintext, cert sai bị chặn; DB hash/salt và log không bí mật |

Giữ TC01–TC23 của đề cương; bổ sung dưới đây là kế hoạch, chưa phải kết quả đã chạy:

| Mã | Tình huống bổ sung | Kết quả mong đợi |
| --- | --- | --- |
| TC24 | Admin tìm/lọc/phân trang user, sửa display_name | items/total đúng; định danh/role không bị tự đổi |
| TC25 | Keyword + category + active kết hợp và xóa filter | AND đúng, total/page đúng, không lọc nhầm ngân hàng khác |
| TC26 | Tìm tiếng Việt NFC/NFD, hoa/thường và khoảng trắng | Chuẩn hóa nhất quán, giữ dấu có nghĩa |
| TC27 | Keyword `%`, `_`, quote, chuỗi giống SQL; sort lạ | Xử lý như text/validation, không SQL injection hoặc sort tùy ý |
| TC28 | Thêm câu cùng nội dung của mình | DUPLICATE_QUESTION và candidate; mặc định chưa ghi DB |
| TC29 | Câu trùng khác chủ đề/đáp án hoặc đang tắt | Vẫn cảnh báo nội dung trùng, không tự xóa/ghi đè |
| TC30 | Sửa câu không đổi nội dung; sửa thành nội dung câu khác | Không tự trùng với chính ID; trùng câu khác có cảnh báo |
| TC31 | Hai giảng viên lưu cùng nội dung | Mỗi ngân hàng độc lập; không lộ candidate của tài khoản khác |
| TC32 | Hai create đồng thời hoặc retry sau timeout | Server kiểm tra/ghi tuần tự, retry không tạo bản ghi ngoài ý muốn |
| TC33 | Xác nhận lưu dù trùng nếu giữ D08 | Chỉ request xác nhận chủ động ghi; không tự bỏ qua warning |
| TC34 | Đổi filter nhanh, response cũ đến sau | Bảng chỉ nhận kết quả của request/bộ lọc hiện hành |
| TC35 | Page/list/detail dài, UTF-8 sát frame limit | Response hợp lệ dưới 64 KiB, không cắt dữ liệu hoặc mất trang |
| TC36 | Internet tắt, hai máy LAN và ba guest | Trọn ván, login/CRUD/filter/trùng không gọi dịch vụ Internet |
| TC37 | Khóa user hoặc đổi/reset credential, auth result cũ | Connection và credential cũ bị thu hồi, dữ liệu riêng vẫn giữ |
| TC38 | Login load/filter/history khi đang thi | Không chạy PBKDF2 trên Game Worker/UI; deadline/queue được đo và không sai điểm |

Các test Unicode/công thức phải kiểm tra giới hạn cảnh báo: nội dung khác dấu/chữ số/ký hiệu không bị chuẩn hóa thành giống nhau. Những trường hợp hoa/thường có nghĩa khác vẫn có thể bị cảnh báo; UI không tuyên bố hai câu tương đương về ngữ nghĩa.

## 11 Phân công giữ nguyên chủ trì

| Thành viên | MSSV | Mã chủ trì | Trọng tâm mới |
| --- | --- | --- | --- |
| Phạm Phúc Thiện, nhóm trưởng | 2380610421 | F01 F02 F04 F05 F12 | TCP/TLS, user auth/quyền, room owner, lỗi và tích hợp |
| Trịnh Viết Mỹ | 2380601382 | F03 F09 F11 | Ngân hàng/lọc/trùng, JDBC, UI quản lý/tài khoản, scoring/history |
| Nguyễn Trường Thi | 2380602092 | F06 F07 F08 F10 | Client-core/TLS, UI chọn vai trò/học sinh, vòng thi và bảng hạng |

Mỹ hỗ trợ UI/repository F02 nhưng Thiện vẫn chủ trì; Trường Thi hỗ trợ client F01 nhưng Thiện vẫn chủ trì. Mỗi mốc tích hợp phải có test chéo của ít nhất một người khác người viết phần đó, không chia ba module rồi ghép một lần cuối kỳ.

## 12 Định nghĩa hoàn thành và phiên tiếp theo

Một milestone hoàn thành khi đầu việc đã code, build/test liên quan đạt, lưu minh chứng và cập nhật trạng thái; không chỉ khi đã tạo class hoặc UI mock. Mọi feature phải kiểm tra quyền/owner ở Server, xử lý error/timeout và giữ dữ liệu đã commit. Chưa chạy bài test nào thì ghi Chưa chạy.

Phiên code tiếp theo: thực hiện P1, sau đó P2 ở phạm vi nhỏ; kiểm thử protocol/TLS trước khi nối auth/DB. Không dựng đồng thời tất cả UI, users và game trong một lần. Sau khi hoàn thành từng gate mới chuyển mốc; nếu chính sách trùng hoặc phạm vi giảng viên thay đổi, sửa context/protocol trước implementation.

## 13 Bổ sung từ phản hồi UI v2 (14/10/2026)

UI được người dùng yêu cầu làm trước backend: đã có bộ linh vật và bảng hạng từng câu trong Preview. Không đánh dấu milestone Server/LAN hoàn thành vì các bài test fixture. Ngữ cảnh và hợp đồng mới ở `UI_v2_Nghiem_thu.md` và `Giao_thuc_Java_LAN.md`.

| Gate tích hợp thêm | Người chủ trì | Điều kiện đạt |
| --- | --- | --- |
| Cấp/đổi avatar nguyên tử trong một phòng | Thiện, Trường Thi hỗ trợ UI | Hai connection tranh avatar chỉ một thắng; check room/role/phase/ready; broadcast khóa/mở khóa; xử lý disconnect |
| Snapshot nickname/avatar/score trong ván và lịch sử | Mỹ | Không đổi danh tính trong ván; người đã rời vẫn có snapshot kết quả; không lấy avatar Client tự khai |
| SHOW_RESULT → SHOW_RANKING mỗi câu, kể cả câu cuối | Thiện, Trường Thi | Server phát event có session/question/deadline; Player/projector cùng trạng thái; không tự chấm/chuyển câu ở UI |
| Animation và khả năng tiếp cận | Trường Thi | Vị trí cũ → mới đúng ID, tie giữ rank, giảm chuyển động lập tức, không mất focus/timer |

TC39–TC44 (chưa chạy LAN): tranh avatar; đổi thất bại giữ avatar cũ; rời/timeout mở khóa và đóng/reset phòng; cấm đổi khi ready/thi; hạng từng câu đồng bộ mọi máy; ranking cũ/trùng/hủy không hồi sinh ván. Giữ mục tiêu 10 Player và 100% LAN, không cần SFX để bắt đầu backend.
