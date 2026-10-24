# Thanh điều hướng và chức năng quản lý

Cập nhật ngày 24/10/2026. Giữ nhận diện navy/cobalt/lime, logo và font Be Vietnam Pro hiện có. Hai ảnh người dùng gửi là tham khảo về chức năng.

## Sử dụng

Admin và giảng viên vào **Tổng quan** sau khi đăng nhập. Các mục bên trái gồm:

- **Tổng quan:** tổng câu hỏi trong ngân hàng riêng, số ván đã lưu, tình hình phòng hiện tại và lối tắt thao tác.
- **Ngân hàng câu hỏi:** tái sử dụng quản lý/lọc/nhập Excel–Word hiện có.
- **Phòng thi:** tạo/cấu hình phòng, mã tham gia, sẵn sàng và bắt đầu ván như trước.
- **Giám sát thi đấu:** trạng thái phòng, số câu hiện tại, thời gian còn lại, kết nối từng thí sinh, trạng thái đã nộp và điểm đã chấm. Chủ phòng có thể bắt đầu, trình chiếu và xác nhận hủy ván; người khác chỉ theo dõi.
- **Đấu trường thí sinh:** bảng hạng/điểm/câu đúng của phòng hiện tại, tìm theo biệt danh và lọc kết nối/sẵn sàng. Giữ bộ lọc khi ván chuyển câu hoặc kết thúc.
- **Lịch sử & Bảng xếp hạng:** mở danh sách ván của tài khoản và bảng điểm đã lưu bằng chức năng lịch sử hiện có.
- **Người dùng:** chỉ Admin thấy và sử dụng.
- **Cài đặt:** truy cập đổi mật khẩu và tùy chọn âm thanh, nhạc nền, âm lượng, giảm chuyển động hiện có.

Tên/vai trò, Đổi mật khẩu và Đăng xuất nằm ở chân thanh bên. Menu cuộn riêng khi cửa sổ thấp. Trạng thái LAN và ping được cập nhật bằng phản hồi thực tế; không giả lập tốc độ đường truyền. Học sinh giữ luồng chơi hiện tại, không có các mục quản lý.

Trận đấu vẫn cập nhật khi quản lý đang xem ngân hàng, lịch sử hoặc các trang mới; không tự chuyển quản lý sang màn chơi khi có sự kiện câu hỏi/kết quả.

## Dữ liệu và triển khai

- Mô hình Server hiện tại vẫn là một phòng tại một thời điểm. Đấu trường và Giám sát dùng dữ liệu phòng này.
- Số câu hỏi và lịch sử lấy theo tài khoản đã đăng nhập, qua gateway hiện có.
- `ROOM_STATE` bổ sung `question_number`; chỉ quản lý nhận `answered`, `score`, `correct_count` của từng người và `ranking`. Không gửi lựa chọn đáp án của người khác hoặc đáp án đúng trước khi công bố cho học sinh.
- Khởi động lại cả Server và Client sau khi build để nhận đầy đủ dữ liệu giám sát. Không thay đổi database, tài khoản, chứng chỉ hoặc cấu hình máy đang dùng.
- Các ô trạng thái của bảng được làm mới khi pha thi đổi, kể cả khi bản ghi của thí sinh không đổi.

## Kiểm thử

`mvn -o -B -ntp install`: 96 kiểm thử đạt, không có failure/error.

`LiveLanUiCheck` dùng form JavaFX → Server TLS thật → SQLite/chứng chỉ/tài khoản tạm. Kiểm tra Tổng quan, điều hướng, phòng rỗng, tiến độ nộp, giữ trang đang xem, tìm thí sinh, cập nhật chữ trong bảng qua các pha, mất kết nối thí sinh, Cài đặt và phân quyền. Các luồng đăng nhập/đăng ký, khám phá LAN, gameplay, lịch sử và nhập câu hỏi tiếp tục được kiểm tra trong cùng lượt chạy.

QA đạt cả 1366×768 và 1024×640 với ba kết quả `COMMANDER_UI_PASS`, `QUESTION_IMPORT_UI_PASS`, `LIVE_LAN_UI_PASS`. Ảnh QA cuối lưu trong `output/sidebar-final/1366/` và `output/sidebar-final/1024/`. Dữ liệu QA chỉ nằm trong môi trường tạm; kiểm thử trên cùng máy không thay thế nghiệm thu giữa nhiều máy LAN vật lý.
