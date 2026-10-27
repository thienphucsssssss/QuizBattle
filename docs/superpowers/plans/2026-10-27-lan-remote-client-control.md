# Đo client được chọn từ máy quản lý

**Goal:** Chủ phòng chọn client thật, nhập mốc đã đo bằng thước và điều khiển lượt đo hai chiều từ trang quản lý. Danh sách client thay khối IP của máy quản lý.

**Architecture:** Server xác thực chủ phòng và gắn một phiên điều khiển với đúng socket/player hiện tại. Client được chọn chạy LanRangeClient hiện có; Server kiểm tra/gộp bằng chứng hai chiều và chuyển tiến trình cho chủ phòng. Máy quản lý lưu bản nhật ký riêng để lịch sử/CSV hoạt động sau khi mở lại. Không suy ra mét từ RTT.

**Files:** QuizServer.java (điều khiển và phân quyền), LanRangeSession.java (bằng chứng Server), LanRangeClient.java (nhận lệnh và theo dõi từ xa), LanRangeView.java (chọn client), ServerIntegrationTest.java và các UI checks, tài liệu sử dụng/giao thức/design system.

**Constraints:** Giữ palette và footer; không tạo IP/client giả trong runtime; mỗi lượt chỉ một client, tối đa 4 lượt toàn Server; khóa lựa chọn khi đo; chỉ chủ phòng điều khiển; không đo lúc thi; kết quả luôn ghi đúng player_id/IP/run_id. Client offline, đổi phòng, mất kết nối quản lý hoặc hết thời gian chuẩn bị phải lưu phần đã có và giải phóng phiên. Học sinh không nhìn thấy IP của người khác.

- [x] Đưa danh sách client lên bên phải Server và bỏ IP máy quản lý ở phần kết nối.
- [x] RED: kiểm thử TLS thật cho quyền, client mục tiêu, dữ liệu Server, dừng và ngắt kết nối.
- [x] Triển khai điều khiển và relay có giới hạn, xác minh run/control/player; không nhận thống kê Server giả từ Client.
- [x] Triển khai client nhận lệnh và máy quản lý theo dõi/lưu nhật ký; kiểm thử đủ 30 giây với hai kết nối thật.
- [x] Giao diện chọn client, không có client thì khóa đo; kiểm tra kết nối/rời/kết nối lại và viewport nhỏ.
- [x] Cập nhật tài liệu, chạy verify, kiểm tra ảnh và build bản dùng qua run.bat.

**Review focus:** client đổi socket sau reconnect không kế thừa quyền điều khiển cũ; kết quả lịch sử không đổi IP theo lựa chọn mới; kết quả thiếu phía Server không đạt; Client cũ không nhận lệnh phải báo timeout; máy quản lý/Client lỗi ghi nhật ký phải báo rõ.

Execution: native trong phiên hiện tại, không tạo subagent hoặc commit.

## Kết quả kiểm tra

- RED: Server chưa có hợp đồng điều khiển; UI chưa có lựa chọn mục tiêu. Kiểm thử lỗi ghi báo cáo phát hiện lượt chưa tự kết thúc; đã sửa để dừng và giữ trạng thái lỗi lưu.
- GREEN: 183 tests, 0 failures/errors; Maven verify thành công toàn reactor (tmp/lan-remote-verify.log).
- TLS thật đủ 30 giây trên Client được chọn, Client khác giữ nguyên; báo cáo máy quản lý có đúng player_id và mẫu hai chiều. Kiểm tra riêng Client ngắt kết nối, quản lý ngắt, reconnect dùng lệnh cũ, giảng viên không sở hữu phòng và thư mục lưu bị lỗi đều đạt.
- LAN_RANGE_STATES_UI_PASS và LAN_RANGE_UI_PASS: chọn/khóa/bỏ client, đo/dừng/đổi tab/mở lại, hoàn tất, CSV, dialog học sinh, 1366×768 và 1024×640. Ảnh trong tmp/lan-remote-ui/.
- Kiểm thử cục bộ trên một máy; không xác nhận khoảng cách thực địa hoặc tự động đo số mét.
