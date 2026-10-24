# Chạy Network Quiz Battle trên LAN

**Cập nhật 22/10/2026, bản 0.3.1:** đã gỡ Preview/seed demo, ngân hàng mới trống và Client luôn hỏi vai trò rồi xác thực thật. Dùng `Dang_nhap_va_test_vai_tro.md` cho hướng dẫn hiện hành. Các lệnh `-Seed`, `-Preview`, `-Capture` trong bản ghi ngày 16/10 bên dưới đã ngừng dùng; kiểm thử UI dùng LiveLanUiCheck trên test classpath.

Phiên bản Java triển khai ngày 16/10/2026. Không cần hosting, Python hoặc Internet khi chơi. Máy Server giữ database SQLite; Client không truy cập trực tiếp file dữ liệu. Windows x64, JDK 21; bản source cần Maven 3.9.x, bản bundle không cần Maven. Không có tài khoản/mật khẩu Admin mặc định.

## 1. Khởi tạo từ source trên máy Server

Mở PowerShell tại thư mục dự án. Build lần đầu cần Internet tải thư viện:

```powershell
mvn -B -ntp install
.\scripts\Setup-Lan.ps1 -ServerAddress 192.168.1.10
.\scripts\Start-Server.ps1 -InitAdmin
.\scripts\Start-Server.ps1 -Seed admin
.\scripts\Start-Server.ps1
```

Thay `192.168.1.10` bằng IPv4 LAN thật của máy Server (xem `ipconfig`). Setup tạo chứng chỉ có SAN khớp IP và cấu hình bind đúng IP đó. Để chỉ thử cùng máy có thể dùng `127.0.0.1`; muốn chuyển sang LAN cần thay chứng chỉ phù hợp, không bỏ xác minh TLS. Script từ chối ghi đè chứng chỉ có sẵn: hãy sao lưu và chủ động xoay chứng chỉ, không xóa khóa khi chưa có bản sao. Không cần mở cổng router.

Hai mật khẩu **khác nhau**: mật khẩu keystore TLS do Setup hỏi; mật khẩu tài khoản `admin` do InitAdmin hỏi (8–128 ký tự). Nhập kín trong terminal, không đưa vào câu lệnh/chat/Git. Chạy Server sẽ hỏi mật khẩu keystore. InitAdmin chỉ chạy lần đầu; không reset Admin hoặc xóa dữ liệu nếu đã có. Lệnh Seed là tùy chọn: thêm 20 câu mạng máy tính vào ngân hàng rỗng của tài khoản được chỉ định; không ghi đè ngân hàng có câu. Chỉ chạy InitAdmin/Seed khi Server đã dừng.

Khi thấy `NQB_SERVER_READY TLS ...:5050`, Server sẵn sàng. Giữ terminal chạy; Ctrl+C để dừng. Không cho máy ngủ trong lúc thi. Cấu hình ở `config/server.properties`, đường dẫn DB/keystore tính từ thư mục cha của `config/`. Mật khẩu không nằm trong cấu hình.

## 2. Chạy Client và một ván thi

```powershell
.\scripts\Run-Quiz.ps1 -Role Admin
.\scripts\Run-Quiz.ps1 -Role Player
```

Chạy từng ứng dụng ở terminal riêng. Giảng viên/Admin dùng cùng launcher quản lý. Admin đăng nhập, cấp tài khoản giảng viên và mật khẩu tạm. Giảng viên phải đổi mật khẩu rồi đăng nhập lại; ngân hàng và lịch sử gắn với đúng tài khoản. Admin cũng có thể tạo phòng từ ngân hàng riêng để demo nhanh. Vai trò UI không cấp quyền: Server quyết định theo tài khoản đã xác thực.

1. Máy quản lý: IP máy Server, cổng 5050, tài khoản/mật khẩu. Tại phần chứng chỉ, chọn `certs/client-truststore.p12`. Truststore chỉ chứa chứng chỉ công khai, mật khẩu mặc định **changeit** (không phải mật khẩu Admin hoặc khóa Server).
2. Tạo câu hỏi hoặc dùng ngân hàng mẫu đã seed; tạo phòng, chọn 1–20 câu và 5–120 giây/câu.
3. Học sinh: cùng IP/cổng/truststore, nhập nickname và mã phòng. Không cần tài khoản học sinh. Nickname không trùng trong phòng.
4. Chọn linh vật rồi Sẵn sàng. Linh vật đã có người dùng sẽ bị khóa; Server xử lý tranh chấp giữa hai máy.
5. Tối thiểu 2, tối đa 10 học sinh; tất cả sẵn sàng thì chủ phòng bắt đầu. Đếm ngược → câu hỏi → đáp án 3 giây → bảng hạng 4,5 giây → câu kế/đích.
6. Cập nhật 24/10/2026: mỗi câu đúng trong hạn nhận 50–100 điểm, trả lời càng nhanh càng nhiều điểm; sai/bỏ trống/trễ +0. Xem `Cham_diem_theo_toc_do.md`. Đồng điểm đồng hạng (1, 1, 3). ACK chỉ xác nhận nộp; kết quả đúng/sai/điểm do Server gửi khi hết giờ. Mỗi người chỉ nộp một lần/câu.
7. Giảng viên xem lịch sử hoặc chơi ván mới. Reset giữ người còn kết nối và avatar, xóa sẵn sàng/điểm, đổi mã phòng. Đóng phòng đưa học sinh ra ngoài. F11 mở màn hình trình chiếu; dùng máy quản lý làm nguồn âm thanh chính.

Mất kết nối giữa ván: học sinh không khôi phục phiên cũ, điểm đã chốt được giữ. Giảng viên đăng nhập lại cùng tài khoản để nhận trạng thái hiện tại. Nếu toàn bộ học sinh rời đi, ván bị hủy và không lưu thành ván hoàn tất. Server khởi động lại không khôi phục ván đang chơi; tài khoản, câu hỏi, các ván đã hoàn thành vẫn nằm trong SQLite.

## 3. Mang sang máy khác, không cần Maven

```powershell
.\scripts\Build-LanBundle.ps1
# Sau khi cache đủ thư viện/plugin có thể thêm -Offline.
```

Script tạo thư mục mới `dist/NetworkQuizBattle-<thời gian>/` gồm `Client/` và `Server/`. Cả hai đã có JAR và dependency Windows; máy đích cần Java 21 x64 trên PATH. Không đổi tên/xóa thư mục lib.

- Copy `Client/` sang từng máy. Nếu build trước Setup thì tự copy truststore công khai vào `Client/certs/client-truststore.p12`. Chạy `./Launch-Lan.ps1 -Role Player` hoặc `-Role Admin`.
- Nếu chạy Server ngay từ source, không cần dùng thư mục Server bundle.
- Nếu muốn triển khai một Server mới từ bundle: vào `Server/`, chạy `./scripts/Setup-Lan.ps1 -ServerAddress <IP-LAN>`, rồi `./Launch-Lan.ps1 -Role Server -InitAdmin`, tùy chọn `-Role Server -Seed admin`, cuối cùng `-Role Server`. Copy truststore của **Server mới này** sang Client.
- Bundle chủ động không chứa database, mật khẩu hay private keystore `server.p12`. Muốn chuyển Server hiện có: dừng Server, sao lưu toàn bộ data và chứng chỉ; tự chuyển chúng chỉ tới máy Server đích, giữ mật khẩu, kiểm tra SAN/IP. Không phát DB/khóa riêng cho học sinh.

## 4. Mạng và TLS

Các máy phải cùng LAN và nhìn thấy nhau. Windows Firewall cần cho phép TCP 5050 inbound trên **Private network**, giới hạn LocalSubnet. Nếu cần, người quản trị tự tạo rule; ứng dụng/script không tự tắt firewall hoặc đổi router. Không cấp inbound Public/Internet. Wi-Fi bật AP/client isolation có thể chặn các máy dù cùng SSID.

```powershell
Test-NetConnection 192.168.1.10 -Port 5050
```

TCP thành công chưa chứng minh TLS đúng. Nếu lỗi chứng chỉ, kiểm tra đúng IP/SAN, ngày giờ máy, hạn chứng chỉ và đúng truststore. Setup hiện dùng chứng chỉ Server tự ký được ghim qua truststore, **không dùng CA riêng**; xác minh fingerprint SHA-256 trực tiếp với người giữ Server khi nhận file. Không tải chứng chỉ từ nguồn lạ. DHCP đổi IP: dành IP ổn định hoặc chủ động cấp lại chứng chỉ và phân phối lại truststore.

TLS mã hóa đường truyền. Mật khẩu lưu bằng PBKDF2-HMAC-SHA256, salt ngẫu nhiên 16 byte, 600.000 vòng; không lưu mật khẩu rõ. SQLite và backup **không được mã hóa toàn bộ**; hạn chế quyền đọc file Server. Đây là đồ án trong LAN tin cậy, không phải hệ thống chống gian lận cấp kỳ thi quốc gia. Chủ phòng vẫn có quyền xem ngân hàng của mình khi thi.

## 5. SFX

17 WAV gốc từ `Network_Quiz_Battle_SFX_V2_17_WAV`, giữ nguyên byte và kiểm hash theo manifest, đóng gói `/ui/audio/`. Không đóng gói file tổng hợp Preview_All_17. Java Sound của JDK phát cục bộ, không gửi âm thanh qua mạng, không cần tải Internet. Tải/phát ngoài JavaFX Application Thread. Có tắt tiếng/âm lượng và chống lặp event; ACK chỉ phát sau Server chấp nhận. Học sinh mặc định tắt âm để cả lớp không phát chồng, giảng viên mặc định bật mức 35%; lưu lựa chọn riêng hai vai trò. QA tự động luôn im lặng, không sửa tùy chọn người dùng. Chất lượng nghe thực tế vẫn cần người dùng thử bằng nút Nghe thử và loa lớp học.

## 6. Kiểm thử và giới hạn nghiệm thu

```powershell
mvn -o -B -ntp verify
mvn -o -B -ntp -f nqb-admin-client/pom.xml exec:java "-Dexec.mainClass=vn.edu.hutech.nqb.admin.LiveLanUiCheck" "-Dexec.classpathScope=test" "-Dexec.args=E:/_HUTECH/DO_AN_LAP_TRINH_MANG/output/lan-ui-qa"
.\scripts\Run-Quiz.ps1 -Role Admin -Capture output/ui-lan-regression
```

LiveLanUiCheck chỉ nằm trong test classpath, tạo DB/chứng chỉ/tài khoản QA tạm, không đụng data thật. Test đi qua form JavaFX và Server TLS sản phẩm, khác Preview. Ảnh lưu trong đường dẫn được chỉ định. Test tích hợp Server dùng 10 TLS Client trên cùng máy: đó không thay thế nghiệm thu nhiều máy vật lý.

Checklist bắt buộc trước buổi nộp/demo:

- [ ] Ít nhất 1 máy Server + 2 máy Client vật lý; cùng phòng, không Internet.
- [ ] 10 người sẵn sàng, hoàn tất ván; bảng hạng nhất quán và animation sau từng câu.
- [ ] Tranh chọn cùng avatar/nickname; Client thua nhận thông báo và vẫn giữ lựa chọn hợp lệ.
- [ ] Rút mạng học sinh, giảng viên; không nộp lại/chấm hai lần, giảng viên đăng nhập lại được.
- [ ] Sai mật khẩu, sai truststore, sai IP/SAN bị từ chối; không tự chuyển Preview/TCP thường.
- [ ] Tắt/mở Server sau ván: tài khoản, ngân hàng riêng, lịch sử còn nguyên.
- [ ] Nghe thử 17 SFX/volume trên loa thực tế; chỉ một nguồn âm chính, giảm chuyển động hoạt động.

Chưa có bộ cài .exe/runtime Java nhúng, nhiều phòng song song, tự dò Server UDP, resume học sinh hoặc tự khôi phục ván khi Server crash. Những mục này không được mô tả là đã hoàn thành.
