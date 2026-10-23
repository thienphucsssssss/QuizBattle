# Bộ cài Client và Server start.bat

**Cập nhật 0.3.3:** bỏ IP/cổng khỏi Client; học sinh có danh sách phòng LAN hoặc nhập mã, Teacher/Admin tự tìm Server. UDP discovery 5051 + TCP/TLS 5050; giữ `data/`, `certs/`, `config/` khi nâng mã Server. Xem [Tìm phòng LAN và nhập mã](Tim_phong_LAN_va_nhap_ma.md). Nội dung các bản cũ bên dưới chỉ để đối chiếu.

**Cập nhật 0.3.2:** Client tự đọc chứng chỉ công khai đi kèm bộ cài và có tự đăng ký giảng viên; không nhập key trên form. Hướng dẫn TLS thủ công của 0.3.1 trở về trước bên dưới là lịch sử. Xem [Đăng ký và TLS tự động](Dang_ky_va_TLS_tu_dong.md). Build mới bắt buộc `-ClientTruststore` trỏ tới kho công khai của Server đang dùng; không phân phối private key.

**Bản hiện hành 0.3.1:** `dist/QuizBattle-Windows-20261022-084345/`. Đã gỡ dữ liệu mẫu và các luồng Preview/seed; xem `Dang_nhap_va_test_vai_tro.md`. Nội dung mô tả seed và bộ phát hành 0.3.0 bên dưới là lịch sử trước thay đổi này.

Ngày 22/10/2026. Đầu ra mới thay cho bundle JAR yêu cầu cài Java ở đợt 16/10:

Bản đã tạo: `dist/QuizBattle-Windows-20261022-073803/`. Client Setup khoảng 51,9 MB; ZIP Server khoảng 53,0 MB. Đã cài Client theo tài khoản Windows hiện tại tại `C:/Users/starg/AppData/Local/QuizBattle/`, có shortcut Desktop và Start Menu.

- `QuizBattle-Setup.exe`: bộ cài Windows x64, cài theo tài khoản Windows hiện tại, có shortcut Desktop/Start Menu và mục gỡ cài đặt. Một Client chung cho Admin, giảng viên và học sinh. Java runtime, JavaFX, font và SFX đi kèm; không cần Java/Maven/Python trên máy đích.
- `Server/` và `QuizBattle-Server.zip`: Server portable có `start.bat`, `setup.bat`, `init-admin.bat`, `seed-demo.bat`, thư viện và Java runtime đi kèm. Không chứa dữ liệu hoặc khóa thật của người dùng.

## Dùng ngay

1. Máy Server: giải nén **toàn bộ** thư mục Server vào nơi có quyền ghi; nhấp đôi `start.bat`. Không chạy bên trong ZIP, không chỉ chép riêng file BAT.
2. Lần đầu: nhập IPv4 LAN máy Server → đặt mật khẩu khóa TLS → đặt mật khẩu tài khoản `admin` → tùy chọn thêm 20 câu demo. Địa chỉ `127.0.0.1` chỉ phù hợp thử trên cùng máy.
3. Khi khởi động, nhập lại mật khẩu khóa TLS và chờ `NQB_SERVER_READY`. Giữ cửa sổ mở; Ctrl+C để dừng. Các lần sau không tạo lại tài khoản hoặc câu hỏi.
4. Đưa `QuizBattle-Setup.exe` cho từng máy Client để cài. Đưa riêng **chỉ** `Server/certs/client-truststore.p12` qua kênh tin cậy và đối chiếu fingerprint. Không phát `server.p12`, database hay mật khẩu khóa Server.
5. Mở QuizBattle, chọn vai trò. Nhập IP Server, cổng 5050; mở phần chứng chỉ TLS và chọn file truststore vừa nhận. Mật khẩu truststore công khai là `changeit`. Đường dẫn đã chọn được nhớ cho lần sau; nên giữ file ở Documents thay vì thư mục tạm.
6. Admin/giảng viên đăng nhập; học sinh nhập nickname và mã phòng. Client vẫn cần Server đang bật, không phải app offline độc lập.

Mật khẩu Admin cần 8–128 ký tự và nhập xác nhận lần hai. Nếu ngắn/quá dài hoặc xác nhận không khớp, chương trình yêu cầu nhập lại ngay. Nếu tạo Admin trước đó bị gián đoạn và chỉ còn database rỗng, `start.bat` nhận biết chưa có Admin và hỏi tạo lại; không cần xóa data hay tạo lại TLS. Có thể dùng `init-admin.bat` khi Server đã dừng; lệnh từ chối thay đổi Admin có sẵn. `seed-demo.bat` chỉ thêm mẫu vào ngân hàng rỗng. `setup.bat` từ chối ghi đè chứng chỉ cũ. Muốn đổi IP/cấp lại chứng chỉ phải sao lưu và thực hiện chủ động.

Firewall vẫn cần cho phép TCP 5050 inbound Private/LocalSubnet trên máy Server nếu đang chặn; không cần mở router. Không script nào tự tắt bảo mật hoặc đổi firewall. Máy đích: Windows x64 hỗ trợ Java 21/JavaFX 21. Chưa build bản ARM64, macOS/Linux hay bộ cài ký số.

## Phân phối và an toàn

EXE chưa ký chứng chỉ nhà phát hành, nên Windows có thể hiện Unknown publisher/SmartScreen. Kiểm tra nguồn nhận file và SHA-256 trong `SHA256SUMS.txt`; không tắt antivirus/SmartScreen toàn hệ thống. Runtime đi kèm không có nghĩa mọi phiên bản Windows đã được nghiệm thu.

Không đóng gói database hiện tại, mật khẩu, test credentials hoặc chứng chỉ Server vào installer. Người vận hành tự tạo chúng lúc chạy lần đầu. Để cập nhật Server cũ, dừng và sao lưu `data/`, `certs/`, `config/`; không thay chúng bằng thư mục trống của bản phát hành mới. Bộ cài Client không quản lý database Server.

## Build lại

```powershell
.\scripts\Build-WindowsRelease.ps1 -Offline -ClientTruststore 'duong-dan/Server/certs/client-truststore.p12' -ServerHost '10.12.89.105'
# Hoặc chỉ định WiX 3 đã có:
.\scripts\Build-WindowsRelease.ps1 -WixBin 'C:\Tools\wix3\tools' -ClientTruststore 'duong-dan/Server/certs/client-truststore.p12'
```

Build cần JDK 21 (jlink/jpackage), Maven và WiX 3. Máy dùng sản phẩm không cần các công cụ này. Script chạy test trước, tạo runtime và app-image, sau đó EXE/Server ZIP, kiểm tra không có DB/keystore ngoài ý muốn và tạo checksum. Mỗi build có thư mục mới, không xóa bản cũ.

Công cụ WiX được dùng cục bộ trong `build/tools`, không cài hệ thống hay sửa PATH vĩnh viễn. Gói đã tải: WiX 3.14.1 từ NuGet chính thức của WiX, SHA-256 `15d50463c73dce31fbea5440ac33af47e92d54d4188166d207e9e39577b8fe0f`.

Tham chiếu đóng gói: [Oracle JDK 21 jpackage](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html), [gói WiX 3.14.1](https://www.nuget.org/packages/wix/3.14.1). Dùng jpackage để tạo launcher/bộ cài Windows không thay đổi ngôn ngữ sản phẩm: Server và Client vẫn Java.

## Kiểm thử bản đóng gói

- Build lại toàn bộ 52 test Java: 0 failure/error/skip.
- EXE app-image chạy từ thư mục làm việc khác và không có Java/Maven trên PATH; hoàn tất 41 trạng thái UI. Ảnh: `output/exe-client-qa/`.
- Cài thực tế `QuizBattle-Setup.exe` thành công: Windows Installer trả 0, có executable/runtime và shortcut. Log tại `tmp/quizbattle-install.log`. Không dùng quyền bỏ xác minh TLS hay tắt bảo mật.
- EXE sau khi cài cũng hoàn tất 41 trạng thái UI ở 1024×640 khi Java hệ thống không nằm trong PATH; ảnh `output/installed-exe-qa/`. Hash launcher đã cài khớp launcher trong app-image.
- Giải nén Server ZIP vào thư mục QA riêng, chạy setup/chứng chỉ, bootstrap Admin, seed mẫu và Start qua Windows PowerShell 5.1, chỉ dùng java/keytool của runtime đi kèm. Server mở loopback thành công; đã dừng tiến trình QA sau kiểm tra. Không ghi tài khoản/chứng chỉ vào thư mục Server phát hành.
- Chạy `LiveLanUiCheck` bằng runtime đã đóng gói: JOIN/LOGIN thật, hai câu, ACK/kết quả/bảng hạng, SQLite/lịch sử, reset/đóng phòng đều đạt. Ảnh: `output/embedded-runtime-lan-qa/`.
- Bản phân phối được rà soát không chứa `.db`, `.p12`, `.jks`, `.key`; checksum EXE/ZIP có trong `SHA256SUMS.txt`.

Các bài trên chạy trên máy phát triển hiện tại; chưa thay thế kiểm thử cài trên Windows sạch hoặc thi trên nhiều máy LAN vật lý. Runtime đóng gói là Temurin Java 21.0.10 Windows x64 hiện có trên máy build; cần cập nhật runtime định kỳ khi phát hành lâu dài. Chưa có ký số hoặc cập nhật tự động.

Sửa lỗi bootstrap ngày 22/10: thêm kiểm tra trạng thái Admin thực tế thay cho kiểm tra file DB tồn tại. Hai test CLI mới xác nhận không tạo DB khi chỉ kiểm tra, phục hồi sau mật khẩu ngắn và không ghi đè Admin đã tạo. Kiểm thử bộ chạy Windows PowerShell 5.1 đã tái hiện database rỗng, nhập 6/129 ký tự và xác nhận sai, sau đó nhập đúng và chạy Server thành công. Bản ZIP Server và thư mục Server tại chỗ đã được cập nhật; Client không cần cài lại.
