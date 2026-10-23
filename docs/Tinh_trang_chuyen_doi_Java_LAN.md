# Tình trạng chuyển đổi sang Java LAN

## Cập nhật thực thi UI ngày 14 tháng 10

Đã triển khai UI JavaFX arcade cho quản lý/học sinh/projector, gateway TLS phía Client và fixture Preview có nhãn. SFX chưa có assets và không phát tiếng. Không sửa Word hoặc PNG mẫu. Xem `UI_JavaFX_Trien_khai.md` và README cho cách chạy, ảnh QA và giới hạn.

ServerMain vẫn placeholder; chưa có auth/PBKDF2/users/owner/SQLite/Game Worker thật hoặc kiểm thử nhiều máy LAN. Không lấy UI Preview làm kết quả hoàn thành MVP. Phần dưới là lịch sử chuẩn bị/chuyển đổi, các con số 8 test và mô tả chưa có UI phản ánh thời điểm trước đợt này.

## Cập nhật chuẩn bị plan ngày 14 tháng 10

- Rà lại toàn bộ source Java/POM/config và chạy `mvn -o -B -ntp verify`: BUILD SUCCESS, 8 test, 0 failure/error/skip. Không thêm test tính năng mới.
- Tạo `Boi_canh_va_yeu_cau_Java_LAN.md` và `Ke_hoach_trien_khai_Java_LAN.md`, đồng bộ kiến trúc 4.2 và hợp đồng users/lọc/trùng.
- Người dùng giữ tài khoản Admin/giảng viên; học sinh guest nickname/mã phòng. Chính sách cảnh báo trùng có xác nhận lưu là đề xuất được ghi riêng.
- Chỉ sửa Markdown trong đợt này; không sửa Java/POM/config/Word. Các chức năng TLS/auth/users/filter/duplicate/game/UI vẫn chưa triển khai. Phần ngày 08/10 bên dưới được giữ làm lịch sử chuyển đổi.

## Bản ghi ngày 8 tháng 10

Ngày kiểm tra 08/10/2026. Phạm vi đợt này: sửa đề cương và kiến trúc, thay cấu trúc dự án; chưa viết lại toàn bộ MVP.

## Đã hoàn tất

- Đề cương Java/LAN cập nhật trực tiếp theo yêu cầu: thêm tài khoản ADMIN/TEACHER, chọn vai trò, ngân hàng/lịch sử riêng, PBKDF2/salt và TCP qua TLS; giữ thông tin nhóm, phân công F01–F12 và mẫu duyệt. Bản trước chỉnh được lưu ở `tmp/de_cuong_tai_khoan/reference.docx`; bản hiện hành được render và kiểm tra từng trang trước bàn giao.
- Kiến trúc phiên bản 4.1, danh mục giao thức LOGIN/user management/owner và kế hoạch TC17–TC23 đã thống nhất với đề cương. Đây là cập nhật tài liệu, không phải bổ sung chức năng vào Java.
- Kiến trúc Java 21/JavaFX/TCP/SQLite JDBC, một Server trong LAN và 5 module Maven.
- Khóa dependency, cấu hình UTF-8 cho source/resources và stdout/stderr Maven; kiểm tra tiếng Việt trong JAR.
- Shared NDJSON byte decoder, giới hạn 64 KiB, envelope JSON và model trạng thái; client-core có Endpoint.
- `mvn -B -ntp verify` và `mvn install` thành công. JUnit: 6 test protocol và 2 test endpoint, không failure/error/skip.
- Ba entry point khung chạy bằng launcher PowerShell. Hiện chỉ in thông báo và kết thúc.
- Lưu nguyên mã Python/test/tài liệu cũ vào `archive/python-prototype/`; không xóa `.venv/` hoặc các đặc tả gốc.

## Chưa triển khai hoặc chưa xác minh

TcpServer/NetworkClient thật, TLS/chứng chỉ, login/PBKDF2, role/owner, quản lý tài khoản/phòng, SQLite CRUD riêng, Game Worker/scoring/history, heartbeat và toàn bộ JavaFX UI chưa hoàn chỉnh. Cấu hình mẫu chưa được bootstrap đọc. Chưa đóng gói bộ cài/runtime.

Chưa có bài kiểm thử TCP của sản phẩm Java qua nhiều máy vật lý, chưa đo tải 10 Player. Các tình huống trong đề cương là kế hoạch nghiệm thu, không phải biên bản Pass. Kết quả test của prototype Python không được tính là kết quả Java.

## Bước kế tiếp

Lập trình TcpServer/client-core qua TLS trên localhost, thử hai máy LAN; tiếp theo SQLite users, bootstrap Admin, login/PBKDF2/role/owner trước CRUD/phòng/vòng thi. UI theo phân công, không dùng Python làm backend. Tám test nền tảng cũ không chứng minh tính năng auth/TLS mới đã hoạt động.
