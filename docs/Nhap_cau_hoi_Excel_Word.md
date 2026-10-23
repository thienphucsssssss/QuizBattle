# Nhập câu hỏi từ Excel và Word

Từ bản 0.3.4, Admin và giảng viên nhập câu hỏi vào **ngân hàng của chính tài khoản đang đăng nhập**. Học sinh không có chức năng này. Phải cập nhật cả Client lẫn Server; không đổi database/config/chứng chỉ khi nâng cấp mã.

## Tải mẫu và xem trước

1. Vào **Ngân hàng → Xem mẫu / Tải mẫu**. Chọn **Tải mẫu Excel** hoặc **Tải mẫu Word**, chọn nơi lưu trên máy.
2. Điền câu hỏi vào mẫu. Excel dùng trang `CauHoi`, hàng 1 là tiêu đề; trang `HuongDan` chỉ chứa hướng dẫn. Word dùng bảng 7 cột có sẵn, không gộp ô. Có thể thêm dòng vào bảng.
3. Giữ nguyên thứ tự cột: **Câu hỏi | A | B | C | D | Đáp án đúng | Chủ đề**. Điền đủ cả 7 cột; đáp án đúng chỉ ghi A/B/C/D (chấp nhận chữ thường). Mẫu không chứa câu mẫu và không tự thêm dữ liệu vào ngân hàng.
4. Chọn **Nhập từ Excel / Word → Chọn tệp…**. Chương trình đọc tệp tại Client rồi đối chiếu với ngân hàng riêng qua kết nối TLS hiện có. Không gửi nguyên tệp Office lên Server.
5. Chọn một dòng trong danh sách để xem đủ nội dung, cả bốn lựa chọn, đáp án đúng và lỗi/trùng. Khi cửa sổ thấp, mở tab **Chi tiết câu hỏi** (hoặc double-click/Enter tại dòng); cửa sổ cao hiển thị chi tiết ngay bên dưới bảng. Vùng chi tiết có thể cuộn để đọc nội dung dài. Chưa có câu nào được lưu ở bước này.
6. Mặc định chỉ chọn các câu hợp lệ, không trùng. Dùng checkbox từng dòng, **Chọn câu hợp lệ** hoặc **Bỏ chọn**. Muốn giữ câu trùng, chủ động bật **Cho phép nhập câu trùng (tạo bản mới)**. Đây là tạo bản riêng, không cập nhật hay ghi đè câu cũ.
7. Bấm **Nhập N câu hỏi** để xác nhận. Toàn bộ các câu đã chọn được lưu trong một transaction; nếu một câu không còn hợp lệ/trùng mới phát sinh thì không lưu bất kỳ câu nào trong lượt đó. Bấm **Kiểm tra lại** để cập nhật bản xem trước.

Hủy/đóng cửa sổ trước xác nhận không thay đổi ngân hàng. Khi đang chờ kết quả lưu, nút nhập và đóng được khóa để tránh bấm lặp. Nếu mất kết nối khi lưu, kiểm tra ngân hàng sau khi kết nối lại trước khi nhập lại; ứng dụng không tự gửi lại lượt nhập chưa rõ kết quả.

## Giới hạn hiện tại

- Chỉ hỗ trợ `.xlsx` và `.docx` theo mẫu. Không hỗ trợ `.xls`, `.doc`, PDF/ảnh hoặc đề Word trình bày tự do. Không cần cài Excel/Word để sử dụng tính năng.
- Tối đa **500 câu**, tệp **5 MiB**, tổng JSON câu hỏi **2 MiB**. Tệp nén có giới hạn số entry và dung lượng giải nén; tệp macro/đối tượng nhúng/liên kết ngoài Excel/hình ảnh bị từ chối.
- Câu hỏi 1–2.000 ký tự; mỗi lựa chọn 1–500; chủ đề 1–80. Ô công thức/lỗi phải đổi thành văn bản trước khi nhập; ứng dụng không tính công thức. Dòng trống được bỏ qua.
- Kiểm tra trùng dựa vào nội dung câu hỏi đã chuẩn hóa khoảng trắng/chữ hoa thường theo quy tắc ngân hàng hiện có. Đây không phải nhận diện ngữ nghĩa hoặc AI.
- Chỉ nhập khi phòng ở trạng thái chờ. Bản xem trước trên Server gắn với kết nối/tài khoản và hết hạn sau 2 phút. Khi xem lâu, bấm **Kiểm tra lại** trước khi nhập.
- Parser Office chạy trong tiến trình Java con giới hạn heap/metaspace và thời gian 20 giây. Đây là giới hạn tài nguyên, **không phải sandbox hệ điều hành**. Không nên mở tệp không rõ nguồn gốc; vẫn giữ phần mềm bảo vệ máy hoạt động.

## Kiến trúc triển khai

- `QuestionFiles` / `QuestionFileWorker`: Apache POI 5.5.1 đọc/tạo XLSX/DOCX bằng Java, giới hạn ZIP/kích thước/thời gian, UTF-8 rõ ràng, không tải thư viện lúc chạy. Mẫu được tạo từ chính schema import.
- `QuestionImportView`: chọn tệp, xem trước, trạng thái từng dòng, bỏ chọn/cho phép trùng có chủ ý, lưu mẫu bằng hộp thoại Windows. Phần nặng chạy ngoài JavaFX Application Thread.
- `QuestionImportRules`: hợp đồng 7 trường và kiểm tra cơ bản dùng chung Client/Server. Bỏ mọi owner/id do Client tự khai.
- `QuestionImportSession`: staging giới hạn 500 dòng/2 MiB, một phiên mỗi kết nối, chia lô tối đa 20 câu và request dưới 64 KiB, thời hạn 2 phút. Preview chỉ đọc database.
- `QuizStore.importQuestions`: Server kiểm tra lại quyền, trạng thái, trường dữ liệu và câu trùng trong transaction. Lỗi rollback toàn lượt. Commit thành công được cache theo request_id trên cùng kết nối như các mutation hiện có.

Nguồn thư viện: [Apache POI](https://poi.apache.org/download.html). Không có Python, Word COM hay dịch vụ online trong tính năng sản phẩm; các công cụ render mẫu chỉ dùng ở bước QA phát triển.

## Bản chạy trên máy này

Bộ cài 0.3.4 ở `dist/QuizBattle-Windows-20261022-110402/QuizBattle-Setup.exe`. Client đã cài tại `C:/Users/starg/AppData/Local/QuizBattle/QuizBattle.exe`. Server đang dùng vẫn là `dist/QuizBattle-Windows-20261022-084345/Server/start.bat`, đã cập nhật mã nhưng giữ nguyên dữ liệu/config/chứng chỉ. Khởi chạy lại Server đó rồi mở QuizBattle để dùng tính năng mới. Không thay nó bằng thư mục Server trống từ bộ phát hành.
