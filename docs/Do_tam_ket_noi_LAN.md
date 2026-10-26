# Đo tầm kết nối LAN

Tính năng kiểm tra giao tiếp thật qua kết nối TCP/TLS đang dùng của QuizBattle. Người vận hành đo khoảng cách bằng thước rồi nhập mốc; ứng dụng không suy ra mét từ IP, ping hoặc cường độ sóng.

## Mở công cụ

- Admin/giảng viên: đăng nhập → chọn tab **Đo tầm kết nối LAN** trên thanh bên. Không cần tạo phòng; công cụ đã tách khỏi Phòng thi và Cài đặt.
- Học sinh: tham gia phòng → **Tùy chọn → Đo tầm kết nối LAN**.
- Ngoài lúc thi đấu mới bắt đầu được. Server và Client cần cùng bản cập nhật; Server cũ sẽ báo chưa hỗ trợ tính năng.

Nhập tên đợt đo, tầng/tuyến, bố trí router/server, cách đo đường thẳng hoặc dọc hành lang và ghi chú vật cản. Nhập **Khoảng cách đo thực tế (m)** từ 0 đến 500, tối đa một số lẻ; dùng dấu phẩy hoặc dấu chấm đều được. Chọn 30 hoặc 60 giây, rồi bấm **Bắt đầu**.

Mỗi phía chủ động gửi một mẫu mỗi giây. Giữ nguyên vị trí cho tới khi kết thúc; có thể **Dừng đo** để lưu lượt một phần. Chuyển tab quản lý không dừng lượt: quay lại tab đo để theo dõi. Đóng dialog đo của học sinh dừng lượt đang chạy. Các nút thao tác/xuất luôn nằm ngoài vùng nội dung cuộn.

Giao diện tham khảo ảnh người dùng: vùng thông số và sơ đồ Server–Client, hai khối kết quả, mốc xa nhất, bảng lịch sử và footer cố định. Mặc định bố trí **Wi-Fi trường / dùng chung**; không cần khai báo vị trí AP. Sơ đồ không theo tỷ lệ, không suy đoán băng tần và không tự đo mét. Các nút testbench trong ảnh chỉ thuộc kiểm thử giao diện, không có trong bản chạy.

## Bạn đang có một máy, một IP

Chạy Server và Client trên cùng máy, đăng nhập tài khoản quản lý rồi mở công cụ. Nhập mốc **0 m** và đặt tên đợt “Kiểm thử cục bộ”. Đây vẫn là gửi/nhận TLS thật, kiểm tra được hai hướng, thao tác dừng, nhật ký và CSV.

Ứng dụng ghi **Kiểm thử cục bộ** khi địa chỉ client là loopback hoặc một địa chỉ của chính máy server. Dùng IP Wi-Fi của chính máy thay 127.0.0.1 vẫn được đánh dấu cục bộ. Một IP có thể mở nhiều kết nối TCP, nhưng không tạo thêm một thiết bị ở xa. Không cần IP giả, máy ảo hoặc đổi quy tắc đăng nhập.

Lượt cục bộ có thể đạt tiêu chí giao tiếp, nhưng **không được tính vào mốc xa nhất thực địa**. Mang laptop đang chạy cả Server và Client đi xa router không đo được khoảng cách giữa hai chương trình này.

## Đọc kết quả

Hai phần **Client → Server** và **Server → Client** là thời gian khứ hồi do phía chủ động gửi tự đo bằng đồng hồ của mình, tính cả lịch xử lý, hàng đợi và TLS. Không phải thời gian một chiều hoặc thời gian truyền sóng.

- Mỗi hướng có 30 hoặc 60 mẫu dự kiến; phản hồi trong tối đa **1.000 ms** là đúng hạn.
- Một lượt đạt cần kết thúc bình thường, đủ kết quả hai hướng, mỗi hướng ít nhất **95%** đúng hạn, không có lỗi gửi/ngắt/hủy/lưu nhật ký.
- 30 mẫu cần ít nhất 29 đúng hạn mỗi hướng; 60 mẫu cần ít nhất 57.
- RTT min/giữa/p95/max chỉ tính mẫu phản hồi đúng hạn. Chưa có phản hồi thì hiện dấu gạch, không điền 0.
- “Chưa xác nhận” nghĩa là chưa có bằng chứng của mẫu đó. Nếu mất sóng trước tổng kết server, ghi **Chưa nhận đủ kết quả server**; ứng dụng không tự xem mẫu đó là thành công hoặc timeout.
- **Dừng**, mất kết nối, ván thi bắt đầu, phòng thay đổi hoặc đóng ứng dụng tạo kết quả một phần. Mở lại ứng dụng giữ dữ liệu đã ghi, không tự tiếp tục lượt cũ.

Thay thiết lập tuyến/bố trí/ghi chú tạo đợt đo mới. Mốc xa nhất chỉ lấy từ lượt đạt cùng đợt và loại lượt cục bộ; chưa có thì hiện **Chưa có mốc đạt**. Tỷ lệ khi đang chạy dùng tổng mẫu dự kiến làm mẫu số, vì vậy sẽ tăng dần trong lúc đo.

## Nhật ký và xuất CSV

- Client tự lưu từng dòng JSON vào `%LOCALAPPDATA%/NetworkQuizBattle/lan-range/client-<run_id>.jsonl`. Nếu không có LOCALAPPDATA, dùng `user.home/.network-quiz-battle/lan-range/`.
- Server tự lưu vào thư mục `lan-range/` cạnh file database đang cấu hình: `server-<run_id>.jsonl`.
- **Xuất tổng hợp** tạo một dòng mỗi lượt/hướng; **Xuất chi tiết** tạo một dòng mỗi mẫu đã có bằng chứng. CSV UTF-8 có BOM, mở được tiếng Việt trong Excel.
- Kết quả xuất có IP socket thực, cấu hình mốc đo, số mẫu, trạng thái và RTT; không xuất mật khẩu.
- Nếu không ghi được thư mục hoặc mất khả năng ghi giữa lượt, ứng dụng báo lỗi lưu và không kết luận đạt.

Hai đầu có cùng run_id để đối chiếu. Client chỉ biết kết quả chiều server đã nhận qua TLS; bản đầu không tự tải/gộp journal server sau reconnect. Nếu thiếu tổng kết thì giữ bản một phần và đo lại mốc khi kết nối phục hồi.

## Nghiệm thu thật trong một tầng

1. Mượn thêm một laptop: một máy chạy Server cố định, máy kia chạy Client. Giữ điểm phát cố định; ghi rõ server phát hotspot, router cạnh server hay Wi-Fi dùng chung.
2. Nếu dùng Wi-Fi trường nhiều AP, ghi nhận việc chuyển AP; kết quả phụ thuộc bố trí thực tế, không chỉ khoảng cách hai laptop.
3. Đo bằng thước các mốc 5, 10, 15…m; gần giới hạn tăng từng mét. Ghi rõ khoảng cách đường thẳng hay dọc hành lang và vật cản.
4. Mỗi mốc chạy đủ 30/60 giây; lặp ít nhất ba lượt ở các mốc gần biên. Giữ journal hai đầu, CSV và ảnh bố trí.
5. Thử đi xa tới mất sóng, quay về, kết nối lại rồi đo lượt mới; kiểm tra lượt gián đoạn còn trong lịch sử.
6. Chỉ báo cáo số mét sau đợt đo này. Kiểm thử cùng máy hoặc thấy một IP LAN không chứng minh khoảng cách thực địa.

## Kiểm tra phát triển

Lệnh **mvn -B -ntp verify** chạy unit/integration tests, gồm một lượt TLS thật đủ 30 mẫu mỗi hướng trên một máy. LanRangeUiCheck trên test classpath kiểm tra tab quản lý riêng, đổi tab khi đang đo, nhập sai, đo/dừng/mở lại, hoàn tất 30 giây, mất kết nối, CSV, dialog phía học sinh và kích thước 1366×768, 1024×640. LanRangeStatesUiCheck chỉ dùng fixture được ghi nhãn để kiểm tra thiếu tổng kết Server và mốc đạt/cục bộ; không tạo phép đo thực địa. Chứng chỉ/database/tài khoản QA đều nằm trong thư mục tạm; không thay dữ liệu Server đang dùng.

Có thể đặt Java system property **nqb.lanRange.dir** cho thư mục journal Client trong môi trường QA. Không cần đặt khi dùng bình thường.
