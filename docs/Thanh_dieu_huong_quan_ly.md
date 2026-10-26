# Thanh điều hướng và chức năng quản lý

Cập nhật ngày 26/10/2026. Giữ nhận diện navy/cobalt/lime, logo và font Be Vietnam Pro hiện có. Hai ảnh người dùng gửi là tham khảo về chức năng.

## Sử dụng

Admin và giảng viên vào **Tổng quan** sau khi đăng nhập. Các mục bên trái gồm:

- **Tổng quan:** tổng câu hỏi trong ngân hàng riêng, số ván đã lưu, tình hình phòng hiện tại và lối tắt thao tác.
- **Ngân hàng câu hỏi:** tái sử dụng quản lý/lọc/nhập Excel–Word hiện có.
- **Phòng thi:** tạo/cấu hình phòng, mã tham gia, sẵn sàng và bắt đầu ván như trước.
- **Giám sát thi đấu:** trạng thái phòng, số câu hiện tại, thời gian còn lại, kết nối từng thí sinh, trạng thái đã nộp và điểm đã chấm. Chủ phòng có thể bắt đầu, trình chiếu và xác nhận hủy ván; người khác chỉ theo dõi.
- **Đo tầm kết nối LAN:** tab riêng cho mốc đo bằng thước, kiểm tra hai chiều, lịch sử và xuất CSV. Chuyển tab không hủy lượt đang đo. Học sinh vẫn mở công cụ từ Tùy chọn.
- **Lịch sử & Bảng xếp hạng:** mở danh sách ván của tài khoản và bảng điểm đã lưu bằng chức năng lịch sử hiện có.
- **Người dùng:** chỉ Admin thấy và sử dụng.
- **Cài đặt:** truy cập đổi mật khẩu và tùy chọn âm thanh, nhạc nền, âm lượng, giảm chuyển động hiện có.

Tên/vai trò, Đổi mật khẩu và Đăng xuất nằm ở chân thanh bên. Menu cuộn riêng khi cửa sổ thấp. Trạng thái LAN và ping được cập nhật bằng phản hồi thực tế; không giả lập tốc độ đường truyền. Học sinh giữ luồng chơi hiện tại, không có các mục quản lý.

Trận đấu vẫn cập nhật khi quản lý đang xem ngân hàng, lịch sử hoặc các trang mới; không tự chuyển quản lý sang màn chơi khi có sự kiện câu hỏi/kết quả.

## Giám sát thi đấu

- **Giám sát:** thanh tiến độ sẵn sàng/bài nộp, số câu và đồng hồ rõ cấp chữ; đồng hồ nhấn màu cảnh báo khi câu hỏi còn tối đa 5 giây. Lọc Tất cả/Chưa sẵn sàng hoặc Chưa nộp/Mất kết nối không thay đổi danh sách trong phòng.
- Hàng thí sinh dùng linh vật hiện có, tên và IP từ Server, nhãn kết nối/tiến độ có chữ. Tổng tiến độ tính toàn bộ người tham gia, kể cả bài đã nộp trước khi thí sinh mất kết nối.
- Nút Bắt đầu/Trình chiếu/Hủy ván nằm ngoài bảng cuộn. Giải thích lý do chưa bắt đầu được; người xem không phải chủ phòng và phiên mất kết nối không được điều khiển.
- Giám sát giữ dữ liệu lần cập nhật cuối khi mất kết nối, ghi rõ tình trạng này; không dùng số liệu cũ như số liệu trực tiếp. Giữ sidebar, vai trò học sinh, luồng Phòng thi và tùy chọn giảm chuyển động hiện có.

## Dữ liệu và triển khai

- Mô hình Server hiện tại vẫn là một phòng tại một thời điểm. Giám sát dùng dữ liệu phòng này.
- Số câu hỏi và lịch sử lấy theo tài khoản đã đăng nhập, qua gateway hiện có.
- `ROOM_STATE` bổ sung `question_number`; chỉ quản lý nhận `answered`, `score`, `correct_count` của từng người và `ranking`. Không gửi lựa chọn đáp án của người khác hoặc đáp án đúng trước khi công bố cho học sinh.
- Khởi động lại cả Server và Client sau khi build để nhận đầy đủ dữ liệu giám sát. Không thay đổi database, tài khoản, chứng chỉ hoặc cấu hình máy đang dùng.
- Các ô trạng thái của bảng được làm mới khi pha thi đổi, kể cả khi bản ghi của thí sinh không đổi.

## Kiểm thử

`CommanderModelTest` kiểm tra tỷ lệ khi mất kết nối, bộ lọc theo pha và quyền điều khiển.

`CommanderViewsUiCheck` kiểm tra thanh điều hướng Admin/giảng viên không còn tab đã gỡ và từ chối đường dẫn cũ; kiểm tra Giám sát tại 1366×768 và 1024×640 với tên dài, tiến độ, bộ lọc, mất kết nối và nút không bị cắt. Fixture chỉ nằm trong test classpath.

`LiveLanUiCheck` dùng form JavaFX → Server TLS thật → SQLite/chứng chỉ/tài khoản tạm. Kiểm tra Tổng quan, điều hướng, phòng rỗng, tiến độ nộp, giữ trang Giám sát qua các pha, cập nhật chữ trong bảng, mất kết nối thí sinh, Cài đặt và phân quyền. Các luồng đăng nhập/đăng ký, khám phá LAN, gameplay, bảng điểm lịch sử và nhập câu hỏi tiếp tục được kiểm tra trong cùng lượt chạy.

Dữ liệu QA chỉ nằm trong môi trường tạm; kiểm thử trên cùng máy không thay thế nghiệm thu giữa nhiều máy LAN vật lý.

Kiểm chứng sau khi gỡ tab ngày 26/10/2026: `mvn -B -ntp install` đạt 171 kiểm thử, không có lỗi/bỏ qua. Kiểm tra component và luồng TLS thật đạt ở cả 1366×768 và 1024×640. Ảnh tại `../output/remove-arena-components/` và `../output/remove-arena-live/{1366,1024}/`.
