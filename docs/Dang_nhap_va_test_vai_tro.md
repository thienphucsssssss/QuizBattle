# Bản chạy thật, không dữ liệu mẫu — 22/10/2026

**Cập nhật 0.3.2:** xem `Dang_ky_va_TLS_tu_dong.md`: giảng viên có thể tự đăng ký, Client không hỏi chứng chỉ/mật khẩu TLS. Bộ cài mới ghép với Server cá nhân đã cấu hình ở `dist/QuizBattle-Windows-20261022-084345/Server/`. Thông tin Server `073803` bên dưới là bản dọn dữ liệu trước đó, có chứng chỉ khác; không dùng lẫn hai bộ.

Client Windows 0.3.1 luôn mở form **Đăng nhập Quiz Battle**, có ô **Bạn là ai?** chưa chọn vai trò. Các lựa chọn: Admin, Giảng viên, Học sinh. Đăng xuất/quay lại hoặc mở cửa sổ mới đều trở về trạng thái chưa đăng nhập. Nút đăng nhập chỉ dùng được sau khi chọn vai trò; tên tài khoản, mật khẩu và nickname để trống.

Admin/giảng viên phải được Server xác thực tài khoản và đúng vai trò đang chọn. Không tự cấp quyền Admin theo máy tính, tên launcher hoặc lựa chọn giao diện. Học sinh vẫn dùng nickname/mã phòng, không có tài khoản học sinh.

Đã gỡ nút Xem thử, PreviewGateway trong JAR sản phẩm, các màn QA chứa tên/người chơi/câu hỏi giả, tùy chọn nạp câu demo và seed-demo.bat. Fixture dùng cho kiểm thử tự động được đặt ở src/test, không nằm trong bộ cài hay database người dùng. Các ảnh/báo cáo UI Preview cũ là lịch sử thiết kế, không phải cách chạy bản mới. Mọi lệnh --preview/-Preview/-Capture trong hướng dẫn cũ đã ngừng sử dụng.

## Dữ liệu trên máy hiện tại

Server đang dùng vẫn ở:

`E:/_HUTECH/DO_AN_LAP_TRINH_MANG/dist/QuizBattle-Windows-20261022-073803/Server/`

Đã xóa đúng 20 câu demo khớp toàn bộ nội dung/lựa chọn/đáp án/chủ đề cũ. Giữ nguyên tài khoản admin bạn tự tạo, mật khẩu, IP 10.12.89.105:5050 và chứng chỉ TLS. Sau dọn: 1 tài khoản Admin, 0 câu hỏi, 0 lịch sử trận. Không có tài khoản giảng viên mẫu trong DB nên không xóa nhầm tài khoản thật.

Backup trước khi xóa câu hỏi:

`E:/_HUTECH/DO_AN_LAP_TRINH_MANG/backups/remove-demo-20261022-084727/quiz-before-cleanup.db`

Không thay database hiện tại bằng database trong thư mục tmp/test. File backup có thể dùng khôi phục sau khi dừng Server.

Bộ phát hành mới: `dist/QuizBattle-Windows-20261022-084345/QuizBattle-Setup.exe` và `QuizBattle-Server.zip`. Client trên máy này đã nâng cấp lên 0.3.1. Server mới trong ZIP để trống dữ liệu/chứng chỉ; để tiếp tục dùng tài khoản và khóa đang có, dùng thư mục Server hiện tại nêu trên, đã được cập nhật code.

## Test ba vai trò trên cùng máy

1. Mở lại start.bat của Server hiện tại; nhập mật khẩu keystore TLS. Chờ NQB_SERVER_READY.
2. Mở QuizBattle ngoài Desktop. Chọn **Admin**, IP Server 10.12.89.105, cổng 5050; chọn client-truststore.p12 hiện có trong Server/certs. Đăng nhập username admin và mật khẩu Admin do bạn đặt.
3. Vào **Người dùng → Thêm giảng viên**, tự đặt username/tên hiển thị và mật khẩu tạm.
4. Đăng xuất, chọn **Giảng viên** và nhập tài khoản mới. Đổi mật khẩu tạm theo yêu cầu rồi đăng nhập lại. Ngân hàng của tài khoản mới trống; tự thêm câu hỏi và tạo phòng.
5. Giữ cửa sổ giảng viên, mở QuizBattle thêm hai lần. Ở mỗi cửa sổ chọn **Học sinh**, nhập nickname khác nhau và mã phòng thật. Chọn avatar rồi Sẵn sàng.
6. Quay lại cửa sổ giảng viên để bắt đầu. Cần ít nhất hai học sinh; tất cả thao tác dùng socket TLS thật dù cùng máy.

Giới hạn một phiên đăng nhập quản lý cho mỗi tài khoản vẫn áp dụng. Muốn mở nhiều cửa sổ quản lý cùng lúc, dùng các tài khoản khác nhau. Admin có ngân hàng riêng; giảng viên có ngân hàng riêng, không tự thấy câu hỏi của Admin.

## Kiểm thử

54 test Java đạt sau clean build. Kiểm thử JavaFX qua Server TLS thật xác nhận: màn đầu chưa có quyền, sai mật khẩu không đăng nhập, đăng xuất xóa trạng thái cũ, chọn sai vai trò bị từ chối, giảng viên không có trang quản lý người dùng và ngân hàng ban đầu trống, nickname không điền mẫu; luồng hai câu/ACK/bảng hạng/lưu kết quả vẫn chạy. Ảnh: output/no-mock-role-qa/.

Skill UI/UX được dùng để giữ form có nhãn, lựa chọn vai trò rõ và phản hồi lỗi ở form. Không dùng tài khoản hoặc câu hỏi QA để điền vào ứng dụng đang cài của người dùng. Nghiệm thu nhiều máy LAN vật lý vẫn là bước riêng.
