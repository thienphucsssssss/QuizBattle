# Hướng dẫn phát triển cho nhóm

Tài liệu bắt đầu dành cho thành viên clone repo. Ứng dụng hiện tại là Java 21 + JavaFX 21, TLS TCP, UDP discovery và SQLite; không có hosting hoặc tài khoản demo. Các báo cáo cũ có thể chứa lệnh Preview/Seed đã ngừng dùng, không áp dụng chúng cho bản hiện tại.

## 1. Clone và kiểm tra môi trường

Cần Git, JDK 21 x64 và Maven 3.9.x. JavaFX runtime Windows được Maven tải theo POM; không cần tự cài JavaFX SDK. Dùng Windows để chạy Client và build bộ cài hiện tại. Lần build đầu cần Internet, khi chơi chỉ dùng LAN.

```powershell
git clone https://github.com/thienphucsssssss/QuizBattle.git
cd QuizBattle
java -version
mvn -version
mvn -B -ntp install
```

Maven phải dùng đúng JDK 21. Build này chạy cả unit/integration tests, không khởi tạo dữ liệu cá nhân. Test tự tạo tài khoản/chứng chỉ/database tạm riêng. Không có mật khẩu Admin mặc định.

## 2. Chạy thử hoàn toàn trên một máy

Chạy tại thư mục repo. Các file sau là cấu hình **cục bộ**, được Git bỏ qua:

```powershell
Copy-Item config/server.example.properties config/server.properties
Copy-Item client.example.properties client.properties
.\scripts\Setup-Lan.ps1 -ServerAddress auto
.\scripts\Start-Server.ps1 -InitAdmin
.\scripts\Start-Server.ps1
```

Không chạy Copy-Item để đè cấu hình đã có. Setup chỉ dùng một lần cho Server mới; từ chối ghi đè chứng chỉ. Tự đặt mật khẩu keystore (ít nhất 6 ký tự) và mật khẩu Admin (8–128 ký tự); đây là hai loại mật khẩu khác nhau. Mỗi lần chạy Server từ source nhập mật khẩu keystore. Đợi `NQB_SERVER_READY` và giữ terminal; Ctrl+C để dừng.

Ở terminal khác, cùng thư mục repo:

```powershell
.\scripts\Run-Quiz.ps1 -Role Admin
```

Chọn Admin, tên đăng nhập `admin`, mật khẩu vừa tạo. Tạo câu hỏi/import Excel hoặc Word vào ngân hàng, rồi tạo phòng. Giảng viên có thể tự đăng ký từ Client; học sinh chỉ dùng nickname/linh vật, không đăng ký tài khoản.

Mở thêm hai terminal để thử hai học sinh:

```powershell
.\scripts\Run-Quiz.ps1 -Role Player
```

Chọn Học sinh, nhập hai nickname khác nhau, chọn phòng hoặc mã phòng, chọn linh vật và sẵn sàng. Ít nhất hai học sinh sẵn sàng mới bắt đầu được. Không dùng `-Preview`, `-Seed` hoặc `-Capture`: không còn trong launcher hiện hành.

## 3. Nhiều máy trên LAN

Chọn **một Server của nhóm**. Với Server mới chưa có chứng chỉ, sao chép cấu hình mẫu rồi chạy Setup với `-ServerAddress auto`. Giữ `server.bind=auto` và các cổng TCP 5050, UDP 5051; cần mạng cho phép các máy liên lạc trực tiếp, không chỉ cùng tên Wi-Fi.

Client nhận `certs/client-truststore.p12` từ người vận hành Server qua kênh tin cậy, xác nhận fingerprint. Chỉ đây là chứng chỉ công khai; không gửi `server.p12`, database hoặc mật khẩu keystore. Giữ `server.host=auto` trong `client.properties` để tự dò mạng hiện tại. Một IP cụ thể chỉ là lựa chọn dự phòng dành cho người vận hành khi UDP bị chặn; các trường kỹ thuật này không hiển thị trên UI.

**Từ bản 0.4.0:** Server hỗ trợ binding auto, Client xác minh khóa công khai Server đã biết cùng kiểm tra chứng chỉ/TLS, độc lập địa chỉ DHCP. Đổi IP không cần thay chứng chỉ hoặc bộ cài. Bộ chạy Windows có `allow-lan.bat` để tạo rule cho runtime/cổng/LocalSubnet với quyền quản trị. Với Server chạy từ source, tạo rule tương ứng cho Java đang dùng. Không dùng IP cá nhân trong báo cáo cũ làm cấu hình chung cho nhóm. Mã phòng không vượt qua firewall/AP isolation. Không tắt firewall và không forward cổng ra Internet. Xem [LAN tự động](LAN_tu_dong_0_4_0.md).

## 4. Phân chia mã nguồn

| Module | Phạm vi |
| --- | --- |
| `nqb-shared` | Giao thức, NDJSON, hằng số và linh vật dùng chung |
| `nqb-server` | TLS/UDP Server, phân quyền, ngân hàng/trùng/import, phòng thi và SQLite |
| `nqb-client-core` | Kết nối TLS, discovery/directory và trạng thái đáp án |
| `nqb-admin-client` | Giao diện JavaFX dùng chung, import tệp, bảng hạng/podium, SFX/BGM |
| `nqb-player-client` | Launcher học sinh, dùng lại presentation chung |

`UI/` là ảnh tham khảo thiết kế, không phải ứng dụng chạy. `design-system/` lưu quyết định UI. `tools/` chứa tiện ích tạo tài liệu bằng Python, không phải runtime của sản phẩm Java và không cần để build/chơi.

## 5. Làm việc bằng nhánh

```powershell
git switch main
git pull --ff-only
git switch -c feature/ten-chuc-nang
# Chỉnh mã và kiểm thử
mvn -B -ntp verify
git status
git add <cac-file-da-kiem-tra>
git commit -m "Describe the change"
git push -u origin feature/ten-chuc-nang
```

Mở Pull Request trên GitHub để nhóm review trước khi gộp vào main. Không dùng force push lên main hoặc commit mật khẩu/khóa/database. Người dùng có quyền write/collaborator mới push được; public repo cho phép clone nhưng không tự cấp quyền ghi. Chủ repo mời thành viên tại Settings → Collaborators.

## 6. Kiểm thử và đóng gói

Từ bản tích hợp 0.5.0, đọc [bộ câu hỏi/lịch sử](Bo_cau_hoi_va_lich_su_0_5_0.md) và [phạm vi tiếp nhận từ feature/viet-my](Tich_hop_viet_my_0_5_0.md) trước khi merge tiếp các nhánh. Database hợp nhất là schema 4; không thay lại bằng migration schema 2/3 từ nhánh cũ. Giữ LAN auto và TLS pin. Từ 0.5.1, dùng [quy tắc điểm tốc độ/streak](Toc_do_va_streak_0_5_1.md) theo yêu cầu mới, thay mức cố định 100 điểm/câu. Từ 0.6.2, [Client chỉ mở một phiên trong mỗi hồ sơ Windows](Mot_Client_tren_Windows_0_6_2.md); Server không chặn theo IP, bộ kiểm thử vẫn tạo nhiều kết nối cùng máy qua test driver.

Sau lần `install` đầu và tải đủ thư viện, có thể thêm `-o` để build offline. Các unit/integration tests nằm trong từng module. QA JavaFX opt-in (tự mở/đóng UI, dùng Server/SQLite tạm riêng):

```powershell
mvn -B -ntp -o -f nqb-admin-client/pom.xml exec:java '-Dexec.mainClass=vn.edu.hutech.nqb.admin.LiveLanUiCheck' '-Dexec.classpathScope=test' '-Dexec.args=../output/team-ui-qa 1024 640'
```

QA trên một máy không thay thế demo nhiều máy vật lý. Khi build bộ cài Windows, xem `scripts/Build-WindowsRelease.ps1`: cần JDK jpackage/jlink, WiX 3 và public truststore của Server triển khai. Không cần WiX để phát triển/chạy từ source. Mỗi bộ cài chỉ mang public truststore, không mang khóa riêng/database.

`dist`, `build`, `backups`, `tmp`, `target`, `output`, môi trường Python cũ và cấu hình/khóa/database cá nhân không có trong Git. Thành viên tự build; các link đến những thư mục đó trong tài liệu lịch sử chỉ áp dụng trên máy tác giả. Không đưa toàn bộ thư mục Server cá nhân lên GitHub Releases.
