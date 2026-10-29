# Bộ câu hỏi và dùng lại đề — 0.5.0

## Chuẩn bị đề

1. Đăng nhập giảng viên hoặc Admin, mở **Ngân hàng**.
2. Tick cạnh từng câu hoặc **Chọn cả trang**. Qua trang tiếp theo để chọn thêm; số **Đã chọn** tính trên mọi trang/bộ lọc. Đăng xuất sẽ xóa lựa chọn.
3. Mở **Bộ câu hỏi**, nhập tên rồi chọn **Tạo từ N câu đã chọn**. **Tạo từ kết quả lọc** lấy toàn bộ kết quả phù hợp, tối đa 500 câu/bộ.
4. Dùng danh sách bộ ở Ngân hàng để lọc. Xóa bộ chỉ xóa nhóm, vẫn giữ câu hỏi. Không xóa bộ đang làm nguồn cho phòng chờ.

Mỗi tài khoản có bộ riêng. Câu trong bộ liên kết với ngân hàng: sửa nội dung/đáp án hoặc bật/tắt sẽ áp dụng cho lần thi mới từ bộ đó. Xóa câu sẽ gỡ liên kết khỏi bộ.

Khi nhập Excel/Word, tên bộ mặc định là tên file, có thể đổi hoặc để trống. Nếu trùng tên bộ của chính bạn, câu mới nhập được thêm vào bộ đó. Xem trước/hủy không ghi dữ liệu. Bộ chỉ chứa các câu được xác nhận nhập; cơ chế kiểm tra trùng vẫn hoạt động.

## Tổ chức phòng

Trong **Tạo phòng / Cấu hình phòng**, chọn **Bộ câu hỏi** hoặc **Toàn bộ ngân hàng**. Server chỉ lấy câu đang bật, thuộc tài khoản chủ phòng. Cần đủ số câu đã chọn cả khi lưu cấu hình và khi bắt đầu. Mỗi ván vẫn tối đa 20 câu và 10 học sinh. Từ 0.5.1, đúng được 100 điểm cơ bản cộng thưởng tốc độ/streak theo [quy tắc mới](Toc_do_va_streak_0_5_1.md).

Đổi nguồn đề đưa trạng thái sẵn sàng về chưa sẵn sàng. Giám sát Wi-Fi nằm cùng hộp cấu hình, vẫn dùng Windows API, quyền vị trí và hiệu chuẩn như trước.

## Lịch sử và dùng lại đề

Mở **Lịch sử**, chọn ván rồi **Xem bảng điểm** hoặc nhấp đúp. Có bục vinh danh, avatar, huy chương và bảng đầy đủ. **Nhật ký giám sát** vẫn mở lịch sử Wi-Fi.

Từ bản này, ván hoàn tất lưu bản chụp câu hỏi/đáp án và thời gian mỗi câu. **Dùng lại bộ câu hỏi** mở cấu hình với đề đã lưu, giữ nguyên nội dung kể cả câu gốc đã sửa/xóa. Có thể giảm số câu; hệ thống lấy các câu đầu trong thứ tự đã lưu. Bỏ chọn dùng lịch sử để chọn bộ hiện tại.

Ván cũ trước khi nâng cấp vẫn xem được điểm nhưng không thể khôi phục đề nếu chưa từng lưu bản chụp. Dùng lại đề thuộc quyền chủ ván; Admin xem giám sát của người khác không tự được dùng lại đề riêng của họ.

## Mạng và cập nhật

Client vẫn tự tìm Server, học sinh chọn phòng hoặc nhập mã. Không thêm form IP/cổng hay công cụ đo khoảng cách thủ công. IP socket chỉ dành cho chủ phòng/Admin trong cửa sổ **Giám sát**, không phải căn cứ nhận diện học sinh hoặc đo khoảng cách. Nhiều Client trên một máy vẫn vào được.

Server mới tự nâng database lên schema 4 khi mở. Sao lưu database trước lần chạy đầu; nếu quay về Server cũ phải khôi phục cả database tương ứng. Giữ nguyên `server.p12`, truststore và cấu hình để Client tiếp tục nhận diện đúng Server.
