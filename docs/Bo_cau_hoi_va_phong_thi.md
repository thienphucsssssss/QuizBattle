# Bộ câu hỏi và lựa chọn cho phòng thi

Cập nhật 25/10/2026. Mỗi Admin/giảng viên có các bộ câu hỏi riêng. Một câu có thể nằm trong nhiều bộ; chủ đề của câu được giữ nguyên.

## Tạo bộ khi nhập Excel hoặc Word

1. Vào **Ngân hàng câu hỏi → Nhập từ Excel / Word** và chọn tệp.
2. Trường **Lưu vào bộ câu hỏi** tự lấy tên tệp, có thể sửa tên (tối đa 80 ký tự).
3. Kiểm tra và chọn các câu rồi xác nhận nhập. Bộ chỉ được tạo/cập nhật cùng lúc các câu được lưu thành công.
4. Nếu đã có bộ cùng tên trong tài khoản, các câu mới được thêm vào bộ đó. Để trống tên nếu chỉ muốn nhập vào ngân hàng.

Đóng/hủy xem trước không tạo bộ. Nếu có lỗi trong lúc lưu, cả câu hỏi và liên kết bộ cùng rollback. Mỗi bộ tối đa 500 câu; bộ đang đầy cần tên bộ mới cho lượt nhập tiếp theo. Mẫu Excel/Word bảy cột hiện có không thay đổi.

## Tạo bộ từ câu đã có

1. Trong Ngân hàng, lọc nội dung, chủ đề, trạng thái hoặc bộ hiện có để thu hẹp danh sách.
2. Bấm **Bộ câu hỏi**, nhập **Tên bộ mới**.
3. Chọn **Tạo từ kết quả lọc** để lấy toàn bộ kết quả trên mọi trang, tối đa 500 câu. Hoặc dùng Ctrl/Shift chọn các dòng ở trang ngân hàng hiện tại rồi chọn **Tạo từ N câu đã chọn**.

Bộ lọc cạnh ô tìm kiếm cho phép xem riêng từng bộ. Xóa bộ chỉ xóa tên/liên kết bộ, các câu vẫn ở trong ngân hàng và những bộ khác. Phòng đang dùng bộ phải chuyển sang nguồn khác hoặc đóng phòng trước khi xóa bộ.

Các câu nhập từ trước bản cập nhật vẫn ở trong ngân hàng. Dùng cách tạo bộ từ kết quả lọc để gom lại; không cần nhập lại file.

## Chọn bộ cho phòng

1. Vào **Phòng thi → Tạo phòng** hoặc **Cấu hình phòng**.
2. Nhập tên phòng và chọn **Bộ câu hỏi**. Danh sách hiển thị số câu đang bật/tổng câu.
3. Chọn số câu (1–20), thời gian mỗi câu (5–120 giây), rồi lưu.

Server chọn ngẫu nhiên đủ số câu yêu cầu trong các câu **đang bật của bộ đã chọn**. Không lấy câu từ bộ khác khi thiếu. Cấu hình và lúc bắt đầu ván đều kiểm tra nguồn câu; bật/tắt/xóa câu sau khi cấu hình có thể làm bộ không còn đủ câu để bắt đầu.

**Toàn bộ ngân hàng** giữ cách lấy câu cũ. **Reset phòng** giữ bộ đang chọn; **Đóng phòng** xóa lựa chọn khỏi phòng. Bộ và thành viên được lưu trong database, tồn tại qua lần khởi động Server tiếp theo. Bật/tắt/sửa một câu có hiệu lực ở mọi bộ chứa câu đó.

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

## Kiểm chứng bản cập nhật

Build/install Maven thành công với 111 bài kiểm thử, không có lỗi. Kiểm thử giao diện trực tiếp ở 1024×640 và 1366×768 đã xác nhận nhập/gom bộ, lọc ngân hàng, cảnh báo thiếu câu, chọn bộ cho phòng, câu thi thuộc đúng bộ và giữ lựa chọn sau reset; các luồng LAN, quản trị và nhập file hiện có vẫn đạt.
