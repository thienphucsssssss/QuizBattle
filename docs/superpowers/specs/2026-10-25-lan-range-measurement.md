# Đặc tả: Đo tầm kết nối LAN

Ngày: 25/10/2026. Trạng thái: đã triển khai và kiểm tra hồi quy trong mã nguồn; chưa có kết quả đo thực địa.

## 1. Mục tiêu và cơ sở yêu cầu

Người dùng xác nhận yêu cầu là **đo tầm kết nối thực nghiệm trong một tầng**, sau đó yêu cầu lập kế hoạch tính năng “Đo tầm kết nối LAN”. Khoảng cách được người vận hành đo thật rồi nhập vào ứng dụng; phần mềm kiểm tra giao tiếp thật hai chiều và lưu bằng chứng.

Sản phẩm hiện dùng Java 21, JavaFX 21, TCP/TLS 5050, discovery UDP 5051, protocol version 1. TlsQuizGateway có heartbeat PING mỗi 5 giây; QuizServer trả PONG, nhưng chưa có phép đo do server chủ động gửi. ArcadeApplication và CommanderViews đã có Cài đặt/Tùy chọn và trạng thái ping.

## 2. Phạm vi bản đầu

- Đo trên kết nối TLS đang dùng của thiết bị mở công cụ, sau LOGIN hoặc JOIN thành công. Admin, giảng viên và học sinh đều dùng được; không cần tạo phòng nếu đã đăng nhập tài khoản quản lý.
- Mở từ Cài đặt của quản lý, hoặc Tùy chọn của học sinh. Không thêm sidebar riêng.
- Một lượt đo ứng với một mốc khoảng cách, một thời lượng và hai hướng khởi tạo thông điệp.
- Giữ nhiều lượt trong một đợt đo, xem từng hướng và xuất CSV tổng hợp, CSV mẫu chi tiết.
- Tự lưu JSONL trên cả hai đầu để mất Wi-Fi không làm mất bằng chứng đã ghi tại mỗi máy.

Chưa làm: tự suy ra mét từ IP/ping/RSSI, GPS trong nhà, Wi-Fi RTT/FTM, quét mạng, tự đổi router/firewall, tự kết nối lại phiên thi, chọn một học sinh khác để điều khiển đo từ xa. Không thêm database hoặc thư viện phụ thuộc.

## 3. Dữ liệu người vận hành nhập

Thiết lập đợt đo, giữ nguyên qua các mốc:

- Tên đợt đo, tầng/tuyến đo: mỗi trường bắt buộc, tối đa 80 ký tự.
- Bố trí: SERVER_HOTSPOT, DEDICATED_AP hoặc SHARED_WIFI.
- Cơ sở khoảng cách: STRAIGHT_LINE hoặc CORRIDOR_ROUTE.
- Ghi chú môi trường/Wi-Fi/vật cản: tùy chọn, tối đa 500 ký tự.

Từng lượt:

- distance_m: số hữu hạn từ 0 đến 500, tối đa một chữ số thập phân. Nhãn cố định “Khoảng cách đo thực tế (m)”; chấp nhận dấu phẩy hoặc dấu chấm thập phân trên form, gửi số JSON chuẩn.
- duration_seconds: chỉ 30 hoặc 60, mặc định 30.
- interval_ms = 1000 và deadline_ms = 1000, cố định và hiển thị trong phần giải thích tiêu chí.

Thay đổi thiết lập đợt đo tạo survey_id mới; thay mốc hoặc thời lượng chỉ tạo lượt mới. Không gộp “mốc xa nhất” giữa các survey_id. UUID chỉ dùng định danh bản ghi, không dùng tạo IP hoặc số mét.

## 4. Phép đo và cách kết luận

Hai hướng đều là **thời gian khứ hồi ở tầng ứng dụng**:

- CLIENT_TO_SERVER: client gửi probe, server phản hồi; client dùng đồng hồ monotonic của chính mình tính RTT.
- SERVER_TO_CLIENT: server gửi challenge, gateway client trả lời tự động; server dùng đồng hồ monotonic của chính mình tính RTT.

Không lấy hiệu timestamp giữa hai máy. RTT bao gồm hàng đợi truyền, TLS, lịch xử lý và phản hồi; không gọi đây là thời gian truyền một chiều hoặc thời gian truyền sóng. Heartbeat hiện có không được tính là mẫu của lượt đo.

Với thời lượng N giây, mỗi phía lên lịch đúng N mẫu, sequence 1..N, ở t0, t0+1 giây, ... t0+(N-1) giây theo đồng hồ của phía đó. Hoàn tất khi mọi mẫu đã có kết quả hoặc qua deadline mẫu cuối; nếu lịch bị trễ thì không bắn dồn các mẫu đã lỡ, phải ghi CANCELLED và đánh dấu lượt không hoàn tất bình thường. Không chặn reader, writer hoặc JavaFX thread để chờ kết quả.

Trạng thái mẫu: ON_TIME, TIMEOUT, SEND_FAILED, DISCONNECTED hoặc CANCELLED. ON_TIME cần phản hồi đúng run_id/sequence, đến trong <=1000 ms; phản hồi trễ/trùng không thay đổi mẫu đã chốt. Mẫu chưa có bằng chứng từ phía khởi tạo là “chưa xác nhận”, không tự chuyển thành TIMEOUT hay thành công.

Mỗi hướng hiển thị số mẫu dự kiến, số mẫu đã xác nhận kết quả, số phản hồi đúng hạn, timeout, lỗi gửi/ngắt kết nối, tỷ lệ đúng hạn và min/median/p95/max RTT. Thống kê RTT chỉ tính ON_TIME; không có phản hồi thì để trống, không điền 0. p95 dùng nearest rank, ceil(0.95 * số RTT) - 1 trên dãy tăng dần.

Một lượt “Đạt tiêu chí tại mốc này” cần đồng thời:

1. Kết thúc COMPLETED, đủ N kết quả mỗi hướng và nhận bản tổng kết server.
2. Mỗi hướng có on_time / N >= 0.95.
3. Không có SEND_FAILED, DISCONNECTED, CANCELLED hoặc lỗi lưu nhật ký trong lượt.

Đây là tiêu chí kiểm thử được chốt cho bản đầu, không phải kết quả đã đo hoặc bảo đảm chất lượng mọi mạng. Với N=30 cần ít nhất 29 phản hồi đúng hạn mỗi hướng; với N=60 cần ít nhất 57.

EndReason: COMPLETED, STOPPED, DISCONNECTED, GAME_STARTED, ROOM_CHANGED, STORAGE_ERROR, CONTROL_TIMEOUT hoặc APP_CLOSED. CONTROL_TIMEOUT chỉ việc bắt tay điều khiển không hoàn tất, không tự chứng minh Wi-Fi đã mất sóng. “Dừng” là kết quả một phần, không được đạt tiêu chí. Nếu tắt ứng dụng đột ngột, đọc lại journal chưa có RUN_FINISHED thành APP_CLOSED và giữ phần đã ghi.

“Mốc xa nhất đã đạt tiêu chí” lấy max(distance_m) từ lượt đủ điều kiện của cùng đợt đo; chưa có thì hiện “Chưa có mốc đạt”. Kết nối loopback phải có nhãn “Kiểm thử cục bộ”; không dùng các lượt này kết luận tầm kết nối LAN thực địa. IP LAN tự nó không chứng minh hai máy vật lý; nghiệm thu thực địa là bước riêng của người vận hành.

## 5. Hợp đồng giao thức bổ sung

Giữ version 1 và envelope NDJSON/TLS hiện có. Nhóm LAN_RANGE_* là tùy chọn, không làm thay đổi PING/PONG. Tất cả frame có run_id sau khi bắt đầu; mỗi probe/challenge có sequence nguyên 1..N.

- LAN_RANGE_START -> LAN_RANGE_START_OK: gửi survey_id và các trường mục 3. Server xác thực giới hạn, tạo run_id, trả lại cấu hình đã chấp nhận, client_ip_observed từ socket.getInetAddress(), server_ip từ socket.getLocalAddress(), server_port thực tế và started_at UTC. Không nhận IP tự khai.
- LAN_RANGE_READY -> LAN_RANGE_READY_OK: client gửi sau khi journal client RUN_STARTED đã lưu; server chỉ lúc này mới bắt đầu đồng hồ/lịch đo. READY_OK được enqueue trước challenge đầu; client bắt đầu lịch đo khi nhận READY_OK. READY lặp không reset đồng hồ. Không nhận READY trong 10 giây sau START_OK thì kết thúc CONTROL_TIMEOUT.
- LAN_RANGE_PROBE -> LAN_RANGE_PROBE_OK: do client khởi tạo, phản hồi chứa đúng run_id/sequence; chỉ phản hồi một lần cho mỗi sequence hợp lệ.
- LAN_RANGE_CHALLENGE -> LAN_RANGE_CHALLENGE_REPLY: do server khởi tạo, reply giữ request_id của challenge. Gateway xử lý nội bộ trước pending RPC và trước chuyển sự kiện lên FX; không tạo future RPC cho reply một chiều này.
- LAN_RANGE_SERVER_SAMPLE: server gửi riêng cho peer đó kết quả SERVER_TO_CLIENT đã chốt, gồm sequence, status và rtt_ms nếu có.
- LAN_RANGE_STOP -> LAN_RANGE_STOP_OK: dừng đúng lượt của peer gửi; idempotent cho lượt vừa kết thúc trên chính peer đó.
- LAN_RANGE_COMPLETE -> LAN_RANGE_COMPLETE_OK: client gửi khi mọi mẫu CLIENT_TO_SERVER đã chốt. Server ghi nhận client đã xong; chỉ phát FINISHED bình thường khi chiều server cũng đo xong. Nếu chưa nhận COMPLETE sau duration_seconds + 5 giây tính từ READY, kết thúc CONTROL_TIMEOUT; không đóng lượt chỉ vì chiều server xong trước.
- LAN_RANGE_FINISHED: server gửi tổng kết SERVER_TO_CLIENT, end_reason, timestamps và tình trạng nhật ký server. Kết quả CLIENT_TO_SERVER vẫn do client đo và lưu; không giả lập hai chiều bằng một số RTT.

LAN_RANGE_START_OK phải được enqueue trước challenge đầu. TlsQuizGateway gắn responder với run_id khi đọc START_OK trước khi hoàn thành future; FINISHED hoặc đóng gateway xóa responder. CHALLENGE chỉ được trả lời khi run_id/sequence/request_id khớp lượt đang nhận, mỗi sequence tối đa một lần. Client chờ tối đa duration_seconds + 6 giây từ READY_OK để nhận FINISHED; quá hạn chốt CONTROL_TIMEOUT, cố gắng STOP và giữ phần đã lưu. Tổng kết đủ điều kiện đạt chỉ được công bố sau khi journal kết thúc phía tương ứng đã flush thành công.

Các lệnh cần danh tính LOGIN/JOIN và đã đổi mật khẩu bắt buộc. Mỗi peer tối đa một lượt, server tối đa bốn lượt đồng thời; vượt giới hạn trả LAN_RANGE_BUSY. Không chạy khi ván đang diễn ra: trả INVALID_STATE. START_GAME, reset/đóng phòng, LEAVE/LOGOUT và ngắt socket kết thúc các lượt liên quan; bắt đầu ván không bị công cụ đo chặn. Không gửi kết quả/IP của lượt vào ROOM_STATE, discovery hoặc bảng xếp hạng.

Probe RPC dùng timeout 1000 ms riêng trong TlsQuizGateway; PING vẫn 20 giây, RPC khác vẫn 10 giây. Phản hồi trễ bị bỏ qua theo run_id/sequence. Server cũ trả UNKNOWN_TYPE phải hiện thông báo cập nhật Server/Client hiện có; không hiển thị số liệu giả hoặc hạ TLS.

## 6. Nhật ký và xuất kết quả

- Client: %LOCALAPPDATA%/NetworkQuizBattle/lan-range/, fallback user.home/.network-quiz-battle/lan-range/.
- Server: thư mục lan-range/ cạnh database đang cấu hình, không dùng working directory hoặc thư mục bộ cài.
- Tên file: client-<run_id>.jsonl và server-<run_id>.jsonl; chỉ định danh do ứng dụng sinh được dùng tạo tên.
- Bản ghi: RUN_STARTED, RUN_READY, SAMPLE, RUN_FINISHED; mỗi bản ghi có schema_version=1, origin, run_id, survey_id, recorded_at UTC; metadata gồm khoảng cách/cách đo/bố trí/tuyến và IP thực tế. RUN_READY lưu thời điểm bắt đầu đo của chính đầu ghi; SAMPLE có direction, sequence, status, rtt_ms nullable và measured_by.
- Client giữ mẫu tự đo và mẫu server đã báo với measured_by tương ứng. Server giữ mẫu SERVER_TO_CLIENT tự đo; không tạo RTT CLIENT_TO_SERVER từ thời gian đến probe.
- Ghi bằng executor I/O riêng có hàng đợi hữu hạn 256 bản ghi, flush sau từng dòng. Không âm thầm bỏ bản ghi; hàng đợi đầy/ghi thất bại phải kết thúc STORAGE_ERROR, giữ dữ liệu trong RAM và hiển thị đường dẫn/lỗi lưu.
- Kiểm tra khả năng ghi ở cả hai phía trước khi bắt đầu mẫu đầu. Đọc lại file thiếu dòng kết thúc giữ các dòng JSON hoàn chỉnh; dòng cuối bị cắt phải báo lượt gián đoạn.
- CSV tổng hợp: một hàng mỗi lượt/hướng, gồm cấu hình, IP, thời gian, trạng thái, số mẫu và RTT. CSV chi tiết: một hàng mỗi mẫu đã có bằng chứng. UTF-8 BOM, escape dấu phẩy/ngoặc kép/xuống dòng; ô văn bản bắt đầu bằng =,+,-,@ được ghi dạng văn bản. Không xuất mật khẩu/tài khoản/token.
- CSV được ghi vào file tạm cùng thư mục rồi thay thế đích sau khi ghi thành công. Hủy hộp thoại không ghi file.

Nếu client mất Wi-Fi và không nhận FINISHED, kết quả chiều server chỉ là phần đã nhận, nhãn “Chưa nhận đủ kết quả server”. Nhật ký server vẫn là bằng chứng tại đầu server; hai file đối chiếu bằng run_id. Bản đầu chưa tự đồng bộ/gộp journal từ server sau reconnect; khi cần kết luận một mốc phải đo lại đủ lượt. Không suy ra trạng thái các mẫu chưa nhận.

## 7. Giao diện và vòng đời

Dùng lại navy/cobalt/lime, Be Vietnam Pro, control JavaFX và FxKit hiện có. Dialog “Đo tầm kết nối LAN” gồm thiết lập đợt đo thu gọn, mốc/thời lượng, hai phần kết quả có nhãn hướng rõ ràng, lịch sử và footer Bắt đầu/Dừng/Xuất tổng hợp/Xuất chi tiết/Đóng.

Giải thích cố định: “Khoảng cách do bạn đo và nhập. Ứng dụng kiểm tra giao tiếp thực tế hai chiều.” Lỗi nhập hiện cạnh trường; không chỉ dựa màu phân biệt trạng thái. Mở ở form chưa đăng nhập hoặc khi phải đổi mật khẩu thì không cho bắt đầu.

Dialog không reset số liệu khi có ROOM_STATE. Đóng dialog khi đang đo thực hiện Dừng và giữ lượt một phần. Đổi màn hình/nhận sự kiện trận đấu không mất journal; logout/reconnect giữ lịch sử đã lưu, bỏ run đang hoạt động và chặn callback của connectionGeneration cũ. Đăng nhập lại không tự tiếp tục một lượt cũ. Dữ liệu khôi phục từ đĩa có nhãn “Đã lưu”, không hiển thị như đang đo.

Network/I/O/tính mẫu ngoài FX thread; mọi thay đổi control trên FX thread theo cách dự án đang dùng. Footer và nút Dừng luôn tới được ở 1366x768 và 1024x640; nội dung cuộn riêng, không dựng biểu đồ/hiệu ứng mới.

## 8. Nghiệm thu

Tự động: kiểm tra công thức/giới hạn, TLS hai chiều, responder độc lập UI, stale/duplicate/late, mất kết nối/đổi pha, lỗi đĩa, xuất/khôi phục và tương thích server cũ. Dữ liệu deterministic trong test chỉ phục vụ kiểm thử logic; loopback không phải bằng chứng khoảng cách thực địa.

Thực địa: hai máy vật lý, cùng tầng; cố định server và điểm phát, tránh roaming hoặc ghi rõ khi nó xảy ra. Đo các mốc bằng thước, tăng từng mét gần giới hạn; lặp ít nhất ba lượt ở mốc gần biên. Lưu topology, cơ sở khoảng cách, vật cản, thời gian, journal hai đầu và CSV. Kiểm tra cả tình huống đi xa mất sóng rồi quay về, app đóng/mở và xuất kết quả. Chỉ báo cáo số mét sau phép đo này.

## Tham chiếu

- AGENTS.md; .cursor/rules/preserve-existing-ui-ux.mdc.
- nqb-client-core/.../TlsQuizGateway.java; nqb-server/.../QuizServer.java.
- nqb-admin-client/.../ArcadeApplication.java, CommanderViews.java, ManagementSidebar.java.
- docs/Giao_thuc_Java_LAN.md; design-system/network-quiz-battle/MASTER.md.
