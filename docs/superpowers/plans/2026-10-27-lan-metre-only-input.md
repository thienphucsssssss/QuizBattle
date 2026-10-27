# Rút gọn nhập mốc đo LAN

**Yêu cầu:** Đặt ô mốc mét vào khoảng trống dưới thông tin Server, bỏ toàn bộ khối Chuẩn bị lượt đo. Chỉ chọn client, nhập mét và bắt đầu.

**Thiết kế:** Giữ Server trái/client phải, footer và nhật ký hiện có. Dùng thời lượng cố định 30 giây; tự tạo tên đợt và nhóm theo client/phòng/Server trong phiên. Tuyến/bố trí/cách đo chưa khai báo được lưu rõ là chưa khai báo, không giả định vị trí AP hoặc hành lang.

**Phạm vi:** LanRangeView.java, LanRangeProtocol.java, các kiểm tra LAN hiện có, CSS/tài liệu liên quan. Thêm enum UNSPECIFIED vẫn đọc được toàn bộ giá trị cũ và không đổi tiêu chí đạt/RTT. Giữ lịch sử cũ và chức năng điều khiển đúng client.

- [x] RED: chỉ chọn client và nhập mét phải bắt đầu được; hiện tại còn yêu cầu tên/tuyến. Kiểm tra cấu hình không tự bịa bố trí mạng.
- [x] Chuyển ô mét vào cột Server, bỏ form/validation/tùy chọn bị loại. Tự tạo metadata, khóa mét khi đo, đổi kết quả thành mục 2.
- [x] GREEN: TLS thật, 30 giây, dừng/mở lại/đổi tab/CSV, lịch sử không tự hiện IP, kiểm tra hai kích thước và dialog.
- [x] Cập nhật hướng dẫn, chạy verify và build để dùng qua run.bat.

Thực thi trực tiếp trong phiên hiện tại, không tạo subagent và không tự đẩy GitHub.

## Kiểm tra

- RED: UI chỉ nhập mét không bắt đầu được vì còn yêu cầu tên/tuyến; giao thức chưa nhận giá trị chưa khai báo.
- GREEN: 184 kiểm thử Maven đạt, toàn reactor verify thành công (tmp/lan-metre-only-verify.log).
- LAN_RANGE_UI_PASS: chỉ chọn client/nhập mét bắt đầu được, cấu hình 30 giây và UNSPECIFIED được Server nhận; chạy TLS thật hai chiều, dừng, đổi tab, mở lại, hoàn tất, CSV và dialog.
- LAN_RANGE_HISTORY_UI_PASS và LAN_RANGE_STATES_UI_PASS: giữ phân biệt lịch sử/hiện tại, dữ liệu thiếu và loại lượt cục bộ khỏi mốc xa nhất.
- Đã xem ảnh 1366×768 và 1024×640 tại tmp/lan-metre-only-ui/. Đây là kiểm thử cục bộ, không xác nhận mét thực địa.
