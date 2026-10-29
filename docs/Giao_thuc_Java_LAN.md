# Giao thức Network Quiz Battle Java LAN

## LAN tự động 0.4.0

Server hỗ trợ `server.bind=auto` (wildcard IPv4); Client dùng `server.host=auto`, dò lại các interface hiện tại và nhận endpoint từ nguồn UDP. Không thêm bản tin mới: vẫn DISCOVER/DISCOVER_OK và ROOM_BROWSE. UDP chưa phải xác thực. TLS kiểm tra pin SHA-256 của khóa công khai leaf được cấp sẵn trong Client, thời hạn/mục đích chứng chỉ và chuỗi/thuật toán bằng trust manager của JDK, độc lập IP DHCP. Kiểm tra danh tính theo IP/SAN trong các ghi chép phiên bản cũ bên dưới đã được thay thế từ 0.4.0. Chi tiết vận hành: [LAN tự động](LAN_tu_dong_0_4_0.md).

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

## Bản 0.6.0: Sơ đồ LAN và lượt thu mẫu mới

Capability `LAN_TOPOLOGY_V1` được Client khai báo qua `client_capabilities` của LOGIN/REGISTER/JOIN và Server công bố trong ROOM_STATE. Client cũ không khai báo vẫn chơi được; các lượt thu mẫu ghi UNSUPPORTED cho máy đó. Schema SQLite giữ 4.

- Server gửi `NETWORK_PROBE {probe_id}` mỗi 5 giây, tối đa một probe đang chờ/Client. `NETWORK_PROBE_REPLY` nhận trên kết nối học sinh đó, đối chiếu nonce và deadline 3 giây; RTT do nanoTime của Server tính. TlsQuizGateway trả lời ngay trên lớp mạng, không đợi JavaFX. Thiếu probe không tự loại người chơi.
- `PROXIMITY_REFRESH`: chủ phòng/Admin, bật giám sát và chọn mốc. Một lượt/phòng, timeout 10 giây, cooldown 10 giây; yêu cầu đồng thời trả cùng lượt. ACK là snapshot giám sát có `refresh {refresh_id,state,total,completed,updated,remaining_ms,retry_after_ms}`.
- `PROXIMITY_REFRESH_REQUEST`: Server gửi cho Client hỗ trợ, kèm refresh_id/room_code/config_epoch/session_id. Client thu 5 mẫu mới trên worker Wi-Fi, giữ throttle báo cáo 5 giây và không trộn mẫu cũ/khác adapter/BSSID/băng tần.
- `PROXIMITY_REPORT` có refresh_id tùy chọn. Báo cáo vẫn kiểm tra seq/epoch/session/device/rate như trước; chỉ bộ 5 mẫu gắn đúng lượt và đúng kết nối mới hoàn tất máy đang chờ. Lượt cũ không hoàn tất lượt mới.
- `PROXIMITY_REFRESH_STATUS {refresh_id,reason}`: học sinh báo CALIBRATING/PERMISSION_DENIED/WIFI_DISABLED/NO_ADAPTER/DISCONNECTED/API_ERROR/CANCELLED/NOT_REPORTING khi không thu được mẫu; không làm giả khoảng cách bằng 0, không ngắt hiệu chuẩn.
- Snapshot `PROXIMITY_GET`/`UPDATE` bổ sung `topology_supported`, ping_ms/ping_age_ms/measurement_age_ms và refresh_status/refresh_reason/refreshed_at_utc từng máy. Các trạng thái lượt: IDLE/RUNNING/COMPLETED/CANCELLED; từng máy: PENDING/UPDATED/FAILED/TIMEOUT/UNSUPPORTED/CANCELLED. Số đo quá 15 giây không được hiển thị như số hiện tại.
- Snapshot toàn phòng chỉ đến chủ phòng/Admin. ROOM_STATE chỉ bổ sung ping/refresh riêng cho chính học sinh đó hoặc quản lý có quyền; không gửi telemetry riêng của bạn khác cho học sinh. Trình chiếu lấy bản đã lược bỏ trường kỹ thuật tại lớp hiển thị.

Làm mới không đổi công thức điểm, deadline, hiệu chuẩn hoặc tự reset ready. Mất điều kiện Wi-Fi thực tế vẫn được xử lý theo quy tắc ready/start đang có. Chi tiết UI, trạng thái và demo: [Sơ đồ LAN 0.6.0](So_do_LAN_va_lam_moi_0_6_0.md).

## Bản 0.5.1: Điểm tốc độ và streak

`ROOM_STATE.scoring_version` là `SPEED_STREAK_V1`. Quy tắc 100 điểm cố định của các bản trước được thay bằng 100 cơ bản + thưởng tốc độ 0–100 + thưởng chuỗi 0–100, chỉ cho đáp án đúng. Công thức và ví dụ: [Tốc độ và streak](Toc_do_va_streak_0_5_1.md).

- `ANSWER_ACK` hợp lệ thêm `answer_elapsed_ms`, đo bằng đồng hồ Server từ khi mở câu; không trả points/correct/streak trước khi đóng câu. Trường Client gửi cùng tên, score hoặc streak không được sử dụng.
- `QUESTION_RESULT` thêm `answer_elapsed_ms`, `streak`, `best_streak`, `speed_bonus`, `streak_bonus`; `points` là tổng thực nhận của câu. Sai/bỏ lỡ có 0 điểm và chuỗi về 0.
- Ranking trực tiếp thêm thời gian câu vừa rồi, streak/best_streak và points. Học sinh có thời gian/chuỗi riêng trong ROOM_STATE; chủ phòng/Admin thấy của người chơi. `-1` nghĩa chưa trả lời.
- Một lần ANSWER hợp lệ khóa cả lựa chọn và thời gian. Gửi trùng/sai phiên/quá hạn không cập nhật chúng. Điểm và chuỗi cập nhật đúng một lần khi chốt câu, reset khi ván mới. Điểm lưu trong SQLite là tổng mới, không tính lại lịch sử cũ; schema vẫn 4.

## Bản 0.5.0: Bộ câu hỏi, nguồn đề và lịch sử

`ROOM_STATE.capabilities` thêm `QUESTION_SETS_V1`, `HISTORY_QUESTIONS_V1` bên cạnh `WIFI_PROXIMITY_V1`.

| Bản tin / trường | Hợp đồng |
| --- | --- |
| `QUESTION_SET_LIST` | Manager đã đăng nhập; chỉ bộ của chính mình; `page`, `page_size` tối đa 100; trả items/total/has_more. |
| `QUESTION_SET_CREATE` | Mutation trong WAITING; `name` 1–80 ký tự và một trong `question_ids` (1–500 ID duy nhất thuộc owner) hoặc `filters` của QUESTION_LIST. Kết quả lọc lấy toàn bộ, tối đa 500. |
| `QUESTION_SET_DELETE` | Mutation trong WAITING; `question_set_id` thuộc owner; không xóa bộ đang được phòng dùng. Chỉ xóa bộ/liên kết, không xóa câu. |
| `QUESTION_LIST.question_set_id` | Tùy chọn, 0 = toàn bộ; owner được kiểm tra tại Server. Các bộ lọc khác vẫn giao nhau. |
| `QUESTION_IMPORT_COMMIT.question_set_name` | Tùy chọn, 0–80 ký tự; rỗng = không nhóm. Tạo/thêm bộ cùng tên của owner và ghi câu trong cùng transaction. |
| `ROOM_CONFIGURE` | WAITING hoặc FINISHED, chủ phòng; thêm `question_set_id` hoặc `history_session_id`, không dùng đồng thời. Kiểm tra nguồn/số câu trước khi sửa trạng thái. Đổi nguồn/count/duration xóa ready; từ FINISHED tạo lượt WAITING. |
| `ROOM_STATE` | Thêm `question_set_id`, `question_set_name`, `question_source_name`; `history_session_id` và tên ván gốc chỉ trả chủ phòng. Các trường proximity vẫn giữ. |
| `HISTORY_LIST`, `HISTORY_GET` | Thêm `duration_seconds`, `saved_question_count`, `reuse_available`, `has_proximity`, owner của ván. Chỉ dùng lại snapshot thuộc chính chủ ván; ván cũ thiếu snapshot xem điểm được nhưng reuse=false. |

`START_GAME` kiểm tra lại nguồn và câu đang bật; lịch sử lấy snapshot theo vị trí đã lưu, không truy vấn lại câu đã sửa/xóa trong ngân hàng. Không phát đáp án đúng của câu đang thi cho học sinh. Chấm đúng vẫn 100 điểm. `QUESTION_SET_CREATE`/`DELETE` dùng cùng mutation request cache hiện có.

`ROOM_STATE.players` thêm `ip_address`, `answered`, `score`, `correct_count` chỉ khi người xem là chủ phòng/Admin; `PROXIMITY_GET`/`UPDATE` có `ip_address` trong dòng người chơi với RBAC tương tự. IP lấy từ socket, không tin giá trị Client gửi; không chặn nhiều Client cùng IP. IP không được dùng làm số đo khoảng cách.

## Bản 0.3.8: Giao thức giám sát khoảng cách Wi-Fi (ước lượng)

Mở rộng trên cùng kết nối TLS/NDJSON hiện tại (capability `WIFI_PROXIMITY_V1` trong `ROOM_STATE` và `ROOM_BROWSE`):

| Bản tin | Quyền gửi | Mục đích |
|---|---|---|
| `PROXIMITY_CONFIGURE` | Chủ phòng (trong `WAITING`) | Cấu hình bật/tắt giám sát, điểm phát làm mốc (`anchor_bssid`, `anchor_band`, `anchor_ssid` hoặc `from_player_id`), ngưỡng cảnh báo (`threshold_meters`) và yêu cầu hiệu chuẩn lại cả phòng (`recalibrate_all`). |
| `PROXIMITY_CALIBRATION` | Sinh viên (trong `WAITING`) | Gửi mẫu hiệu chuẩn hai bước (`samples_1m`, `samples_5m`, tối thiểu 20 mẫu/bước, MAD ≤ 6 dB, $n \in [1, 6]$) hoặc xác nhận dùng lại hồ sơ cục bộ (`reused_local_profile`, `position_confirmed`). |
| `PROXIMITY_REPORT` | Sinh viên | Gửi tối đa 5 mẫu RSSI nguyên trong [-100, -15] dBm, trạng thái thiết bị, adapter/BSSID/băng tần, `config_epoch` bắt buộc và `seq` nguyên tăng dần. Trong trận phải gửi đúng `session_id`. Client và Server giới hạn 1 báo cáo được nhận / 5 giây. Server gắn `player_id` theo kết nối, kiểm tra toàn bộ payload trước khi cập nhật trạng thái. |
| `PROXIMITY_GET` / `PROXIMITY_UPDATE` | Chủ phòng / Admin | Lấy và nhận sự kiện cập nhật bảng giám sát trực tiếp. Sinh viên chỉ nhận phần `self` của chính mình trong `ROOM_STATE` và `PROXIMITY_REPORT_OK`. |
| `PROXIMITY_HISTORY_LIST` / `PROXIMITY_HISTORY_GET` | Giảng viên (phòng mình) / Admin (toàn bộ) | Đọc qua hàng đợi DB riêng để không chặn game. Mỗi kết nối chỉ có một truy vấn lịch sử đang chờ. GET nhận `session_id`, `alert_page` (mặc định 1) và trả `alerts` tối đa 20 dòng, `alert_page`, `alert_total`, `alerts_has_more`, cùng tóm tắt phiên/người chơi. |
