# Giao thức Network Quiz Battle Java LAN

## Một học sinh hoạt động trên mỗi IP — cập nhật 25/10/2026

Mỗi IP nguồn mà Server nhìn thấy chỉ được có **một học sinh còn kết nối**. JOIN từ kết nối khác có cùng IP trả `ERROR` với `code=IP_ALREADY_IN_USE` và thông báo: “Máy/IP này đã có một học sinh đang hoạt động. Hãy rời phiên học sinh hiện tại trước khi tham gia lại.” Quy tắc áp dụng dù đổi biệt danh, gửi danh tính/IP giả hoặc mở ứng dụng khác; phiên đang hoạt động được giữ nguyên. Server lấy IP từ socket TLS và kiểm tra trên Game Worker trước khi cấp `player_id`/avatar, nên hai JOIN đồng thời cùng IP chỉ có một thành công. Không đổi protocol_version/schema SQLite.

LEAVE/LOGOUT, socket đã đóng hoặc heartbeat hết hạn giải phóng IP; ROOM_CLOSE kết thúc toàn bộ phiên học sinh. ROOM_RESET vẫn giữ IP của học sinh còn kết nối. Dòng kết quả của người mất mạng không giữ chỗ IP; các điều kiện chỉ JOIN khi WAITING, biệt danh không trùng và còn chỗ vẫn áp dụng. Kết nối quản lý và kết nối chưa JOIN không chiếm lượt học sinh. IP là địa chỉ nguồn Server quan sát được: các thiết bị cùng IP qua NAT chia sẻ giới hạn; không suy ra định danh phần cứng. Xem [hướng dẫn vận hành](Theo_doi_hoc_sinh_va_IP.md).

## Theo dõi học sinh/phòng/IP — cập nhật 25/10/2026

`ROOM_STATE` và các phản hồi chứa snapshot phòng bổ sung `players[].ip_address` chỉ khi người nhận là Admin hoặc giảng viên chủ phòng. Trường này lấy từ socket TLS tại JOIN, giữ IP cuối nếu học sinh mất mạng trong ván; `connected` xác định trạng thái hiện tại. Tên/mã phòng tiếp tục nằm ở `room_name`/`room_code` của snapshot. Học sinh, giảng viên khác, `ROOM_BROWSE`, ranking và lịch sử điểm không nhận IP của học sinh khác. Không đổi protocol_version hoặc schema SQLite.

Cập nhật 27/10/2026: snapshot phòng đã xác thực có `connection_info` của chính socket người nhận: `server_ip`, `server_port`, `client_ip_observed`, `player_id` (rỗng cho kết nối quản lý). Server lấy địa chỉ trực tiếp từ hai đầu socket, không nhận giá trị khai báo từ Client. Dữ liệu này có trước khi chạy LAN_RANGE_START, không gồm số đo và không đưa vào ROOM_BROWSE/discovery. Học sinh có thể thấy endpoint của chính kết nối mình nhưng không nhận thêm quyền xem IP bạn khác.

Server ghi console log `NQB_STUDENT_TRACKING` cho JOINED/LEFT/DISCONNECTED/ROOM_UPDATED/ROOM_RESET/ROOM_CLOSED với thời gian UTC, server/player ID, nickname, tên/mã phòng, IP và trạng thái. Reset dọn cả socket đã đóng nhưng chưa được tick xử lý. Xem [cách sử dụng và giới hạn](Theo_doi_hoc_sinh_va_IP.md).

## Bộ câu hỏi riêng cho phòng — cập nhật 25/10/2026

Thêm `QUESTION_SET_LIST/CREATE/DELETE` cho quản lý theo chủ sở hữu; tạo/xóa chỉ khi phòng chờ, có cache mutation như các thao tác câu hỏi khác. `QUESTION_LIST` nhận bộ lọc `question_set_id`; `QUESTION_IMPORT_COMMIT` nhận `question_set_name` để tạo hoặc thêm các câu đã nhập vào bộ cùng transaction. `ROOM_CONFIGURE` nhận `question_set_id` (0: toàn bộ ngân hàng; bỏ trường: giữ nguồn của phòng hiện tại). `ROOM_STATE` trả ID/tên bộ. Chỉ lấy câu đang bật trong bộ đã chọn và không bù bằng câu ngoài bộ; kiểm tra lại khi bắt đầu. Mẫu Office và protocol_version giữ nguyên. Database nâng v1→v2 bằng transaction để thêm bộ/liên kết mà giữ dữ liệu cũ. Xem `Bo_cau_hoi_va_phong_thi.md`.

## Chấm điểm tốc độ — cập nhật 24/10/2026

Không đổi protocol_version hoặc các trường hiện có. `QUESTION_RESULT.points` trả điểm câu vừa chấm: đúng trong hạn nhận `50 + round(50 × thời_gian_còn_lại / thời_lượng_câu)` (50–100), sai/bỏ trống/trễ nhận 0; `score`, ranking và lịch sử dùng tổng điểm đã cộng. Server ghi thời gian của đáp án hợp lệ đầu tiên, bỏ qua mọi điểm/timestamp/thời lượng tự khai trong payload Client. Đáp án lặp hoặc bị từ chối không thay đổi thời gian ban đầu; ACK không công bố điểm/đúng sai. Xem `Cham_diem_theo_toc_do.md`.

## Nhập câu hỏi Excel và Word 0.3.4

Client đọc tệp cục bộ; Server chỉ nhận JSON chuẩn hóa qua TLS, không nhận ZIP/Office và không chạy POI. Không đổi protocol_version. API chỉ cho Admin/Teacher đã đổi mật khẩu tạm; gắn owner theo connection, chỉ cho `WAITING`.

- `QUESTION_IMPORT_PREVIEW`: payload `{offset:0, rows:[{content,option_a,option_b,option_c,option_d,correct_answer,category}], complete:false}` mở mới staging khi không có `import_id`. Các phần tiếp theo mang `import_id` và `offset` đúng tổng dòng đã nhận; phần cuối `complete:true`. Tối đa 20 dòng/phần, 500 dòng và 2 MiB JSON/phiên, vẫn giữ giới hạn envelope 64 KiB. Client chủ động chia phần khoảng 40 KiB và chờ giữa các phần để không vượt rate limit.
- `QUESTION_IMPORT_PREVIEW_OK`: `{import_id,total,complete,items:[{index,valid,errors:[],duplicate_in_bank,duplicate_in_file?}]}`. `duplicate_in_file` là index 0-based của lần xuất hiện hợp lệ đầu tiên; index không phải dòng Excel/Word. `errors` độc lập cho từng dòng; không ghi database khi preview. Trùng ngân hàng chỉ đối chiếu owner hiện tại.
- `QUESTION_IMPORT_COMMIT`: `{import_id,selected_indexes:[0,3],allow_duplicates:false}`. Chỉ nhận một lần xuất hiện của mỗi index hợp lệ trong staging đã hoàn tất. Không nhận nội dung/ID/owner mới ở commit. Kiểm tra lại toàn bộ dòng và câu trùng ở trong transaction; lỗi rollback toàn bộ; thành công append, không overwrite.
- `QUESTION_IMPORT_COMMIT_OK`: `{imported,skipped,question_ids:[]}`. Cache phản hồi thành công tối đa 128 mutation/connection theo request_id/type/payload; gửi lặp cùng ID nhận lại kết quả, không tạo bản sao. Không bảo đảm exactly-once sau reconnect/restart. Client không tự gửi lại khi chưa rõ kết quả.
- Một staging/kết nối, thời hạn cố định 2 phút; dữ liệu tạm được dọn khi hết hạn. Chọn tệp mới/kiểm tra lại mở staging mới và thay staging cũ. Hủy UI không ghi database; chưa có API hủy staging riêng.
- Lỗi: `INVALID_INPUT`, `IMPORT_INVALID_STATE`, `IMPORT_EXPIRED`, `DUPLICATE_QUESTION`, các lỗi auth/quyền/trạng thái/rate hiện có. Nếu ngân hàng thay đổi sau preview làm phát sinh trùng, commit không ghi một phần; UI yêu cầu kiểm tra lại. `allow_duplicates=true` chỉ bỏ cảnh báo trùng, không bỏ quyền/giới hạn/validation.

Các phần lịch sử dưới đây vẫn mô tả thêm các API cũ. Hướng dẫn người dùng ở `Nhap_cau_hoi_Excel_Word.md`.

Cập nhật 0.3.3: UDP cổng 5051 nhận `DISCOVER`, trả `DISCOVER_OK` với payload service `network-quiz-battle`, nonce 32 ký tự hex được echo và tcp_port; vẫn dùng envelope protocol_version/type/payload UTF-8. Datagram giới hạn 512 byte, responder tối đa 30 phản hồi/giây, nguồn IPv4 private/loopback. Không trả room metadata qua UDP. Thêm `ROOM_BROWSE` trên TLS, payload `{}`, cho phép trước LOGIN/JOIN, chỉ trả server_id/server_name và rooms (tối đa 1 phòng/Server: room_code, room_name, teacher_name, phase, player_count, max_players, joinable). Không cấp danh tính/quyền hoặc thay JOIN. Bị áp dụng giới hạn request/idle như kết nối thường. Xem `Tim_phong_LAN_va_nhap_ma.md`.

Cập nhật 22/10/2026, bản 0.3.2: thêm `REGISTER` cho connection chưa LOGIN/JOIN, payload `username`, `display_name`, `password`. Server chỉ tạo `TEACHER`, trả `REGISTER_OK` với dữ liệu công khai, chưa cấp phiên đăng nhập; Client tiếp tục `LOGIN` trên cùng kết nối TLS. Không bắt đổi mật khẩu tự đăng ký. Các mã lỗi: `USERNAME_TAKEN`, `INVALID_INPUT`, `FORBIDDEN`, `REGISTRATION_DISABLED`, `RATE_LIMITED`, `ALREADY_JOINED`, `SERVER_BUSY`. Giới hạn 5 REGISTER/phút/IP, có thể tắt qua `registration.enabled=false`. Client không tự retry REGISTER khi mất ACK; tài khoản đã được tạo thì chuyển về LOGIN. Kho TLS công khai được cấp kèm Client, không hỏi người dùng trên form. Chi tiết: `Dang_ky_va_TLS_tu_dong.md`.

Cập nhật triển khai 16/10/2026: các handler quản lý/phòng/game ở tài liệu này đã có Server và kiểm thử TLS/SQLite, không còn chỉ Preview. Các câu “chưa triển khai/mục tiêu” bên dưới là bản thiết kế ngày 14/10 được giữ để đối chiếu; kết quả thực tế ở `Nghiem_thu_Server_LAN.md`. Bản hiện tại nhận `room_code`/`session_id` của JOIN/ANSWER ở envelope; Server trả `session_id` trong payload sự kiện/phản hồi (Client chuẩn hóa và hỗ trợ cả vị trí envelope). `ROOM_STATE_OK` gửi snapshot cá nhân hóa gồm question/result/remaining_ms để chủ phòng trở lại giữa ván. Event `ROOM_CLOSED` thông báo rồi đóng kết nối guest. Không có API kick trong bản hiện tại.

Mutation ngân hàng cache tối đa 128 phản hồi **thành công**/connection theo request_id và chữ ký type+payload; cùng ID khác nội dung bị từ chối khi đã có trong cache. Lỗi trước khi ghi không được cache; không bảo đảm exactly-once qua reconnect/Server restart. Client không tự retry mutation. Một writer/connection, outbound 100, game queue 1024, auth 2 worker/queue 8, TLS handshake 4 worker/queue 16; 60 request/giây/connection, 20 LOGIN/phút/IP. Handshake 5 giây, chưa JOIN/LOGIN 10 giây, heartbeat 5 giây và disconnect 20 giây.

Protocol version 1, thiết kế cập nhật ngày 14/10/2026. Chưa có handler phát hành nên bổ sung hợp đồng lọc/trùng ngay trong version 1; đây không phải giao thức đã triển khai đầy đủ. TCP qua TLS, port mặc định 5050; sau handshake/xác minh chứng chỉ mới gửi JSON UTF-8 một dòng kết thúc LF, tối đa 65536 byte trước LF. Decoder ghép byte rồi decode frame hoàn chỉnh, không decode từng chunk cắt giữa ký tự tiếng Việt. Không fallback raw TCP.

```json
{"protocol_version":1,"type":"ANSWER","request_id":"req-1042","room_code":"QZ7K2","session_id":"s001","payload":{"question_id":25,"answer":"B"}}
```

Ví dụ ở trên kết thúc bằng LF khi gửi qua TLS. `session_id` là ID ván, không phải phiên đăng nhập. Server gắn user_id/role sau LOGIN hoặc player_id sau JOIN vào connection. Chọn vai trò chỉ điều hướng UI; Server không tin role/owner/player_id/điểm/timestamp do Client tự khai. LOGIN không cần room_code/session_id.

| Nhóm | Message chính |
| --- | --- |
| Xác thực | LOGIN, LOGIN_OK, LOGIN_ERROR, LOGOUT, LOGOUT_OK, PASSWORD_CHANGE, PASSWORD_CHANGE_OK |
| Tài khoản | USER_LIST/CREATE/UPDATE/SET_ACTIVE/RESET_PASSWORD và phản hồi *_OK; chỉ ADMIN |
| Ngân hàng | QUESTION_LIST/GET/CATEGORIES/CHECK_DUPLICATE/CREATE/UPDATE/DELETE/SET_ACTIVE và phản hồi *_OK |
| Phòng | ROOM_CONFIGURE, JOIN, JOIN_OK, AVATAR_SET, AVATAR_SET_OK, READY_SET, PLAYER_LIST, ROOM_STATE, LEAVE |
| Điều khiển | START_GAME, GAME_START, ABORT_GAME, GAME_ABORTED, ROOM_RESET, ROOM_CLOSE |
| Vòng câu | QUESTION, ANSWER, ANSWER_ACK, QUESTION_RESULT, ROUND_RANKING |
| Kết quả | GAME_OVER, HISTORY_LIST |
| Kết nối | PING, PONG, ERROR |

Schema payload/response chi tiết được bổ sung cùng từng handler trước khi tích hợp; bảng này là danh mục, không phải tuyên bố đã triển khai đủ message. `JsonMessages` hiện chỉ kiểm tra envelope tối thiểu, chưa kiểm tra toàn bộ type, quyền hay payload từng lệnh.

## Tài khoản và phiên

| Message | Payload yêu cầu hoặc phản hồi mục tiêu | Quyền |
| --- | --- | --- |
| LOGIN | username, password; không nhận role tự cấp | Connection chưa JOIN/LOGIN |
| LOGIN_OK | user_id, username, display_name, role, must_change_password | Server trả sau xác thực |
| LOGIN_ERROR | code và thông báo chung; không trả user/hash/salt | Server trả cùng request_id |
| PASSWORD_CHANGE | current_password, new_password | User đã login, kể cả phiên bắt buộc đổi password |
| LOGOUT | Object rỗng; trả LOGOUT_OK rồi đóng connection | User đã login |
| USER_LIST | keyword username/display_name, role, is_active, page, page_size; *_OK trả metadata/items/total không bí mật | ADMIN |
| USER_CREATE | username, display_name, temporary_password; Server cố định role TEACHER | ADMIN |
| USER_UPDATE | user_id, display_name; không đổi username/role | ADMIN, tài khoản TEACHER |
| USER_SET_ACTIVE | user_id, is_active | ADMIN, tài khoản TEACHER |
| USER_RESET_PASSWORD | user_id, temporary_password | ADMIN, tài khoản TEACHER |

Các phản hồi *_OK/ERROR luôn giữ request_id của yêu cầu. LOGIN_ERROR dùng AUTH_FAILED cho sai username/password hoặc user bị khóa; không tiết lộ username có tồn tại. Mật khẩu chỉ gửi trong TLS, không lưu/log payload này. USER_CREATE không cho role ADMIN và không trả password/hash/salt; người vận hành cấp mật khẩu tạm qua kênh riêng. Admin đầu tiên bootstrap cục bộ trên Server, không dùng message tự đăng ký.

must_change_password=true chỉ cho PASSWORD_CHANGE/LOGOUT/PING; các API quản lý khác trả PASSWORD_CHANGE_REQUIRED. PASSWORD_CHANGE xác minh mật khẩu hiện tại, lưu hash mới, trả kết quả rồi đóng phiên để login lại. Khóa/reset cũng tăng credential_version và thu hồi connection liên quan. Đổi/reset/khóa chủ phòng trong COUNTDOWN/QUESTION_ACTIVE/SHOW_RESULT/SHOW_RANKING trả INVALID_STATE; ở WAITING/FINISHED thì đóng phòng đó và giải phóng owner. Tối đa một connection quản lý/user; login thứ hai trả ALREADY_LOGGED_IN sau khi xác thực thành công. Auth executor có queue/timeout/rate limit; auth result cũ không được gắn lại connection mới.

## Quyền sở hữu và phòng

QUESTION_LIST/GET/CATEGORIES/CHECK_DUPLICATE/CREATE/UPDATE/DELETE/SET_ACTIVE và HISTORY_LIST dùng user_id tại Server, không nhận owner_user_id/host_user_id do Client lựa chọn. Question ID của người khác trả NOT_FOUND như ID không tồn tại; không trả nội dung/đáp án/candidate của ngân hàng khác. UPDATE/DELETE phải lọc cả ID và owner trong SQL. ADMIN có ngân hàng/lịch sử của mình, không mặc nhiên vượt kiểm tra owner.

ROOM_CONFIGURE khi chưa có owner nhận user_id đã login làm chủ phòng; nếu đã có owner khác trả ROOM_BUSY. START_GAME/ABORT_GAME/ROOM_RESET/ROOM_CLOSE chỉ cho owner, nếu user khác thì FORBIDDEN. START_GAME chỉ lấy câu hỏi của owner. ROOM_CLOSE chỉ ở WAITING/FINISHED, dọn lobby và mã phòng cũ để user khác tổ chức; ROOM_RESET giữ owner. Logout/rớt mạng thu hồi quyền connection nhưng giữ room owner, cùng tài khoản đăng nhập lại mới nhận điều khiển; không phải khôi phục phiên học sinh.

Các lỗi mục tiêu thêm: AUTH_REQUIRED, AUTH_FAILED, PASSWORD_CHANGE_REQUIRED, ALREADY_LOGGED_IN, FORBIDDEN, NOT_FOUND, ROOM_BUSY, RATE_LIMITED, SERVER_BUSY. role TEACHER không gọi API USER_*, STUDENT không gọi auth management/question/history/room control. Không trả stack trace hoặc metadata bí mật trong lỗi.

ANSWER_ACK trả request_id, session_id, question_id, accepted và reason. Các reason mục tiêu: DUPLICATE_ANSWER, TIME_EXPIRED, WRONG_SESSION, WRONG_QUESTION, INVALID_ANSWER, INVALID_STATE. ERROR dùng code và message dễ hiểu, không lộ stack trace hoặc bí mật.

QUESTION gồm ID câu, số thứ tự/tổng câu, nội dung, A/B/C/D và remaining_ms; không có correct_answer. QUESTION_RESULT mới công bố đáp án. Sau 3 giây Server phát ROUND_RANKING để công bố bảng hạng trong 4,5 giây, kể cả câu cuối; sau đó mới chuyển câu hoặc GAME_OVER. GAME_OVER có ranking và result_saved. Ranking gồm player_id, nickname, avatar_id, score, rank, connected. Chi tiết bổ sung bên dưới.

PING/PONG dùng request_id, chu kỳ 5 giây, timeout 20 giây. JOIN/auth timeout 10 giây. Hỏng frame/UTF-8/quá lớn đóng riêng connection; quyền/trạng thái/payload sai trả ERROR hoặc ACK từ chối. Mỗi Socket chỉ một writer để tránh trộn byte.

Tests hiện có framing/envelope/endpoint, TLS với SSL peer localhost và UI fixture; chưa chứng minh login, role/owner, lưu ngân hàng hoặc Server sản phẩm nhận các message qua LAN. Các tình huống TC17–TC23 trong đề cương là kế hoạch kiểm thử, không phải kết quả Pass.

## Bổ sung UI v2: linh vật và bảng hạng từng câu

Đây là hợp đồng mục tiêu cho Server. PreviewGateway đã mô phỏng hợp đồng này; ServerMain vẫn chưa mở socket.

- JOIN payload nickname; room_code ở envelope. Server gắn player_id vào connection, chọn avatar trống trong danh mục 24 ID `quizling-1` … `quizling-24`, trả JOIN_OK với avatar_id. Nếu chưa có phòng, sai mã, hết chỗ hoặc đang thi thì từ chối, không giữ avatar. Mục tiêu tải vẫn 10 Player, 24 là số lựa chọn chứ không phải cam kết tải mới.
- AVATAR_SET payload `{ "avatar_id": "quizling-8" }`, room_code ở envelope; không nhận player_id tự khai. Chỉ STUDENT đã JOIN, trong WAITING và chưa ready. AvatarCatalog kiểm tra allowlist. Một AvatarRoster thuộc một phòng; Game Worker kiểm tra và đổi nguyên tử. Tranh chấp trả AVATAR_TAKEN, giữ avatar cũ, kèm players hiện tại trong ERROR để UI cập nhật. Thao tác chọn lại avatar của mình là idempotent.
- Thành công trả AVATAR_SET_OK cùng request_id với payload ROOM_STATE và broadcast ROOM_STATE. `players[]` thêm avatar_id. UI không tự chọn thành công trước phản hồi, không retry mutation tự động. Lỗi timeout phải lấy ROOM_STATE để đối chiếu.
- LEAVE/kick/disconnect được xác nhận qua heartbeat giải phóng avatar ở lobby. Trong ván dùng snapshot người chơi để giữ avatar của người đã rời trong kết quả, không cho JOIN mới. ROOM_CLOSE dọn toàn bộ; ROOM_RESET giữ người/linh vật còn kết nối, xóa ready/điểm. Server restart không khôi phục guest.
- Chuỗi phase: WAITING → COUNTDOWN → QUESTION_ACTIVE → SHOW_RESULT → SHOW_RANKING → QUESTION_ACTIVE hoặc FINISHED. Tất cả guard “đang thi” (đổi/khóa/reset mật khẩu owner, JOIN, sửa phòng, avatar, reset) phải bao gồm SHOW_RANKING.
- ROUND_RANKING là event không request_id; envelope session_id, payload question_id/number/total/remaining_ms/ranking. Mỗi câu phát một lần sau chấm; đồng điểm đồng hạng 1,1,3, thứ tự phụ ổn định. Player và projector cùng nhận bảng công khai; không đưa dữ liệu tài khoản vào event.
- Client bỏ event khác session/question hoặc ranking lặp. Chuyển động lấy thứ tự mảng cũ theo player_id, vị trí mới từ mảng Server. Mũi tên tính chênh lệch rank Server, không suy ra hạng từ số thứ tự hàng. Không animation cho lần đầu, bản ghi mới, chung cuộc hoặc khi bật giảm chuyển động. Timer về 0 chỉ chờ event tiếp theo, không tự chuyển câu.

Kiểm thử bắt buộc khi tích hợp: hai socket đồng thời tranh cùng avatar chỉ một thành công; cập nhật khóa trên mọi Player; đổi thất bại không mất avatar cũ; rớt mạng giải phóng đúng lúc; không đổi khi ready/đang thi; thứ tự RESULT → RANKING → QUESTION/GAME_OVER trên mọi máy; đồng hạng, hủy ván và event cũ không làm UI quay ngược.

## Dùng lại đề từ lịch sử

- `HISTORY_LIST` và `HISTORY_GET` trả thêm `duration_seconds`, `saved_question_count`, `reuse_available`. Ván cũ thiếu đề đã lưu có `reuse_available=false`; không đoán câu từ ngân hàng hiện tại. Response không trả nội dung đề hoặc đáp án.
- `ROOM_CONFIGURE` nhận `history_session_id` (chuỗi tối đa 80 ký tự) để chọn câu đã thi từ một ván của chính tài khoản đang đăng nhập. Gửi `question_set_id=0` khi dùng nguồn này; gửi đồng thời bộ riêng khác 0 bị từ chối. Chuỗi rỗng xóa nguồn lịch sử. Khi không gửi cả hai trường nguồn, giữ nguồn hiện tại; gửi riêng `question_set_id` chuyển về nguồn ngân hàng/bộ như các Client cũ.
- Kiểm tra quyền và đủ câu tại cả cấu hình và bắt đầu. Lỗi `NOT_FOUND` khi kết quả không thuộc tài khoản; `HISTORY_QUESTIONS_UNAVAILABLE` khi ván chưa lưu đủ đề; `NOT_ENOUGH_QUESTIONS` khi vượt số câu đã lưu. Không lấy bù từ ngân hàng. Khi giảm số câu, lấy phần đầu theo thứ tự gốc.
- Cấu hình nguồn lịch sử được phép trong `WAITING`, hoặc `FINISHED` của chính chủ phòng. Mọi kiểm tra phải thành công trước khi reset phòng đã kết thúc: cấp mã mới, giữ người đang kết nối, xóa ready/điểm. Các pha đang thi vẫn bị chặn. Hủy form ở Client không gửi mutation.
- `ROOM_STATE` thêm `question_source_name`; chỉ chủ phòng nhận `history_session_id` và `history_room_name`. `ROOM_RESET` giữ nguồn, `ROOM_CLOSE` xóa nguồn. `START_GAME` cấp session mới và chỉ dùng câu đã lưu; kết quả ván mới được lưu độc lập.
- Schema 3 có thời lượng trong `matches` và `match_questions(session_id,position,question_json)`. Đề giữ nguyên nội dung/options/correct_answer đã thi, không phụ thuộc vòng đời câu gốc và không thêm bản sao vào ngân hàng. Đề, bảng điểm và thông tin ván commit/rollback cùng nhau.

## Đo tầm kết nối LAN — 25/10/2026

Sơ đồ Phòng thi dùng ROOM_STATE.players[] và bổ sung max_players, server_port từ cấu hình/socket Server. Bản hiện tại không tạo distance_m/wifi_rtt_measurement cho client; không coi dữ liệu mốc nhập tay LAN_RANGE_START là Wi-Fi RTT phần cứng. Xem [nguồn dữ liệu sơ đồ](So_do_ket_noi_phong_thi.md).

Nhóm LAN_RANGE_* dùng protocol version 1, NDJSON và TLS hiện có. Chỉ sau LOGIN/JOIN, đã đổi mật khẩu bắt buộc; không chạy trong ván thi. Mỗi peer một lượt, toàn server tối đa bốn lượt. Không đưa dữ liệu đo vào discovery, ROOM_STATE hoặc kết quả thi.

- LAN_RANGE_START: survey_id UUID, survey_name/route tối đa 80 ký tự, topology (SERVER_HOTSPOT/DEDICATED_AP/SHARED_WIFI), distance_basis (STRAIGHT_LINE/CORRIDOR_ROUTE), notes tối đa 500, distance_m trong 0..500 tối đa một số lẻ, duration_seconds 30 hoặc 60. START_OK trả cấu hình cùng run_id, client_ip_observed/server_ip/server_port lấy từ socket, loopback và started_at UTC. Không nhận IP tự khai.
- Client ghi RUN_STARTED rồi gửi LAN_RANGE_READY với run_id. Server enqueue READY_OK trước challenge đầu; READY lặp không reset lịch. Quá 10 giây chưa READY => CONTROL_TIMEOUT.
- CLIENT_TO_SERVER: LAN_RANGE_PROBE {run_id,sequence} → LAN_RANGE_PROBE_OK giữ hai trường; client tính RTT bằng monotonic riêng. RPC này timeout 1000 ms; PING vẫn 20 giây và RPC khác 10 giây.
- SERVER_TO_CLIENT: LAN_RANGE_CHALLENGE có request_id dạng range:<run_id>:<sequence>; gateway trả LAN_RANGE_CHALLENGE_REPLY cùng request_id/run_id/sequence, không tạo RPC future. Xử lý trực tiếp trên transport reader, không chờ JavaFX.
- LAN_RANGE_SERVER_SAMPLE: sự kiện riêng cho peer khởi tạo, gồm run_id, direction, sequence, status, rtt_ms nullable và measured_by=server. Status ON_TIME/TIMEOUT/SEND_FAILED/DISCONNECTED/CANCELLED; server chỉ tính RTT của chiều mình khởi tạo. Mẫu trễ/trùng không chốt lại.
- Mỗi hướng N mẫu (N=30/60), cách nhau 1000 ms, deadline 1000 ms; không phát dồn khi trễ lịch. Client gửi LAN_RANGE_COMPLETE sau khi chốt hết mẫu; server trả COMPLETE_OK, đợi chiều server xong rồi phát LAN_RANGE_FINISHED sau flush journal, gồm server_summary, server_journal_healthy, end_reason và ended_at.
- Chưa COMPLETE sau N+5 giây từ READY => CONTROL_TIMEOUT. Client chờ FINISHED tối đa N+6 giây từ READY_OK. LAN_RANGE_STOP {run_id} → STOP_OK, idempotent cho lượt cuối cùng trên cùng peer; giữ kết quả một phần.
- End reason: COMPLETED/STOPPED/DISCONNECTED/GAME_STARTED/ROOM_CHANGED/STORAGE_ERROR/CONTROL_TIMEOUT/APP_CLOSED. START_GAME, reset/close phòng, LEAVE/LOGOUT hoặc đóng socket kết thúc lượt; công cụ không chặn bắt đầu ván.
- Lỗi AUTH_REQUIRED/PASSWORD_CHANGE_REQUIRED/INVALID_STATE theo giao thức sẵn có; INVALID_INPUT khi sai cấu hình/mẫu; LAN_RANGE_BUSY khi đã có lượt hoặc hết slot; LAN_RANGE_NOT_FOUND khi run_id không thuộc peer; STORAGE_ERROR khi không ghi nhật ký. Server cũ UNKNOWN_TYPE không được dẫn tới Preview hoặc số liệu giả.

Đánh giá đạt cần đủ bằng chứng hai hướng, mỗi hướng ít nhất 95% đúng hạn và không có lỗi gửi/ngắt/hủy/lưu. RTT là khứ hồi tầng ứng dụng, không phải thời gian một chiều hoặc khoảng cách. Cùng máy kể cả qua IP LAN được đánh dấu cục bộ và loại khỏi mốc xa nhất thực địa. Chi tiết journal/CSV và nghiệm thu: [Đo tầm kết nối LAN](Do_tam_ket_noi_LAN.md).

### Điều khiển lượt đo client từ chủ phòng

- `LAN_RANGE_REMOTE_START`: chủ phòng gửi `control_id` UUID mới, `player_id` đang kết nối và cấu hình mốc/thời lượng như `LAN_RANGE_START`. Server kiểm tra chủ phòng, pha, socket mục tiêu và giới hạn 4 lượt; trả ACK cùng metadata thực và gửi `LAN_RANGE_REMOTE_REQUEST` cho đúng Client. Một máy quản lý chỉ điều khiển một lượt tại một thời điểm.
- Client mới tự nhận lệnh qua LanRangeClient, gửi `LAN_RANGE_START` kèm control_id rồi thực hiện READY/probe/complete như cũ. Không tạo probe từ máy quản lý. Metadata run_id/IP/loopback do Server quyết định.
- `LAN_RANGE_REMOTE_REPORT`: chỉ socket mục tiêu được gửi snapshot cùng control_id/run_id. Server kiểm tra mẫu chiều Client, sequence, probe đã quan sát và không cho sửa mẫu đã ghi; tự tổng hợp chiều Server từ LanRangeSession, bỏ thống kê Server/IP/đạt do Client khai. Kết quả được gửi riêng cho chủ điều khiển qua `LAN_RANGE_REMOTE_STATE`.
- `LAN_RANGE_REMOTE_STOP`: chỉ chủ điều khiển của control_id được dừng. Server gửi `LAN_RANGE_REMOTE_CANCEL` tới Client và chốt phiên đo. Lỗi bắt đầu/Client cũ không nhận lệnh trả `LAN_RANGE_REMOTE_FAILED`. Chuẩn bị quá 10 giây, Client/quản lý rời, đổi phòng hoặc bắt đầu thi giải phóng phiên và giữ kết quả một phần; không kết luận đạt khi thiếu tổng kết.
- Máy quản lý lưu bản nhật ký trong `controlled/`, cùng run_id để đối chiếu hai đầu. CSV kèm player_id/biệt danh. Không công khai quyền điều khiển hoặc kết quả qua UDP/ROOM_BROWSE, không tự nối lại phiên cũ khi Client đổi socket.

## Bộ lọc câu hỏi

Ví dụ request mục tiêu, gửi dưới dạng một dòng JSON/LF sau TLS:

```json
{"protocol_version":1,"type":"QUESTION_LIST","request_id":"q-list-1","payload":{"keyword":"mạng","category":"TCP/IP","is_active":true,"page":1,"page_size":10,"sort":"UPDATED_DESC"}}
```

Các filter kết hợp AND; category/is_active null là không lọc. Page 1-based, page_size 1–20, mặc định 10. Sort allowlist ID_ASC/UPDATED_DESC, thêm id làm thứ tự phụ. Server kiểm tra type/length/page/sort, dùng normalized fields và owner từ connection; không tin SQL/owner do Client gửi. User list dùng quy tắc phân trang tương tự, keyword trên username/display_name.

QUESTION_LIST_OK payload gồm items/total/page/page_size/has_more; item gồm question_id/content_preview/category/is_active/updated_at_utc, không trả cả bốn đáp án cho toàn trang. QUESTION_GET request question_id, *_OK trả nội dung/A–D/correct_answer/category/is_active của owner. QUESTION_CATEGORIES *_OK trả categories của riêng owner, gồm câu active/inactive để dùng filter. Giới hạn input/detail/preview được chốt trong context; response phải encode dưới 64 KiB, không cắt nửa JSON hoặc gây bỏ sót hàng khi đổi kích thước trang.

## Kiểm tra và cảnh báo trùng

QUESTION_CHECK_DUPLICATE request có content và exclude_question_id (null nếu tạo). Nếu exclude ID được gửi, phải là câu của owner trước khi loại nó. *_OK trả has_duplicate/total_matches/candidates, tối đa 5 candidate gồm question_id/content_preview/category/is_active; tuyệt đối không trả ngân hàng người khác.

QUESTION_CREATE có content/options/correct_answer/category/is_active và allow_duplicate boolean mặc định false. QUESTION_UPDATE thêm question_id. CREATE/UPDATE luôn kiểm tra trùng lại ở Server, không phụ thuộc request xem trước. Trùng normalized_content cùng owner, kể cả inactive hoặc category/options khác; update loại chính ID. Không fuzzy/AI trong MVP.

Nếu có trùng và chưa xác nhận, Server trả ERROR payload có code DUPLICATE_QUESTION, message, candidates và total_matches cùng request_id; chưa ghi. Chính sách đề xuất cho phép UI xác nhận Lưu dù trùng rồi gửi allow_duplicate=true với request_id mới. Chỉ bỏ cảnh báo có xác nhận, không bỏ role/owner/validation/trạng thái. QUESTION_SET_ACTIVE có question_id/is_active, là mutation nên chỉ cho owner trong WAITING.

Đối với các mutation ngân hàng, cần cache kết quả hữu hạn theo connection/request_id để retry cùng yêu cầu không tạo bản ghi mới. Nếu cùng ID nhưng nội dung yêu cầu khác, trả REQUEST_ID_REUSED; không lấy request_id làm quyền truy cập. Không lưu payload chứa password trong cache/log vì cơ chế này chỉ áp dụng các mutation ngân hàng, không phải auth.

Gate bổ sung nằm ở TC24–TC38 trong plan. Tất cả message/handler nêu ở hai mục mới vẫn là hợp đồng cần triển khai và test, không phải chức năng đã có.
