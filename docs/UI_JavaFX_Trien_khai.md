# Triển khai UI/UX JavaFX — Network Quiz Battle

Ghi chú 16/10/2026: tài liệu dưới đây ghi nhận đợt UI ngày 14/10. Backend/SQLite/TLS tích hợp và SFX đã triển khai sau đó; xem `Chay_LAN_va_kiem_thu.md` và `Nghiem_thu_Server_LAN.md` cho trạng thái hiện hành. Không còn phải chờ file âm thanh.

Ngày 14/10/2026. Thực thi phần giao diện arcade của plan; SFX hoãn theo yêu cầu người dùng. Không sửa đề cương Word hoặc các PNG mẫu trong `UI/`.

## 1 Đã có và chưa có

Đã có JavaFX chạy được, không còn launcher chỉ in thông báo. Có hai launcher quản lý/học sinh; UI dùng `QuizGateway` để không gắn control trực tiếp vào socket hoặc DB.

| Hạng mục | Trạng thái |
| --- | --- |
| Design system v2 cobalt/lime, font tiếng Việt offline, vector logo/24 linh vật | Đã dựng theo phản hồi UI |
| Vai trò, login/JOIN, truststore, chờ kết nối/lỗi, giảm chuyển động | Đã dựng UI |
| Workspace, ngân hàng, lọc/phân trang/chi tiết/editor/cảnh báo trùng | Đã chạy với fixture RAM |
| Admin quản lý giảng viên, đổi mật khẩu, lịch sử | Đã dựng và bind gateway; chưa có backend thật |
| Lobby, ready, countdown, câu hỏi, ACK, kết quả, đồng hạng | Đã chạy với fixture RAM |
| Chọn/đổi linh vật, khóa đã chọn, kiểm tra tranh chấp nguyên tử | UI + Preview + unit test; chưa đồng bộ nhiều máy LAN |
| Bảng hạng riêng sau từng câu, chuyển động lên/xuống, giảm chuyển động | Đã dựng và kiểm thử JavaFX |
| Projector Stage riêng, chọn màn hình, F11/Esc | Đã dựng; chưa thử máy chiếu/màn hình thứ hai thật |
| Client TLS, NDJSON, correlation, heartbeat, timeout | Đã test bằng SSL peer localhost |
| Server auth/PBKDF2/owner, SQLite, Game Worker | Chưa triển khai; ServerMain vẫn là placeholder |
| Một ván nhiều máy thật trong LAN, tải 10 Player | Chưa kiểm thử |
| SFX/volume/mute runtime | Chờ file từ người dùng; hiện không phát tiếng |
| Bộ cài/runtime jpackage | Chưa làm |

Chưa hoàn thành toàn bộ plan UI tích hợp, vì gate nối Server và nghiệm thu LAN chưa đạt. Không gọi bản này là MVP thi LAN đã hoàn thiện. Preview không kiểm tra mật khẩu thật, không lưu tài khoản/câu hỏi/lịch sử xuống SQLite, không có đồng bộ giữa hai Client. Restart Preview khôi phục fixture ban đầu.

## 2 Chạy thử

PowerShell tại `E:\_HUTECH\DO_AN_LAP_TRINH_MANG`:

```powershell
mvn -o -B -ntp install
.\scripts\Run-Quiz.ps1 -Role Admin -Preview
.\scripts\Run-Quiz.ps1 -Role Admin -Preview -PreviewRole Admin
.\scripts\Run-Quiz.ps1 -Role Player -Preview
```

Chạy từng lệnh Client trong terminal riêng nếu muốn mở đồng thời; hai Preview độc lập, không tạo LAN. Trong màn học sinh: Đổi linh vật → chọn bạn còn trống → Xong → Sẵn sàng → Chạy thử một ván mẫu → chọn A–D. Muốn đổi lại linh vật, bỏ Sẵn sàng trước. Mẫu mặc định 3 câu, 15 giây/câu, countdown/đáp án 3 giây; thêm bảng hạng 4,5 giây sau mọi câu kể cả câu cuối. Với giảng viên có thể đổi cấu hình 1 câu/5 giây để xem nhanh. Không đặt tài khoản/password mẫu cố định để giả lập bảo mật.

Không thêm `-Preview` thì hiển thị form kết nối thật. Server hiện chưa lắng nghe nên chưa login/JOIN thật được. TLS chỉ dùng truststore/chứng chỉ có SAN đúng, không có trust-all/raw TCP fallback. `-Role Server` hiện chỉ in trạng thái khung.

Lần tải dependencies đầu cần `mvn -B -ntp install` có Internet; script chạy dùng offline Maven. Không cần Python để build/chạy Java. JavaFX có cảnh báo classpath “unnamed module” ở cấu trúc không JPMS hiện tại; chưa ảnh hưởng các lần capture, cần kiểm tra lại khi đóng gói.

## 3 Cấu trúc mã

- `nqb-client-core`: QuizGateway, TlsQuizGateway, PreviewGateway, AnswerState, TextKey. Không import JavaFX/JDBC. Fixture mô phỏng tính điểm chỉ phục vụ Preview, không phải luật chấm của Client thật.
- `nqb-shared`: AvatarCatalog/AvatarRoster Java thuần bên cạnh giao thức. Roster phải do Server sở hữu khi tích hợp, không lấy roster Client làm thẩm quyền.
- `nqb-admin-client`: ArcadeApplication, ManagementViews, FxKit, QuizlingArt, RankBoard, SfxSlots, UiCapture; presentation layer dùng chung cho các role.
- `nqb-player-client`: PlayerMain mở Application ở luồng học sinh; phụ thuộc presentation layer của admin-client để không sao chép view hoặc thêm module thứ sáu. Tên admin-client là tên lịch sử, không đồng nghĩa code trong JAR có quyền Admin.
- CSS/font offline dùng chung ở resources `/ui/` của client-core; đó là tài nguyên, không thêm dependency JavaFX vào core.

Luồng phụ thuộc UI thực tế được cập nhật trong kiến trúc. Server vẫn độc lập GUI. Trong bản thật, role/owner do Server kiểm tra ở mọi request; việc UI ẩn nút không thay thế kiểm tra quyền.

## 4 Hợp đồng cần hoàn thiện khi nối Server

UI gọi theo `docs/Giao_thuc_Java_LAN.md`: LOGIN/JOIN; USER_*; QUESTION_*; ROOM_STATE/CONFIGURE/READY_SET/START_GAME/ABORT_GAME/RESET/CLOSE; AVATAR_SET; ANSWER; HISTORY_LIST/GET. HISTORY_GET là truy vấn chi tiết bảng điểm cần thêm handler/schema, chưa có implementation.

Gateway đưa room_code/session_id của thao tác ván vào envelope; HISTORY_GET giữ session_id của lịch sử trong payload. Incoming event có session_id trên envelope được merge vào payload cho view model. GAME_START/GAME_ABORTED vừa đối chiếu request_id vừa phát state event để không bỏ countdown/hủy ván khi response đồng thời là sự kiện.

Payload trạng thái UI hiện dùng: phase, room_code, room_name, is_owner, question_count, duration_seconds, players(player_id/nickname/avatar_id/ready/connected), connected_player_count, ready_count, answered_count. QUESTION dùng number/total/content/option_a..d/category/remaining_ms, không correct_answer. QUESTION_RESULT/ROUND_RANKING/GAME_OVER dùng ranking(rank/nickname/player_id/avatar_id/score/connected); các trường correct/your_answer/score là phần cá nhân của Player, không hiển thị ở projector. ROUND_RANKING thêm session_id/question_id/remaining_ms; UI không tự công bố hạng chỉ vì local timer hết. Server phải chốt/validate DTO và test hợp đồng này trước tích hợp.

Không auto-retry mutation/ANSWER sau timeout: không suy ra chưa ghi chỉ vì không nhận response. Phiên mới loại callback của phiên cũ. UI game không tự chuyển câu/chấm điểm khi timer về 0; chờ sự kiện Server. Thiết kế guest không khôi phục connection giữa ván.

## 5 Kiểm thử đã thực hiện

JUnit reactor: protocol/framing và endpoint cũ; thêm normalizer tiếng Việt, khóa lựa chọn/ACK/deadline; fixture lọc AND/phân trang/trùng inactive/sửa loại chính ID/role UI/không trả password tạm; TLS localhost với DNS/IP SAN hợp lệ, sai SAN, không tin cậy, phản hồi lỗi/correlation và fragmented Unicode/event.

Số test và trạng thái build cuối xem README và surefire reports. Đây không phải test auth/PBKDF2/SQLite hoặc nhiều máy thật. Không lấy test của Python prototype làm minh chứng Java.

QA JavaFX opt-in (chạy rồi tự đóng):

```powershell
.\scripts\Run-Quiz.ps1 -Role Admin -Capture tmp/ui-qa/final-1366 -Width 1366 -Height 768
.\scripts\Run-Quiz.ps1 -Role Player -Capture tmp/ui-qa/final-1024 -Width 1024 -Height 640
```

41 trạng thái mỗi lượt, ảnh Scene thật, không ảnh mockup sinh từ prompt. Runner còn thao tác search/xóa lọc/chuyển trang và assert số hàng; xác nhận lỗi kết nối không mở fixture. Luồng form thực tế: điền câu trùng → Lưu → cảnh báo → Quay lại → Lưu lần nữa → chủ động Lưu dù trùng → assert ngân hàng tăng từ 20 lên đúng 21 câu mẫu. QA mới bấm chọn avatar, kiểm tra nút đã có chủ bị khóa, avatar cũ được thả, lựa chọn nhận ACK; kiểm tra bảng hạng khởi đầu ở tọa độ cũ, kết thúc tọa độ mới, giảm chuyển động không dịch hàng, thêm projector bảng hạng. ACK/hết giờ/câu dài/ranking đổi vị trí là trạng thái dựng có nhãn để kiểm tra view, không phải traffic của Server thật. Chiều rộng 1366 có thể ra 1367 do quy đổi pixel của JavaFX/Windows.

Ảnh v2 ở `output/ui-preview-v2/1366` và `output/ui-preview-v2/1024`; giữ nguyên ảnh v1 trong `output/ui-preview/` để đối chiếu. Projector 1280×720. Chưa nghiệm thu screen reader, DPI Windows 125%/150%, điều hướng Tab đầy đủ bằng người dùng, nhiều màn hình hoặc máy chiếu thực tế. Không ghi Pass cho các mục đó.

JUnit v2 thêm kiểm tra catalog, roster đầy/độc lập giữa phòng/giải phóng/đổi avatar thất bại, 12 luồng cùng tranh một avatar chỉ một thắng; Preview kiểm tra vai trò/ready/khóa/đóng phòng và chuỗi đầy đủ hai câu RESULT → RANKING → QUESTION hoặc GAME_OVER. Không dùng các test này để tuyên bố đã có nhiều Client thật qua LAN.

Copy v2: bỏ nhãn JavaFX/RAM/DB/Server khỏi màn chơi thường ngày; giữ badge “Xem thử” và tooltip giới hạn. Không giấu cảnh báo mất kết nối, TLS hoặc lỗi lưu kết quả. Skill UI/UX hướng dẫn focus/giảm chuyển động/phân biệt trạng thái bằng chữ; frontend-skill định hướng bớt lời giải thích và dùng linh vật làm nhận diện.

## 6 SFX sau khi nhận file

Giữ bộ prompt 17 âm trong `UI_Sound_Effects_Prompts.md`. `SfxSlots` hiện chỉ enum/hook im lặng và loại event trùng; chưa có engine phát âm, preload, giới hạn channel hoặc volume. Khi nhận tài nguyên: kiểm tra định dạng/clip/quyền sử dụng, giữ bản gốc, thêm JavaFX media/AudioClip, map cue, mute/volume persist và test offline. Projector không tự phát lại cue để tránh hai tiếng trùng trên cùng máy.

Nộp đáp án: click chỉ là input; ANSWER_ACK accepted mới kích hoạt cue đã nhận; correct/wrong chờ QUESTION_RESULT. Teacher/projector dùng round-close, không phát cue sai cá nhân. Không thêm nhạc nền.

## 7 Tiếp tục theo phụ thuộc

1. Chốt DTO/schema/validation và payload projector với Server; bổ sung HISTORY_GET.
2. Xây SSLServerSocket/Game Worker, bootstrap Admin, PBKDF2, users/role/owner và SQLite riêng.
3. Thay Preview bằng TlsQuizGateway, test CRUD/trùng, một Teacher + ba Player qua mạng thật; kiểm tra packet không lộ auth/answer.
4. Kiểm thử mất mạng, retry/idempotency mutation, deadline, đồng hạng và lỗi lưu history.
5. Test màn hình/DPI/keyboard, SFX khi có assets, sau đó đóng gói runtime.

Không cần chờ SFX để làm backend/integration. Chưa thay đổi firewall/router hoặc thuê dịch vụ hosting.
