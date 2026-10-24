# Chấm điểm theo tốc độ trả lời

Cập nhật ngày 24/10/2026 theo yêu cầu người dùng. Quy tắc này thay thế cách cộng cố định 100 điểm cho mỗi câu đúng.

## Quy tắc

- Đúng trong thời hạn: **50–100 điểm**. Điểm gồm 50 điểm đúng và tối đa 50 điểm thưởng tốc độ.
- Công thức: `50 + round(50 × thời_gian_còn_lại / thời_lượng_câu)`.
- Sai, bỏ trống hoặc không được nhận trước hạn: **0 điểm**.
- Điểm làm tròn thành số nguyên; hai đáp án rất sát nhau có thể bằng điểm. Đồng tổng điểm vẫn đồng hạng theo quy tắc 1, 1, 3.

Ví dụ với câu có thời lượng 10 giây: trả lời đúng sau 1 giây nhận 95 điểm; sau 5 giây nhận 75 điểm; sau 9 giây nhận 55 điểm.

## Ghi nhận và hiển thị

Server bắt đầu đếm khi mở câu hỏi và ghi thời gian của đáp án hợp lệ đầu tiên bằng đồng hồ đơn điệu. Mốc ghi nhận là lúc Game Worker xử lý đáp án; thời gian truyền/hàng đợi vẫn nằm trong thời gian phản hồi. Client không tự tính hoặc khai thời gian/điểm để được thưởng.

Đáp án trùng, sai câu, sai ván hoặc quá hạn không thay thế đáp án/thời gian đã được nhận. Server vẫn chờ hết hạn mới chấm một lần; ACK chỉ xác nhận nhận bài. Trước khi công bố kết quả, tổng điểm chưa tăng và không lộ đáp án đúng.

`QUESTION_RESULT.points` là điểm riêng của câu vừa chấm, `score` là tổng điểm. Bảng hạng, bục vinh danh và lịch sử dùng tổng điểm do Server gửi/lưu. Giao diện hiện tại tự hiển thị số điểm mới, kèm nhắc ngắn trong phòng chờ. Không đổi giao thức, cấu trúc database hoặc điểm của các ván cũ.

## Kiểm thử và chạy bản mới

- `SpeedScoringTest`: các mốc điểm cụ thể, giới hạn 50/100, làm tròn, sai/bỏ trống/quá hạn, thời lượng 5–120 giây.
- `ServerIntegrationTest`: hai câu qua TLS/SQLite thật với người trả lời nhanh/chậm/sai/bỏ trống; bỏ qua thời gian/điểm giả do Client gửi; từ chối đáp án lặp; không tái sử dụng điểm/thời gian của câu trước; điểm cuối khớp lịch sử.
- `LiveLanUiCheck`: cố ý chờ trước khi trả lời đúng ở câu thứ hai; kiểm tra điểm thưởng giảm và chữ `+N điểm` trên JavaFX khớp `points` từ Server.

Kết quả: `mvn -o -B -ntp install` đạt **103 kiểm thử**, không có failure/error. QA JavaFX ở 1024×640 đạt `COMMANDER_UI_PASS`, `QUESTION_IMPORT_UI_PASS`, `LIVE_LAN_UI_PASS`; ảnh câu trả lời đúng chậm hiển thị **+88 điểm**. Ảnh tại `output/speed-scoring-qa/`. Các lượt kết nối dùng cùng máy, không thay thế nghiệm thu nhiều máy vật lý.

Sau khi build, khởi động lại Server để áp dụng quy tắc mới và mở lại Client để có dòng nhắc trong phòng chờ. Dữ liệu kiểm thử dùng database/chứng chỉ/tài khoản tạm, không thay đổi dữ liệu sử dụng thật.
