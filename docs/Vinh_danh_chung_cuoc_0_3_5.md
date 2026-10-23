# Vinh danh chung cuộc 0.3.5

Màn kết thúc ván chuyển từ thẻ hạng nhất sang bục vinh danh có cột cao thấp, khung linh vật và huy chương vector. Chỉ thay đổi Client JavaFX; không đổi giao thức, thuật toán chấm điểm, tài khoản, Server hoặc schema database.

## Nội dung hiển thị

- Hạng 1 vàng ở giữa, hạng 2 bạc bên trái, hạng 3 đồng bên phải khi có ba hạng phân biệt. Mỗi người có linh vật trong khung tròn, huy chương ghi hạng, nickname và điểm do Server trả về.
- Chiều cao bục biểu thị **thứ hạng**, không phải trục số điểm tỷ lệ. Điểm chính xác được ghi thành chữ ở từng cột. Người cùng hạng có cùng chiều cao/màu/huy chương.
- Giữ tất cả người có rank 1–3, không cắt ba dòng đầu. Ví dụ 1/1/3 có hai huy chương vàng và một đồng, không tạo hạng 2; 1/2/2 không tạo huy chương đồng. Mười người đồng hạng 1 vẫn được vinh danh đầy đủ qua các hàng cuộn.
- Mỗi người là một cột, nên trường hợp nhiều người đồng hạng tự xuống dòng thay vì tràn ngang. Bảng xếp hạng đầy đủ nằm bên dưới; không bỏ người đứng ngoài nhóm huy chương.
- Nút Chơi ván mới/Đóng phòng hoặc Rời phòng đặt sau bục, trước bảng dài. Màn thấp dùng khung/cột nhỏ hơn. Lần đầu đến chung cuộc đưa focus về tiêu đề để không tự cuộn xuống nút phía dưới bục.
- Ván bị hủy không hiện bục hay huy chương. Thông báo chưa lưu kết quả vẫn được giữ. Trình chiếu dùng cùng dữ liệu công khai và cùng bục, không có nút quản lý/mật khẩu.

## Chuyển động và dữ liệu

Avatar/cột xuất hiện bằng opacity và translate trong 300–400 ms, hạng nhất tới sau nhì/ba. Mỗi session/màn chỉ chạy một lần; không lặp theo heartbeat hay khi mở lại màn. Không đổi timer, điểm hoặc hạng để phục vụ hiệu ứng. Khi bật Giảm chuyển động hoặc rời màn, chốt opacity/vị trí cuối và dừng animation ngay.

Không thêm font/ảnh/âm thanh tải từ Internet. Linh vật hiện có và huy chương đều là JavaFX vector. Âm kết thúc ván tiếp tục dùng MATCH_FINISH sẵn có, không nhân đôi âm ở trình chiếu.

## Kiểm thử phát triển

- `PodiumModelTest`: 5 test cho thứ tự 2–1–3, giữ tất cả đồng hạng, không bịa hạng, trường hợp rỗng/2 người/rank không hợp lệ, chiều cao bằng nhau khi đồng hạng và không tự sửa điểm.
- `VictoryPodiumUiCheck`: component JavaFX thật ở 1366×768, 1024×640 và 1280×720; 3 hạng, 2 người, 1/1/3, 0 điểm đồng hạng, 10 người đồng hạng, nickname dài, chuyển động/giảm chuyển động, settle khi tháo component khỏi Scene. Ảnh ở `output/podium-ui-qa-final/`.
- `LiveLanUiCheck`: một Server TLS/SQLite tạm và 3 Client chơi hai câu, điểm chung cuộc 200/100/0; UI nhận 3 cột đúng rank/điểm và lưu lịch sử. Luồng import Excel/Word, auth, khám phá LAN, reset/đóng phòng vẫn đạt. Ảnh nguồn tại `output/podium-live-ui-qa/`.
- Những ca QA dùng dữ liệu tạm/test classpath, không tạo thêm tài khoản hoặc câu hỏi trong Server cá nhân. Không coi kiểm thử cùng máy là nghiệm thu LAN nhiều máy vật lý ở trường.

## Đóng gói và cập nhật

- Build sạch có **84 test**, không failure/error (shared 17, client-core 32, server 22, admin-client 13). Bộ phát hành `dist/QuizBattle-Windows-20261022-114430/`, SHA256SUMS đối chiếu đạt.
- Client tại `C:/Users/starg/AppData/Local/QuizBattle` đã nâng lên 0.3.5 (installer exit 0); hash JAR đã cài khớp bản đóng gói. Người dùng đã cho phép đóng Client để cập nhật. Bản cũ/profile công khai sao lưu ở `backups/podium-client-20261022-114705/`.
- Chạy lại `LiveLanUiCheck` bằng **runtime/JAR đã cài**, loại Java/Maven hệ thống khỏi PATH: `LIVE_LAN_UI_PASS` và `QUESTION_IMPORT_UI_PASS`; đã kiểm tra ảnh chung cuộc ba người với huy chương/cột đúng hạng, điểm 200/100/0. Ảnh tại `output/installed-runtime-podium-qa/`. Không dùng database Server cá nhân cho QA.
- Không chạy script cập nhật Server: tiến trình đang nghe `10.12.89.105:5050` vẫn PID 24720 trước và sau cài Client. Không ghi đè `data/`, `config/`, `certs/` hoặc JAR Server cá nhân. Bản này dùng được với Server 0.3.4 đang chạy.
- Mở lại QuizBattle để dùng bản mới. Máy khác muốn thấy bục vinh danh cũng cần cài Client mới, không phải khởi tạo lại Server.
