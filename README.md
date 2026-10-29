# Network Quiz Battle Java LAN

**Đợt B — thu mẫu khảo sát có tọa độ:** thêm **Tùy chọn → Khảo sát Wi-Fi**, tạo khu/điểm và tọa độ server, thu nhiều scan tại mỗi điểm, xác nhận hoặc loại lượt, lưu SQLite offline và nhập/xuất JSON. Giảng viên/Admin có thể đồng bộ dữ liệu lên Server qua TLS, thử lại sau mất mạng không tạo bản sao. Xem [hướng dẫn khảo sát](docs/Khao_sat_WiFi_fingerprinting.md). Chưa triển khai KNN/WKNN hoặc hiển thị khoảng cách client–server từ khảo sát; cần bộ dữ liệu thực địa tiếp theo.

**Đợt A — mạng riêng và chuẩn bị fingerprinting:** thêm chọn máy chủ bằng IP/hostname (dùng được với Tailscale), tự khôi phục phiên thí sinh khi mất mạng ngắn và **Tùy chọn → Chẩn đoán quét Wi-Fi** để đọc nhiều BSSID, phân biệt dữ liệu mới/cũ, tự lưu danh mục quan sát và xuất JSON. Xem [hướng dẫn đợt A và phần cần nghiệm thu tại trường](docs/Ket_noi_mang_rieng_va_chan_doan_WiFi.md). Chưa triển khai định vị theo tọa độ khảo sát.

**Theo dõi AP và hướng di chuyển:** nhận diện BSSID khi client đi qua các AP cùng SSID, cấu hình tên/vị trí AP tại **Giám sát → Cấu hình AP**, xem lịch sử và hướng suy đoán trong tab **AP & hướng di chuyển**. Xem [hướng dẫn thiết lập và demo](docs/Nhan_dien_AP_va_huong_di_chuyen.md).

Dự án môn Lập trình mạng của nhóm VUA, lớp 23DTHA3. Toàn bộ ứng dụng dùng **Java 21**; chạy qua **LAN hoặc địa chỉ mạng riêng**. Tailscale là phần mềm mạng cài ngoài ứng dụng khi cần đi qua mạng trường. Một máy chạy Server, các máy khác chạy Client giảng viên/Admin hoặc học sinh.

## Thành viên nhóm bắt đầu tại đây

Xem **[Hướng dẫn clone, build, chạy và làm việc theo nhánh](docs/PHAT_TRIEN_NHOM.md)**. Cần Windows, JDK 21 x64 và Maven 3.9.x; chạy `mvn -B -ntp install` tại thư mục gốc. Repo chứa mã nguồn/tài nguyên, không chứa tài khoản mặc định, private key, database hoặc bộ cài cá nhân. Tự tạo `config/server.properties` và `client.properties` từ các file mẫu, rồi thiết lập Server phát triển theo hướng dẫn. Không dùng lệnh Preview/Seed trong các ghi chép lịch sử bên dưới.

Hỗ trợ đăng nhập Admin/giảng viên, học sinh nickname/linh vật; ngân hàng riêng, bộ câu hỏi, lọc/trùng/import Excel–Word; chọn đề cho phòng, dùng lại đề và bảng điểm từ lịch sử; chơi LAN thời gian thực, bảng hạng sau từng câu và bục vinh danh, SFX và nhạc nền offline. Tự dò mạng hiện tại và xác minh khóa Server độc lập IP DHCP: đổi điểm phát giữa các buổi không cần build lại Client. Xem [LAN tự động](docs/LAN_tu_dong_0_4_0.md).

**Bản 0.5.0 — tích hợp chọn lọc từ `feature/viet-my`:** chọn câu qua nhiều trang, tạo/lọc bộ riêng, nhập file thành bộ, chọn bộ khi mở phòng và dùng lại bản đề đã lưu. IP kết nối chỉ hiển thị cho chủ phòng/Admin trong giám sát hiện có. Giữ chấm đúng 100 điểm, cho phép nhiều Client cùng máy, LAN tự động và xin quyền Wi-Fi. Database dùng schema 4, nhận diện cả hai biến thể schema 2 của các nhánh. Xem [hướng dẫn bộ câu hỏi và lịch sử](docs/Bo_cau_hoi_va_lich_su_0_5_0.md) và [báo cáo tích hợp](docs/Tich_hop_viet_my_0_5_0.md).

**Bản 0.5.1 — tốc độ và chuỗi đúng:** đúng được 100 điểm cơ bản, cộng tối đa 100 điểm tốc độ và tối đa 100 điểm chuỗi; sai/bỏ lỡ ngắt chuỗi. Server đo và chấm, Client hiển thị số giây nhỏ cạnh nickname sau khi nhận đáp án. Áp dụng cho ván mới, giữ nguyên điểm lịch sử. Xem [quy tắc điểm và hiển thị](docs/Toc_do_va_streak_0_5_1.md). Quy định cố định 100 điểm trong các ghi chép bản cũ đã được thay thế theo yêu cầu mới.

**Bản 0.6.0 — sơ đồ LAN và làm mới số đo:** giảng viên/Admin xem ping và mét ước lượng trên sơ đồ điểm phát Wi-Fi; có trình chiếu và chế độ Danh sách trong Giám sát. Nút Làm mới số đo yêu cầu 5 mẫu RSSI mới, dùng được khi đang thi. Chung cuộc bố trí bục và bảng hạng song song khi đủ rộng. Giữ dữ liệu/chứng chỉ/schema 4 và điểm tốc độ/streak. Xem [hướng dẫn và demo](docs/So_do_LAN_va_lam_moi_0_6_0.md).

**Bản 0.6.2 — một Client trong mỗi hồ sơ Windows:** mở QuizBattle lần nữa sẽ đưa cửa sổ đang chạy lên trước, khôi phục nếu thu nhỏ và giữ nguyên phiên đang dùng. Áp dụng chung cho mọi vai trò và cả hai điểm vào Admin/Player; thay thế việc cho phép nhiều Client cùng máy ở bản cũ. Server chạy độc lập. Xem [cơ chế và cách kiểm tra](docs/Mot_Client_tren_Windows_0_6_2.md).

## Bản dùng chính trên máy tác giả — không có trong Git

Từ 22/10/2026, dùng duy nhất `dist/QuizBattle/`: chạy [Server/start.bat](dist/QuizBattle/Server/start.bat), gửi [Client/QuizBattle-Setup.exe](dist/QuizBattle/Client/QuizBattle-Setup.exe) cho máy khác. Xem [hướng dẫn ngắn](dist/QuizBattle/HUONG_DAN.txt). Bộ cài Client **0.6.2** đi cùng Server đã nâng cấp, chứng chỉ khớp và dữ liệu cũ. Vẫn hỗ trợ xin quyền Windows trực tiếp; xem [Cấp quyền Wi-Fi](docs/Cap_quyen_WiFi_0_4_1.md). Các đường dẫn timestamp trong báo cáo trước đây là lịch sử.

Từ 0.4.0, giữ Server và Client ở chế độ `auto`: IP DHCP được dò lại, danh tính TLS được kiểm tra bằng khóa tin cậy đã biết, không cần đổi chứng chỉ/bộ cài theo IP. Các IP và thao tác bind cụ thể trong báo cáo `docs/Cap_nhat_mang_4G_20261022.md` là lịch sử; xem [LAN tự động](docs/LAN_tu_dong_0_4_0.md).

12 bản cũ đã được chuyển nguyên vẹn sang `backups/dist-archive-20261022-123050/`, không xóa hoặc tự gộp database. Không chia sẻ thư mục Server/backups vì chứa khóa riêng và dữ liệu người dùng. Kiểm tra đầy đủ SHA256 237 tệp Server sau di chuyển; Client đang cài trên máy không bị thay đổi bởi việc dọn dist.

## Tình trạng hiện tại

**Bản 0.3.8 — ước lượng khoảng cách tới điểm phát Wi-Fi:** đọc tín hiệu Wi-Fi trên Windows qua JNA (`wlanapi.dll`), chọn điểm phát làm mốc theo BSSID + băng tần, hiệu chuẩn hai bước (1 m và 5 m, 30 giây/bước) trên từng laptop và lưu hồ sơ cục bộ. Server tự tính khoảng cách ước lượng theo mô hình suy hao logarithm từ trung vị 5 mẫu gần nhất, hiển thị nhãn “ước lượng” trên cửa sổ **Giám sát** riêng của giảng viên/Admin (giữ mở khi chuyển câu hỏi, không hiện trên màn trình chiếu). Khi đang thi, cảnh báo vượt ngưỡng (sau 3 báo cáo liên tiếp) hoặc mất dữ liệu (> 15 giây) không tự loại người chơi hay trừ điểm. Nâng SQLite lên schema 2, lưu tóm tắt và nhật ký cảnh báo qua kết nối/hàng đợi riêng. Xem [Hướng dẫn và báo cáo giám sát Wi-Fi](docs/Giam_sat_khoang_cach_WiFi_0_3_8.md).

**Bản 0.3.6 — nhạc nền:** hai WAV người dùng cung cấp được đóng gói nguyên bản vào Client. BGM lặp từ đếm ngược đến hết các câu/bảng hạng; khi chung cuộc chuyển sang High Score Glory phát một lần, không chồng hai bản nhạc. Hủy ván/rời phòng/mất kết nối/reset/đóng ứng dụng dừng nhạc. Tùy chọn có bật/tắt và âm lượng nhạc riêng với SFX, lưu theo vai trò; mặc định giảng viên/Admin bật 20%, học sinh tắt để tránh nhiều loa cùng phát. Không thay Server hoặc dữ liệu. Xem `docs/Nhac_nen_0_3_6.md`.

**Bản 0.3.5 — bục vinh danh chung cuộc:** cột cao thấp theo hạng, khung linh vật và huy chương vector vàng/bạc/đồng cho các hạng 1–3. Giữ nguyên hạng Server (đồng hạng nhận cùng loại huy chương, không lấy ba dòng đầu rồi loại người đồng hạng); bảng đầy đủ ở bên dưới. Có hiệu ứng xuất hiện ngắn, hỗ trợ giảm chuyển động, màn hình nhỏ và trình chiếu. Đây là bục theo thứ hạng, không phải biểu đồ có trục điểm tỷ lệ. Chỉ thay Client; không đổi giao thức, Server hoặc database.

**Bản 0.3.4 — nhập câu hỏi Excel/Word:** Ngân hàng → Nhập từ Excel / Word, xem trước đầy đủ câu hỏi/đáp án, báo lỗi và trùng trong tệp/ngân hàng, chọn câu rồi xác nhận. Có Xem mẫu / Tải mẫu để lưu `.xlsx` hoặc `.docx` trống ngay trên máy. Chỉ lưu vào ngân hàng tài khoản đang đăng nhập, không ghi đè câu cũ; lượt nhập nguyên tử và chỉ thực hiện khi phòng đang chờ. Toàn bộ đọc/tạo tệp và lưu dữ liệu vẫn bằng Java, offline trong LAN. Xem [Hướng dẫn nhập câu hỏi](docs/Nhap_cau_hoi_Excel_Word.md).

**Bản 0.3.3 — hai cách vào phòng, không nhập IP/cổng trên Client:** thêm tìm máy chủ bằng UDP LAN, danh sách phòng lấy qua TLS đã xác minh, học sinh chọn phòng hoặc nhập mã 6 ký tự. Admin/giảng viên tự tìm máy chủ để đăng nhập/đăng ký. Cấu hình mạng chỉ còn ở phía vận hành, không có form IP/cổng trên Client. Xem [Tìm phòng LAN và nhập mã](docs/Tim_phong_LAN_va_nhap_ma.md). Các hướng dẫn nhập IP/cổng ở Client dưới đây thuộc các bản cũ.

**Bản 0.3.2 — đăng ký và TLS tự động:** đã bỏ chọn file/nhập mật khẩu chứng chỉ khỏi form. Bộ cài Client ghép với chứng chỉ công khai của Server; giảng viên tự đăng ký rồi vào sử dụng, Admin vẫn khởi tạo tại Server, học sinh vẫn dùng nickname. TLS/IP vẫn được xác minh, không bỏ mã hóa. Xem [Đăng ký và TLS tự động](docs/Dang_ky_va_TLS_tu_dong.md). Hướng dẫn yêu cầu người chơi chọn truststore trong các tài liệu cũ không còn áp dụng cho bản này.

**Khởi chạy Server bằng config:** đã thêm `Server/config/server.json` và `configure.bat`; sau một lần lưu cấu hình, `start.bat` khởi động không hỏi lại. Mật khẩu TLS lưu bằng Windows DPAPI; bootstrap Admin chỉ dùng khi chưa có tài khoản. Xem [Cấu hình Server tự động](docs/Cau_hinh_Server_tu_dong.md).

**Bản 0.3.1 ngày 22/10:** đã gỡ chế độ xem thử và câu/tài khoản giả khỏi runtime. Client luôn mở form “Bạn là ai?” chưa có vai trò; Admin/giảng viên đăng nhập thật, học sinh dùng nickname/mã phòng. Server khởi tạo ngân hàng rỗng, không còn seed demo. Xem [Đăng nhập và test vai trò](docs/Dang_nhap_va_test_vai_tro.md) cho bộ chạy mới và thao tác test trên cùng máy. Các mô tả Preview bên dưới ghi nhận giai đoạn trước, không dùng để chạy bản hiện tại.

**Bổ sung đóng gói ngày 22/10/2026:** có script tạo `QuizBattle-Setup.exe` cho Client và thư mục Server có `start.bat`, cả hai kèm Java runtime. Xem [Bộ cài Windows](docs/Bo_cai_Windows.md). Các đoạn “cần cài JDK” bên dưới dành cho source/bundle JAR cũ, không áp dụng bộ phát hành Windows mới.

Ngày 16/10/2026: đã nối **JavaFX với Server TLS thật và SQLite**: đăng nhập Admin/giảng viên, cấp/khóa/reset tài khoản, ngân hàng riêng/lọc/trùng, phòng chờ, avatar độc quyền, nhận đáp án, chấm điểm và lưu lịch sử. Font tiếng Việt và 17 WAV do người dùng cung cấp đều được đóng gói offline. Có âm lượng/tắt tiếng; học sinh mặc định tắt âm.

UI v2: sân chơi cobalt/lime, 24 linh vật vector; Server khóa avatar nguyên tử giữa các Client. Sau mỗi câu: đáp án 3 giây → bảng hạng 4,5 giây với hiệu ứng đổi vị trí/mũi tên → câu kế hoặc chung cuộc. Có giảm chuyển động. Server chốt deadline/điểm/hạng, không tin điểm hoặc danh tính do Client tự khai.

**Đã kiểm thử tích hợp trên cùng máy, chưa nghiệm thu LAN nhiều máy vật lý.** Server chạy TLS Socket, auth worker PBKDF2, game worker tuần tự và database SQLite. Học sinh dùng nickname/mã phòng, không có tài khoản. Preview vẫn riêng biệt, gắn nhãn và không được dùng để kết luận kết nối LAN; lỗi TLS không chuyển sang Preview. Đợt này không sửa đề cương Word.

**Bắt đầu tại [Hướng dẫn chạy LAN](docs/Chay_LAN_va_kiem_thu.md).** Có script Setup-Lan, Start-Server, Build-LanBundle và launcher Client/Server không cần Maven. Cần tự nhập mật khẩu Admin/keystore và cấp truststore cho Client; chưa tạo thông tin đăng nhập thật hoặc thay đổi firewall trên máy của bạn.

Hướng dẫn/giới hạn/QA: `docs/UI_JavaFX_Trien_khai.md`. Thiết kế: `design-system/network-quiz-battle/MASTER.md`. Ảnh JavaFX thật mới: `output/ui-preview-v2/`; giữ ảnh cũ trong `output/ui-preview/`.

Mã Python và kết quả kiểm thử cũ được lưu trong `archive/python-prototype/`. Không dùng kết quả test Python để kết luận chất lượng bản Java. `.venv/` chỉ được giữ lại từ bản cũ, không cần cho sản phẩm Java.

## Kiến trúc và hồ sơ

- Bắt đầu đọc ngữ cảnh mới: `docs/Boi_canh_va_yeu_cau_Java_LAN.md`.
- Kế hoạch theo milestone/gate: `docs/Ke_hoach_trien_khai_Java_LAN.md`.
- Kiến trúc hiện hành: `docs/Kien_truc_Network_Quiz_Battle_Java_LAN.md`.
- Giao thức: `docs/Giao_thuc_Java_LAN.md`.
- Đề cương nộp giảng viên: `output/documents/De_cuong_Network_Quiz_Battle_Java_LAN_Nop_Giang_Vien.docx`.
- Hai đặc tả Markdown gốc ở thư mục gốc là bản lịch sử, không phải quyết định triển khai hiện hành.

## Cấu trúc

```text
pom.xml                  Maven reactor, Java 21 và UTF-8
nqb-shared/              protocol, NDJSON, model dùng chung
nqb-server/              TLS Server, auth/role/owner, SQLite, phòng, deadline/chấm điểm
nqb-client-core/         TLS gateway, answer state, fixture Preview; không phụ thuộc JavaFX
nqb-admin-client/        presentation JavaFX dùng chung, launcher quản lý
nqb-player-client/       launcher học sinh, tái sử dụng presentation JavaFX
config/                  cấu hình mẫu, không chứa mật khẩu thật
docs/                    kiến trúc, giao thức và lộ trình
output/documents/        đề cương Word
archive/                 bản Python cũ, không tham gia build Java
```

Các module dùng cấu trúc Maven chuẩn `src/main/java`, `src/main/resources`, `src/test/java` khi có nguồn tương ứng. Không dùng JPMS/module-info ở giai đoạn đầu; Maven module là đơn vị chia dự án, không phải Java module.

## Build và test

Cài JDK 21 và Maven 3.9.x. Mở PowerShell tại thư mục gốc:

```powershell
java -version
mvn -version
mvn -B -ntp verify
```

Lần đầu Maven cần Internet để tải thư viện. Sau khi tải đủ, việc chạy sản phẩm LAN không cần Internet. Không cần cài Python/PySide6 hay dịch vụ cơ sở dữ liệu riêng.

Để xem UI (dữ liệu mẫu được gắn nhãn):

```powershell
mvn -B -ntp install
.\scripts\Run-Quiz.ps1 -Role Admin -Preview
.\scripts\Run-Quiz.ps1 -Role Admin -Preview -PreviewRole Admin
.\scripts\Run-Quiz.ps1 -Role Player -Preview
```

Chạy từng Client trong terminal riêng. Preview học sinh: Sẵn sàng → Chạy thử một ván mẫu. Preview quản lý: cấu hình/bắt đầu, ngân hàng, lọc, thêm/sửa câu và cảnh báo trùng. Preview không phải Server LAN và không ghi dữ liệu lâu dài. F11 hoặc nút Trình chiếu ở quản lý mở Stage công khai; Esc thoát fullscreen, không hủy ván.

Bỏ `-Preview` mở form kết nối Server thật. `-Role Server` gọi script nhập mật khẩu keystore rồi mở Server; cần Setup và InitAdmin trước. Script dùng Maven offline sau bước install. JAR hiện tại là JAR mỏng; dùng launcher trong bundle (JAR + dependency), không dùng `java -jar`. Máy đích cần JDK 21 x64; chưa nhúng runtime/bộ cài .exe.

QA opt-in chụp 41 trạng thái UI và tự đóng ứng dụng:

```powershell
.\scripts\Run-Quiz.ps1 -Role Admin -Capture tmp/ui-qa/1366 -Width 1366 -Height 768
```

## Cách triển khai LAN

1. Chọn một máy làm Server; không tắt máy hoặc cho máy ngủ khi đang thi.
2. Giữ `server.bind=auto` để Server lắng nghe trên các địa chỉ hiện tại. Client dùng `server.host=auto` và tự dò mỗi lần mở/làm mới, không đóng sẵn IP hotspot.
3. MSI và các Client cùng kết nối vào điểm phát điện thoại; chạy Server, mở Client rồi chọn phòng hoặc nhập mã. Đổi IP/điện thoại giữa các buổi không cần build lại Client.
4. Chạy `Server/allow-lan.bat` một lần với quyền quản trị Windows để thêm rule TCP/UDP giới hạn theo runtime và LocalSubnet, áp dụng các network profile. Quy tắc không gắn IP cụ thể. Điểm phát phải cho phép các máy giao tiếp với nhau.
5. Chạy Setup-Lan một lần khi khởi tạo Server để tạo keystore/chứng chỉ và Client truststore công khai. Client 0.4.0+ kiểm tra khóa công khai Server đã được phân phối, thời hạn chứng chỉ và kiểm tra TLS của JDK; không xác thực danh tính bằng IP DHCP. Xác minh fingerprint khi phân phối. Giữ nguyên certs khi đổi mạng; không dùng trust-all hoặc hạ xuống TCP rõ.
6. Bootstrap Admin cục bộ, Admin cấp tài khoản giảng viên; user đổi mật khẩu tạm trước CRUD/tạo phòng. Chọn vai trò trên UI không cấp quyền. Học sinh chỉ cần nickname/mã phòng.
7. Client quản lý có thể chạy cùng Server hoặc máy khác nhưng luôn gửi API qua socket, không mở DB trực tiếp. Có nhiều tài khoản nhưng một chủ phòng tại một thời điểm; ngân hàng/lịch sử giữ theo tài khoản trên cùng Server.

TLS mã hóa đường truyền, PBKDF2/salt bảo vệ mật khẩu lưu; SQLite/backup không tự được mã hóa. Hạn chế quyền file Server, không mở ra Internet. Cần nghiệm thu checklist nhiều máy vật lý trong hướng dẫn; kiểm thử loopback không chứng minh cấu hình firewall/Wi-Fi của lớp.

Các phần backend và tích hợp UI đã triển khai; chưa đánh dấu toàn bộ gate P1–P8 hoàn thành vì còn nghiệm thu nhiều máy, DPI/máy chiếu và SFX trên loa thực tế. Test 10 Client TLS cùng máy không phải benchmark hay bằng chứng tải mọi LAN.

Test tự động: framing/TLS/answer state, Server với SQLite thật, auth/owner/khóa/reset, trùng câu, tranh avatar, 10 Client, chấm/hạng/lưu sau restart và hash/định dạng 17 WAV. Kiểm thử `LiveLanUiCheck` đi qua form JavaFX thật → TLS → Server, có ảnh trong `output/lan-ui-qa/`. Hồi quy UI 41 trạng thái trong `output/ui-lan-regression/` được chạy từ bundle. Số test và kết quả cập nhật ở `docs/Nghiem_thu_Server_LAN.md`.
