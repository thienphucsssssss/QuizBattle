# Bộ câu hỏi và lựa chọn cho phòng thi

Cập nhật 25/10/2026. Mỗi Admin/giảng viên có các bộ câu hỏi riêng. Một câu có thể nằm trong nhiều bộ; chủ đề của câu được giữ nguyên.

## Giao diện Ngân hàng câu hỏi

Tìm kiếm và các bộ lọc được gom phía trên danh sách. Nút **Nhập từ Excel / Word** và **+ Thêm câu hỏi** nằm ở đầu trang; **Bộ câu hỏi**, **Mẫu câu hỏi** và các thao tác chọn nằm ngay dưới bộ lọc.

Mỗi hàng hiển thị chủ đề, trạng thái đang bật/đã tắt, nội dung xem trước và thời gian cập nhật. Tick ô đầu dòng để chọn cho bộ; bấm nội dung để xem chi tiết độc lập với lựa chọn tick. Khi cửa sổ rộng, chi tiết hiện bên phải; khi hẹp, bấm **Xem chi tiết** để mở cửa sổ có vùng cuộn. Bốn đáp án được tách rõ, đáp án đúng có nền xanh nhạt và nhãn **Đáp án đúng**. Các nút chỉnh sửa, bật/tắt và xóa vẫn kiểm tra quyền/trạng thái phòng như trước.

## Tạo bộ khi nhập Excel hoặc Word

1. Vào **Ngân hàng câu hỏi → Nhập từ Excel / Word** và chọn tệp.
2. Trường **Lưu vào bộ câu hỏi** tự lấy tên tệp, có thể sửa tên (tối đa 80 ký tự).
3. Kiểm tra và chọn các câu rồi xác nhận nhập. Bộ chỉ được tạo/cập nhật cùng lúc các câu được lưu thành công.
4. Nếu đã có bộ cùng tên trong tài khoản, các câu mới được thêm vào bộ đó. Để trống tên nếu chỉ muốn nhập vào ngân hàng.

Đóng/hủy xem trước không tạo bộ. Nếu có lỗi trong lúc lưu, cả câu hỏi và liên kết bộ cùng rollback. Mỗi bộ tối đa 500 câu; bộ đang đầy cần tên bộ mới cho lượt nhập tiếp theo. Mẫu Excel/Word bảy cột hiện có không thay đổi.

## Tạo bộ từ câu đã có

1. Trong Ngân hàng, lọc nội dung, chủ đề, trạng thái hoặc bộ hiện có để thu hẹp danh sách.
2. Tick ô ở cột đầu của từng câu hỏi (thay cột ID), hoặc tick ô trên đầu cột / bấm **Chọn cả trang** để chọn 10 câu ở trang đang xem. Ô trên đầu cột có dấu gạch khi mới tick một phần; bỏ tick ô này chỉ bỏ chọn trang hiện tại. Sang **Sau →** và tick thêm: lựa chọn được cộng dồn qua các trang và bộ lọc. Nhãn **Đã chọn N câu** hiển thị tổng hiện tại; quay lại trang trước sẽ thấy các ô vẫn được tick.
3. Bấm **Bộ câu hỏi**, nhập **Tên bộ mới**, rồi chọn **Tạo từ N câu đã chọn**. Số N là số câu thực tế đã chọn, không phải ô nhập. Ví dụ: chọn 10 câu trang 1 và 10 câu trang 2 thì nút hiển thị **Tạo từ 20 câu đã chọn**.
4. Hoặc chọn **Tạo từ kết quả lọc** để lấy toàn bộ kết quả trên mọi trang, tối đa 500 câu, không cần chọn từng trang. Ví dụ ngân hàng có 40 câu phù hợp thì tạo được bộ 40 câu dù danh sách chỉ hiện 10 câu/trang.

**Bỏ chọn tất cả** xóa lựa chọn ở mọi trang, kể cả các câu đang bị bộ lọc ẩn đi. Lựa chọn cũng được xóa khi đăng xuất/đăng nhập lại để không dùng nhầm câu của tài khoản hoặc Server khác. Mỗi bộ tối đa 500 câu; nếu chọn quá giới hạn, nút tạo từ lựa chọn bị khóa và hiển thị hướng dẫn bỏ bớt.

Bấm vào nội dung dòng để xem chi tiết không thay đổi các ô đã tick. Nút **Xem chi tiết** mở câu đang được xem; tạo bộ dùng các câu đã tick. Có thể Tab tới ô tick rồi nhấn Space để chọn/bỏ chọn.

Bộ lọc cạnh ô tìm kiếm cho phép xem riêng từng bộ. Xóa bộ chỉ xóa tên/liên kết bộ, các câu vẫn ở trong ngân hàng và những bộ khác. Phòng đang dùng bộ phải chuyển sang nguồn khác hoặc đóng phòng trước khi xóa bộ.

Các câu nhập từ trước bản cập nhật vẫn ở trong ngân hàng. Dùng cách tạo bộ từ kết quả lọc để gom lại; không cần nhập lại file.

## Chọn bộ cho phòng

1. Vào **Phòng thi → Tạo phòng** hoặc **Cấu hình phòng**.
2. Nhập tên phòng và chọn **Bộ câu hỏi**. Danh sách hiển thị số câu đang bật/tổng câu.
3. Chọn số câu (1–20), thời gian mỗi câu (5–120 giây), rồi lưu.

Server chọn ngẫu nhiên đủ số câu yêu cầu trong các câu **đang bật của bộ đã chọn**. Không lấy câu từ bộ khác khi thiếu. Cấu hình và lúc bắt đầu ván đều kiểm tra nguồn câu; bật/tắt/xóa câu sau khi cấu hình có thể làm bộ không còn đủ câu để bắt đầu.

**Toàn bộ ngân hàng** giữ cách lấy câu cũ. **Reset phòng** giữ bộ đang chọn; **Đóng phòng** xóa lựa chọn khỏi phòng. Bộ và thành viên được lưu trong database, tồn tại qua lần khởi động Server tiếp theo. Bật/tắt/sửa một câu có hiệu lực ở mọi bộ chứa câu đó.

## Dùng lại câu hỏi từ lịch sử

1. Vào **Lịch sử & Bảng xếp hạng**, chọn ván đã lưu và bấm **Xem bảng điểm**.
2. Bấm **Dùng lại bộ câu hỏi**. Form điền sẵn tên phòng, số câu và thời gian mỗi câu; lựa chọn **Dùng câu hỏi từ ván đã lưu** được bật sẵn.
3. Điều chỉnh tên, số câu hoặc thời gian rồi bấm **Lưu cấu hình**. Số câu không được vượt quá số câu đã lưu; khi giảm số câu, lấy các câu đầu theo thứ tự của ván cũ.
4. Các thí sinh tham gia/sẵn sàng rồi bắt đầu như một ván mới. Nếu phòng hiện tại đã kết thúc, lưu cấu hình hợp lệ sẽ cấp mã mới, giữ người còn kết nối và xóa điểm/trạng thái sẵn sàng. Bấm **Hủy** không thay đổi phòng.

Đề dùng lại gồm đúng các câu đã xuất hiện trong ván được chọn, giữ nguyên nội dung, bốn lựa chọn và đáp án tại thời điểm thi. Sửa, tắt hoặc xóa câu trong ngân hàng sau đó không làm thay đổi đề đã lưu. Tính năng không tạo thêm câu hay bộ trùng trong ngân hàng. Ván thi lại có kết quả lịch sử riêng.

Chỉ chủ sở hữu kết quả được dùng lại đề. Không thay cấu hình khi ván đang chạy hoặc khi phòng thuộc giảng viên khác. **Reset phòng** giữ nguồn từ lịch sử; **Đóng phòng** xóa lựa chọn nguồn khỏi phòng.

Các kết quả trước bản cập nhật chỉ lưu điểm, chưa lưu nội dung câu hỏi: vẫn xem được bảng điểm, nhưng nút dùng lại bị khóa và có giải thích. Chỉ các ván hoàn thành sau khi Server được cập nhật mới có đề đã lưu để dùng lại.

Database nâng lên phiên bản 3 bằng migration có transaction, giữ tài khoản/ngân hàng/kết quả cũ. Câu đã thi và thời gian mỗi câu được lưu cùng kết quả trong một transaction; lỗi lưu đề không được báo là đã lưu thành công.

## Giao thức và dữ liệu

- `QUESTION_SET_LIST`: chỉ bộ của tài khoản hiện tại; `page` và `page_size` (tối đa 100). Mỗi dòng có `question_set_id`, `name`, `question_count`, `active_count`.
- `QUESTION_SET_CREATE`: `name` và `question_ids` (1–500 ID riêng biệt) hoặc `filters` (các điều kiện của `QUESTION_LIST`, không giới hạn ở trang hiện tại). Tên bộ phải khác các bộ đã có trong cùng tài khoản.
- `QUESTION_SET_DELETE`: `question_set_id`. Giữ nguyên các câu. Bộ đang được phòng sử dụng bị từ chối với `QUESTION_SET_IN_USE`.
- `QUESTION_LIST` bổ sung bộ lọc `question_set_id`; `0` là toàn bộ ngân hàng. Các bộ lọc kết hợp AND.
- `QUESTION_IMPORT_COMMIT` có thêm `question_set_name` tùy chọn; phản hồi bổ sung ID/tên bộ nếu được gán.
- `ROOM_CONFIGURE` nhận `question_set_id`; `0` chọn toàn bộ ngân hàng. Nếu bỏ trường này, phòng mới dùng toàn bộ ngân hàng, phòng đang có giữ nguồn hiện tại.
- `ROOM_STATE` trả `question_set_id` và `question_set_name`. Server kiểm tra quyền sở hữu, trạng thái chờ và số câu, không tin owner do Client tự khai.

Database v1 nâng lên v2 bằng transaction, thêm `question_sets` và `question_set_items` cùng khóa ngoại. Không sửa các bản ghi tài khoản, câu hỏi hoặc kết quả cũ; lỗi nâng cấp rollback bảng mới và version. Database phiên bản không hỗ trợ vẫn bị từ chối.

Khởi động lại cả Server và Client từ bản source vừa build để dùng các điều khiển/API mới. Các bài kiểm thử dùng database, chứng chỉ và tài khoản tạm.

## Khi Client mới kết nối với Server cũ

Thông báo **“Yêu cầu không được hỗ trợ”** khi tải bộ câu hỏi nghĩa là Server đang kết nối chưa có API này. Build xong không thay mã đang nằm trong tiến trình Java đã chạy: cần tắt cửa sổ Server cũ, mở lại Server từ cùng thư mục dự án/bộ cài với Client, đợi `NQB_SERVER_READY`, rồi mở và đăng nhập lại Client. Không cần xóa database, tạo lại tài khoản hay nhập lại câu hỏi.

`run.bat` hiện kiểm tra dấu vân tay của source, cấu hình build và bản biên dịch thay vì chỉ nhìn dấu `ready` cũ. Khi source hoặc bản biên dịch thay đổi, launcher tự build lại trước khi chạy. Thông báo trên Client cũng hướng dẫn cập nhật/khởi động lại Server; form dừng trạng thái đang tải và chặn lưu khi chưa tải được bộ.

## Kiểm chứng bản cập nhật

Bản dùng lại đề từ lịch sử: build/install toàn dự án đạt **134 bài kiểm thử**, không lỗi hoặc bỏ qua. Kiểm thử giao diện thật ở 1024×640 và 1366×768 xác nhận nút dùng lại, form điền đúng tên/số câu/thời lượng, Hủy không đổi phòng, chặn số câu vượt đề và thi lại bằng đúng nguồn đã lưu. Component QA xác nhận kết quả cũ vẫn đọc được và nút dùng lại bị khóa khi thiếu đề. Test persistence kiểm tra migration/rollback, đề tồn tại qua restart và không bị đổi khi sửa/xóa câu gốc. Log: `output/history-reuse-verify.log`, `output/history-reuse-final-1024.log`, `output/history-reuse-final-1366.log`, `output/history-reuse-components-final.log`.

Bản nâng cấp giao diện Ngân hàng: **126 bài kiểm thử** đạt; kiểm thử giao diện 1024×640 và 1366×768 đạt các luồng xem đủ bốn đáp án thật, đánh dấu đáp án đúng, chuyển nhanh câu đang xem, xóa chi tiết khi lọc rỗng, tick qua nhiều trang và tạo bộ. Đã xem ảnh câu/đáp án tiếng Việt dài và các thao tác trong vùng cuộn. Log: `tmp/bank-polish-final-build.log`, `tmp/bank-polish-final-1024.log`, `tmp/bank-polish-final-1366.log`; ảnh: `output/bank-polish-final/`.

Build/install Maven thành công với 111 bài kiểm thử, không có lỗi. Kiểm thử giao diện trực tiếp ở 1024×640 và 1366×768 đã xác nhận nhập/gom bộ, lọc ngân hàng, cảnh báo thiếu câu, chọn bộ cho phòng, câu thi thuộc đúng bộ và giữ lựa chọn sau reset; các luồng LAN, quản trị và nhập file hiện có vẫn đạt.

Bản sửa tương thích cùng ngày: build/install đạt **116 bài kiểm thử**, không lỗi hoặc bỏ qua. Kiểm thử launcher thực thi trên dự án tạm có đường dẫn chứa khoảng trắng, xác nhận marker cũ, thay đổi/xóa source và thay đổi runtime class đều kích hoạt build lại; source không đổi tái sử dụng bản build. Kiểm thử giao diện 1024×640 đạt cả luồng chọn bộ trên Server hiện hành và lỗi `UNKNOWN_TYPE` giả lập từ Server cũ: kết thúc loading, hướng dẫn cập nhật/khởi động lại, không cho lưu nguồn chưa tải được. Log tại `tmp/server-compat-final-build.log`, `tmp/server-compat-ui.log`; ảnh tại `output/server-compat-qa/1024/`.

Bản sửa chọn câu qua nhiều trang: build/install toàn dự án đạt **119 bài kiểm thử**, không lỗi hoặc bỏ qua. Kiểm thử giao diện 1024×640 xác nhận chọn 10+10 câu ở hai trang tạo được bộ 20 câu thật trên Server; quay lại khôi phục lựa chọn, bỏ một câu còn 19, bỏ chọn tất cả xóa cả các trang ẩn, nút chọn cả trang hoạt động và đổi tài khoản xóa lựa chọn cũ. Các luồng giao diện hiện có vẫn đạt. Log tại `tmp/question-selection-final-build.log`, `tmp/question-selection-final-ui.log`; ảnh tại `output/question-selection-final/1024/`.

Bản cập nhật ô tick: **119 bài kiểm thử** đạt. Kiểm thử giao diện 1024×640 xác nhận tick từng câu, trạng thái chọn một phần của ô đầu cột, chọn/bỏ cả trang không ảnh hưởng trang khác, xem chi tiết không làm mất tick, tạo đủ bộ 20 câu qua hai trang và xóa lựa chọn khi đổi tài khoản. Đã kiểm tra ảnh ô tick ở đầu cột và từng dòng thẳng hàng/cùng kích thước. Log tại `tmp/question-checkbox-final-build.log`, `tmp/question-checkbox-final-ui.log`; ảnh tại `output/question-checkbox-final/1024/`.
