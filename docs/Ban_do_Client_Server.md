# Bản đồ Client — Server

Mở **Tùy chọn → Bản đồ Client — Server**. Cửa sổ bản đồ hoạt động bên cạnh giao diện phòng thi hiện tại.

Chạy mã nguồn đã build bằng `powershell -ExecutionPolicy Bypass -File .\scripts\Run-Quiz.ps1 -Role Admin` tại thư mục dự án. Bộ `.exe`/ZIP 0.6.4 đã phát hành trước đó chưa chứa phần này; cần đóng gói lại khi phát bản mới cho nhóm.

## Mô phỏng tại nhà

- Không cần Server, kết nối mạng hoặc dữ liệu khảo sát.
- Sơ đồ mặc định: một hành lang, 6 phòng mỗi bên. Phòng quy ước 8 × 8 m, hành lang rộng 3 m. Đây là giả định cho demo, **không phải số đo hoặc kích thước trung bình đã xác minh của HUTECH**.
- Chọn phòng đặt Server, chọn Client 01/02, kéo thanh lộ trình hoặc nhấn **Chạy mô phỏng**. Client đi từ phòng ra cửa rồi dọc hành lang. Đường nối và số mét biểu diễn khoảng cách thẳng, kể cả xuyên tường.
- Trong **Sơ đồ quy ước và mốc khảo sát**, có thể chỉnh kích thước. Sơ đồ chưa sao chép đầy đủ tên phòng, thang máy, cầu thang trong ảnh; A1…A6/B1…B6 là nhãn quy ước.
- Dữ liệu chuyển động ở chế độ này không gửi lên Server và không ghi thành mẫu Wi-Fi.

## Chuẩn bị theo dõi thực tế

1. Chọn phòng đặt Server, tên khu, SSID và kích thước quy ước. Nhấn **Tạo mốc để khảo sát Wi-Fi**.
2. Ứng dụng tạo khu khảo sát gồm 18 mốc: 6 mốc giữa hành lang và 12 mốc trong phòng. Không bắt buộc thu đủ tất cả mốc; chỉ các lượt đã xác nhận được dùng. Mốc trên sơ đồ phải được gắn nhất quán với vị trí thực tế như trước cửa phòng. Có thể sửa tên/tọa độ bằng phiên bản khu mới trong công cụ khảo sát.
3. Đến trường, đứng tại từng mốc cần dùng, thu mẫu rồi xác nhận đúng điểm. Cần ít nhất hai điểm có lượt được xác nhận để nạp mô hình; điều này chỉ là điều kiện chạy, chưa bảo đảm phân biệt vị trí. Nên thu nhiều mốc dọc tuyến demo và một số mốc trong phòng Server.
4. Xuất JSON khảo sát. Chủ phòng tạo phòng thi, mở bản đồ → **Theo dõi Wi-Fi → Mở khảo sát JSON → Dùng khảo sát cho phòng thi**. Dữ liệu được đồng bộ vào kho khảo sát của tài khoản và dùng cho phòng hiện tại. Chỉ chủ phòng được bật/tắt khi phòng đang chờ.
5. Client phiên bản mới tham gia phòng sẽ tự quét nhiều BSSID theo SSID khảo sát. Client không cần giữ bản khảo sát riêng. Chọn **Theo dõi Wi-Fi** để xem vị trí; chủ phòng xem tất cả Client, học sinh chỉ xem chính mình.

Server vật lý vẫn phải đặt tại vị trí đã khai báo trong khu khảo sát. Di chuyển Server hoặc đổi quy ước tọa độ cần tạo phiên bản khu mới. Kích thước sơ đồ tạo bằng công cụ này được giữ trong metadata `corridor-v1` của `axisNote`, cùng phiên bản tọa độ, nên mở lại JSON vẫn giữ tỷ lệ. Khảo sát cũ không có metadata dùng sơ đồ mặc định hoặc cấu hình người vận hành chọn; phải kiểm tra các tọa độ có cùng quy ước trục.

Cấu hình đang theo dõi thuộc phiên phòng hiện tại; sau khi đóng phòng hoặc khởi động lại Server cần chọn lại khảo sát. Các mẫu đã đồng bộ vẫn được giữ trong kho khảo sát.

## Luồng kỹ thuật

`Windows WLAN scan → FLOOR_REPORT qua TLS → Server KNN/WKNN → FLOOR_UPDATE → bản đồ JavaFX`

- Dùng lại `FingerprintModel` và lõi Java `AnyplaceKnn` đã tích hợp từ [DMSL/Anyplace](https://github.com/dmsl/anyplace), giấy phép MIT và nguồn gốc được giữ trong dự án. Xem `Nguon_mo_va_danh_gia_fingerprinting.md`. Không đổi ping/RTT thành mét.
- Một lượt quét tại một thời điểm trên worker Wi-Fi hiện có; sau khi nhận phản hồi đợi 5 giây mới quét tiếp. Tốc độ cập nhật thực tế còn phụ thuộc driver và thời gian quét.
- Server kiểm tra phiên bản cấu hình, thứ tự báo cáo, định dạng/timestamp của scan và chống dùng lại tín hiệu đã nhận. Dữ liệu quá 20 giây tính từ lúc nhận không còn được hiển thị như vị trí hiện tại.
- KNN/WKNN dùng các radio trùng BSSID/tần số, lọc thiếu tín hiệu và trường hợp mơ hồ. Khi không định vị được, chấm và số mét được ẩn. Không chuyển sang tọa độ ngẫu nhiên.
- Làm mượt tọa độ khi nhận mẫu mới, sau đó nội suy hiển thị 650 ms; không đoán chuyển động vượt quá vị trí mới nhất. Số mét hiển thị đi cùng chấm đang vẽ. Có thể tắt **Chuyển động mượt**.
- Định vị độc lập với chấm điểm và điều kiện sẵn sàng. Chuyển AP hoặc thiếu vị trí không tự loại Client khỏi phòng. Mất kết nối vẫn dùng cơ chế giữ chỗ/nối lại hiện có; phụ thuộc thời hạn giữ chỗ và khả năng kết nối đến Server.
- Cần nâng cấp cả Server và Client. Khi mạng trường chặn giao tiếp ngang, có thể dùng địa chỉ kết nối trực tiếp qua mạng overlay đã cấu hình; phép đo Wi-Fi vẫn lấy từ card Wi-Fi đang khảo sát.

Đây là khoảng cách ước lượng theo tọa độ quy ước. Chưa có nghiệm thu di chuyển thực tế trên Wi-Fi HUTECH, chưa khẳng định sai số theo mét hay tốc độ theo dõi tại trường. Việc xem được demo ở nhà chỉ xác nhận luồng hiển thị.

## Kiểm tra

- `mvn test`: tọa độ/lộ trình, cùng lõi WKNN cho scan trực tiếp, tuổi mẫu, chống lặp, đóng/dừng worker, TLS với phân quyền và Client nối lại giữa ván thi.
- `LiveLanUiCheck ... floor-map`: mở từ Tùy chọn, hai dãy phòng, Client/số mét thay đổi, phát/tạm dừng, tách mô phỏng/dữ liệu theo dõi, ẩn mẫu cũ, đóng cửa sổ và không ghi dữ liệu khảo sát giả lập.

Không cần cài đặt thêm thư viện bản đồ hoặc dịch vụ Google Maps.

Đã chạy bộ 220 kiểm thử thành công, kiểm tra các ca mới qua TLS và kiểm tra giao diện 1024×640, 1366×768. Kiểm tra giao diện dùng vị trí kiểm thử tổng hợp; chưa thay thế việc cầm laptop đi thực tế ở trường.
