# Phòng thi và công cụ đo mạng

Cập nhật 26/10/2026 theo yêu cầu người dùng: Phòng thi đã trở lại bố cục cũ; công cụ **Đo tầm kết nối LAN** nằm trong tab riêng.

## Phòng thi

- Mã phòng lớn, tên phòng và cấu hình câu hỏi/thời lượng lấy từ Server.
- Mỗi thí sinh thật có một ô linh vật, tên và trạng thái chuẩn bị/sẵn sàng/mất kết nối.
- Khi máy đang xem mất kết nối, các trạng thái đã lưu chuyển thành **Chưa xác nhận**.
- Giữ các nút cấu hình, bắt đầu, reset, đóng phòng và trình chiếu hiện có; footer nằm ngoài nội dung cuộn.
- Không còn sơ đồ vòng tròn RTT hay nhãn mét trong Phòng thi.

## Tab Đo tầm kết nối LAN

Admin/giảng viên mở mục **Đo tầm kết nối LAN** trên sidebar. Học sinh mở cùng giao diện từ **Tùy chọn** trong dialog riêng.

Sơ đồ Server–Client trong tab đo chỉ giải thích mốc nhập từ thước, không phải bản đồ vị trí hoặc tầm phủ sóng. Số mét được người vận hành đo và nhập; kết quả kiểm tra TCP/TLS, RTT và nhật ký là dữ liệu thật. Có thể dùng Wi-Fi trường mà không biết vị trí điểm phát.

Xem [hướng dẫn đo thực nghiệm và xuất CSV](Do_tam_ket_noi_LAN.md).

## Giới hạn đo tự động

Thiết bị hiện tại báo FTM as Initiator: Not Supported. Chưa có kết nối phần cứng ESP/FTM hoặc nguồn đo mét tự động trong QuizBattle. IP, ping và RSSI không được chuyển thành khoảng cách thực. Tự đo bằng FTM là hướng tích hợp phần cứng riêng trong tương lai.
