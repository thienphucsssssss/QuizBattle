# Sơ đồ LAN và làm mới số đo — 0.6.0

## Giao diện

Phòng thi của giảng viên/Admin có sơ đồ với điểm phát Wi-Fi ở giữa và nút Máy chủ riêng. Học sinh dùng linh vật đã chọn; số nhỏ trên nhánh gồm ping với Server và khoảng cách ước lượng đến điểm phát. Đây là sơ đồ kết nối, không phải bản đồ vị trí thật.

Màn Giám sát có Sơ đồ / Danh sách, mở độc lập và giữ nguyên khi chuyển câu. Bấm học sinh để mở chi tiết, Thu gọn để dành chỗ cho sơ đồ. IP/BSSID/RSSI nằm trong quản lý; trình chiếu chỉ hiện tên, linh vật, trạng thái, ping và số mét. Học sinh nhận số đo của chính mình, không nhận snapshot riêng của các bạn.

Chung cuộc dùng bục vinh danh và bảng hạng song song khi nội dung rộng từ 1.050 px. Cửa sổ hẹp xếp dọc và cuộn. Đồng hạng vẫn giữ đầy đủ mọi người có hạng 1–3, không cắt còn ba người. Bảng sau từng câu giữ hoạt ảnh đổi vị trí; điểm tốc độ/streak và nhạc High Score Glory giữ nguyên.

## Làm mới số đo

1. Cho các máy cùng kết nối điểm phát điện thoại, mở Server và vào phòng theo luồng LAN hiện tại.
2. Bật giám sát, chọn đúng điểm phát/băng tần; học sinh cấp quyền và hiệu chuẩn 1 m/5 m như trước.
3. Chủ phòng/Admin bấm **Làm mới số đo** tại phòng chờ hoặc cửa sổ Giám sát khi đang thi.
4. Các Client hỗ trợ thu bộ 5 mẫu RSSI mới; không lấy lại cửa sổ mẫu cũ. Giao diện hiện tiến độ và làm mờ số cũ trong lúc chờ.
5. Báo cáo vẫn tối đa một lần mỗi 5 giây. Lượt mới thường hoàn thành sau khoảng 4–6 giây, hết chờ sau 10 giây; giới hạn mỗi phòng một lượt/10 giây. Nhiều cửa sổ cùng theo dõi một lượt.

Nếu đang hiệu chuẩn, thiếu quyền, tắt Wi-Fi, sai điểm phát hoặc chưa hiệu chuẩn, từng máy có nguyên nhân riêng; không tự bật hộp quyền hay hủy hiệu chuẩn đang chạy. Bấm nút không reset điểm, sẵn sàng hay deadline. Kiểm tra mất điều kiện Wi-Fi thực tế vẫn theo chính sách sẵn sàng trước đây.

Lượt bị hủy khi đổi phòng, đổi epoch/mốc hoặc chuyển phiên thi; phản hồi cũ không hoàn tất lượt mới. Đăng xuất đóng cửa sổ giám sát và xóa snapshot để không giữ dữ liệu tài khoản trước. Timer giao diện dừng khi ẩn/đóng cửa sổ.

## Demo di chuyển điện thoại

- Giữ hai laptop đứng yên, ghi nhận số đo ban đầu và thời điểm cập nhật.
- Di chuyển điện thoại tới một vị trí khác trong khoảng hiệu chuẩn 1–5 m, giữ yên rồi bấm làm mới; chờ lượt kết thúc.
- Kiểm tra số mét từng máy và thời điểm **Đã đo mới**; phần chi tiết có RSSI để đối chiếu. Mang điện thoại về vị trí trước và thử lại.
- Hướng thay đổi của từng máy phụ thuộc vị trí tương đối với điện thoại. Không yêu cầu tất cả cùng tăng, hoặc mọi lần bấm đều đổi số: RSSI có thể giữ nguyên/dao động; phần mềm không ép thay số để minh họa.
- Quá 15 giây không có số đo hợp lệ thì không hiện số cũ như số hiện tại. Mất kết nối có trạng thái riêng. Số mét luôn là **ước lượng**, ping không được dùng để suy ra mét.

## Giao thức và tương thích

Capability mới: `LAN_TOPOLOGY_V1`, khai báo tự động trong LOGIN/REGISTER/JOIN của Client mới. JOIN không có capability vẫn chơi được; phần làm mới ghi Cần cập nhật Client. Không đổi phiên bản NDJSON/TLS hoặc schema SQLite (vẫn 4).

| Bản tin / dữ liệu | Cách dùng |
| --- | --- |
| `NETWORK_PROBE` → `NETWORK_PROBE_REPLY` | Server tạo probe_id theo đúng kết nối, đo RTT bằng nanoTime; Client phản hồi ở lớp mạng. Mỗi máy một probe/5 giây, timeout 3 giây; không tự loại người chơi do thiếu probe. |
| `PROXIMITY_REFRESH` | Chủ phòng/Admin, giám sát đã bật và có mốc; trả snapshot có refresh_id/tiến độ. Yêu cầu trong cùng lượt được gộp, không khởi chạy thêm bộ mẫu. |
| `PROXIMITY_REFRESH_REQUEST` | Server gửi xuống từng Client hỗ trợ, kèm room_code/config_epoch/session_id/refresh_id. |
| `PROXIMITY_REPORT.refresh_id` | Gắn bộ 5 mẫu mới vào lượt; giữ seq tăng dần và kiểm tra epoch/session/throttle hiện có. |
| `PROXIMITY_REFRESH_STATUS` | Học sinh báo không thu được bộ mẫu, ví dụ CALIBRATING hoặc PERMISSION_DENIED; không làm giả một số mét bằng 0. |
| Snapshot giám sát | Bổ sung ping_ms/ping_age_ms/measurement_age_ms, topology_supported và refresh status/reason/timestamp. Ping hết hạn không có ping_ms. Tiến độ chỉ dành cho quản lý. |

Thời gian, nonce và người trả probe do Server đối chiếu; không tin ping_ms Client tự gửi. RSSI vẫn là thông tin thiết bị báo, phục vụ giám sát ước lượng, không dùng để tự trừ điểm hoặc làm căn cứ vị trí chính xác.

## Kiểm chứng

Kiểm thử tự động gồm cửa sổ mẫu/throttle, hủy khi đổi phiên, quyền Windows và hiệu chuẩn, probe giả/hết hạn, phản hồi sai người, refresh gộp/timeout/cooldown/Client cũ, TLS với 10 Client và RBAC. QA JavaFX kiểm tra 1024×640 và 1366×768, 10 nút không chồng nhau, giữ mọi người đồng hạng, trình chiếu không lộ IP/BSSID, làm mới trong lúc thi và timer dừng khi đóng.

QA sử dụng Server TLS + SQLite thật trong thư mục tạm và nguồn RSSI kiểm thử; không đưa dữ liệu mẫu vào runtime. Bài demo di chuyển điện thoại trên hai laptop vật lý cần người dùng trực tiếp thao tác, không được suy ra là đã đạt từ QA mô phỏng.

## Phát hành trên máy MSI — 29/10/2026

### Bổ sung Client 0.6.1

Form giám sát bỏ cụm “ước lượng” lặp trong tiêu đề cột, từng dòng, ngưỡng và chi tiết; giữ dấu ≈. Tooltip giải thích giá trị tính từ tín hiệu Wi-Fi và hiệu chuẩn. Không đổi cách đo hay độ chính xác vì việc đổi nhãn.

Đã kiểm tra luồng runtime: `WifiSignalProvider.createDefault()` khởi tạo `WindowsWifiSignalProvider`, đọc RSSI trực tiếp qua Windows WLAN; Server lấy trung vị và áp dụng mô hình suy hao/hiệu chuẩn riêng của Client. Không chọn số mét ngẫu nhiên; lỗi quyền/driver trả trạng thái thiếu dữ liệu. Ngày 29/10, đọc trực tiếp trên MSI qua các thư viện Client nhận CONNECTED, card Intel AX201, băng 2,4 GHz và RSSI -38/-45/-45 dBm. Log không chứa mật khẩu: `tmp/actual-wifi-read-20261029.log`.

Ảnh có “SV Đo 1/2”, địa chỉ 127.0.0.1 và cùng 5 m thuộc QA dùng RSSI -61 dBm cố định; không phải đo hai laptop vật lý. Các ảnh QA giám sát mới ghi rõ RSSI GIẢ LẬP. Bộ cài thông thường không chạy driver QA hoặc nạp nguồn RSSI này.

RSSI là số đo tín hiệu thật, còn mét là kết quả suy ra; phương án hiện tại không đo mét trực tiếp và không cam kết độ chính xác như thước đo. Cần hiệu chuẩn đúng 1 m/5 m với đúng điểm phát, adapter và băng tần trước bài test thực địa.

Client 0.6.1 đã cài trên MSI; kiểm tra giao diện toàn luồng đạt, xác minh JAR cài đặt khớp bản đóng gói. Server giữ nguyên, 7 tệp data/certs/config không đổi hash. Backup `backups/Client-before-0.6.1-20261029-081838/`; installer SHA-256 `085345d7d66abd06b8077240814cfd662f996e70047abbc1105c9eb31639f991`. Yêu cầu được chốt: mét có thể có sai số, nhưng phải tính từ mẫu RSSI/hiệu chuẩn và không hardcode hoặc sinh ngẫu nhiên.

- Maven verify: **159 kiểm thử, 0 failures/errors**, có kiểm thử hồi quy biên 5 m để nhiễu dấu phẩy động không tạo cảnh báo ngoại suy sai.
- JavaFX toàn luồng đạt ở 1024×640 và 1366×768; 10 nút không chồng nhau, giữ 10 người đồng hạng, không lộ IP/BSSID khi trình chiếu. Hai Client thu mẫu mới qua TLS lúc đang thi, bảng chuyển từ số cũ sang số mới mà đáp án vẫn gửi được.
- Client 0.6.0 đã cài tại máy MSI; Server/lib cập nhật cùng mã. Bộ chạy duy nhất `dist/QuizBattle/`, không tự push GitHub.
- Installer SHA-256: `8043f9d85295166f3b6e31b43dd285867e94e483c00a86fcd6e19033395e3e8a`.
- Sao lưu trước cập nhật: `backups/QuizBattle-before-0.6.0-20261029-080840/`. Mọi trường dữ liệu cũ khớp bản sao lưu; schema vẫn 4, 6 tệp chứng chỉ/cấu hình giữ nguyên hash. Runtime Java không thay đổi.
- Log: `tmp/topology-final-tests.log`, `tmp/topology-ui-1024.log`, `tmp/topology-ui-1366.log`, `tmp/topology-installed-ui.log`, `tmp/topology-db-preservation.log`. Ảnh: `output/topology-ui-*` và `output/topology-installed-ui`.
