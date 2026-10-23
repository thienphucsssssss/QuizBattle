# Network Quiz Battle — Arcade hiện đại

Ngày triển khai: 14/10/2026. Stack: Java 21 + JavaFX 21. Đây là hệ desktop LAN, không phải trang web/landing page. Bản gốc PNG trong `UI/` giữ nguyên làm tham chiếu; UI mới nằm trong mã JavaFX.

Cập nhật 22/10/2026 theo yêu cầu người dùng: màn khởi động là form đăng nhập có “Bạn là ai?” chưa chọn vai trò. Không còn Preview, tên/nickname mẫu, tài khoản giả hoặc câu hỏi demo trong runtime. Admin/giảng viên xác thực đúng vai trò qua Server; học sinh dùng nickname/mã phòng. Đăng xuất xóa toàn bộ danh tính/trạng thái phòng và trả về form chưa chọn vai trò. Các đoạn mô tả Preview bên dưới thuộc giai đoạn thiết kế trước, không được dựng lại vào sản phẩm.

## Định hướng

Cập nhật 0.3.6 — âm thanh: một nguồn nhạc cho mỗi Client, projector không phát thêm. Tùy chọn dùng checkbox/slider native có nhãn riêng cho SFX và nhạc nền, điều khiển bằng bàn phím; nội dung cuộn trên cửa sổ nhỏ, nút Đóng ngoài vùng cuộn. Nhạc nền chỉ phát trong ván, chung cuộc phát riêng một lần; hủy/rời phòng/mất kết nối dừng. Mặc định giảng viên/Admin bật nhạc ở 20%, học sinh tắt; lưu lựa chọn theo vai trò. Nạp WAV và mở thiết bị ngoài JavaFX thread, lỗi thiết bị không ảnh hưởng gameplay. Hướng dẫn UI/UX Pro Max áp dụng nhãn rõ, focus native và tránh chặn luồng giao diện.

Cập nhật 0.3.5 — chung cuộc: visual thesis là bục vinh danh 2–1–3 trên sân cobalt, giữ linh vật nguyên bản làm tâm điểm; vàng/bạc/đồng chỉ mang ý nghĩa huy chương. Content plan: tiêu đề kết thúc → avatar có khung/huy chương → tên và điểm chính xác → bục cao theo hạng → hành động ván mới/rời phòng → bảng toàn bộ người chơi. Interaction thesis: xuất hiện 300–400 ms bằng opacity/translate, hạng nhất tới sau hạng nhì/ba; mỗi session/màn chỉ diễn một lần, rời màn/giảm chuyển động chốt ngay trạng thái cuối. Không scale chiều cao giả theo điểm, không confetti lặp hoặc animation cản thao tác. Dùng rank Server và giữ mọi người có rank ≤3, gồm đồng hạng; rank1/1/3 không tạo người hạng2. Nhiều người đồng hạng tự xuống dòng trong vùng cuộn, không tràn ngang. Không sửa luồng trận bị hủy thành màn vinh danh.

Cập nhật 0.3.4: Ngân hàng có Nhập từ Excel / Word và Xem mẫu / Tải mẫu. Dialog xem trước dùng bảng native JavaFX có checkbox/vị trí/nội dung/đáp án/trạng thái, vùng chi tiết cuộn với bốn đáp án 2×2 và nhãn đáp án đúng. Lỗi/trùng có chữ, không chỉ màu; lỗi có vị trí gốc để sửa tệp. Câu lỗi không chọn được, câu trùng bỏ chọn mặc định và chỉ nhập thêm khi xác nhận. Không hiển thị thuật ngữ schema/parser/TLS ở luồng thường. Giữ nút nhập một lần, loading và thông báo kết quả; đóng/hủy trước commit không ghi dữ liệu. Save dialog lưu mẫu trống tại vị trí người dùng chọn; parsing/sinh mẫu không chặn JavaFX thread. Theo UI/UX Pro Max: trạng thái async ổn định, lỗi cạnh dữ liệu, focus lỗi có thể truy cập bằng bàn phím, danh sách virtualized và cùng palette hiện có.

Cập nhật 0.3.3: bỏ IP/cổng khỏi toàn bộ form Client và trạng thái kết nối chính. Học sinh có hai chế độ ngang hàng “Phòng trong LAN” / “Nhập mã phòng”, danh sách tên phòng/giảng viên/số người/trạng thái; giữ lựa chọn khi làm mới, báo lỗi mã không tồn tại ngay trong form. Teacher/Admin chọn máy chủ đã xác minh theo tên, tự chọn khi chỉ có một. Có loading, empty, offline, refresh; không hiển thị phòng lấy trực tiếp từ UDP chưa xác minh. Tham khảo UI/UX Pro Max về empty/loading/focus; dùng native JavaFX controls và giữ palette/tokens hiện có.

Cập nhật 0.3.2: bỏ toàn bộ trường truststore/mật khẩu TLS trên auth form; chứng chỉ công khai đi kèm Client và vẫn xác minh tự động. Có “Tạo tài khoản giảng viên” ở màn chưa chọn vai trò/giảng viên, form tên hiển thị + username + mật khẩu + xác nhận, lỗi cạnh trường, loading và không gửi trùng; tạo xong LOGIN thật rồi vào ngân hàng riêng trống. Không tự đăng ký Admin hoặc tài khoản học sinh. Hướng dẫn kỹ thuật về TLS chỉ ở tài liệu người vận hành.

Visual thesis (v2, theo phản hồi người dùng): sân chơi cobalt/lime, chữ lớn, bộ linh vật vector “Biệt đội Quiz” làm điểm nhận diện; workspace trắng lạnh, không còn vẻ kem/navy cổ điển. Không sao chép nhân vật, nhận diện hoặc âm thanh Kahoot.

Content plan: chọn vai trò → biệt danh/kết nối → chọn linh vật trong phòng → sẵn sàng → câu hỏi → đáp án (3 giây) → bảng hạng (4,5 giây) → câu kế hoặc chung cuộc. Workspace dùng table, bộ lọc và inspector, không dùng dashboard KPI trang trí.

Interaction thesis: nhân vật vào màn 350 ms, stagger 90 ms; chuyển màn 180 ms; bảng hạng giữ vị trí cũ 450 ms rồi trượt 850 ms theo player_id. Giảm chuyển động bỏ animation và đặt ngay vị trí cuối, không thay deadline. Mọi trạng thái đúng/sai/đã lưu/đã giữ avatar lấy từ gateway, không suy ra từ việc bấm nút.

Tham khảo ui-ux-pro-max cho focus, nhãn form, trạng thái loading/error/empty và JavaFX controls; frontend-skill cho typography, whitespace và workspace tiết chế. Preset pixel-art/marketing không phù hợp nên không dùng; các quyết định dưới đây là thiết kế riêng theo lựa chọn arcade của người dùng.

## Tokens

| Token | Giá trị | Dùng cho |
| --- | --- | --- |
| ink | #202146 | Chữ chính |
| cream | #f4f6fc | Workspace nền sáng |
| cobalt | #3049d5 / #2438aa | Welcome / sân đấu |
| surface | #ffffff | Form và bảng |
| mint | #c5f45b | CTA, timer, điểm, đáp án C |
| coral | #ff936b | Đáp án A |
| sky | #6fdfed | Đáp án B |
| lilac | #c6b3ff | Đáp án D |
| muted | #56617a | Chữ phụ nền sáng |
| muted-dark | #d4ddff | Chữ phụ sân đấu |
| focus | #256c90 / trắng | Focus nền sáng / sân đấu |

Không dùng màu làm dấu hiệu duy nhất: mỗi đáp án có A/B/C/D và nội dung; ready, lỗi, lựa chọn đã nhận đều có chữ. Mint là accent hệ thống; ba màu phụ chỉ dùng cho phân biệt đáp án, không rải khắp workspace.

Font đóng gói: Be Vietnam Pro Regular / SemiBold / ExtraBold, SIL OFL 1.1. JavaFX nhận ba family riêng; không chỉ tăng numeric font-weight vì sẽ quay về Regular. Body 15 px, chữ phụ 12–13, subtitle 20, tiêu đề 30, tên sản phẩm 64, mã phòng 66, điểm 68; câu hỏi Player 26–30, projector 42. Không tải font trong lúc chạy.

Spacing: 8/12/16/20/24/28/36. Button radius 14; answer radius 22. Nút tối thiểu 44 px cao, đáp án 88 px ở cửa sổ thấp, 122 px ở cửa sổ chuẩn. Focus luôn giữ lại. Đáp án có bóng cạnh dưới 6 px, không phủ gradient/shadow lên mọi panel.

## Linh vật và câu chữ

24 ID cố định trong AvatarCatalog; hình vẽ JavaFX nguyên bản, 4 silhouette, 6 màu, phụ kiện khăn/tai nghe/kính phân biệt các biến thể. Không tải ảnh mạng, không emoji. Linh vật dùng nhất quán trong lobby, kết quả cá nhân, podium và bảng hạng.

Sau JOIN, Server gán avatar trống rồi mở bộ chọn. Nút có chủ hiện “Đã chọn”, không bấm được; lựa chọn của mình hiện “✓ Của bạn”. Chỉ đổi khi WAITING và chưa ready. Không đổi avatar cục bộ trước ACK; nếu tranh chấp thì giữ avatar cũ và cập nhật danh sách. Khóa duy nhất trong một phòng, không khóa toàn hệ thống.

Không đưa “JavaFX”, “RAM”, “DB”, “deadline”, mô tả kiến trúc vào màn chơi. Badge “Xem thử” luôn hiện, tooltip giải thích giới hạn để không khiến dữ liệu mẫu bị hiểu là ván LAN thật. Giữ thông tin chứng chỉ/cấu hình trong form kỹ thuật cần thiết. Không bỏ thông báo lỗi lưu, mất kết nối hoặc xác nhận hành động nguy hiểm.

## Bố cục và trạng thái

- Cửa sổ mặc định 1366×768, tối thiểu 1024×640; kích thước là vùng Scene, chưa tính title bar của Windows.
- Sidebar 208 px cho quản lý; Player không có sidebar nghiệp vụ. Admin thêm trang Người dùng; chọn vai trò không cấp quyền.
- Ngân hàng: bảng + inspector bên phải; ẩn inspector nếu vùng bảng quá hẹp, vẫn có nút xem chi tiết. Lọc AND, debounce 300 ms, phân trang, loại phản hồi cũ.
- Question form có nhãn thật, kiểm tra giới hạn, lỗi ngay form, chờ phản hồi trước đóng. Ctrl+S; hỏi lại khi bỏ nháp.
- Duplicate dialog mặc định Quay lại chỉnh sửa; Lưu dù trùng phải bấm chủ động. Xem câu cũ chỉ đọc, không ghi tự động.
- Câu ngắn: bốn đáp án 2×2. Câu/lựa chọn dài: xếp dọc trong vùng cuộn. Timer và ACK ở vùng cố định.
- OPEN → SENDING → RECORDED hoặc REJECTED; EXPIRED nếu chưa nộp đến hạn. ACK không phải đúng/sai, client không cộng điểm.
- Ranking giữ rank từ nguồn, đồng điểm 1,1,3; tất cả người hạng 1 được nêu, không tự chọn một người thắng vì thứ tự mảng.
- Projector là Stage riêng, dùng cùng gateway; chỉ mã phòng/nickname/câu công khai/kết quả. Không show username quản lý, form, mật khẩu, ngân hàng hoặc đáp án đúng trước RESULT. F11 mở/chuyển toàn màn hình; Esc chỉ thoát fullscreen.
- Preview luôn có nhãn, chỉ dữ liệu RAM. Kết nối TLS lỗi ở lại form lỗi, không tự đưa vào Preview.

## Âm thanh đã tích hợp

17 SFX WAV PCM và 2 bản nhạc WAV PCM stereo do người dùng cung cấp, dùng Java Sound có sẵn trong runtime Java, không cần JavaFX media hoặc mạng Internet. `SfxSlots` dành cho hiệu ứng ngắn; `MusicSlots` dành cho một bản nhạc tại một thời điểm. BGM lặp liên tục qua các câu; High Score Glory chỉ khi FINISHED không aborted, phát một lần. Giữ nguyên file gốc, manifest và SHA256 riêng cho nhạc. Tùy chọn bật/tắt và âm lượng luôn có; QA tự động im lặng và không ghi đè tùy chọn âm thanh cá nhân.

## QA và giới hạn

Chụp Scene JavaFX thật, kiểm tra chọn vai trò, quản lý, form/trùng, lobby/game/projector và trạng thái dài/rỗng/ACK/hết giờ/lỗi TLS. Test core riêng; không coi ảnh mẫu hoặc test Preview là chứng minh Server/SQLite/quyền sở hữu qua LAN. Xem báo cáo trong `docs/UI_JavaFX_Trien_khai.md`.
