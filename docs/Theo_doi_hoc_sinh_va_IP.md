# Theo dõi học sinh, phòng và IP

Server ghi nhận biệt danh, `player_id`, phòng hiện tại (tên + mã phòng), địa chỉ IP của kết nối TLS và trạng thái kết nối. IP lấy trực tiếp từ socket của Server; các trường IP do Client tự gửi không thay đổi thông tin này.

## Giới hạn một học sinh trên mỗi máy/IP

- Mỗi IP nguồn chỉ được có **một học sinh hoạt động tại một thời điểm** trên Server. Mở thêm ứng dụng, đổi biệt danh hoặc gửi danh tính/IP khác từ cùng IP sẽ bị từ chối tại JOIN với mã `IP_ALREADY_IN_USE`; học sinh đang hoạt động vẫn tiếp tục sử dụng bình thường.
- Thông báo: **“Máy/IP này đã có một học sinh đang hoạt động. Hãy rời phiên học sinh hiện tại trước khi tham gia lại.”** Muốn chuyển học sinh, rời phòng/đăng xuất hoặc đóng ứng dụng cũ trước, rồi tham gia từ ứng dụng mới.
- LEAVE/LOGOUT giải phóng ngay trong lượt xử lý của Server. Đóng socket giải phóng sau khi Server phát hiện; mất mạng im lặng được phát hiện qua heartbeat, timeout hiện tại 20 giây. Không thể đổi biệt danh để bỏ qua phiên chưa được xác nhận mất kết nối.
- Reset phòng vẫn giữ giới hạn cho học sinh đang kết nối. Đóng phòng giải phóng toàn bộ phiên. Dòng học sinh mất mạng được giữ trong kết quả không chiếm lượt hoạt động, nhưng phòng vẫn chỉ nhận JOIN khi đang chờ.
- Admin/giảng viên có thể dùng cùng IP với một học sinh; kết nối quản lý hoặc chỉ duyệt phòng không chiếm lượt học sinh.

Giới hạn dựa trên **IP mà Server nhìn thấy**, không có mã phần cứng. Hai thiết bị dùng chung IP qua NAT cũng chỉ có một lượt; cùng máy dùng các địa chỉ nguồn khác nhau không thể được nhận diện là cùng phần cứng bằng IP. Trong LAN lớp học, nên cho mỗi máy học sinh kết nối trực tiếp với IP LAN riêng. Khi thử nhiều học sinh trên một máy, các Client cùng IP sẽ bị chặn theo đúng quy tắc này.

Sau khi build mã nguồn mới, khởi động lại Server bằng bản vừa build để áp dụng. Client hiện có đã hiển thị thông báo lỗi từ Server; không cần đổi cấu hình hoặc database.

## Xem trên Client quản lý

1. Build bản mới bằng `mvn -B -ntp install` hoặc chọn **1** trong `run.bat`.
2. Khởi động lại Server và Client quản lý bằng bản vừa build.
3. Đăng nhập **Admin** hoặc tài khoản **giảng viên chủ phòng**.
4. Mở **Giám sát thi đấu** hoặc **Đấu trường thí sinh**. Phía trên bảng hiển thị tên/mã phòng; cột **Địa chỉ IP** hiển thị IP của từng học sinh bên cạnh biệt danh và trạng thái.

Giảng viên không sở hữu phòng vẫn xem theo quyền giám sát hiện có, nhưng cột IP hiển thị `—`. Học sinh, danh sách phòng LAN, màn trình chiếu và bảng xếp hạng công khai không nhận địa chỉ IP. Client quản lý mới kết nối Server cũ cũng hiển thị `—`; cần nâng cấp/khởi động lại Server để nhận dữ liệu.

Mất mạng giữa ván: giữ dòng học sinh cùng IP cuối và trạng thái **Mất kết nối** đến khi reset/đóng phòng. Rời phòng khi chờ hoặc sau khi thi: xóa khỏi danh sách hiện tại. Reset chỉ giữ học sinh còn kết nối, đổi mã phòng và cập nhật log. Đóng phòng xóa toàn bộ danh sách.

## Xem tại cửa sổ Server

Server ghi log bằng Java logging, mặc định ra console (stderr). Mỗi bản ghi có tiền tố `NQB_STUDENT_TRACKING`, tiếp theo là JSON một dòng:

```text
NQB_STUDENT_TRACKING {"timestamp_utc":"2026-10-25T03:00:00Z","event":"JOINED","server_id":"...","player_id":"...","nickname":"Minh","room_code":"ABC234","room_name":"Lớp LAN","ip_address":"192.168.1.25","connected":true}
```

Ví dụ trên chỉ minh họa định dạng. Thời gian lưu theo UTC (`Z`); cộng 7 giờ để đối chiếu giờ Việt Nam.

- `JOINED`: tham gia phòng thành công.
- `LEFT`: học sinh gửi LEAVE/LOGOUT.
- `DISCONNECTED`: Server phát hiện socket đã đóng hoặc heartbeat hết hạn (timeout hiện tại 20 giây).
- `ROOM_UPDATED`: đổi tên phòng, ghi tên mới cho từng học sinh còn trong danh sách.
- `ROOM_RESET`: đổi mã phòng, ghi mã mới cho từng học sinh được giữ lại.
- `ROOM_CLOSED`: đóng phòng, ghi mã/tên phòng trước khi xóa danh sách, `connected=false` nghĩa là đã kết thúc tư cách thành viên trong phòng.

Mỗi học sinh vẫn được phân biệt bằng `player_id` trong danh sách/kết quả; IP nguồn dùng để kiểm tra giới hạn phiên học sinh đang hoạt động. Nickname/tên phòng được escape theo JSON. Không ghi mật khẩu hoặc nội dung gói tin xác thực vào log.

## Phạm vi

- Mỗi Server hiện có **một phòng hoạt động**. Chức năng này theo dõi phòng đó; không bổ sung khả năng mở nhiều phòng đồng thời.
- IP là địa chỉ mà Server nhìn thấy trên kết nối: trong LAN thường là IP LAN của máy học sinh; chạy cùng máy qua loopback sẽ là `127.0.0.1` hoặc `::1`. Không suy ra vị trí địa lý/phòng học vật lý từ IP.
- Danh sách/IP nằm trong RAM và không lưu vào lịch sử điểm hoặc SQLite. Console log không tự tạo tệp lưu trữ; muốn giữ qua lần chạy, cấu hình Java logging/redirect stdout và stderr theo quy trình vận hành.
- Đây là thay đổi mã nguồn và JAR build mới. Các bộ cài đã phát hành trong `dist/` chưa tự được cập nhật.
- Kiểm thử tự động dùng TLS/SQLite thật trên cùng máy; vẫn cần nghiệm thu nhiều máy LAN vật lý.

## Kết quả kiểm thử 25/10/2026

`mvn -B -ntp verify` thành công: 119 test, không lỗi/thất bại/bỏ qua. Ba kiểm thử bổ sung xác nhận IP từ socket dù JOIN gửi IP giả, quyền xem của chủ phòng/Admin, ẩn IP với học sinh/giảng viên khác, giữ IP khi mất mạng và dọn đúng khi reset, cùng các log vòng đời phòng. Kiểm thử JavaFX/TLS thật thành công ở 1366×768 và 1024×640, bao gồm kiểm tra IP hiển thị trong hai bảng; đã xem ảnh để xác nhận bố cục.

### Bổ sung giới hạn một học sinh/IP

`mvn -B -ntp install` thành công trên toàn dự án: **126 test, không lỗi/thất bại/bỏ qua**. Bảy ca mới đã thất bại đúng kỳ vọng trước khi thêm giới hạn, rồi đạt sau khi triển khai: mở thêm ứng dụng/đổi danh tính/gửi IP giả, hai JOIN đồng thời, giải phóng sau LEAVE/LOGOUT/mất kết nối, giữ giới hạn sau reset và giải phóng khi đóng phòng, JOIN sai không chiếm lượt. Toàn bộ 33 kiểm thử tích hợp Server đạt, bao gồm ván 10 học sinh với 10 địa chỉ nguồn loopback riêng qua socket thật.

Kiểm thử JavaFX/TLS đầy đủ ở 1024×640 đạt: form hiện thông báo IP đã có học sinh, phiên bị từ chối không làm tăng số người, rời phiên cũ rồi thử lại thành công; gameplay, giám sát/IP, nhập câu hỏi, bộ câu hỏi và tương thích Server cũ tiếp tục hoạt động. Đã sửa một race trong bộ kiểm thử nút “Tạo phòng” và chạy lại thành công; không đổi UI sản phẩm. Đã xem ảnh thông báo và bảng IP. Log build: `tmp/one-student-ip-build.log`; log/ảnh UI cuối: `tmp/one-student-ip-ui-final.log` và `tmp/one-student-ip-ui-final/`. Các cảnh báo JavaFX unnamed-module và POI thiếu logging provider vẫn xuất hiện trong QA như trước.

Đã build/cài JAR mã nguồn vào Maven cục bộ. Chưa thay bộ cài `dist/`, khởi động lại Server đang triển khai hoặc nghiệm thu trên nhiều máy LAN vật lý. Cần chạy Server bản vừa build để bật quy tắc mới.
