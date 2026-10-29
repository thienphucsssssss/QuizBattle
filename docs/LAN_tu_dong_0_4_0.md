# LAN tự động khi đổi điểm phát điện thoại — 0.4.0

Client cài một lần và tìm máy MSI theo mạng hiện tại. Đổi điện thoại hoặc IP DHCP giữa các buổi không cần tạo chứng chỉ hay build bộ cài mới. Cả Client và Server cần được cập nhật lên 0.4.0 để có hành vi này.

## Cách sử dụng

1. Kết nối MSI và các máy học sinh/giảng viên vào cùng điểm phát điện thoại.
2. Trên MSI, giữ `Server/config/server.json`: `server.bind` là `auto`. Chạy `Server/allow-lan.bat` một lần và chấp nhận yêu cầu quyền quản trị Windows để chuẩn bị Firewall.
3. Chạy `Server/start.bat`, chờ `NQB_SERVER_READY`. Console hiển thị các card mạng LAN đang hoạt động; không yêu cầu nhập IP mỗi lần chạy.
4. Mở Client, giảng viên/Admin đăng nhập và tạo phòng. Học sinh chọn phòng trong LAN hoặc nhập mã phòng. Dùng **Tìm lại** nếu vừa đổi mạng hoặc vừa bật Server.

Sau khi cập nhật Client 0.4.0 lần đầu, tiếp tục dùng chính bộ cài đó khi đổi hotspot. Giữ nguyên Server `data/`, `certs/` và thông tin đăng nhập. Không chạy lại Setup-Lan chỉ vì IP đổi.

## Kết nối và danh tính

- `server.bind=auto` được Java chuyển thành địa chỉ lắng nghe wildcard `0.0.0.0`. IP cụ thể/loopback vẫn được hỗ trợ như chế độ vận hành thủ công. TCP mặc định 5050, UDP discovery mặc định 5051.
- Client `server.host=auto` không chứa IP cũ. Mỗi lượt dò liệt kê lại card mạng, ưu tiên Wi-Fi, bỏ card ảo/VPN nhận diện được. Gửi UDP bằng socket gắn từng địa chỉ mạng, nhận IP Server từ nguồn gói phản hồi, kiểm tra service/nonce/kích thước/cổng.
- UDP chỉ là gợi ý địa chỉ. Sau đó Client bắt tay TLS và gọi ROOM_BROWSE; chỉ kết quả TLS hợp lệ xuất hiện trong danh sách. Hai cách chọn phòng/nhập mã dùng cùng danh sách đã xác minh.
- TLS xác minh SHA-256 của khóa công khai leaf Server (SPKI) với các chứng chỉ công khai trong truststore đóng sẵn. Đồng thời giữ kiểm tra chuỗi, thuật toán TLS của JDK, thời hạn leaf và mục đích serverAuth nếu chứng chỉ có EKU. Không có trust-on-first-use hoặc tin chứng chỉ do UDP gửi. Kiểm tra HTTPS IP/SAN được thay bằng kiểm tra khóa đã biết; dữ liệu và mật khẩu vẫn đi qua TLS 1.2/1.3.
- Server MSI giữ nguyên keystore hiện tại. Chứng chỉ mới tạo ở chế độ auto có nhãn DNS `quizbattle-server`, nhưng không cần cài DNS/mDNS trên máy học sinh. Khóa tin cậy đóng sẵn mới quyết định danh tính Server.
- Nếu cấu hình Client thủ công một IP thay cho auto, IP đó chỉ là đường kết nối dự phòng bên cạnh discovery. Không nên dùng cho bộ cài đem qua nhiều hotspot.

## Firewall và vận hành

`allow-lan.bat` tạo/cập nhật hai quy tắc inbound TCP/UDP chỉ cho `Server/runtime/bin/java.exe`, các cổng trong cấu hình và `RemoteAddress=LocalSubnet`. Các profile Windows đều được áp dụng để hotspot mới bị gắn Public vẫn dùng được. Công cụ cần quyền quản trị/UAC, không tắt Firewall, không sửa rule của ứng dụng khác và không mở port router. Khi di chuyển thư mục Server hoặc đổi cổng, chạy lại công cụ này.

Quy tắc thật chỉ được tạo sau khi công cụ chạy thành công với quyền quản trị Windows. Build và kiểm thử không tự cấp quyền Firewall. Các kiểm thử trên một máy không chứng minh Firewall của máy khác hay việc điểm phát cho phép broadcast/giao tiếp giữa thiết bị.

Đổi điểm phát trong lúc đang thi có thể làm đứt kết nối TCP; nên đổi trước khi tạo phòng mới. Mã phòng không vượt qua cách ly thiết bị của hotspot. Nếu chỉ UDP bị chặn, Client auto không thể tìm Server bằng mã; cần kiểm tra điểm phát/Firewall trước.

Đổi điện thoại/BSSID hoặc băng tần là đổi mốc giám sát khoảng cách: chọn lại điểm phát và hiệu chuẩn lại trên laptop. Việc này không yêu cầu bộ cài Client mới.

Chứng chỉ hết hạn, mất/thay keystore hoặc thay danh tính Server cần quy trình cập nhật chứng chỉ tin cậy; việc này độc lập với đổi IP.

## Kiểm chứng

Ngày 26/10/2026:

- `mvn -B -ntp -o clean install`: **126 tests**, 0 failures, 0 errors (`tmp/lan-auto-all-tests-final.log`). Kiểm tra hồ sơ auto không chứa IP, UDP nhận địa chỉ nguồn mới ở lượt dò tiếp theo, TLS chấp nhận khóa đã biết tại địa chỉ ngoài SAN, từ chối khóa khác/leaf hết hạn/EKU không phù hợp/private keystore, Server auto và dò lại sau restart bằng cùng Client profile.
- QA PowerShell: kiểm tra các giá trị bind, sinh bộ chứng chỉ đầu tiên với auto và từ chối ghi đè khóa đã có (`tmp/lan-auto-config-qa.log`). Khởi chạy Server đóng gói trên thư mục/DB/chứng chỉ tạm đạt `NQB_SERVER_READY TLS 0.0.0.0:...` mà không nhập IP; bootstrap và chuyển mật khẩu sang DPAPI đạt (`tmp/lan-auto-launcher.log`).
- Java runtime và thư viện đóng gói kết nối được TLS tới `127.0.0.1`, `127.0.0.2` và IPv4 thực tế của MSI tại thời điểm kiểm thử (`192.168.2.7`) bằng chứng chỉ tạm chỉ có DNS, không có các IP này trong SAN. JNA đọc được trạng thái Wi-Fi CONNECTED (`tmp/lan-auto-bundle.log`). Đây là các kết nối trên cùng máy, không phải thử hai laptop.
- JavaFX 1024×640: đạt luồng đăng nhập, dò Server, chọn phòng/nhập mã, câu hỏi/bảng hạng, import và giám sát với profile auto (`tmp/lan-auto-ui-1024.log`).
- Kiểm thử lại cùng luồng ở 1366×768 bằng Java runtime/JAR Client đã cài trên MSI và thư viện Server trong dist: đạt (`tmp/lan-auto-installed-ui.log`).
- Bộ cài 0.4.0 đã cập nhật vào `dist/QuizBattle/Client/QuizBattle-Setup.exe` và cài lên MSI. Đã đối chiếu JAR, profile auto và public truststore với Server. Database/chứng chỉ Server giữ nguyên; cấu hình JSON chỉ thay `server.bind` thành auto. Bản sao trước cập nhật ở `backups/QuizBattle-before-0.4.0-20261026-200212/`.
- SHA-256 bộ cài: `c8f4a397f6f257c2cd6ba47c2e62c1fd68dde4b0242ee460783a197fa577ee4d`.

Chưa thực hiện thay đổi Firewall thật trong phiên kiểm thử vì cần chấp nhận quyền quản trị Windows. Chuyển hai điểm phát điện thoại thật trên nhiều laptop vẫn cần thử trực tiếp trước buổi demo.
