# UI v2 — Checklist tích hợp

Cập nhật 16/10/2026: checklist bên dưới là mốc Preview trước khi có backend. Đã nối UI với Server TLS/SQLite và 17 WAV người dùng cung cấp, kiểm thử socket/UI thật trên cùng máy. Xem `Nghiem_thu_Server_LAN.md`; chưa nghiệm thu các máy LAN vật lý.

Ngày 14/10/2026. Thay đổi từ phản hồi: phong cách vui hơn, ít chữ dư, bảng hạng từng câu và avatar duy nhất trong phòng. Không đổi Java 21/JavaFX, LAN/TLS, tài khoản Admin/giảng viên hoặc guest nickname. Không sửa Word và không tạo âm thanh.

## Đã triển khai trong bản xem thử

- Nền cobalt, accent lime, typography lớn; linh vật vector tự vẽ, không sao chép tài nguyên Kahoot.
- Bộ 24 linh vật có silhouette/phụ kiện riêng. Đổi trong phòng trước ready. Nút có chủ bị khóa, lựa chọn hiện tại có dấu tick. Lỗi đổi không xóa avatar cũ; đóng phòng giải phóng roster.
- Hai bước riêng sau mỗi câu: đáp án → bảng hạng. Hạng từ nguồn dữ liệu, không tự chấm hoặc xếp hạng ở JavaFX. Đổi vị trí bằng translate, có mũi tên tăng/giảm hạng, đồng điểm giữ đồng hạng. Giảm chuyển động đặt thẳng vị trí cuối. Projector có cùng màn hạng nhưng không thông tin cá nhân riêng tư.
- Bỏ chữ giải thích công nghệ khỏi màn chơi, giữ badge Xem thử và tooltip. Chưa có SFX.

## Kiểm thử tự động

- Maven: 40 test (12 shared, 28 core). Các test mới gồm 12 luồng tranh một avatar, chỉ một thắng; lỗi giữ avatar cũ; giải phóng/độc lập phòng; catalog/full room; quyền/ready; đóng phòng; đủ chuỗi 2 câu QUESTION → QUESTION_RESULT → ROUND_RANKING và GAME_OVER.
- UiCapture: 41 bước, hai kích thước Scene 1366×768 và 1024×640. Giữ regression ngân hàng/form/trùng/filter/TLS-error; thêm chọn avatar, khóa avatar, thả avatar cũ, rank khởi đầu ở vị trí trước/settle/giảm chuyển động/projector. Ảnh tại output/ui-preview-v2. Các frame 37–38 cho biết hai thời điểm của chuyển động; ảnh tĩnh không thay thế nghiệm thu cảm giác animation trực tiếp.

## Gate chưa đạt — không ghi Pass

- ServerMain chưa mở socket. Preview giữa hai Client độc lập; AvatarRoster là logic dùng lại cho Game Worker, không phải Server đã tích hợp. Chưa có PBKDF2/SQLite/role-owner handlers.
- Cần test hai máy tranh cùng avatar, cập nhật khóa trên mọi Player, heartbeat/disconnect, khôi phục điều khiển của giảng viên và room reset mã mới. Preview reset hiện giữ mã mẫu QZ7K2, không chứng minh mục tiêu đổi mã.
- Cần đồng bộ QUESTION_RESULT/ROUND_RANKING trên Teacher + Player + projector qua TLS, chặn mọi mutation trái phase, loại event cũ, chấm/lưu một lần, tie-break ổn định.
- Chưa nghiệm thu máy chiếu vật lý, DPI 125%/150%, screen reader, Tab toàn bộ, 10 Player hoặc audio.

## Xem trực tiếp

```powershell
.\scripts\Run-Quiz.ps1 -Role Player -Preview
```

Chọn Đổi linh vật → Xong → Sẵn sàng → Chạy thử một ván mẫu. Câu 2 trong fixture có thay đổi vị trí giữa các đối thủ; điểm đối thủ là dữ liệu mô phỏng để xem chuyển động, không phải người chơi LAN thật.
