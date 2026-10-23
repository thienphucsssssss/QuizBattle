# Nghiệm thu nhập câu hỏi Excel và Word 0.3.4

Ngày 22/10/2026. Phạm vi: nhập câu hỏi theo mẫu XLSX/DOCX, xem trước, kiểm tra lỗi/trùng, chọn câu, lưu nguyên tử vào ngân hàng riêng, lưu mẫu trống xuống máy. Không sửa đề cương Word đã nộp, không thêm dữ liệu mẫu vào Server cá nhân.

## Kiểm thử mã và giao thức

- Build sạch Maven offline: **79 test**, không failure/error/skip (shared 17, client-core 32, server 22, admin-client 8).
- File parser: hai mẫu trống đọc lại không sinh câu; tiếng Việt/xuống dòng/chữ thường A–D; dòng sai/thiếu đáp án; giữ bảng Word và bỏ đoạn hướng dẫn; không đánh giá công thức; từ chối sai tiêu đề, `.xls`, hơn 500 dòng, tệp nhúng, tệp nén phình lớn và tệp hơn 5 MiB; parser tiến trình Java con chạy thật.
- TLS/SQLite: preview không ghi; chia phần và trùng nội bộ; commit chỉ các index hợp lệ; token theo connection; owner do Server gắn; kiểm tra trùng ngân hàng riêng; race phát sinh trùng giữa preview/commit rollback cả câu đã chèn trong transaction; cho phép trùng chỉ append; từ chối index lặp/sai, lô sai thứ tự/quá lớn và staging chưa hoàn tất; guest/chưa đăng nhập/đang thi không được nhập.
- Gửi lại cùng `request_id` của commit qua TLS phân mảnh trả cùng phản hồi, không tạo thêm câu, kể cả staging đã được dọn sau commit đầu tiên.
- UTF-8: scan các nguồn mới sạch; kiểm tra chuỗi tiếng Việt trong JAR build sạch đạt. Kết quả worker dùng tệp JSON UTF-8 riêng, không trộn log thư viện/stdout vào dữ liệu.

## Kiểm thử giao diện và mẫu

- `LiveLanUiCheck` dùng JavaFX thật → TLS Server thật → SQLite tạm. Nhập Excel gồm câu hợp lệ/trùng/lỗi, xem chi tiết lỗi, hủy không ghi, nhập đúng hai câu đã chọn, kiểm tra lại báo trùng ngân hàng và opt-in rõ ràng; Word nhập thêm một câu thành công. Ngân hàng giảng viên QA kết thúc đúng 3 câu, không ảnh hưởng Server cá nhân.
- Luồng LAN/game/auth sẵn có vẫn đạt: UDP discovery khi TCP profile cố ý sai, chọn phòng/nhập mã, mã sai, avatar/ready, hai câu thi, bảng hạng từng câu, kết quả/lịch sử, reset/đóng phòng, login sai, vai trò sai, đăng ký tài khoản/ngân hàng rỗng và Server offline.
- 1366×768: `output/question-import-ui-qa/`. 1024×640 bản cuối có tab Danh sách/Chi tiết, hủy không để sót worker: `output/question-import-ui-qa-final/`. Không coi đây là bằng chứng hai máy vật lý chơi qua Wi-Fi trường.
- Mẫu ở `output/templates/` tạo từ `QuestionFiles.writeTemplate`, cũng là hàm dùng khi người dùng chọn vị trí lưu. Đọc lại bằng parser thật thành 0 câu; không có câu mẫu hay tài khoản mẫu.
- Excel render bằng bundled artifact-tool, kiểm tra cả trang CauHoi và HuongDan. Word renderer chuẩn không có LibreOffice trên máy; thay bằng một instance Word ẩn, mở chỉ đọc, xuất PDF QA, render PNG bằng bundled Poppler. Đã xem toàn bộ 1 trang Word và 2 ảnh Excel: không cụt chữ, không chồng bảng, tiếng Việt đúng. Tệp mẫu vẫn là bản Java POI, không được Word/JS sửa lại.
- Ảnh render mẫu: `tmp/import-artifact-qa/`. Các tệp `qa-only-import.*` chỉ ở thư mục QA, không đi vào bộ cài/Server người dùng. Hộp thoại chọn tệp/lưu tệp là native Windows; kiểm thử tự động gọi cùng đường đọc/tạo mẫu với đường dẫn QA, không giả lập thao tác bàn phím trong native picker.

## Bộ cài và cập nhật máy phát triển

- Bộ phát hành 0.3.4: `dist/QuizBattle-Windows-20261022-110402/`, gồm Client EXE, Server `start.bat`, ZIP Server và SHA256SUMS. Đối chiếu checksum đạt. Client có POI/dependency; Server không có POI. JAR Client kiểm tra chuỗi UTF-8 đạt.
- Nâng cấp Client tại `C:/Users/starg/AppData/Local/QuizBattle` thành công (installer exit 0), cấu hình jpackage ghi 0.3.4; hash 3 JAR ứng dụng đã cài khớp app-image.
- Server cá nhân tiếp tục dùng **`dist/QuizBattle-Windows-20261022-084345/Server/start.bat`**. Chỉ cập nhật hai JAR Server/shared khi Server đã dừng. Hash toàn bộ config/database/cert không đổi sau chép mã.
- Backup mã Server cũ và profile Client công khai tại `backups/question-import-20261022-110634/`. Không ghi đè database hoặc cấu hình cá nhân bằng Server trống trong bộ phát hành mới.
- Kiểm thử lại toàn luồng với **runtime và JAR đã cài**, không có Java/Maven hệ thống trong PATH: `QUESTION_IMPORT_UI_PASS` và `LIVE_LAN_UI_PASS`. Parser Java con, mẫu Excel/Word, preview/cancel/commit và gameplay đều đạt; ảnh ở `output/installed-runtime-question-import-qa/`. Dữ liệu thử nằm trong database tạm, không phải database cá nhân.

## Giới hạn triển khai

Chỉ văn bản theo mẫu; không hiểu Word trình bày tự do, PDF/ảnh, công thức hoặc nhiều đáp án đúng. Không kiểm tra trùng ngữ nghĩa. Giới hạn tài nguyên của tiến trình đọc Office không phải sandbox hệ điều hành. Staging hết hạn sau 2 phút; không bảo đảm exactly-once sau reconnect/restart. Nếu mất kết nối khi commit, cần xem ngân hàng trước khi nhập lại. Nghiệm thu LAN nhiều máy vật lý tại trường vẫn cần làm riêng.
