# Tự kiểm thử QuizBattle

## Bộ câu hỏi chính

Mở Bo_40_cau_hoi_test_QuizBattle.xlsx. Có 40 câu hợp lệ, bốn chủ đề, đáp án đúng A/B/C/D phân bố đều 10 câu mỗi chữ. Câu 39 dài; câu 40 có xuống dòng và dấu tiếng Việt. Đáp án đúng nằm ở cột F.

1. Đăng nhập Admin hoặc giảng viên, mở Ngân hàng câu hỏi.
2. Chọn Nhập từ Excel / Word, chọn bộ 40 câu, kiểm tra xem trước rồi nhập.
3. Tạo phòng với 5, 10 hoặc 20 câu; đặt 10 giây/câu để dễ thử tốc độ.
4. Mở hai Client học sinh với biệt danh khác nhau, vào cùng phòng và sẵn sàng.
5. Thử trả lời đúng sớm/muộn, trả lời sai và bỏ trống. Điểm đúng là 50–100, sai/bỏ trống/quá hạn là 0. Chờ kết thúc rồi kiểm tra bảng hạng và lịch sử.
6. Mở lại bộ 40 câu trong chức năng nhập để kiểm tra cảnh báo trùng ngân hàng.

## Bộ thử báo lỗi nhập

Mở Bo_test_loi_nhap_QuizBattle.xlsx sau khi đã nhập bộ chính. Tệp có 8 dòng dữ liệu: ba dòng không lỗi định dạng (gồm một câu mới, một bản trùng trong tệp và một câu trùng bộ chính) và năm dòng lỗi cố ý.

- Dòng Excel 2: câu mới hợp lệ.
- Dòng 3: trùng dòng 2.
- Dòng 4: trùng câu đầu trong bộ chính.
- Dòng 5: thiếu lựa chọn D.
- Dòng 6: đáp án đúng là E.
- Dòng 7: thiếu chủ đề.
- Dòng 8: thiếu câu hỏi.
- Dòng 9: lựa chọn A chứa công thức Excel.

Các dòng lỗi phải không chọn được. Những dòng trùng được bỏ chọn mặc định. Với ngân hàng chưa có câu test nhập mới, chỉ dòng 2 được chọn mặc định sau khi bộ chính đã được nhập. Có thể đóng xem trước để hủy mà không lưu câu nào.

Mẫu Mau_cau_hoi_QuizBattle.xlsx được giữ nguyên. Các tệp chỉ nằm trong thư mục này; chưa được tự động nhập vào ngân hàng hay thay đổi dữ liệu Server.
