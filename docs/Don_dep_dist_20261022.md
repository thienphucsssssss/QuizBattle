# Thư mục phát hành dùng chính

Theo yêu cầu người dùng, `dist/` hiện chỉ có `QuizBattle/`:

```text
dist/QuizBattle/
  Client/
    QuizBattle-Setup.exe
    SHA256SUMS.txt
  Server/
    start.bat
    config/ certs/ data/ lib/ runtime/ scripts/ ...
  HUONG_DAN.txt
```

Client là bộ cài 0.3.6 mới nhất, lấy từ bản timestamp `20261022-121419`. Server lấy nguyên trạng từ bản `20261022-084345`, vì chứng chỉ đã khớp Client đang cài. So sánh bytecode cho thấy cả 28 class Server/shared giống bản mới nhất; không cần thay mã Server. Toàn bộ 237 tệp Server được kiểm tra SHA256 trước/sau, gồm database, khóa TLS, cấu hình/mật khẩu đã bảo vệ DPAPI. Không reset Admin, gộp ngân hàng hoặc tạo lại chứng chỉ.

12 thư mục cũ chuyển nguyên vẹn ra `backups/dist-archive-20261022-123050/`. Mỗi thư mục giữ tên cũ; các tài khoản/câu hỏi ở Server từng tạo riêng, đặc biệt bản `121419`, vẫn còn trong backup tương ứng nhưng không được tự gộp vào Server dùng chính. Không xóa vật chất các bản cũ. Server không chạy trong lúc sao chép/di chuyển; không đóng Client đang mở và không khởi chạy Server ngoài ý muốn.

Không dùng lại đường dẫn timestamp trong các báo cáo lịch sử để hướng dẫn chạy hằng ngày. Sau các lần build tiếp theo, cập nhật bản dùng chính một cách có kiểm soát; giữ nguyên `Server/data`, `Server/certs`, `Server/config`, không tạo Server mới hoặc thay chứng chỉ khi chỉ nâng Client. Script build timestamp là công cụ tạo release sạch, không phải Server cá nhân để chạy lại từ đầu. Không chuyển toàn bộ Server chứa dữ liệu vào bộ cài phát cho học sinh.

Thao tác file sử dụng PowerShell native, kiểm tra đường dẫn tuyệt đối nằm trong workspace trước từng lần di chuyển. Script thực hiện: `tmp/Consolidate-Dist.ps1`; có điều kiện dừng nếu Server/launcher đang dùng dist hoặc nếu thư mục đích đã tồn tại, không nên chạy lại khi chưa kiểm tra.
