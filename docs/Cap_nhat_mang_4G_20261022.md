# Cập nhật profile LAN qua điểm phát 4G — 22/10/2026

## Cấu hình dùng chính

- Server: `dist/QuizBattle/Server/config/server.json`, mục `server.bind=10.122.30.170`.
- Runtime public properties được sinh lại từ JSON; legacy `server.properties` đã đồng bộ IP mới.
- Client trên máy phát triển: `C:/Users/starg/AppData/Local/QuizBattle/app/client.properties`, `server.host=10.122.30.170`.
- Cổng TCP 5050 và UDP 5051 giữ nguyên. Không sửa router, firewall, quyền Windows hoặc VPN.
- Bộ cài duy nhất: `dist/QuizBattle/Client/QuizBattle-Setup.exe`, phiên bản 0.3.7. Không tạo thêm thư mục phát hành trong dist. Máy Client khác phải cài bản này để có profile/truststore đồng bộ.

## TLS và dữ liệu

Sao lưu trước cập nhật tại `backups/lan-address-20261022-142527/`: Server `config`, `certs`, `data`; bộ cài trước; profile/truststore Client trước. Không chia sẻ backup vì có khóa riêng và database.

Chứng chỉ Server được cấp lại bằng chính cặp khóa hiện có, SAN gồm `10.122.30.170`, `10.12.89.105`, `127.0.0.1`, `localhost`. Đã so sánh giá trị public key trước/sau để xác nhận không đổi danh tính khóa. Truststore Client giữ chứng chỉ trước và thêm chứng chỉ mới, chỉ chứa trusted certificate entries, không có private/secret key. Mật khẩu TLS lưu DPAPI giữ nguyên; không đổi mật khẩu Admin hay tạo tài khoản/câu hỏi.

SHA256 chứng chỉ mới:
`BA:CC:43:56:4C:F6:10:19:63:59:9B:1A:AE:7D:F9:F1:08:19:16:85:AF:8F:B9:56:4A:84:2F:43:4F:67:9A:CB`.

SHA256 bộ cài:
`6f65890a4f0c249b795047c45f5bd2a6a9bb8c44a7b7f2020ff0d327b1c50300`.

## Kiểm thử và giới hạn

Mã ứng dụng/JAR không thay đổi so với bản đã kiểm thử 0.3.6; chỉ profile, chứng chỉ và metadata bộ cài thay đổi. Bộ cài mới tạo bằng jpackage, cài thành công trên máy phát triển; đã đối chiếu hash profile, public truststore và ba JAR cốt lõi sau cài.

`tmp/HotspotTlsCheck.java` dùng Server thật và runtime đã cài, với bản sao database tại build, không đăng nhập hoặc thay đổi dữ liệu cá nhân. Đạt:

- Loopback trên cổng tạm: UDP discovery, verified directory và TLS ROOM_BROWSE.
- Xác minh tên/IP TLS cho cả `10.122.30.170` lẫn `10.12.89.105` thành công; tên/IP không có trong SAN bị từ chối.
- Khi máy Server đã nối lại đúng hotspot: bind tại `10.122.30.170:5050`, discovery UDP 5051, TLS ROOM_BROWSE và danh sách Server đã xác minh đều thành công trên máy Server.
- Hash database cá nhân trước/sau kiểm thử không đổi. Server kiểm thử tự đóng sau hoàn tất; người vận hành mở Server thật bằng `start.bat`.

Trong lúc cập nhật, Wi-Fi được người dùng chuyển qua lại giữa mạng trường và hotspot nên các lần thử đầu gặp bind sai địa chỉ hoặc cổng bị Server khác giữ. Không tự tắt tiến trình do người dùng mở; kiểm thử cuối thực hiện sau khi IP đúng và cổng rảnh. Những lỗi môi trường ban đầu không được coi là kiểm thử đạt.

Chưa xác nhận kết nối từ máy học sinh: cần cả hai cùng điểm phát, máy Server mở `start.bat`, rồi máy học sinh chạy `Test-NetConnection 10.122.30.170 -Port 5050`. Khi TCP đạt mới kiểm tra tạo phòng và JOIN từ máy khác. Không suy ra một bài kiểm tra trên máy Server là chứng minh hotspot cho phép kết nối giữa hai máy.

Nếu quay lại trường, SAN đã hỗ trợ IP cũ nhưng vẫn phải đổi `server.bind` theo IP đang được cấp và khởi động lại Server; fallback `server.host` của Client cũng nên đồng bộ nếu UDP bị chặn. Nếu DHCP cấp IP ngoài hai địa chỉ trên, cần cập nhật SAN tương ứng, không tắt kiểm tra TLS.
