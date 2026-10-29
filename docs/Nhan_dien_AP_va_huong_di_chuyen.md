# Nhận diện AP và hướng di chuyển

Server đứng cố định, client Windows di chuyển. Client đọc BSSID của kết nối Wi-Fi hiện tại bằng Native Wi-Fi API rồi gửi qua TLS trong `PROXIMITY_REPORT`, mỗi 5 giây. SSID có thể là HUTECH E1 hoặc mạng phát triển khác; không khóa cứng mạng trường.

## Thiết lập demo

1. Cập nhật cả Server và Client từ mã nguồn mới, tạo phòng rồi mở **Giám sát → Cấu hình AP** khi phòng đang chờ.
2. Kết nối client với Wi-Fi cần khảo sát, bật quyền đọc Wi-Fi/vị trí trên Windows. Client trong phòng tự gửi thông tin AP; có thể bấm **Bật đo Wi-Fi** để kiểm tra lại quyền.
3. Chọn BSSID client đã báo về bằng **Thêm AP đã thấy**, hoặc nhập BSSID đã khảo sát. Nhập SSID chính xác, tên AP, tầng/hành lang và thứ tự vị trí thực tế.
4. Ví dụ AP1, AP2, AP3 cùng `HUTECH E1`, cùng `Tầng 3 / hành lang A`, lần lượt thứ tự 1, 2, 3. Một AP có nhiều BSSID (các radio/băng tần) phải có nhiều dòng cùng tên, hành lang và thứ tự.
5. Bấm **Lưu bản đồ AP**. Cấu hình lưu cạnh database server, tên `<database>.access-points.json`, được nạp lại khi khởi động. Mỗi lần lưu bắt đầu lại lịch sử theo dõi hiện tại.
6. Chọn tab **AP & hướng di chuyển**, chọn client để xem BSSID, AP hiện tại và 8 lần xác nhận AP gần nhất. Lịch sử này ở RAM và không phải nhật ký phiên lưu trong SQLite.

Muốn demo di chuyển tự do, có thể tắt **giám sát khoảng cách** trong cấu hình phòng; theo dõi AP vẫn hoạt động. Nếu bật giám sát khoảng cách thì quy tắc hiệu chuẩn và mốc AP ban đầu vẫn áp dụng: đổi sang AP khác sẽ không dùng hồ sơ RSSI của AP cũ để tính khoảng cách.

## Cách diễn giải kết quả

- AP hiện tại lấy trực tiếp từ BSSID và SSID client báo. Chưa có ánh xạ thì hiển thị BSSID chưa gán tên, không tự đặt tên AP theo thứ tự phát hiện.
- Xác nhận AP sau ít nhất hai báo cáo liên tiếp, cách nhau tối thiểu 4 giây. Chu kỳ thông thường là 5 giây, nên xác nhận sau khoảng 5–10 giây từ khi thiết bị chuyển AP.
- Chuyển AP1 → AP2 trong cùng hành lang suy ra hướng tăng thứ tự; AP3 → AP2 suy ra hướng giảm thứ tự. Chuyển radio trong cùng AP không tạo lần di chuyển.
- Mất kết nối, quyền Wi-Fi, đổi card mạng, gặp AP chưa ánh xạ hoặc không nhận báo cáo quá 15 giây sẽ ngắt đoạn suy luận. Khác hành lang không suy ra hướng. Dấu `|` trong lịch sử đánh dấu đoạn mới.
- Hướng từ lần chuyển AP hết hạn sau 30 giây. Không có chuyển AP không chứng minh client đứng yên. Thiết bị có thể bám AP cũ dù đã đi qua vùng phủ sóng AP khác.
- Đây là hướng suy đoán theo vị trí AP đã khảo sát; không phải la bàn hoặc tọa độ chính xác. Cần đi thử tại trường sau khi nhập BSSID thật. Nếu Wi-Fi trường cô lập các client, cần cấu hình mạng cho phép client liên lạc với server.

## Giao thức và quyền

`AP_CONFIG_GET` và `AP_CONFIGURE` dành cho chủ phòng. Chỉ thay đổi cấu hình lúc `WAITING`; `revision` ngăn ghi đè bản đồ đã được người khác cập nhật. Cấu hình được kiểm tra toàn bộ trước khi ghi file và cập nhật RAM; tối đa 64 BSSID, không chấp nhận BSSID trùng, vị trí mâu thuẫn hoặc hai AP khác tên cùng thứ tự trong một SSID/hành lang.

`PROXIMITY_GET`/`PROXIMITY_UPDATE` bổ sung `ap_config` tóm tắt và `players[].access_point`. Client chỉ nhận `proximity.self.access_point` của chính mình; không phát lịch sử AP của client khác trong danh sách phòng hoặc trên máy chiếu. Báo cáo vẫn kiểm tra người gửi, phiên, epoch, sequence, tần suất và dữ liệu trước khi cập nhật AP.

## Kiểm tra

`mvn test` bao gồm kiểm thử AP đi xuôi/quay lại, cùng AP nhiều BSSID, dao động roaming, hết hạn hướng, mất dữ liệu, mạng khác, đổi card, phân quyền TLS, lưu/nạp bản đồ và lỗi ghi file. `LiveLanUiCheck ... ap-only` kiểm tra UI JavaFX thật: nhập/lưu bản đồ qua TLS, chuyển AP bằng báo cáo giả lập và hiển thị ở 1024×640 và 1366×768. Ảnh QA là dữ liệu giả lập, không thay thế nghiệm thu thực địa tại HUTECH E1.

Tham khảo: Microsoft mô tả BSSID của kết nối trong [WLAN_ASSOCIATION_ATTRIBUTES](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/ns-wlanapi-wlan_association_attributes).
