# Đăng ký và kết nối tự động — bản 0.3.2

Bộ cài đã tạo: `dist/QuizBattle-Windows-20261022-093338/QuizBattle-Setup.exe`; Client trên máy này đã nâng cấp. Server cá nhân tiếp tục dùng `dist/QuizBattle-Windows-20261022-084345/Server/start.bat`, đã cập nhật mã mà không thay database/config/chứng chỉ. Kết quả kiểm thử: [Nghiệm thu 0.3.2](Nghiem_thu_Dang_ky_TLS_0_3_2.md).

Người dùng không chọn chứng chỉ hoặc nhập mật khẩu TLS trên màn đăng nhập nữa. Client đọc hồ sơ kết nối công khai đi kèm bộ cài và xác minh chứng chỉ/IP máy chủ trước khi gửi mật khẩu; không dùng trust-all hoặc kết nối TCP không mã hóa.

## Sử dụng

1. Bật Server hiện có bằng `start.bat`, chờ `NQB_SERVER_READY`.
2. Cài Client do người vận hành chính Server đó cung cấp. Chọn vai trò; IP/cổng có sẵn trong hồ sơ kết nối và có thể đổi trên form.
3. Giảng viên chọn **Tạo tài khoản giảng viên**, điền tên hiển thị, tên đăng nhập và mật khẩu hai lần. Đăng ký thành công tự đăng nhập, có ngân hàng riêng trống. Mật khẩu cần 8–128 ký tự; tên đăng nhập 3–40 ký tự a–z, 0–9, _, . hoặc -.
4. Admin dùng tài khoản khởi tạo ở Server, không tự đăng ký Admin. Admin vẫn tạo/khóa/reset tài khoản giảng viên được; tài khoản Admin cấp bằng mật khẩu tạm vẫn phải đổi mật khẩu.
5. Học sinh không đăng ký: dùng nickname, mã phòng và linh vật như trước.

## Hai loại mật khẩu khác nhau

- Mật khẩu tài khoản được lưu dưới dạng PBKDF2-HMAC-SHA256 có salt, 600.000 vòng, không lưu bản rõ trong SQLite. Việc băm không thay thế mã hóa đường truyền.
- Mật khẩu `server.p12` bảo vệ khóa bí mật trên máy Server; chỉ người vận hành cấu hình một lần, được launcher lưu bằng Windows DPAPI. Không phát khóa này cho Client.
- `client-truststore.p12` chỉ chứa chứng chỉ công khai. Mật khẩu kho công khai `changeit` không phải mật khẩu tài khoản hay khóa bí mật; phần mềm tự đọc nó, người chơi không cần biết.

## Người vận hành / build

Bộ cài được ghép với chứng chỉ của một Server cụ thể. Khởi tạo/cấu hình Server trước, sau đó build Client từ **chỉ kho chứng chỉ công khai** của Server đó:

```powershell
.\scripts\Build-WindowsRelease.ps1 -Offline -ClientTruststore 'duong-dan/Server/certs/client-truststore.p12' -ServerHost '10.12.89.105'
```

Script từ chối kho có private key. Bộ cài chứa `app/client.properties` và `app/certs/client-truststore.p12`, không chứa database, tài khoản hoặc `server.p12`. Java xác định đường dẫn theo thư mục ứng dụng, không theo thư mục hiện hành của CMD/shortcut.

Đổi IP chỉ dùng được nếu IP mới có trong SAN chứng chỉ. Khi thay Server/chứng chỉ, người vận hành cấp lại hồ sơ công khai hoặc bộ cài Client qua kênh tin cậy; không bật chế độ bỏ xác minh để xử lý lỗi. ZIP Server mới là gói trống để cài mới/cập nhật mã, không phải bản sao Server cá nhân đã cấu hình. Khi cập nhật Server cũ, giữ nguyên `data/`, `certs/` và `config/`.

Trong chế độ chạy source, `client.properties` đặt ở thư mục làm việc, đường dẫn truststore tính tương đối với file đó. Có thể dùng `-Dnqb.client.config=C:/duong-dan/client.properties` để chọn hồ sơ công khai riêng. Không đưa mật khẩu đăng nhập vào hồ sơ này.

## Chính sách đăng ký

Server cố định tài khoản tự đăng ký là `TEACHER`, không tin vai trò do Client tự khai; dùng cùng kiểm tra quyền sở hữu ngân hàng/lịch sử như tài khoản do Admin tạo. Giới hạn 5 lần REGISTER/phút/IP, auth executor có hàng đợi hữu hạn. Cùng tên đăng nhập, kể cả khác hoa/thường hoặc đăng ký đồng thời, chỉ có một tài khoản được tạo.

Mặc định cho phép đăng ký và dùng ngay, không xác minh danh tính giảng viên. Người trong LAN biết địa chỉ Server có thể đăng ký giảng viên và chiếm phòng đang trống. Nếu buổi thi cần kiểm soát tài khoản, đặt `"registration": { "enabled": false }` trong `Server/config/server.json`, khởi động lại Server, rồi cấp tài khoản qua Admin. Đây là lựa chọn vận hành, không phải học sinh được cấp quyền Admin.
