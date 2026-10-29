# Tự tìm phòng LAN và vào bằng mã — 0.3.3

**Cập nhật 0.4.0:** các giới hạn bind IP/SAN và đường dẫn bộ chạy theo timestamp trong tài liệu lịch sử này đã được thay thế. Dùng `dist/QuizBattle/`, giữ Server/Client ở chế độ auto; xem [LAN tự động khi đổi điểm phát](LAN_tu_dong_0_4_0.md).

Bộ cài hiện hành: `dist/QuizBattle-Windows-20261022-101323/QuizBattle-Setup.exe`; Client trên máy này đã cài lại bản cuối cùng. Server cá nhân tiếp tục chạy tại `dist/QuizBattle-Windows-20261022-084345/Server/start.bat`, đã cập nhật mã mà giữ nguyên database/config/chứng chỉ. Installer mới ghép với chứng chỉ **Server 084345**, không dùng lẫn với Server 073803 hoặc một Server mới khởi tạo. Thư mục/ZIP Server trong bản phát hành 101323 là gói sạch để cài mới/cập nhật mã, không chứa dữ liệu/chứng chỉ cá nhân. [Kết quả kiểm thử](Nghiem_thu_LAN_Discovery_0_3_3.md).

Client không còn ô IP/cổng trên màn đăng nhập, đăng ký hoặc tham gia. Hai cách vào phòng chạy song song, không đổi mô hình Java Client–Server hay quy tắc điểm/linh vật.

## Học sinh

1. Mở QuizBattle, chọn Học sinh.
2. **Phòng trong LAN**: danh sách tự tìm và làm mới mỗi 4 giây, hiển thị tên phòng, giảng viên, số người, trạng thái; chọn phòng đang chờ. Có nút **Tìm lại**.
3. Hoặc chọn **Nhập mã phòng**, nhập mã 6 ký tự giảng viên cung cấp. Client tìm đúng phòng trên các Server đã xác minh, không yêu cầu nhập IP/cổng.
4. Nhập biệt danh → Tham gia phòng → chọn linh vật → Sẵn sàng. Hai cách dùng cùng JOIN và mọi kiểm tra phía Server.

Phòng đầy, đang thi hoặc đã kết thúc có thông báo trạng thái, không được JOIN. Server vẫn kiểm tra lại khi người dùng bấm tham gia để xử lý phòng vừa đóng/đổi mã/đầy sau lần tìm. Nếu nhiều Server có cùng mã, Client yêu cầu chọn trong danh sách, không tự đoán Server.

## Giảng viên / Admin

Máy chủ trong LAN được tìm tự động và tự chọn khi chỉ có một Server hợp lệ. Nếu nhiều Server hợp lệ, chọn tên máy chủ, không điền địa chỉ. Giảng viên đăng nhập/tạo tài khoản như trước; Admin dùng tài khoản đã khởi tạo. Bật Server bằng start.bat rồi giảng viên tạo phòng thì học sinh mới thấy phòng.

Mỗi Server hiện hỗ trợ **một phòng tại một thời điểm**, tối đa 10 học sinh. Danh sách có thể gom phòng từ nhiều Server được chứng chỉ Client cho phép; tính năng này không biến một Server thành hệ thống nhiều phòng đồng thời.

## Kết nối và xác thực

- UDP IPv4 cổng mặc định **5051** chỉ dùng DISCOVER/DISCOVER_OK để nhận diện endpoint TCP. Gói tối đa 512 byte, có service/version/nonce ngẫu nhiên; bỏ nonce sai, gói quá lớn, nguồn không phải private IPv4/loopback, và cổng không hợp lệ. Client gửi tới loopback, địa chỉ broadcast và broadcast từng interface, thêm địa chỉ Server cấu hình khi có. Không quét cả dải IP.
- UDP **không chứa tài khoản, mật khẩu, danh sách phòng hoặc đáp án**. Nó chưa chứng minh máy chủ đáng tin.
- Sau UDP, Client dùng kho công khai đi kèm để xác minh TLS và IP/SAN rồi mới gửi `ROOM_BROWSE`. Chỉ dữ liệu qua TLS hợp lệ được hiển thị; Server không được tin cậy không nhận mật khẩu đăng nhập.
- ROOM_BROWSE là API chỉ đọc trước đăng nhập, chỉ trả server_id/server_name và metadata phòng công khai. Không trả username/hash/salt, ngân hàng, danh sách học sinh hoặc nội dung câu hỏi. Chọn vai trò hay thấy phòng không cấp quyền quản lý.
- Kết nối đăng nhập và JOIN tiếp tục dùng TCP/TLS, mặc định 5050. Client không chuyển xuống TCP rõ hoặc trust-all khi tìm thất bại.
- Mỗi đợt tìm chờ UDP khoảng 650 ms, tối đa 8 endpoint, tối đa 4 probe TLS đồng thời và thời hạn chờ kết quả 6 giây. Không chạy mạng/PBKDF2 trên JavaFX thread; kết quả cũ bị bỏ khi đổi màn, đóng ứng dụng dừng tác vụ tìm.

## Người vận hành

`Server/config/server.json` có thể thêm `"discovery": { "port": 5051 }`; config cũ thiếu phần này vẫn dùng mặc định 5051. TCP bind/port/database/TLS tiếp tục nằm ở config Server, không ở form Client.

Hồ sơ công khai `app/client.properties` có `discovery.port=5051`. `server.host` và `server.port` là đích TLS dự phòng: nếu **chỉ UDP bị chặn** nhưng địa chỉ dự phòng đúng và TCP vẫn thông, cả hai cách tìm phòng vẫn dùng được. Khi IP thay đổi, tự tìm UDP có thể tìm endpoint mới, nhưng IP mới phải có trong chứng chỉ TLS và Server phải bind đúng IP; không bỏ xác minh chứng chỉ để né cấu hình này.

Firewall trên máy Server có thể cần cho phép TCP 5050 và UDP 5051 trong phạm vi LAN tin cậy. Chương trình không tự tắt firewall, sửa router hoặc mở cổng Internet. Mã phòng không xuyên được mạng Wi-Fi cô lập thiết bị; chung SSID chưa chắc chung broadcast domain. Nếu trường chặn cả TCP giữa các máy, cần mạng LAN được cho phép hoặc nhờ người quản trị hỗ trợ; nhập mã không khắc phục được giới hạn đó.

Bộ cài vẫn ghép với chứng chỉ công khai của Server được chọn khi build. Không phát server.p12/database cho học sinh. Cả Client lẫn Server cần bản 0.3.3 để có Discovery/ROOM_BROWSE; không dùng kết quả tìm của Client mới với Server 0.3.2 cũ.
