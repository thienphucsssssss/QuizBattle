# Nghiệm thu LAN Discovery — 0.3.3

Ngày 22/10/2026. Phạm vi: bỏ cấu hình IP/cổng trên Client, tìm phòng LAN và nhập mã song song; giữ TLS, tài khoản và gameplay hiện có.

## Thực hiện

- Thêm responder UDP LAN ở Server và discovery có nonce, giới hạn gói/nguồn/thời gian ở Client; không quét từng IP và không truyền dữ liệu đăng nhập qua UDP.
- Danh sách Server/phòng chỉ hiển thị sau xác minh chứng chỉ + IP/SAN qua TLS và `ROOM_BROWSE`. Snapshot công khai không chứa username/hash/salt, danh sách học sinh, ngân hàng hay đáp án.
- JavaFX có Phòng trong LAN / Nhập mã phòng, Tìm lại, tự làm mới, trạng thái trống/offline/đầy/đang thi. Lựa chọn phòng được giữ theo server_id + room_code khi làm mới, phòng cũ bị xóa khi đóng/đổi mã.
- Teacher/Admin tự tìm Server và tự chọn khi chỉ có một; không có ô IP/cổng hoặc truststore trên Client. Đăng nhập/đăng ký vẫn phải đi qua Server thật.
- Dừng tìm kiếm khi rời màn/kết nối thành công/đóng ứng dụng. Cấu hình cũ tương thích, discovery.port mặc định 5051. Chứng chỉ công khai tiếp tục được ghép vào installer; private key/database không nằm trong bộ cài Client.

## Kiểm thử

- Build sạch có **66 test**: shared 15, client-core 32, server 18, UI assets 1; không failure/error/skip.
- Test discovery bỏ nonce sai, deduplicate, giới hạn thời gian, nguồn Internet/gói quá lớn/sai version; Server được tìm dù TCP port cấu hình cố ý sai. Test directory trước đăng nhập, không cấp quyền qua browse, phòng đang thi, reset đổi mã, mã cũ bị từ chối, đóng phòng biến mất, fallback TCP khi không có UDP, kho không tin cậy không hiển thị Server.
- `LiveLanUiCheck` qua UI JavaFX → UDP/TLS → Server → SQLite QA tạm: vào bằng danh sách, vào bằng mã, mã không tồn tại, avatar/ready, hai câu/ACK/đáp án/bảng hạng/chung cuộc/lịch sử, reset/đóng phòng, LOGIN sai mật khẩu/sai vai trò, REGISTER và ngân hàng riêng trống. Profile QA cố ý dùng `server.port=1`, nên không thể vượt kiểm thử chỉ nhờ kết nối trực tiếp đã cấu hình.
- UI kiểm tra ở 1366×768 và trên runtime/JAR đã cài ở 1024×640; có ảnh trạng thái không có phòng và Server tắt. Source QA: `output/lan-discovery-ui-qa-v2/`; bản cài cuối: `output/installed-runtime-lan-discovery-final-qa/`. Bản cuối thu gọn danh sách ở cửa sổ thấp, giữ đầy đủ chữ Tìm lại khi thông báo offline dài và có assertion chống cụt nút.
- Probe chỉ đọc trên Server cá nhân hiện có: profile dò cố ý dùng loopback:1 thay vì IP/cổng thật, broadcast tìm được endpoint Server, xác minh TLS/PING/ROOM_BROWSE đạt. Không LOGIN, tạo tài khoản, câu hỏi hay phòng thật trong probe.
- Installer nâng cấp theo tài khoản Windows hiện tại; đối chiếu SHA256SUMS và JAR đã cài với bản đóng gói. Mã Server cũ đã sao lưu trước cập nhật; kiểm tra hash config/database/cert không đổi khi chép mã. Xem ghi chú bộ phát hành ở `Tim_phong_LAN_va_nhap_ma.md`.
- Bản cuối: `dist/QuizBattle-Windows-20261022-101323/`. Vì hai build cùng version 0.3.3, Windows Installer từ chối cài đè bằng mã 1638; đã gỡ đúng product QuizBattle 0.3.3 ở đường dẫn cài hiện tại rồi cài lại bản cuối, cả hai exit code 0. Không gỡ dữ liệu Server. Giữ bộ cài cũ và backup profile công khai tại `backups/lan-discovery-20261022-101049/Client-public-profile/`; JAR đã cài khớp release cuối. Backup mã Server trước nâng từ 0.3.2 ở cùng thư mục backup.

## Giới hạn cần nghiệm thu thực tế

Các kiểm thử chạy trên **cùng máy phát triển** (loopback và interface LAN), chưa chứng minh hai máy vật lý liên lạc qua Wi-Fi trường. Cần thử trên mạng cho phép thiết bị liên lạc; có thể cần cho phép UDP 5051/TCP 5050 trong firewall phía Server. Chương trình không sửa firewall/router. Cô lập thiết bị có thể chặn cả tự tìm và nhập mã; mã phòng không xuyên qua cô lập mạng.

Một Server hiện chỉ có một phòng đồng thời; Client tìm tối đa 8 endpoint mỗi đợt và chỉ nhận Server được kho chứng chỉ cho phép. Installer chưa ký số. Không đổi những giới hạn này thành tuyên bố hỗ trợ đa phòng hoặc mọi mạng LAN.
