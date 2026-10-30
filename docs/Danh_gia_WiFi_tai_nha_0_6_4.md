# Đánh giá Wi-Fi tại nhà — Client 0.6.4

Màn này chạy **offline, không cần Server, Internet, Tailscale hoặc Wi-Fi trường**. Nó giúp thử luồng khảo sát/đánh giá và cách đọc kết quả, chưa phải định vị trực tiếp khi thi.

## Mở bản chạy ngay

1. Giải nén toàn bộ [QuizBattle-Portable-0.6.4.zip](../dist/QuizBattle/Client/QuizBattle-Portable-0.6.4.zip).
2. Đóng Client cũ nếu đang mở, rồi chạy `QuizBattle/QuizBattle.exe` trong thư mục vừa giải nén. Không cần setup hoặc cài Java; giữ thư mục `app` và `runtime` cạnh EXE.
3. Ở màn đầu, mở **Tùy chọn → Đánh giá định vị Wi-Fi**. Không cần đăng nhập.
4. Chọn **Thử mô phỏng tại nhà → Đánh giá**.

Portable không có nghĩa là mọi dữ liệu đều nằm trong thư mục EXE: ứng dụng vẫn dùng hồ sơ Windows để lưu tùy chọn và khảo sát thật. Bản mới dùng cùng khóa một Client với bản đã cài. ZIP này chỉ nâng Client; Server đang dùng không được thay mã hay dữ liệu trong đợt này.

## Có gì để thử

- Bộ mô phỏng có 5 điểm huấn luyện trong phòng/hành lang, 3 lượt kiểm tra, SSID `DEMO-OFFLINE`. Tọa độ, tín hiệu và thời gian là dữ liệu tổng hợp cố định, không phải quét Wi-Fi trong nhà hay dữ liệu HUTECH.
- Với K=2, có 2 lượt định vị được và 1 lượt thiếu radio trùng bị từ chối. Xem cả **tỷ lệ từ chối** bên cạnh trung vị/P90. Giá trị `—` nghĩa là không có kết quả, không phải khoảng cách 0 m.
- Chọn một lượt ở bảng **Kết quả**, mở **Sơ đồ**. Vòng tròn là tọa độ đã biết, dấu × là ước lượng, hình vuông là Server. Đường liền biểu diễn khoảng cách thẳng Client–Server, đường đứt biểu diễn sai số vị trí. Hai trục dùng cùng tỷ lệ mét; không vẽ vị trí AP hoặc tường chưa biết.
- Đổi KNN/WKNN để xem từng phương pháp trên cùng lượt. Đổi K hoặc đánh dấu lượt kiểm tra sẽ xóa báo cáo cũ và yêu cầu tính lại.
- Chọn lượt bị từ chối: vị trí ước lượng/đường tới Server cũ biến mất. Chỉ giữ điểm đã biết để đối chiếu, cùng lý do từ chối.
- **Xuất báo cáo JSON** có cả hai phương pháp, ID các lượt, tham số, hash dữ liệu và cờ `datasetKind=SYNTHETIC_DEMO`. Không ghi đè file đã tồn tại. Nhãn mô phỏng luôn hiện trên giao diện; bộ mẫu không ghi vào kho khảo sát và không gửi Server.

Kết quả mô phỏng chỉ kiểm tra phần mềm, **không chứng minh độ chính xác tại trường**. Không dùng nó làm số liệu thực nghiệm trong báo cáo đồ án.

## Khi có dữ liệu thật

Từ **Khảo sát Wi-Fi → chọn khu → Lượt đã lưu → Đánh giá định vị**, mở dữ liệu của đúng phiên bản khu. Hoặc chọn **Mở khảo sát JSON** với file xuất từ màn khảo sát. Chỉ các lượt ACCEPTED/Đã xác nhận được đưa vào bảng; phần mềm không tự chọn lượt kiểm tra của bạn.

Đánh dấu toàn bộ lượt kiểm tra bằng checkbox, hoặc chọn dòng rồi nhấn **Chọn / bỏ lượt đang xem** / phím Space. Các lượt không đánh dấu là huấn luyện. Cần ít nhất một lượt kiểm tra và hai điểm huấn luyện khác nhau. Mẫu bị loại/đang chờ xác nhận không được dùng. Đọc thêm [quy trình tách dữ liệu và nguồn thuật toán](Nguon_mo_va_danh_gia_fingerprinting.md).

File do người dùng cung cấp được ghi `datasetKind=USER_SURVEY`; nhãn này chỉ mô tả nguồn nhập, không xác nhận dữ liệu thực địa hay độ chính xác của tọa độ. Báo cáo mô phỏng và báo cáo người dùng đều có lời giải thích tương ứng.

## Phần mềm và kiểm chứng

Tái sử dụng `FingerprintEvaluation` và lõi KNN/WKNN từ Anyplace/MIT đã tích hợp. Màn JavaFX, sơ đồ, dữ liệu giả lập và thao tác file là phần nối của QuizBattle; không thay công thức tính toán. Đọc file, kiểm tra dữ liệu, tính toán và xuất báo cáo chạy ngoài luồng JavaFX. Đóng cửa sổ sẽ bỏ callback cũ; không đưa kết quả từ cửa sổ trước sang cửa sổ mở lại.

Đã bổ sung test dữ liệu mô phỏng, phân tách lượt, cờ nguồn báo cáo, không ghi đè file, JSON lỗi/nhiều phiên bản/quá 32 MiB. Kiểm tra UI bằng `LiveLanUiCheck ... phase-c` tại 1024×640 và 1366×768: mở qua Tùy chọn, chọn mô phỏng, đánh giá, sơ đồ/ca từ chối, xuất, đổi K, đọc JSON và đóng khi còn công việc. Không tạo database khảo sát khi chỉ dùng mô phỏng.

Nghiệm thu ngày 30/10/2026: toàn dự án 213 test đạt, không lỗi/bỏ qua; build thành công. Luồng UI cũng đạt khi chạy bằng Java runtime và JAR nằm trong app-image Portable. Các JAR Client/Core/Shared trong ZIP khớp SHA-256 với bản build. ZIP khoảng 103,5 MiB; checksum đi kèm cùng thư mục phát hành. Chưa cài thay bản Client hiện tại và chưa nâng Server cá nhân.

Đóng gói lại bằng JDK 21/Maven, với kho chứng chỉ **công khai** của Server:

```powershell
.\scripts\Build-PortableClient.ps1 -Version 0.6.4 -ClientTruststore '.\dist\QuizBattle\Server\certs\client-truststore.p12'
```

Script mặc định chạy test, nhúng Java runtime, tạo ZIP và SHA-256, không cần WiX. Nó từ chối ghi đè bản ZIP đã tồn tại và không đóng gói database/khóa riêng. `-SkipTests` chỉ dùng khi vừa chạy bộ kiểm thử tương ứng thành công. Bản app-image giữ tại thư mục `build/portable-*` để kiểm tra; không cài hoặc thay ứng dụng đang cài trên máy.
