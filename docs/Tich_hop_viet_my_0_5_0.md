# Tích hợp chọn lọc feature/viet-my — 0.5.0

Rà soát nguồn `origin/feature/viet-my` đến commit `434c4c4db4368041a9fcc5f21cd52ebe1bfe65af`, đã fetch lại ngày 29/10/2026. Phần ngân hàng/bộ đề/lịch sử được tiếp nhận chủ yếu từ `6043968` và `01429cb` trong mốc `2bab171`. Bốn commit mới sau mốc đó đã được đối chiếu riêng. Đây là chuyển và điều chỉnh các tính năng vào mã hiện tại, không phải merge nguyên nhánh. Nhánh làm việc cục bộ: `codex/integrate-viet-my`. Không tự push lên GitHub.

## Phần đã tiếp nhận

| Chức năng | Cách hòa vào dự án |
| --- | --- |
| Bộ câu hỏi riêng | List/create/delete, chọn theo ID hoặc bộ lọc; giới hạn 500 và kiểm tra owner ở Server |
| Ngân hàng | Tick nhiều trang, lọc theo bộ, chi tiết đáp án, thao tác bàn phím; giữ điều hướng và màu hiện tại |
| Import | Tên bộ theo file, thêm vào bộ cùng tên trong cùng transaction; giữ preview/trùng/chọn dòng |
| Nguồn đề | Chọn bộ/toàn ngân hàng, xác minh số câu ở cấu hình và START_GAME |
| Lịch sử | Lưu snapshot đề, thời gian; xem bảng điểm/bục và cấu hình lại từ snapshot riêng |
| IP kết nối | Lấy từ socket thật, chỉ chủ phòng/Admin; thêm cột trong cửa sổ giám sát Wi-Fi hiện có |
| Quy tắc UI | AGENTS và quy tắc giữ giao diện, không thay hệ điều hướng |

## Phần không đưa vào

- Chặn người chơi cùng IP: không phù hợp yêu cầu thử nhiều Client trên máy MSI; IP cũng không định danh tài khoản.
- Chấm điểm theo tốc độ: giữ quy tắc hiện hành đúng 100 điểm/câu.
- Công cụ LAN range dùng nhập khoảng cách/RTT, màn arena/commander và launcher nguồn riêng: trùng hoặc khác hướng với giám sát RSSI, UI và bộ chạy đã có.
- Transport/config cũ: giữ LAN auto, TLS pin khóa Server độc lập IP và xin quyền Windows trực tiếp.
- Theme sáng/tối và thay đổi CSS toàn hệ thống trong `434c4c4`: không kéo cả hệ giao diện mới vào bản tích hợp; giữ UI hiện hành theo AGENTS.
- Thay đổi nhạc của `ade6729` bỏ track VICTORY ở FINISHED: không áp dụng vì yêu cầu hiện tại vẫn phát **High Score Glory** khi hoàn tất. Các thay đổi SFX đi kèm cũng không nằm trong phần tiếp nhận lần này.
- Điều khiển lượt đo LAN từ xa và nhập mốc mét của `f82468a`/`8ac2204`: thuộc công cụ đo tầm kết nối khác với ước lượng RSSI đã chọn, không tạo hai hệ giám sát song song.

## Giải quyết xung đột và dữ liệu

Hai nhánh dùng cùng `PRAGMA user_version=2` cho cấu trúc khác nhau: bản cục bộ có proximity, nhánh Việt Mỹ có question_sets; bản 3 của nhánh có match_questions. `DatabaseSchema` kiểm tra cấu trúc thực, bổ sung bảng còn thiếu trong một transaction rồi lên 4. Không reset tài khoản/ngân hàng/điểm và không thay chứng chỉ.

Kiểm tra migration bao gồm v1, v2 proximity, v2 question sets, v3 history, lỗi giữa transaction phải rollback. Database schema 4 không được mở bằng Server cũ; rollback phải dùng cả bản sao database trước nâng cấp.

Trước tích hợp đã sao lưu mã đang làm trong `backups/pre-viet-my-20261026-205459/`. Mã tham khảo/candidate và log nằm trong `tmp/viet-my-review-20261026-205459/`; đây không phải module chạy và được Git ignore.

## Kiểm chứng

- 142 kiểm thử tự động đạt, 0 failures/errors: công thức proximity, Native Wi-Fi, quyền Windows, TLS/LAN, nhóm/owner/import/transaction, snapshot và lịch sử, migration ba cấu trúc nhánh.
- Kiểm thử Server xác nhận phát lại đề sau khi xóa câu ngân hàng, đúng vẫn 100 điểm; hai Client loopback cùng IP vào được; học sinh và giảng viên khác không thấy IP.
- Đã nâng cấp thử trên bản sao database cá nhân: giữ nguyên 2 tài khoản, 10 câu hỏi, 1 ván và 2 dòng kết quả; so sánh fingerprint toàn bộ trường cũ, kiểm tra khóa ngoại và integrity.
- JavaFX QA dùng Server TLS/SQLite tạm, không đưa dữ liệu kiểm thử vào database cá nhân. Ảnh và log kiểm tra nằm ở `output/viet-my-ui-*` và `tmp/viet-my-ui-*.log`.

Các phép thử cùng máy không thay thế nghiệm thu lại trên hai laptop qua điểm phát điện thoại. Nhận diện LAN tự động, quyền Windows, TLS pin và SFX từ bản trước được giữ trong bản tích hợp.

## Bản chạy đã cập nhật

- Duy nhất `dist/QuizBattle/`, Client và Server cùng mã tích hợp 0.5.0; Client đã cài trên MSI là 0.5.0.
- SHA-256 `Client/QuizBattle-Setup.exe`: `6cb9c65776e6ebe3fd8cb085e5e6e3beecde3999455109c4601f00036d94928f`.
- Sao lưu trước nâng cấp: `backups/QuizBattle-before-0.5.0-20261029-064224/` gồm lib/data/config/certs Server, bộ cài cũ và hướng dẫn. Giữ runtime không thay đổi.
- Server cá nhân đã lên schema 4 qua `--check-admin`, không mở cổng hay tự khởi động ván. Fingerprint mọi trường dữ liệu cũ khớp bản sao lưu sau nâng cấp; 6 tệp chứng chỉ/cấu hình giữ nguyên hash.
- QA toàn luồng còn chạy lại bằng thư viện lấy từ Client vừa cài, kết quả `LIVE_LAN_UI_PASS` trong `tmp/viet-my-installed-ui.log`. Không tạo dữ liệu demo trong database cá nhân.
