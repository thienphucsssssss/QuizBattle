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

## Nâng cấp Giám sát và Đấu trường ngày 25/10/2026

- **Giám sát:** thanh tiến độ sẵn sàng/bài nộp, số câu và đồng hồ rõ cấp chữ; đồng hồ nhấn màu cảnh báo khi câu hỏi còn tối đa 5 giây. Lọc Tất cả/Chưa sẵn sàng hoặc Chưa nộp/Mất kết nối không thay đổi danh sách trong phòng.
- Hàng thí sinh dùng linh vật hiện có, tên và IP từ Server, nhãn kết nối/tiến độ có chữ. Tổng tiến độ tính toàn bộ người tham gia, kể cả bài đã nộp trước khi thí sinh mất kết nối.
- Nút Bắt đầu/Trình chiếu/Hủy ván nằm ngoài bảng cuộn. Giải thích lý do chưa bắt đầu được; người xem không phải chủ phòng và phiên mất kết nối không được điều khiển.
- **Đấu trường:** dải dẫn đầu gọn lấy hạng 1–3 từ Server, giữ mọi đồng hạng và cuộn khi đông. Chưa chấm câu đầu thì chưa hiển thị dải này. Bảng có linh vật, hạng, điểm và câu đúng; tìm biệt danh có hoặc không dấu.
- Chọn dòng để xem IP, kết nối và thành tích. Chi tiết đi theo `player_id` khi dữ liệu đổi, xóa khi dòng bị lọc/rời phòng. IP chỉ hiển thị nếu Server cấp cho người xem: Admin hoặc giảng viên sở hữu phòng; không lưu lại IP của dòng cũ khi quyền/dữ liệu thay đổi.
- Cả hai tab giữ dữ liệu lần cập nhật cuối khi mất kết nối, ghi rõ tình trạng này; không dùng số liệu cũ như số liệu trực tiếp. Giữ sidebar, vai trò học sinh, luồng Phòng thi và tùy chọn giảm chuyển động hiện có.

## Dữ liệu và triển khai

- Mô hình Server hiện tại vẫn là một phòng tại một thời điểm. Đấu trường và Giám sát dùng dữ liệu phòng này.
- Số câu hỏi và lịch sử lấy theo tài khoản đã đăng nhập, qua gateway hiện có.
- `ROOM_STATE` bổ sung `question_number`; chỉ quản lý nhận `answered`, `score`, `correct_count` của từng người và `ranking`. Không gửi lựa chọn đáp án của người khác hoặc đáp án đúng trước khi công bố cho học sinh.
- Khởi động lại cả Server và Client sau khi build để nhận đầy đủ dữ liệu giám sát. Không thay đổi database, tài khoản, chứng chỉ hoặc cấu hình máy đang dùng.
- Các ô trạng thái của bảng được làm mới khi pha thi đổi, kể cả khi bản ghi của thí sinh không đổi.

## Kiểm thử

Lượt nâng cấp 25/10/2026: `mvn -B -ntp install` đạt 139 kiểm thử, không có failure/error/skipped. `CommanderModelTest` kiểm tra tỷ lệ khi mất kết nối, bộ lọc theo pha, tìm không dấu, đồng hạng và quyền điều khiển.

`CommanderViewsUiCheck` dùng fixture chỉ trong test classpath, kiểm tra 1366×768 và 1024×640, tên dài, 12 người đồng hạng, giữ/xóa lựa chọn, quyền xem IP, mất kết nối, nút không bị cắt và ít nhất hai dòng bảng Đấu trường ở cửa sổ nhỏ. Ảnh ở `../output/monitor-arena-components-final/`. Fixture này không thay thế kiểm thử kết nối thật bên dưới.

`LiveLanUiCheck` dùng form JavaFX → Server TLS thật → SQLite/chứng chỉ/tài khoản tạm. Kiểm tra Tổng quan, điều hướng, phòng rỗng, tiến độ nộp, giữ trang đang xem, tìm thí sinh, cập nhật chữ trong bảng qua các pha, mất kết nối thí sinh, Cài đặt và phân quyền. Các luồng đăng nhập/đăng ký, khám phá LAN, gameplay, lịch sử và nhập câu hỏi tiếp tục được kiểm tra trong cùng lượt chạy.

QA cuối ngày 25/10/2026 đạt cả 1366×768 và 1024×640 với ba kết quả `COMMANDER_UI_PASS`, `QUESTION_IMPORT_UI_PASS`, `LIVE_LAN_UI_PASS`. Ảnh QA cuối lưu trong `../output/monitor-arena-final/1366/` và `../output/monitor-arena-final/1024/`. Dữ liệu QA chỉ nằm trong môi trường tạm; kiểm thử trên cùng máy không thay thế nghiệm thu giữa nhiều máy LAN vật lý.
