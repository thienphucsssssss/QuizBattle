# Đo tầm kết nối LAN

Tính năng kiểm tra giao tiếp thật qua kết nối TCP/TLS đang dùng của QuizBattle. Người vận hành đo khoảng cách bằng thước rồi nhập mốc; ứng dụng không suy ra mét từ IP, ping hoặc cường độ sóng.

## Mở công cụ

- Admin/giảng viên: đăng nhập → tạo phòng → chờ client tham gia → mở tab **Đo tầm kết nối LAN**. Chủ phòng chọn client và điều khiển đo; công cụ vẫn tách khỏi Phòng thi.
- Học sinh: tham gia phòng → **Tùy chọn → Đo tầm kết nối LAN**.
- Ngoài lúc thi đấu mới bắt đầu được. Server và Client cần cùng bản cập nhật; Server cũ sẽ báo chưa hỗ trợ tính năng.

Giao diện gồm ba bước:

1. **Kiểm tra kết nối:** Server bên trái, **Client trong phòng** bên phải. Chọn tên/IP thật trong ô **Client cần đo**. Không hiển thị IP máy quản lý như một client mục tiêu. Không có client, chưa chọn client hoặc không phải chủ phòng thì khóa bắt đầu.
2. **Chuẩn bị lượt đo:** nhập tên đợt, tầng/tuyến, **Mốc đã đo bằng thước** và thời gian 30 hoặc 60 giây. Khoảng cách từ 0 đến 500 m, tối đa một số lẻ; chấp nhận dấu phẩy hoặc dấu chấm. Mục **Bố trí Wi-Fi và ghi chú** có thể mở thêm; mặc định là Wi-Fi trường/dùng chung và khoảng cách dọc hành lang.
3. **Kết quả lượt đo:** sau khi bắt đầu, phần chuẩn bị thu gọn để nhường chỗ cho tiến trình, IP của lượt đo và hai chiều phản hồi. Mở lại **Chuẩn bị lượt tiếp theo** để sửa mốc rồi đo tiếp.

Đặt laptop Client tại mốc đã đo bằng thước. Trên máy giảng viên, chọn client đó, nhập số mét rồi bấm **Bắt đầu**. Server gửi lệnh tới đúng kết nối của client được chọn; Client tự chạy lượt đo hai chiều và gửi bằng chứng về. Không cần mở dialog đo trên Client, nhưng Client phải đang chạy bản mới và còn tham gia phòng.

Máy quản lý nhận tiến trình và kết quả, có thể **Dừng đo** từ xa. Lựa chọn client bị khóa trong lúc đo. Client khác trong phòng không bị tác động. Chọn client khác sau khi lượt trước kết thúc để đo tiếp; mỗi cửa sổ quản lý điều khiển một lượt tại một thời điểm, toàn Server tối đa 4 lượt. Client rời phòng hoặc kết nối lại không tự kế thừa lựa chọn/phiên cũ.

Mỗi phía chủ động gửi một mẫu mỗi giây. Giữ nguyên vị trí cho tới khi kết thúc; có thể **Dừng đo** để lưu lượt một phần. Chuyển tab quản lý không dừng lượt: quay lại tab đo để theo dõi. Đóng dialog đo của học sinh dừng lượt đang chạy. Các nút thao tác/xuất luôn nằm ngoài vùng nội dung cuộn.

**Lịch sử trên máy này** mặc định thu gọn và không tự chọn lượt cũ. Khi mới mở công cụ, phần kết quả hiện chưa có lượt đo hiện tại, không điền IP từ nhật ký. Mở Lịch sử rồi chọn một lượt mới hiện IP/số liệu đã lưu, kèm nhãn **Lịch sử đã lưu · không phải trạng thái kết nối hiện tại**. Bấm **Đóng kết quả lịch sử** để trở về trạng thái chưa đo; thao tác này không xóa nhật ký. Lượt đang chạy hoặc vừa đo trong phiên hiện tại vẫn được giữ khi đổi tab. Phần **Kiểm tra kết nối** luôn dùng kết nối hiện tại; IP ở kết quả là địa chỉ ghi nhận tại thời điểm đo, kể cả khi client đã rời phòng.

Mốc xa nhất chỉ tính đợt của lượt đang xem. Không cần biết vị trí AP khi dùng Wi-Fi trường; ứng dụng không suy đoán băng tần và không tự đo mét. Không có nút dữ liệu mẫu/testbench trong bản chạy.

## Bạn đang có một máy, một IP

Chạy Server, mở Client quản lý để tạo phòng, rồi mở thêm Client học sinh trên cùng máy và tham gia phòng đó. Tại trang đo của giảng viên, chọn học sinh vừa tham gia, nhập **0 m**, đặt tên đợt “Kiểm thử cục bộ” và bắt đầu. Đây vẫn là gửi/nhận TLS thật, kiểm tra được hai hướng, thao tác dừng, nhật ký và CSV. Một Client quản lý đứng riêng không còn là mục tiêu mặc định để tự đo.

Ứng dụng ghi **Kiểm thử cục bộ** khi địa chỉ client là loopback hoặc một địa chỉ của chính máy server. Dùng IP Wi-Fi của chính máy thay 127.0.0.1 vẫn được đánh dấu cục bộ. Một IP có thể mở nhiều kết nối TCP, nhưng không tạo thêm một thiết bị ở xa. Không cần IP giả, máy ảo hoặc đổi quy tắc đăng nhập.

Lượt cục bộ có thể đạt tiêu chí giao tiếp, nhưng **không được tính vào mốc xa nhất thực địa**. Mang laptop đang chạy cả Server và Client đi xa router không đo được khoảng cách giữa hai chương trình này.

## Đọc kết quả

Hai khối được tách rõ: **Server** bên trái (Server gửi → Client) và **Client** bên phải (Client gửi → Server). Đây là thời gian khứ hồi do phía chủ động gửi tự đo bằng đồng hồ của mình, tính cả lịch xử lý, hàng đợi và TLS. Không phải thời gian một chiều hoặc thời gian truyền sóng.

Từ bản cập nhật 27/10/2026, địa chỉ Server hiển thị trước khi đo. Danh sách **Client trong phòng** tự cập nhật tên/IP thật khi học sinh tham gia, rời hoặc kết nối lại. Không cần chạy lượt đo để nhận IP. Khi chưa có học sinh, hiển thị trạng thái rỗng; mất kết nối với Server thì danh sách không được coi là trực tiếp. IP từ socket do Server quan sát; chỉ Admin/chủ phòng có danh sách IP học sinh, học sinh chỉ nhận IP của chính kết nối mình.

Danh sách client đang kết nối và số liệu lượt đo là hai nguồn riêng. Số liệu thuộc đúng player_id, IP và run_id của lượt đang xem; xem lại lịch sử không biến IP cũ thành kết nối mới. Server chỉ cho chủ phòng ra lệnh; địa chỉ lấy từ socket, thống kê chiều Server lấy trực tiếp từ phiên đo. RTT chiều Client được đo trên chính client, không phải trên máy quản lý.

- Mỗi hướng có 30 hoặc 60 mẫu dự kiến; phản hồi trong tối đa **1.000 ms** là đúng hạn.
- Một lượt đạt cần kết thúc bình thường, đủ kết quả hai hướng, mỗi hướng ít nhất **95%** đúng hạn, không có lỗi gửi/ngắt/hủy/lưu nhật ký.
- 30 mẫu cần ít nhất 29 đúng hạn mỗi hướng; 60 mẫu cần ít nhất 57.
- RTT min/giữa/p95/max chỉ tính mẫu phản hồi đúng hạn. Chưa có phản hồi thì hiện dấu gạch, không điền 0.
- “Chưa xác nhận” nghĩa là chưa có bằng chứng của mẫu đó. Nếu mất sóng trước tổng kết server, ghi **Chưa nhận đủ kết quả server**; ứng dụng không tự xem mẫu đó là thành công hoặc timeout.
- **Dừng**, mất kết nối, ván thi bắt đầu, phòng thay đổi hoặc đóng ứng dụng tạo kết quả một phần. Mở lại ứng dụng giữ dữ liệu đã ghi, không tự tiếp tục lượt cũ.

Thay client hoặc thiết lập tuyến/bố trí/ghi chú tạo đợt đo mới. Mốc xa nhất chỉ lấy từ lượt đạt cùng đợt và loại lượt cục bộ; chưa có thì hiện **Chưa có mốc đạt**. Tỷ lệ khi đang chạy dùng tổng mẫu dự kiến làm mẫu số, vì vậy sẽ tăng dần trong lúc đo.

## Nhật ký và xuất CSV

- Client tự lưu từng dòng JSON vào `%LOCALAPPDATA%/NetworkQuizBattle/lan-range/client-<run_id>.jsonl`. Nếu không có LOCALAPPDATA, dùng `user.home/.network-quiz-battle/lan-range/`.
- Server tự lưu vào thư mục `lan-range/` cạnh file database đang cấu hình: `server-<run_id>.jsonl`.
- Máy quản lý lưu bản theo dõi vào thư mục con `controlled/` của thư mục nhật ký Client. Lịch sử đọc cả hai nguồn, gộp theo run_id; không ghi đè nhật ký Client khi kiểm thử nhiều chương trình trên cùng máy.
- **Xuất tổng hợp** tạo một dòng mỗi lượt/hướng; **Xuất chi tiết** tạo một dòng mỗi mẫu đã có bằng chứng. CSV UTF-8 có BOM, mở được tiếng Việt trong Excel.
- Kết quả xuất có player_id/biệt danh, IP socket thực, cấu hình mốc đo, số mẫu, trạng thái và RTT; không xuất mật khẩu.
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
