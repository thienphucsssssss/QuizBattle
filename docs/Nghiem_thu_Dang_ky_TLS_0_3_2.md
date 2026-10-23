# Nghiệm thu bản 0.3.2 — 22/10/2026

## Bộ chạy trên máy hiện tại

- Client đã nâng cấp thành công (installer exit code 0): `C:/Users/starg/AppData/Local/QuizBattle/QuizBattle.exe`, dùng shortcut QuizBattle như trước.
- Server cá nhân đã cập nhật mã: `E:/_HUTECH/DO_AN_LAP_TRINH_MANG/dist/QuizBattle-Windows-20261022-084345/Server/start.bat`.
- Installer để phát cho các máy khác: `dist/QuizBattle-Windows-20261022-093338/QuizBattle-Setup.exe`. Chứng chỉ công khai được ghép với **Server 084345**, không phải Server 073803 hoặc một Server mới khởi tạo.
- `dist/QuizBattle-Windows-20261022-093338/Server` và ZIP Server là gói sạch cho cài mới/cập nhật mã; không phải bản sao dữ liệu/chứng chỉ cá nhân. Không chuyển sang chạy Server sạch đó với Client đã ghép hiện tại.

## Kết quả

- Build sạch và **59 test Java** đạt: shared 12, client core 30, server 16, assets 1; không failure/error/skip. REGISTER tự đăng ký chỉ tạo TEACHER, không lộ hash/salt; kiểm tra trùng tên (kể cả đồng thời), giới hạn đăng ký, tắt đăng ký, quyền riêng ngân hàng và từ chối dùng private keystore ở Client.
- Kiểm tra TLS giữ nguyên: chứng chỉ không tin cậy hoặc sai SAN bị từ chối, không chuyển sang TCP rõ/trust-all/Preview. Test TLS cũ đã được sửa fixture để dùng public truststore riêng thay vì dùng chung private keystore tạm.
- `LiveLanUiCheck` đạt trên giao diện 1366×768 và trên **runtime/JAR đã cài** ở 1024×640: LOGIN đúng/sai, sai vai trò, JOIN/avatar/ready, hai câu/ACK/đáp án/bảng hạng/chung cuộc, reset/đóng phòng/lịch sử, đăng ký có xác nhận mật khẩu → LOGIN thật → ngân hàng riêng trống. Form không còn ô chọn truststore hay mật khẩu TLS.
- Ảnh kiểm tra: `output/auto-tls-registration-ui-qa/` và `output/installed-runtime-auto-tls-registration-qa/`. Tài khoản/câu hỏi trong ảnh chỉ thuộc DB QA tạm, không được tạo trong DB cá nhân.
- Hồ sơ công khai trong thư mục Client đã cài kết nối được tới đúng Server cá nhân `10.12.89.105:5050` bằng xác minh TLS, PING đạt và handler REGISTER nhận biết payload không hợp lệ. Probe không đăng nhập/tạo tài khoản/ghi dữ liệu. Server chỉ được bật tạm trong probe rồi dừng lại, trả về trạng thái dừng ban đầu.
- SHA256SUMS của EXE/ZIP được đối chiếu; JAR đã cài khớp app-image đã build. Client chỉ có trustedCertEntry, không private key hoặc DB.
- Trước/sau chép mã Server, SHA-256 của `config/server.json`, `certs/server.p12`, `certs/client-truststore.p12`, `data/quiz.db` không thay đổi. Backup mã trước cập nhật: `backups/auto-tls-registration-20261022-094144/`.

## Cách thử ngay

Mở **start.bat của Server 084345** và giữ cửa sổ chạy; mở QuizBattle từ Desktop. Giảng viên chọn Tạo tài khoản giảng viên, Admin dùng tài khoản đã tạo, học sinh dùng nickname và mã phòng. Không cần nhập key TLS. Khi đăng ký bị tắt, liên hệ Admin cấp tài khoản.

Chưa nghiệm thu LAN trên nhiều máy vật lý hoặc trên Windows sạch; không dùng kết quả loopback/cùng máy để tuyên bố đã kiểm thử tất cả môi trường. Installer vẫn chưa ký số. Hồ sơ công khai chỉ phù hợp với chứng chỉ đã ghép; thay IP/chứng chỉ phải cập nhật hồ sơ đúng qua người vận hành.
