# Đợt A: mạng riêng, khôi phục phiên và quét nhiều BSSID

Đây là bước chuẩn bị cho định vị bằng fingerprinting. Chưa có chức năng nhập tọa độ khảo sát, KNN/WKNN hay tính khoảng cách Client–Server theo bản đồ tín hiệu.

## Kết nối đến Server

- **Tự dò LAN:** giữ lựa chọn mặc định. Client vẫn dùng UDP 5051 để tìm Server rồi xác minh danh sách phòng qua TLS.
- **Máy chủ cụ thể:** bật lựa chọn ở màn đăng nhập/tham gia, nhập IP hoặc hostname của Server và cổng TCP (mặc định 5050), nhấn **Áp dụng**. Danh sách phòng và mã phòng hoạt động như trước.
- Chế độ máy chủ cụ thể không dò UDP và không tự chuyển sang một Server khác nếu địa chỉ đó lỗi. Chứng chỉ Server vẫn phải khớp bộ cài Client; không có chế độ bỏ xác minh TLS.
- Địa chỉ được lưu theo tài khoản Windows tại `%USERPROFILE%/.quizbattle/connection.properties`. Chỉ lưu host/cổng; cấu hình chứng chỉ vẫn lấy từ `client.properties` đi cùng bộ cài.

### Dùng Tailscale ở trường

1. Cài/đăng nhập Tailscale trên cả Server và Client; đưa các máy vào mạng riêng được phép truy cập nhau. Giữ kết nối Wi-Fi **HUTECH E1** để đọc tín hiệu thật tại trường.
2. Chạy Server bản mới, `server.bind=auto`, cổng 5050. Lấy IP Tailscale của máy Server, thường bắt đầu bằng `100.`.
3. Trên bản Server đóng gói mới có `allow-tailscale.bat`. Script cần quyền quản trị, chỉ cho phép đúng Java runtime của bộ Server, TCP đúng cổng, card Tailscale và các dải địa chỉ Tailscale. Script không cài Tailscale, không mở UDP discovery và không tắt firewall. `allow-lan.bat` cũ vẫn chỉ phục vụ LAN.
4. Từ Client, chạy `Test-NetConnection -ComputerName <IP-Tailscale-Server> -Port 5050`. Sau khi TCP thành công, nhập địa chỉ đó trong **Máy chủ cụ thể**, nhấn **Áp dụng**, chọn/nhập mã phòng.
5. Nếu TCP được nhưng ứng dụng báo chứng chỉ: sử dụng Client có truststore công khai đúng với Server. Không gửi `server.p12` hoặc khóa riêng cho thành viên nhóm.

Kết nối qua mạng riêng phụ thuộc Internet, cấu hình quyền truy cập Tailscale và mạng trường. Cần thử bằng hai laptop thật; việc quét Wi-Fi thành công không chứng minh đường truyền giữa hai máy đã hoạt động. Mã phòng không thay cho địa chỉ Server.

## Khôi phục phiên thí sinh

- Client mới thương lượng `SESSION_RESUME_V1` khi JOIN. Server giữ chỗ mặc định **60 giây** sau khi phát hiện socket mất kết nối; thí sinh hiện **Đang kết nối lại…** và tự thử lại có giãn cách.
- Server cấp token ngẫu nhiên, giữ hash trong RAM; Client giữ token trong RAM. Token luân chuyển mỗi lần khôi phục, chỉ đúng người chơi và thế hệ phòng. Không khôi phục theo biệt danh/IP.
- Nếu mất phản hồi RESUME, Client có thể thử token kế tiếp đã chuẩn bị trước. Token cũ đã tiêu thụ không được Server chấp nhận lại.
- Giữ nguyên player, linh vật, điểm và đáp án Server đã ghi nhận. **Đồng hồ vẫn chạy**; không lưu hàng đợi và không tự gửi lại đáp án trong lúc mất mạng. Khi trở lại, màn hình đọc trạng thái hiện hành từ Server.
- Trong phòng chờ, khôi phục xong cần bấm Sẵn sàng lại. Dữ liệu Wi-Fi cũ không được hiển thị như số đo mới; chờ báo cáo tiếp theo.
- Rời phòng, đóng/reset phòng, hết thời gian giữ chỗ hoặc Server khởi động lại làm token cũ mất hiệu lực. Đóng ứng dụng không có khả năng khôi phục sau khi mở lại vì token không được lưu ra ổ đĩa. Khi rời trong lúc hoàn toàn mất mạng, Server dọn chỗ theo thời gian hết hạn.
- Admin/giảng viên vẫn đăng nhập lại để quản lý; tự khôi phục trong đợt này áp dụng cho phiên thí sinh.

Có thể đặt `session.resumeGraceSeconds=60` (1–300) trong cấu hình nguồn `server.properties`, hoặc thêm `"session": { "resumeGraceSeconds": 60 }` trong `config/server.json` của bộ Server đóng gói. Đọc lúc khởi động Server.

## Kiểm tra quét Wi-Fi trước khảo sát

Mở **Tùy chọn → Chẩn đoán quét Wi-Fi**, dùng được cả khi chưa vào phòng:

1. Chọn card Wi-Fi vật lý đang sử dụng, thường là Intel/Realtek. Danh sách lấy từ Windows Native Wi-Fi; Tailscale không phải nguồn RSSI.
2. Nhập chính xác `HUTECH E1` để lọc. Để trống sẽ xem tất cả SSID quan sát được.
3. Nhấn **Quét Wi-Fi**. Chờ tối đa 5 giây; không gọi quét mới trong vòng 5 giây kể từ yêu cầu trước. Có thể hủy/đóng cửa sổ mà không khóa UI.
4. Xem RSSI, BSSID, tần số, kênh, dữ liệu mới/cũ và BSSID đang kết nối. Một AP vật lý có thể phát nhiều BSSID; không diễn giải số hàng thành số AP vật lý.
5. **Xuất JSON** để lưu kết quả chẩn đoán. File chứa BSSID/SSID/card/timestamp; không có tọa độ khảo sát, mật khẩu Wi-Fi hay token phòng thi.

Mỗi lượt yêu cầu `WlanScan`, chờ thông báo hoàn tất đúng card rồi lấy BSS list. Một hàng chỉ được đánh dấu mới nếu timestamp nhận beacon/probe của driver nằm trong lượt hiện tại và chưa dùng ở lượt trước. Timeout, lỗi quyền, lỗi driver và cache không được tính là mẫu mới. Bảng giới hạn 64 hàng, ưu tiên dữ liệu mới/tín hiệu mạnh; hiển thị số hàng bỏ bớt.

Danh mục quan sát tự lưu cục bộ tại `%USERPROFILE%/.nqb/wifi-observed.json` (tối đa 2048 radio/card), có first_seen/last_seen, SSID, BSSID, tần số. Danh mục này **không** tự gán AP1…APn, không suy ra vị trí vật lý, không sửa bản đồ AP thủ công hiện có và chưa phải tập mẫu fingerprint theo tọa độ.

Quét chạy chung worker với đọc RSSI, không chồng lên hiệu chuẩn/thu mẫu đo mới. Chủ động quét có thể tăng độ trễ Wi-Fi; nên kiểm tra trước buổi demo. Nếu bị từ chối quyền, bật Windows Location và quyền vị trí cho ứng dụng desktop, rồi thử lại.

## Kiểm thử và điều kiện tiếp theo

- Kết quả phần mềm ngày 29/10/2026: 189 kiểm thử Maven đạt, không lỗi. Kiểm tra JavaFX/TLS/SQLite thật ở 1024×640 và 1366×768 cho luồng đợt A; hồi quy chọn phòng LAN, đăng nhập/đăng ký, thi, chấm điểm, lịch sử, nhập đề và giám sát đạt. Dữ liệu trong ảnh UI chẩn đoán được gắn nhãn giả lập; kết quả quét native bên dưới là phép kiểm tra riêng.
- Kiểm thử tự động: TLS thật/SQLite thật, cắt TCP bằng proxy rồi khôi phục; token sai/cũ/khác thế hệ/hết hạn; mất ACK; bảo toàn đáp án; chặn gửi lệnh khi đang retry; LAN cũ và lỗi TLS.
- Kiểm thử native: parser BSS, timestamp cũ/trùng/tương lai, timeout, hủy, quyền truy cập, giải phóng bộ nhớ/callback và lưu danh mục.
- Test native trên máy hiện tại ngày 29/10/2026: card Intel Wi-Fi 6 AX201, đang kết nối HUTECH E1; hai lượt không lọc SSID thấy 10/10 và 10/12 hàng có dữ liệu mới, khoảng 3,0 và 4,5 giây. Đây không phải số AP vật lý hoặc kết quả định vị.
- Khi lọc riêng HUTECH E1 tại cùng vị trí: hai lượt thấy 2/2 và 1/2 hàng có dữ liệu mới, khoảng 2,6 và 3,3 giây. Không giả định thấy đủ 7 AP hoặc mọi BSSID đều cập nhật trong mỗi lần quét.
- Nghiệm thu còn cần: hai laptop kết nối qua Tailscale tại trường, thử đi đổi BSSID/mất mạng ngắn, đo tần suất scan hữu ích. Sau đó mới thu các điểm có tọa độ cho đợt B; độ chính xác vị trí chỉ đánh giá bằng dữ liệu thực địa độc lập.

## Nguồn API

- [Microsoft WlanScan](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/nf-wlanapi-wlanscan)
- [Microsoft WlanRegisterNotification](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/nf-wlanapi-wlanregisternotification)
- [Microsoft WLAN_BSS_ENTRY](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/ns-wlanapi-wlan_bss_entry)
- [Tailscale: kết nối đến thiết bị](https://tailscale.com/docs/how-to/connect-to-devices)
