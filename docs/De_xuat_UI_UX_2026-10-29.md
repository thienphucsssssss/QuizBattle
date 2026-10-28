# Đề xuất nâng cấp UI/UX QuizBattle

Ngày: 29/10/2026. Trạng thái: **Người dùng đã duyệt và yêu cầu triển khai ngày 29/10/2026**.

Phạm vi đã xác nhận: toàn bộ giao diện, triển khai theo từng nhóm màn hình.

## 1. Cơ sở thiết kế

- Sản phẩm hiện tại: Java 21, JavaFX 21.0.12; giảng viên/Admin và học sinh dùng trong LAN, có cửa sổ trình chiếu riêng.
- Giữ nền tảng theo `AGENTS.md`, `.cursor/rules/preserve-existing-ui-ux.mdc` và `design-system/network-quiz-battle/MASTER.md`.
- Skill áp dụng ở giai đoạn phân tích/đề xuất: Impeccable và UI/UX Pro Max. Dùng chỉ dẫn JavaFX theo phiên bản thực tế; không đưa preset website/marketing vào ứng dụng.
- Đối chiếu source hiện tại với ảnh kiểm thử có sẵn trong `output/audio-lifecycle-ui/`: đăng nhập, phòng chờ, câu hỏi, ngân hàng. Ảnh phòng chờ có mã lớn và ô linh vật khớp code `lobby()`; ảnh cũ có sơ đồ kết nối đã lỗi thời, không dùng làm chuẩn.
- Giai đoạn này chưa chạy bộ kiểm thử mới và chưa xác nhận toàn bộ lỗi bố cục. Các nguy cơ từ source cần được tái hiện khi triển khai.

## 2. Nhận định giao diện hiện tại

Điểm cần giữ:

- Bản sắc cobalt/lime, Be Vietnam Pro đóng gói offline, logo và linh vật vector riêng.
- Sidebar quản lý có luồng điều hướng rõ; ngân hàng có danh sách và khung chi tiết; phòng chờ có mã phòng lớn; câu hỏi có A/B/C/D và phím tắt.
- Đã có nhiều trạng thái tải/rỗng/lỗi, điều khiển native, bảng ảo hóa, chân thao tác cố định, giảm chuyển động và trạng thái xác nhận từ Server.

Điểm cần cải thiện:

1. **Design system chưa phủ đều.** CSS chung còn các sắc nền cũ như `#e9ebe2`, `#fbfcf8`, màu hover `#d9dfd3`, trong khi các màn mới dùng trắng lạnh/cobalt. Nhiều màu được đặt trực tiếp theo màn, làm việc bảo trì và thêm theme khó nhất quán.
2. **Mật độ ngân hàng còn cao theo chiều dọc.** Ở ảnh 1366×768, header, bộ lọc và thanh chọn chiếm phần lớn vùng đầu; mỗi hàng cao 128 px, chỉ đọc được khoảng ba câu. Cần tăng diện tích đọc mà vẫn giữ vùng tick và nội dung có dấu rõ.
3. **Phân cấp thao tác cần thống nhất.** Đăng nhập, thêm câu, sẵn sàng và công cụ quản lý hiện có nhiều cách nhấn màu/kích thước khác nhau. Một số nút ở các workspace giảm còn 40 px; cần chuẩn chung và ngoại lệ có chủ đích.
4. **Responsive đang xử lý riêng từng màn.** Sidebar cố định 248 px; ngân hàng có ngưỡng riêng cho header và inspector. Cần rà soát toàn hệ thống theo vùng nội dung thực tế, đặc biệt ở 1024×640 và DPI cao.
5. **Chưa có theme light/dark toàn ứng dụng.** Nền cobalt của sân đấu là ngữ cảnh thi đấu, không đồng nghĩa với dark mode cho các form, bảng và dialog.

## 3. Hướng thiết kế đề xuất

**QuizBattle: quản lý rõ ràng, thi đấu giàu năng lượng.**

Giữ bố cục tổng thể, sidebar và luồng hiện tại. Nâng chất lượng bằng phân cấp thông tin, nhịp khoảng cách, độ dễ đọc và phản hồi tương tác; hình ảnh nhận diện tập trung ở logo, linh vật và sân thi đấu.

- **Workspace quản lý:** nền sáng lạnh, bảng rõ hàng/cột, khoảng trắng có nhịp; cobalt cho hành động chính trong form và trạng thái chọn. Lime dành cho sẵn sàng/bắt đầu và điểm nhấn của sản phẩm. Thông tin phụ nhẹ hơn nhưng vẫn đọc được.
- **Học sinh:** câu hỏi, thời gian, đáp án và xác nhận nộp là trọng tâm. Giữ bảng màu bốn đáp án và linh vật. Nội dung dài được bố trí lại thay vì giảm chữ liên tục.
- **Trình chiếu:** chữ lớn, thông tin công khai, tương phản rõ; giữ vị trí đồng hồ/tiến độ ổn định.
- **Dark mode:** có lựa chọn Sáng/Tối trong tùy chọn; mặc định giữ giao diện sáng hiện tại và lưu lựa chọn cục bộ. Theme phủ form, bảng, popup và dialog. Sân đấu/projector giữ bảng màu riêng, kiểm tra tương phản độc lập.
- Giữ sidebar bắt đầu từ đỉnh cửa sổ; không thêm lại thanh header đã được bỏ theo yêu cầu trước đây.

## 4. Design system dự kiến

### Màu

- Nhận diện: cobalt `#3049D5`, sân đấu `#2438AA`, lime `#C5F45B`, navy sidebar hiện có.
- Light: canvas `#F4F6FC`, surface `#FFFFFF`, chữ `#202146`, chữ phụ `#56617A`, viền `#D7DDEB`.
- Dark đề xuất: canvas `#111729`, surface `#1B2338`, chữ `#F1F4FF`, chữ phụ `#B8C3DC`, viền `#35425F`; cobalt sáng hơn cho nội dung cần nổi trên nền tối.
- Tách token theo vai trò: nền trang, bề mặt, chữ chính/phụ, viền, thương hiệu, hành động, lựa chọn, thành công, cảnh báo, lỗi và focus. Mỗi màu trạng thái có cặp nền/chữ riêng cho hai theme.
- Màu dark là giá trị khởi đầu cần kiểm tra tương phản; không coi bảng màu là đã nghiệm thu. Chữ thông thường mục tiêu tối thiểu 4.5:1, thành phần tương tác có focus rõ. Trạng thái luôn có chữ hoặc biểu tượng kèm màu.

### Chữ và khoảng cách

- Giữ Be Vietnam Pro Regular/SemiBold/ExtraBold đóng gói sẵn; không tải font hoặc thêm thư viện giao diện trong runtime.
- Body 15–16 px, thông tin phụ thông thường 13 px, nhãn gọn 12 px; tiêu đề workspace 28–32 px. Chữ câu hỏi/projector theo ngữ cảnh và nội dung thật.
- Giữ scale khoảng cách 8/12/16/20/24/28/36 để hòa vào hệ thống hiện có.
- Radius theo vai trò: input 10, button 14, panel 16, đáp án 22; tránh thêm nhiều biến thể trang trí.
- Nút chính tối thiểu 44 px; ô tick giữ vùng bấm 44×44. Không thu nhỏ chữ chỉ để ép đủ chiều ngang.

### Components dùng chung

- Page heading và nhóm thao tác; button primary/secondary/quiet/danger.
- Field có label, mô tả, lỗi và trạng thái bị khóa.
- Thanh tìm kiếm/bộ lọc, thanh lựa chọn, hàng dữ liệu, inspector và phân trang.
- Status badge, thông báo inline, vùng loading/empty/error/success.
- Dialog cuộn nội dung riêng, chân hành động luôn tới được.
- Dùng lại JavaFX controls và `FxKit`; mở rộng presentation helpers khi có mẫu thực sự lặp lại. Tách CSS theo lớp component/màn, giữ gateway và model hiện có.

## 5. Bố cục và trạng thái

- Kích thước mục tiêu: **1024×640, 1366×768 và 1920×1080**; kiểm tra thêm ảnh hưởng DPI Windows 125–150% khi môi trường cho phép. Phạm vi là desktop JavaFX, không mở rộng thành ứng dụng mobile.
- Ngân hàng rộng giữ danh sách + inspector; cửa sổ hẹp dùng dialog chi tiết hiện có. Tối ưu toolbar bằng wrap và khoảng cách, giữ lựa chọn qua bộ lọc/phân trang.
- Form và nội dung dài cuộn riêng; hành động lưu/hủy/sẵn sàng và thông tin thời gian quan trọng luôn tới được.
- Loading xuất hiện gần vùng đang tải, khóa thao tác gửi trùng và giữ kích thước bố cục ổn định.
- Empty phân biệt chưa có dữ liệu và không có kết quả lọc; gắn hành động phù hợp với quyền hiện có.
- Error đặt gần nguồn lỗi, giữ thông tin đã nhập, có hướng thử lại nếu hành động cho phép.
- Success chỉ xuất hiện khi có xác nhận thật. Phân biệt rõ đã gửi, Server đã nhận và kết quả đúng/sai.
- Mất kết nối phải thể hiện dữ liệu lần cập nhật cuối và khóa điều khiển theo trạng thái hiện có; không trình bày dữ liệu cũ như đang trực tiếp.
- Motion cho hover/nhấn/chuyển vùng khoảng 120–200 ms. Giữ hiệu ứng bảng hạng/podium đã có và tùy chọn giảm chuyển động; không thêm hiệu ứng lặp hoặc thay thời gian của ván.

## 6. Thứ tự triển khai sau khi được duyệt

1. **Nền tảng và vào ứng dụng:** token light/dark, controls dùng chung, sidebar, đăng nhập/đăng ký, tìm phòng và nhập mã. Kiểm tra chuyển theme không làm mất dữ liệu form hoặc trạng thái.
2. **Soạn và vận hành:** ngân hàng câu hỏi, bộ câu hỏi, import Excel/Word, cấu hình phòng, phòng chờ và giám sát. Ưu tiên tăng diện tích đọc, phân cấp thao tác và tình huống thu nhỏ cửa sổ.
3. **Thi đấu và kết quả:** màn học sinh, câu hỏi dài, xác nhận nộp, đáp án, bảng hạng, podium, lịch sử và trình chiếu. Giữ đầy đủ đồng hạng và dữ liệu Server.
4. **Công cụ và hoàn thiện:** đo LAN, người dùng, tùy chọn, dialog còn lại; rà soát toàn bộ states, bàn phím, focus, tương phản và tính đồng nhất.

Sau mỗi nhóm: chạy kiểm tra phù hợp, mở JavaFX thật, xem ảnh ở các kích thước mục tiêu, sửa lỗi nhìn thấy rồi mới tiếp tục. Không yêu cầu người dùng duyệt lại từng chỉnh sửa nhỏ trong phương án đã được chấp thuận.

## 7. Tiêu chí nghiệm thu

- Không có chữ/nút bị cắt, đè, tràn ngang ngoài chủ đích; tên dài có cách xem đầy đủ, nội dung câu hỏi dài vẫn tới được.
- Light/dark phủ các controls và dialog trong phạm vi; focus, selection, disabled và chữ phụ đều đọc được.
- Tab/Shift+Tab, Enter/Esc, phím A/B/C/D và F11 tiếp tục hoạt động theo ngữ cảnh; không chiếm phím khi nhập liệu.
- Kiểm tra loading, empty, error, success, mất kết nối và giảm chuyển động bằng trạng thái có thể tái hiện.
- Dùng `LiveLanUiCheck` và các UI check chuyên biệt phù hợp trên test classpath, với database/chứng chỉ/tài khoản tạm; không seed dữ liệu vào runtime hoặc dùng dữ liệu sản phẩm để thử.
- Chạy Maven verify sau thay đổi phù hợp; không thêm kiểm thử chỉ lặp lại giá trị CSS. Giữ TableView ảo hóa và tránh xử lý nặng trên JavaFX Application Thread.
- Báo cáo rõ phần đã chạy, ảnh đã xem và giới hạn còn lại. Kiểm thử cùng máy không chứng minh trải nghiệm LAN nhiều máy hoặc chất lượng trên máy chiếu vật lý.
- Chỉ sửa presentation, theme và lưu tùy chọn giao diện cần thiết. Không đổi giao thức, chấm điểm, deadline, phân quyền, dữ liệu câu hỏi hoặc hành vi nghiệp vụ không liên quan.

## Quyết định cần duyệt

Chấp thuận nâng cấp toàn bộ giao diện theo bản sắc hiện tại, thêm light/dark cho khu vực ứng dụng, giữ màu sân đấu riêng và triển khai bốn nhóm theo thứ tự trên. Mọi thay đổi lớn ngoài phạm vi này cần được thống nhất riêng.
