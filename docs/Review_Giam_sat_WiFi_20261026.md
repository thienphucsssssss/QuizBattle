# Review và sửa lỗi giám sát Wi-Fi — 26/10/2026

Phạm vi chính: phần giám sát khoảng cách mới thêm ở shared, Client, Server, giao diện và lịch sử SQLite; chạy hồi quy toàn bộ bộ kiểm thử hiện có. Bản đóng gói sau sửa: **0.3.9**.

## Lỗi đã sửa

| Mức | Vấn đề phát hiện | Cách sửa |
|---|---|---|
| Cao | Đọc Wi-Fi và đóng native handle có thể chặn luồng JavaFX khi driver phản hồi chậm. | Xếp đọc/giải phóng tài nguyên vào worker; bỏ kết quả khi phiên đã đổi. |
| Cao | Hủy rồi đo lại hiệu chuẩn có thể nhận callback của lần cũ hoặc để lần cũ hủy tác vụ mới. | Mỗi lần hiệu chuẩn có mã vòng đời riêng; kiểm tra trước/sau lệnh native và khi gửi kết quả. |
| Cao | Server chấp nhận báo cáo thiếu phiên đang chạy hoặc thiếu epoch; dữ liệu lỗi có thể tiêu thụ seq và thay đổi trạng thái trước khi được kiểm tra hết. | Kiểm tra phiên, epoch, seq nguyên, adapter/BSSID/băng tần và RSSI trước khi cập nhật; tối đa 5 mẫu/báo cáo. |
| Cao | Truy vấn lịch sử và flush đồng bộ có thể giữ game worker khi SQLite đang bận. | Đọc/ghi trên hàng đợi DB riêng, giới hạn truy vấn đang chờ theo kết nối; bỏ flush chặn khi kết thúc ván. |
| Vừa | Giới hạn báo cáo phía Server là 800 ms thay vì 5 giây như thiết kế. | Thực thi 5 giây phía Server; Client giữ thời điểm gửi khi khởi động lại đo trong cùng phiên. |
| Vừa | Bộ đếm vượt ngưỡng/phục hồi nối tiếp qua khoảng mất dữ liệu; mẫu cũ vẫn có thể đủ điều kiện sau hiệu chuẩn lại. | Ngắt chuỗi khi thiếu dữ liệu; xóa số đo cũ sau hiệu chuẩn và yêu cầu mẫu mới trước Ready. |
| Vừa | Mẫu hết hạn có thể phát lại thay đổi trạng thái mỗi tick 50 ms. | Chỉ chuyển trạng thái một lần khi hết hạn, xóa mẫu/khoảng cách hiện tại. |
| Vừa | Lịch sử nhiều cảnh báo có thể vượt giới hạn frame giao thức. | Phân trang 20 cảnh báo, bổ sung điều khiển trang trong UI; giới hạn danh sách mốc ứng viên. |
| Vừa | RSSI từ cache quét có thể bị coi là mẫu kết nối mới; suy đoán băng tần bằng số kênh có thể sai. | Yêu cầu RSSI kết nối hiện tại và metadata tần số; trả trạng thái lỗi nếu driver thiếu dữ liệu. |
| Vừa | UI có thể nhận báo cáo cũ sau chuyển cấu hình/đăng xuất hoặc gửi session của trận đã kết thúc. | Context phiên bất biến, kiểm tra vòng đời callback, đóng hộp thoại khi đổi phiên/cấu hình và xóa session khi kết thúc/hủy trận. |
| Nhẹ | Lỗi ghi hồ sơ cục bộ bị bỏ qua, file tạm dùng chung dễ xung đột. | Báo lỗi lưu cho người dùng; dùng file tạm riêng và thay thế nguyên tử khi hệ thống hỗ trợ. |

## Kiểm chứng

- `mvn -B -ntp -o clean install`: **119 tests**, 0 failures, 0 errors. Có thêm 12 bài so với bộ 107 bài trước review.
- Sau sửa cuối ở hộp thoại lịch sử, chạy lại 24 tests của `nqb-admin-client` bằng Maven install: đạt.
- Kiểm thử mới bao gồm payload lỗi không làm đổi trạng thái, báo cáo sai phiên/epoch, giới hạn tần suất, hết hạn một lần, chuỗi cảnh báo qua mất dữ liệu, yêu cầu mẫu mới sau hiệu chuẩn, hủy tác vụ khi native read đang chậm, SQLite bị khóa không chặn PING và phân trang 205 cảnh báo trong giới hạn frame.
- `LiveLanUiCheck`: đạt tại **1024×640** và **1366×768**, dùng Server/TLS/SQLite tạm. Kiểm tra form, khám phá LAN, tham gia bằng mã, trận đấu, bảng hạng, nhập Excel/Word và giám sát/lịch sử. Đã xem ảnh bảng giám sát và hộp thoại lịch sử ở kích thước nhỏ.
- Kiểm thử lại bằng Java runtime đi kèm app-image 0.3.9, các JAR Client đã đóng gói và thư viện Server vừa cập nhật: đạt toàn bộ luồng `LiveLanUiCheck` ở 1024×640 (`tmp/review-wifi-bundle.log`). Không dùng database hay khóa riêng của Server cá nhân trong bài kiểm thử này.
- Log: `tmp/review-wifi-tests.log`, `tmp/review-wifi-ui-1024.log`, `tmp/review-wifi-ui-1366.log`, `tmp/review-fix-package.log`. Ảnh: `output/review-wifi-1024/`, `output/review-wifi-1366/`.

## Giới hạn còn lại

Chưa xác minh sai số khoảng cách bằng thước trong lớp học. RSSI và mẫu hiệu chuẩn do Client cung cấp; Client bị sửa vẫn có thể giả số đo. Giám sát hỗ trợ giảng viên theo dõi và không tự thay đổi điểm số. Đã thay bảng sai số chưa có chứng cứ trong hướng dẫn bằng mẫu ghi nhận thực nghiệm.

Hàng đợi lưu có giới hạn và có thông báo khi ghi lỗi. Tắt đột ngột có thể làm mất phần chưa ghi; trạng thái INTERRUPTED không bảo đảm nhật ký đầy đủ tới thời điểm mất điện.

## Bộ chạy sau review

- Client: `dist/QuizBattle/Client/QuizBattle-Setup.exe` (0.3.9). Người dùng chạy bộ cài này để cập nhật Client đang cài trên máy.
- Server: cập nhật JAR trong `dist/QuizBattle/Server/lib/`, khởi chạy bằng `start.bat` như trước. Đã đối chiếu hash và giữ nguyên 7 file thuộc config/certs/data.
- Sao lưu thư viện Server, bộ cài và hướng dẫn trước cập nhật tại `backups/QuizBattle-code-before-0.3.9-20261026-191141/`.
- SHA-256 bộ cài: `85626ba839823e2884bb92bb9b4a97958de26b77d81fb50b886420b5f9577f39`.
