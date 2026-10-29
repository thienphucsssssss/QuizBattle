# Một cửa sổ QuizBattle trong mỗi hồ sơ Windows — 0.6.2

Mở QuizBattle lần thứ hai sẽ đưa cửa sổ hiện tại lên trước. Nếu đang thu nhỏ, cửa sổ được khôi phục; vai trò, nickname, tài khoản và ván đang chơi được giữ nguyên. Muốn đổi vai trò, đăng xuất ngay trong cửa sổ đó.

## Phạm vi

- Dùng chung cho Client Admin, giảng viên và học sinh, gồm cả `AdminMain` và `PlayerMain` khi chạy mã nguồn.
- Các bản 0.6.2 được sao chép sang thư mục khác vẫn dùng chung khóa trong cùng hồ sơ Windows.
- Server có thể chạy cùng Client trên MSI. Cửa sổ Giám sát/trình chiếu của cùng Client vẫn hoạt động bình thường.
- Khóa thuộc hồ sơ Windows hiện tại. Đây là cơ chế chống mở trùng ứng dụng; không xác thực danh tính thiết bị trên Server hoặc ngăn máy ảo, hồ sơ Windows khác, Client cũ hay Client đã sửa mã. Các máy tham gia nên cập nhật bộ cài 0.6.2.

## Cơ chế

`SingleClientInstance` lấy khóa hệ điều hành tại `%LOCALAPPDATA%\QuizBattle\run\client.lock` trước khi khởi tạo JavaFX. Chỉ tiến trình giữ được khóa mới mở giao diện. Hệ điều hành tự thu hồi khóa khi ứng dụng đóng hoặc bị lỗi; tệp khóa còn lại không ngăn mở lần sau.

Lần mở tiếp theo đọc cổng và token ngẫu nhiên của tiến trình hiện tại trong `client.instance`, gửi yêu cầu kích hoạt qua loopback `127.0.0.1`, rồi thoát. Cổng được hệ điều hành chọn tự động, không dùng cổng LAN của Server. Kênh này chỉ yêu cầu mở lại cửa sổ, không gửi tài khoản hay thay đổi phiên chơi.

Nếu mở liên tiếp khi ứng dụng đang khởi động, yêu cầu được giữ cho đến khi cửa sổ sẵn sàng. Nếu ứng dụng đang treo và không phản hồi, lần mở sau kết thúc với thông báo lỗi thay vì mở thêm Client. Không cần xóa tệp khóa thủ công.

Trên Windows, tiến trình mới cho phép tiến trình cũ nhận foreground trước khi gửi yêu cầu. JavaFX khôi phục cửa sổ chính và hộp thoại đang mở. Windows vẫn có quyền hạn chế việc ứng dụng giành focus: xem [AllowSetForegroundWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-allowsetforegroundwindow).

## Kiểm tra

1. Cài bộ cài mới rồi mở QuizBattle, chọn một vai trò.
2. Mở lại từ shortcut hoặc file `.exe`: cửa sổ cũ xuất hiện, không tạo thêm cửa sổ đăng nhập và không đổi vai trò.
3. Thu nhỏ ứng dụng, mở lại: cửa sổ cũ được khôi phục.
4. Đóng ứng dụng rồi mở lại: khởi động bình thường.
5. Khi thử vai trò khác trên một máy, dùng đăng xuất; khi thử nhiều học sinh đồng thời, dùng các máy Client khác nhau.

Kiểm thử tự động bao gồm mở lại liên tiếp, yêu cầu đến trước khi có cửa sổ, token không hợp lệ, khởi động chưa có metadata, đóng/mở lại, tiến trình lỗi và sáu tiến trình khởi động đồng thời. Bộ kiểm thử UI nhiều Client dùng test driver riêng trong mã kiểm thử để tiếp tục kiểm tra LAN; bản phát hành không có tùy chọn bỏ qua khóa.

## Kết quả trên MSI ngày 29/10/2026

- `mvn -B -ntp -o clean install`: 166 kiểm thử đạt, không lỗi hoặc bỏ qua; gồm 7 kiểm thử khóa/kích hoạt Client.
- `LiveLanUiCheck` tại 1024×640 đạt toàn bộ luồng LAN/TLS, import/bộ câu hỏi, sơ đồ, quyền Wi-Fi, hiệu chuẩn, giám sát, lịch sử và kết quả.
- Trên Client 0.6.2 đã cài: thu nhỏ cửa sổ rồi thực thi `.exe` thêm ba lần, các lần mở sau đều thoát thành công, cửa sổ cũ được khôi phục với cùng PID và window ID.
- Mở thêm từ app-image ở thư mục khác và từ `PlayerMain` cũng dùng lại Client đang mở; hộp Tùy chọn hiện tại vẫn được giữ nguyên. Đóng rồi mở lại tạo được phiên mới bình thường.
- Bộ cài chính đã cập nhật tại `dist/QuizBattle/Client/QuizBattle-Setup.exe`; sao lưu Client cũ trước khi cài. Bảy tệp database/chứng chỉ/cấu hình Server được đối chiếu SHA-256 và giữ nguyên.

Trình khởi chạy Windows của `jpackage` có thể xuất hiện dưới dạng hai tiến trình cha/con trong Task Manager cho một lần chạy. Tiêu chí kiểm tra là một cửa sổ/phiên Client và các lần mở bổ sung thoát, không phải đếm mọi tiến trình tên `QuizBattle.exe` là một người chơi.
