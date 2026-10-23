# Server khởi động bằng config — 22/10/2026

Tất cả thiết lập dùng khi mở `start.bat` nằm trong **Server/config/server.json**. `start.bat` không hỏi lại IP, mật khẩu TLS hoặc mật khẩu Admin. Nếu config thiếu/sai, chương trình báo trường cần sửa và dừng; không tự xóa dữ liệu hoặc tạo lại khóa.

## Server hiện có: làm một lần

1. Dừng Server bằng Ctrl+C trong cửa sổ đang chạy.
2. Mở `configure.bat` trong cùng thư mục Server. Nhấn Enter để giữ IP/cổng hiện tại, nhập **mật khẩu keystore TLS đã đặt trước đó** một lần. Tài khoản Admin đã có nên không hỏi tạo Admin lại.
3. Từ lần sau, chỉ mở `start.bat` và chờ `NQB_SERVER_READY`.

Các thư mục Server trên máy đã được tạo config với IP/cổng/database/keystore lấy từ cấu hình cũ. Mật khẩu TLS hiện tại không được suy đoán hoặc lấy từ bộ nhớ của tiến trình: bạn phải cung cấp một lần qua configure.bat hoặc cách sửa file dưới đây. Client không cần cài lại.

## Hoặc tự điền file config

Ví dụ cho máy trường, thay giá trị mật khẩu bằng giá trị do bạn chọn:

```json
{
  "server": {
    "bind": "10.12.89.105",
    "port": 5050,
    "maxPlayers": 10,
    "maxConnections": 32
  },
  "database": { "path": "data/quiz.db" },
  "tls": {
    "keyStore": "certs/server.p12",
    "password": "DIEN_MAT_KHAU_KEYSTORE_CUA_BAN",
    "passwordProtected": ""
  },
  "bootstrap": {
    "adminUsername": "admin",
    "adminPassword": "",
    "adminPasswordProtected": ""
  }
}
```

Server đang có Admin: chỉ cần điền `tls.password` đúng mật khẩu đã tạo khóa; để `bootstrap.adminPassword` trống. Chưa có Admin: điền thêm mật khẩu mới 8–128 ký tự tại `bootstrap.adminPassword`. Đây là thông tin tạo Admin lần đầu, không phải tự động đăng nhập Client hoặc reset mật khẩu Admin cũ.

Nếu Server mới chưa có chứng chỉ, lần mở start.bat sẽ tạo chứng chỉ cho `server.bind` bằng mật khẩu TLS trong config; yêu cầu đường dẫn mặc định `certs/server.p12`. Sau đó cấp truststore công khai cho Client như trước. Nếu đã có chứng chỉ thì giữ nguyên. Sửa IP trong JSON không tự cấp lại chứng chỉ; địa chỉ Client dùng vẫn phải khớp SAN.

Mật khẩu trong ví dụ chỉ là chỗ để thay, không phải mật khẩu mặc định. JSON dùng dấu nháy kép; đường dẫn nên dùng `/`. Nếu mật khẩu có dấu `"` hoặc `\`, dùng JSON escape tương ứng. configure.bat giúp nhập kín và tránh phải tự escape.

## Cách lưu mật khẩu

Lần start đầu đọc mật khẩu từ `tls.password`, chương trình chuyển sang Windows DPAPI, ghi chuỗi mã hóa vào `tls.passwordProtected` rồi để `tls.password` trống. Những lần sau tự giải mã dưới cùng tài khoản Windows. Không ghi mật khẩu vào câu lệnh Java, log hoặc file properties sinh ra.

Sau khi tạo Admin thành công, cả hai trường mật khẩu bootstrap được xóa khỏi JSON; mật khẩu Admin chỉ còn dạng hash trong SQLite. Server đã có Admin không bị thay đổi bởi giá trị bootstrap mới.

File mã hóa gắn với tài khoản Windows và máy đang dùng. Chuyển sang máy/tài khoản Windows khác: mở configure.bat và nhập lại mật khẩu TLS một lần. Không sửa thủ công chuỗi passwordProtected. Muốn thay giá trị đang nhớ, điền lại tls.password hoặc chạy configure.bat.

`server.runtime.properties` là cấu hình công khai được sinh từ JSON cho Java, không chứa mật khẩu. Chỉ sửa **server.json**; sửa file runtime sẽ bị ghi lại khi start. File server.properties cũ được giữ để tham chiếu và chỉ dùng nhập cấu hình khi chưa có server.json.

Tài khoản/mật khẩu đăng nhập của người sử dụng vẫn nhập ở Client như trước; cấu hình tự khởi động này dành cho tiến trình Server.

## Kiểm thử

Đã kiểm tra trên Windows PowerShell 5.1 với Java đi kèm bộ Server: khởi tạo mới từ JSON, tạo TLS/Admin với username và đường dẫn DB tùy chỉnh, chạy lại không hỏi thông tin, mật khẩu TLS không còn dạng rõ, mật khẩu bootstrap được xóa, chứng chỉ không đổi. Có kiểm tra mật khẩu sai, thiếu mật khẩu và chuỗi mã hóa không đọc được: dừng với thông báo rõ, không chuyển sang hỏi tương tác hoặc ghi bí mật vào log.

Bộ Server ZIP phát hành chỉ chứa template với mọi mật khẩu để trống. Không đóng gói JSON đã điền mật khẩu hoặc chuỗi DPAPI cá nhân vào ZIP chung.
