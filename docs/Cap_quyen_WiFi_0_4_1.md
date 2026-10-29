# Cấp quyền đọc Wi-Fi trên Client — 0.4.1

Khi cần đo/hiệu chuẩn mà Windows chưa cho đọc Wi-Fi, QuizBattle hiển thị mục đích sử dụng và nút **Cho phép**. Nút này gọi yêu cầu quyền hệ điều hành ngay trong tiến trình Client. Khi Windows hiện hộp thoại, người dùng chọn đồng ý; ứng dụng kiểm tra lại Wi-Fi rồi tự tiếp tục công việc đang làm.

Ứng dụng Java gọi `Geolocator.RequestAccessAsync` qua JNA/WinRT trên luồng JavaFX đang hiển thị. API này hỗ trợ ứng dụng desktop không đóng gói MSIX; không cần thêm C#/PowerShell/helper exe. Chỉ xin quyền, không gọi API lấy tọa độ GPS. Tất cả trạng thái, hủy thao tác và giải phóng COM/HSTRING đều xử lý trên cùng luồng UI; chờ kết quả bất đồng bộ, có thời hạn 120 giây.

Windows quyết định có hiện hộp thoại hay không. Thường chỉ hỏi lần đầu; nếu đã từ chối, tắt dịch vụ vị trí hoặc bị chính sách quản trị chặn, ứng dụng không thể tự cấp quyền hay buộc Windows hỏi lại. Khi đó mới hiện **Mở cài đặt và cấp quyền**, mở đúng trang Windows trực tiếp. Khi người dùng quay về hộp thoại QuizBattle, ứng dụng tự đọc lại Wi-Fi và tiếp tục nếu truy cập được. **Kiểm tra lại** là lựa chọn dự phòng; **Để sau** hủy tác vụ đang chờ.

Kết quả API xin quyền không thay cho kiểm tra Wi-Fi thật. Nếu vẫn bị từ chối, Client giữ thông báo khắc phục. Các vấn đề khác như tắt Wi-Fi, sai điểm phát/băng tần hoặc không có card hỗ trợ tiếp tục có thông báo riêng. Việc này không bỏ bước hiệu chuẩn khoảng cách.

## Cơ sở API

- [Microsoft: Geolocator.RequestAccessAsync](https://learn.microsoft.com/en-us/uwp/api/windows.devices.geolocation.geolocator.requestaccessasync): yêu cầu luồng UI/ứng dụng ở foreground, hành vi hỏi quyền lần đầu và chuyển Cài đặt khi bị từ chối.
- [Microsoft: Get the user's location](https://learn.microsoft.com/en-us/windows/apps/develop/maps-and-location/get-location): trường hợp ứng dụng unpackaged không cần khai báo capability trong manifest.
- [Microsoft: thay đổi quyền Wi-Fi/vị trí](https://learn.microsoft.com/en-us/windows/win32/nativewifi/wi-fi-access-location-changes): quyền vị trí áp dụng với truy cập BSSID và luồng khôi phục qua Windows Settings.

## Kiểm chứng

Kiểm thử tự động kiểm tra kết quả đồng ý/từ chối, hủy và timeout, cùng vòng đời tài nguyên đúng luồng. QA giao diện kiểm tra tự tiếp tục sau đồng ý, luồng bị chặn, tự kiểm tra khi trở về từ cửa sổ Cài đặt giả lập và bỏ callback sau khi đóng.

Native probe trên MSI đã gọi API Windows thật từ JavaFX và nhận `ALLOWED` (`tmp/wifi-consent-native.log`). Máy này đã có quyền nên kết quả đó không chứng minh hộp thoại Windows sẽ hiện lần đầu trên máy mới. Cần xác nhận thêm trên một laptop chưa cấp quyền; không tự thay đổi quyền riêng tư của Windows để tạo tình huống kiểm thử.

Kết quả ngày 26/10/2026: 129 bài kiểm thử JUnit đạt (0 failures/errors), gồm 3 bài mới về quyền. Hồi quy đầy đủ JavaFX/TLS/game/import/giám sát đạt; luồng quyền và bố cục đạt ở 1024×640 và 1366×768, gồm trở về từ cửa sổ Cài đặt giả lập để tự tiếp tục. Bản Client đã cài trên MSI cũng nhận `ALLOWED` từ WinRT thật (`tmp/wifi-consent-installed-native.log`).

Bộ cài mới ở `dist/QuizBattle/Client/QuizBattle-Setup.exe`, đã cài trên MSI phiên bản 0.4.1 và đối chiếu JAR với bản kiểm thử. Đã kiểm tra giữ nguyên 7 file config/cert/database Server; không cần nâng Server cho thay đổi giao diện/quyền này. Bản sao Client cũ ở `backups/Client-before-0.4.1-20261026-203754/`. SHA-256 bộ cài: `8190287e143da6a764d2f26da8f083bbe4de2be9584054ea7d6e82d25948d13c`.
