# Minh chứng triển khai Server + JavaFX LAN

Ngày 16/10/2026. Đợt này triển khai code Server, tích hợp giao diện đã duyệt và 17 SFX có sẵn; không sửa Word, không tạo lại UI mẫu, không thuê hosting. Chưa thay đổi firewall, chưa khởi tạo tài khoản/chứng chỉ production của người dùng.

## Kết quả xác nhận

`mvn -o -B -ntp install` thành công: **52 test Java, 0 failure/error/skip** (12 shared + 28 client-core + 11 server + 1 SFX). Kết quả chi tiết nằm ở `*/target/surefire-reports/`; test không dùng database thật của người dùng.

| Kiểm tra | Minh chứng/phạm vi |
| --- | --- |
| Server sản phẩm + Client TLS sản phẩm | 10 integration test, mỗi test có SQLite tạm và chứng chỉ TLS tạm |
| 10 học sinh + chủ phòng | Socket TLS riêng cho mỗi Client, hoàn thành một câu; người thứ 11 bị từ chối |
| 3 học sinh, 2 câu | ACK không lộ đúng/sai; RESULT → RANKING sau từng câu → GAME_OVER; đồng điểm 1,1,3; lưu SQLite và đọc sau mở lại DB |
| Tài khoản và dữ liệu riêng | Admin tạo Teacher; bắt đổi mật khẩu; từ chối quyền/ngân hàng người khác; khóa/reset thu hồi phiên |
| Ngân hàng câu hỏi | Cảnh báo trùng chuẩn hóa, chỉ lưu khi xác nhận; lọc AND; input giống SQL không vượt bộ lọc |
| Avatar trên hai socket | Cùng chọn một avatar chỉ một bên thắng; người kia giữ avatar; ngắt kết nối lobby giải phóng đúng |
| Đáp án không hợp lệ | Sai session/question, gửi lặp, đáp án ngoài A–D và gửi sau chốt không được thêm điểm |
| Idempotency ngân hàng | Gửi lại request_id tạo câu qua TLS phân mảnh: một bản ghi; cùng ID đổi nội dung bị từ chối |
| Mất kết nối | Giảng viên đăng nhập lại lấy snapshot đang thi; người chơi mất mạng vẫn có trong bảng điểm cuối và DB |
| SFX | Một test duyệt đủ 17 WAV: hash SHA-256 khớp manifest, PCM mono/44.1 kHz/16-bit và thời lượng khớp |

`LiveLanUiCheck` chạy form JavaFX thật: học sinh JOIN → phòng chờ → ready → hai câu/ACK/kết quả/bảng hạng → chung cuộc đã lưu → reset phòng → đóng phòng → đăng nhập Admin → ngân hàng và lịch sử lấy từ SQLite. Ảnh ở `output/lan-ui-qa/`. Không gọi PreviewGateway trong luồng này. Test chạy cùng máy qua loopback, không chứng minh kết nối giữa hai máy vật lý.

Hồi quy 41 trạng thái UI đã chạy thành công bằng `java -cp` từ **bundle Client** (không Maven), gồm tạo câu/cảnh báo trùng/giữ bản nháp, chọn avatar, chuyển hạng và giảm chuyển động. Đã chạy cả 1366×768 và 1024×640: ảnh ở `output/ui-lan-regression/` và `output/ui-lan-regression-1024/`. Trình chiếu 1280×720. Bộ thư viện runtime Client không chứa Server/SQLite; runtime Server không chứa JavaFX hay Client. ServerMain cũng đã bootstrap/seed DB QA và mở listener loopback từ bundle trong tiến trình Java độc lập, sau đó dừng tiến trình kiểm thử.

Kiểm tra UTF-8: source Server sạch, JAR Server/Client có đúng chuỗi tiếng Việt; source UI có một số cảnh báo heuristic ở các chữ đúng như `MÃ`, `CÂU`, `Âm` nên không tự sửa hàng loạt. Phát hiện Windows/JDK chọn stdout Cp1252 mặc dù file.encoding=UTF-8; đã ép byte log Server sang UTF-8 và đặt stdout/stderr UTF-8 trong launcher. Skill UTF-8 được dùng để xác nhận byte nguồn/artifact trước khi sửa, không chuyển đổi file WAV.

## Cấu trúc thực hiện

- `QuizServer`: TLS accept/reader/writer, queue hữu hạn, heartbeat; một Game Worker duy nhất sở hữu phòng, avatar, deadline và JDBC. Hash mật khẩu ở executor riêng, không chặn đồng hồ.
- `QuizStore`: schema SQLite version 1, prepared statements, owner tại mọi truy vấn riêng tư, transaction lưu cả trận/bảng điểm. Từ chối DB lạ/không có version để không ghi đè dữ liệu cũ.
- `PasswordHasher`: PBKDF2-HMAC-SHA256 600.000 vòng, salt 16 byte, hash 32 byte; không lưu/log mật khẩu rõ. Khóa/reset/đổi mật khẩu tăng credential version và thu hồi phiên.
- `ArcadeApplication`: nhận snapshot Server khi chủ phòng quay lại, reset về lobby trên tất cả Client, xử lý phòng đóng/mất kết nối; giữ giao diện và animation v2.
- `SfxSlots`: Java Sound cục bộ, queue/background thread, 17 cue, mute/volume theo vai trò, không nhân đôi âm trên projector. Skill UI/UX được áp dụng cho thao tác âm lượng và tránh công việc I/O trên JavaFX thread, không đổi thiết kế đã duyệt.
- Script Setup/Start/Build/Launch: cấu hình TLS LAN và Admin có nhập mật khẩu kín; bundle chứa dependency nhưng không chứa DB/khóa riêng/mật khẩu.

## Chưa nghiệm thu/không cam kết

- **Chưa chơi qua nhiều máy LAN vật lý**; cần kiểm tra IP, firewall Private/LocalSubnet, Wi-Fi isolation và tắt Internet thực tế. Test 10 socket không phải chứng nhận hiệu năng cho mọi mạng.
- Chưa nghe/đo SFX trên loa lớp, chưa kiểm tra mọi DPI/máy chiếu. QA tự động im lặng và không thay đổi cài đặt âm thanh người dùng.
- Chưa có installer/runtime nhúng, nhiều phòng song song, resume học sinh hoặc khôi phục trận đang chơi sau crash Server.
- Chưa mô phỏng mọi trường hợp disk-full, crash giữa transaction, flood/slow-reader hoặc chạy bền nhiều giờ. Có timeout/giới hạn queue nhưng không coi đó là kiểm thử tải đã hoàn thành.
- Cache request_id hữu hạn và chỉ trong connection, không bảo đảm exactly-once xuyên reconnect/restart. UI không tự retry mutation.
- Cảnh báo JavaFX unnamed module và generic unchecked của bảng quản lý vẫn còn; không làm fail build/QA hiện tại. Sản phẩm dùng classpath, chưa JPMS.

Hướng dẫn thao tác và checklist nghiệm thu nhiều máy: `Chay_LAN_va_kiem_thu.md`. Không đánh dấu toàn bộ P1–P8 hoặc mọi TC trong đề cương là đã Pass chỉ từ các kết quả trên.
