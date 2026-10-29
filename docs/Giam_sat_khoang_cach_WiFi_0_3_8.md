# Giám sát khoảng cách Wi-Fi (ước lượng) — Bản 0.3.8

Tài liệu hướng dẫn cấp quyền đọc Wi-Fi trên Windows, hiệu chuẩn hai bước (1 m và 5 m), quy trình tổ chức thi có giám sát khoảng cách trong LAN và cách ghi nhận sai số thực nghiệm. Đã cập nhật sau đợt review ngày 26/10/2026.

---

## 1. Hướng dẫn bật quyền truy cập vị trí trên Windows

Windows có thể yêu cầu quyền truy cập vị trí (**Location services**) khi ứng dụng đọc thông tin Wi-Fi từ `wlanapi.dll`, tùy phiên bản và thiết lập quyền của hệ điều hành. QuizBattle hiển thị hướng dẫn khi Windows trả về lỗi từ chối truy cập.

1. QuizBattle **không** kiểm tra quyền ở màn hình đăng nhập; ứng dụng chỉ kiểm tra khi sinh viên bấm **Hiệu chuẩn Wi-Fi** hoặc **Sẵn sàng** trong phòng bật giám sát.
2. Từ Client 0.4.1, nếu chưa đọc được Wi-Fi, hộp thoại giải thích mục đích và có nút **Cho phép**. Ứng dụng gọi API xin quyền của Windows; người dùng đồng ý nếu hệ điều hành hiện hộp thoại.
3. Sau yêu cầu quyền, Client đọc lại Wi-Fi và tự tiếp tục hiệu chuẩn. Windows thường chỉ hỏi lần đầu. Nếu đã từ chối hoặc dịch vụ vị trí bị tắt/chặn, ứng dụng hiển thị **Mở cài đặt và cấp quyền** để mở thẳng trang tương ứng, không đưa đường dẫn bắt người dùng tự nhập. Bật quyền rồi quay về QuizBattle: ứng dụng tự kiểm tra và tiếp tục. Có **Kiểm tra lại** và **Để sau**. Xem [Cấp quyền Wi-Fi 0.4.1](Cap_quyen_WiFi_0_4_1.md).
4. Phân biệt với các nguyên nhân thiết bị khác:
   - **Tắt Wi-Fi**: hiển thị thông báo công tắc Wi-Fi đang tắt, hướng dẫn bật Wi-Fi và kết nối vào điểm phát của phòng.
   - **Không có card Wi-Fi**: thông báo máy không có card mạng không dây hỗ trợ (ví dụ máy bàn chỉ gắn cáp LAN); chủ phòng có thể tắt giám sát khoảng cách trong **Cấu hình phòng** nếu tổ chức thi trên các máy này.
   - **Chưa kết nối hoặc kết nối sai điểm phát / sai băng tần**: hiển thị rõ BSSID và băng tần đang kết nối so với điểm phát làm mốc của phòng để sinh viên chuyển đúng mạng.

---

## 2. Hướng dẫn hiệu chuẩn từng laptop (1 m và 5 m)

Do mỗi dòng card Wi-Fi và thiết kế ăng-ten trên màn hình laptop có độ nhạy thu khác nhau, từng laptop sinh viên cần hiệu chuẩn theo điểm phát làm mốc của phòng trước khi bấm **Sẵn sàng**.

### Các bước thực hiện lần đầu
1. Sinh viên tham gia phòng chờ và bấm **Hiệu chuẩn Wi-Fi**.
2. **Bước 1 (1 m)**: Đặt laptop cách điểm phát Wi-Fi (router hoặc điện thoại phát sóng) khoảng **1 m**, giữ máy cố định và bấm **Đo bước 1 m (30 giây)**. Hệ thống lấy mẫu mỗi giây trong 30 giây.
3. **Bước 2 (5 m)**: Di chuyển laptop ra vị trí cách điểm phát khoảng **5 m**, giữ máy cố định và bấm **Đo bước 5 m (30 giây)**.
4. Điều kiện đạt ở mỗi bước:
   - Tối thiểu **20 mẫu hợp lệ** (RSSI trong khoảng `-100` đến `-15 dBm`, đúng BSSID và băng tần mốc).
   - Độ lệch tuyệt đối trung vị (**MAD**) không vượt quá **6 dB**.
   - Sau hai bước, mức thu ở 1 m phải cao hơn ở 5 m và hệ số suy hao đường truyền $n$ nằm trong khoảng **1.0 – 6.0**:
     $$n = \frac{\text{RSSI}_1 - \text{RSSI}_2}{10 \times \log_{10}(5 / 1)}$$
5. Nếu một bước có nhiễu lớn hoặc thiếu mẫu, bấm **Đo lại bước 1 m** hoặc **Đo lại bước 5 m**. Khi cả hai bước đạt yêu cầu, bấm **Hoàn tất hiệu chuẩn**.

### Dùng lại hồ sơ trong buổi sau và khi nào cần hiệu chuẩn lại
- Hồ sơ hiệu chuẩn được lưu cục bộ tại `~/.nqb/proximity-calibrations.json` theo bộ khóa `(adapterId, BSSID, băng tần)`.
- Ở buổi thi sau với cùng card Wi-Fi, cùng BSSID và cùng băng tần, ứng dụng hiển thị hộp thoại xác nhận: sinh viên đánh dấu **xác nhận điểm phát vẫn đặt ở cùng vị trí** rồi bấm **Dùng hồ sơ này** mà không phải đo lại 60 giây.
- Tự động yêu cầu hiệu chuẩn lại khi:
  - Đổi card Wi-Fi (ví dụ cắm thêm USB Wi-Fi).
  - Đổi sang điểm phát khác (khác BSSID) hoặc chuyển giữa băng tần `2.4 GHz` và `5 GHz`.
  - Chủ phòng bấm **Hiệu chuẩn lại cả phòng** sau khi dời vị trí đặt router/điện thoại phát sóng.

---

## 3. Quy trình tổ chức và demo trước lớp

1. **Tạo phòng và chọn điểm phát làm mốc**:
   - Giảng viên/Admin đăng nhập, vào **Phòng thi → Cấu hình phòng**.
   - Tính năng **Giám sát khoảng cách Wi-Fi (ước lượng)** mặc định được bật.
   - Chọn **Điểm phát làm mốc** (xác định bằng cả **BSSID và băng tần**):
     - Nếu laptop giảng viên đang nối Wi-Fi của lớp: chọn trực tiếp điểm phát đang kết nối trên máy giảng viên.
     - Nếu máy giảng viên dùng dây mạng LAN: chờ một máy sinh viên vào phòng chờ, sau đó chọn điểm phát từ danh sách máy sinh viên đã báo cáo.
   - Giữ ngưỡng cảnh báo mặc định **10 m** (hoặc chỉnh trong khoảng 2–100 m trước khi bắt đầu). Khả năng nhập ngưỡng lớn không bảo đảm độ chính xác ở khoảng cách đó.
2. **Mở bảng Giám sát trực tiếp**:
   - Bấm nút **Giám sát** tại phòng chờ hoặc trên thanh điều khiển trận để mở cửa sổ **Giám sát khoảng cách Wi-Fi** độc lập.
   - Cửa sổ này giữ nguyên trạng thái mở xuyên suốt khi bắt đầu ván, chuyển câu hỏi, hiện đáp án và bảng xếp hạng.
   - Màn hình **Trình chiếu (F11)** chỉ hiển thị câu hỏi và bảng hạng, không hiển thị dữ liệu giám sát.
3. **Kiểm tra điều kiện trước trận**:
   - Sinh viên hoàn tất hiệu chuẩn và gửi báo cáo tín hiệu mới (trong vòng 15 giây) mới bật được **Sẵn sàng**.
   - Nếu sinh viên tắt Wi-Fi, đổi điểm phát hoặc mất dữ liệu quá 15 giây khi đang ở phòng chờ, Server tự hủy trạng thái sẵn sàng của sinh viên đó và chặn lệnh **Bắt đầu** cho đến khi khắc phục.
4. **Trong khi thi**:
   - Client đọc RSSI mỗi giây trên luồng nền riêng và gửi báo cáo lên Server tối đa mỗi **5 giây**.
   - Server lọc trung vị **5 mẫu gần nhất** và tính khoảng cách ước lượng:
     $$d = d_1 \times 10^{\frac{\text{RSSI}_1 - \text{RSSI}}{10 \times n}}$$
   - Hiển thị dưới dạng `≈ 6 m (ước lượng)`. Nếu ngoài khoảng hiệu chuẩn 1–5 m, phần chi tiết ghi rõ kết quả được ngoại suy.
   - **Cảnh báo vượt ngưỡng**: kích hoạt sau **3 báo cáo hợp lệ liên tiếp** vượt ngưỡng; tự xóa cảnh báo sau **3 báo cáo hợp lệ liên tiếp** giảm xuống dưới **80% ngưỡng** (ví dụ `< 8 m` khi ngưỡng là `10 m`).
   - **Tách biệt cảnh báo**: cảnh báo vượt ngưỡng được tách riêng khỏi cảnh báo mất dữ liệu (> 15 giây không có mẫu) và cảnh báo đổi điểm phát/băng tần.
   - **An toàn điểm số**: khi ván thi đã bắt đầu, việc vượt ngưỡng hoặc mất dữ liệu chỉ ghi nhận cảnh báo cho giảng viên, **không tự loại người chơi và không thay đổi điểm số**.
5. **Xem lại lịch sử giám sát**:
   - Vào mục **Lịch sử → Nhật ký giám sát** (hoặc bấm **Xem giám sát** từ một ván trong bảng điểm).
   - Xem tóm tắt từng sinh viên (khoảng cách ước lượng nhỏ nhất/lớn nhất, tổng thời gian thiếu dữ liệu, số lần cảnh báo) và dòng thời gian cảnh báo, phân trang 20 sự kiện/trang.
   - Ván kết thúc hoặc hủy được gửi vào hàng đợi lưu. Khi Server bị tắt đột ngột, lịch sử chỉ giữ dữ liệu đã ghi trước đó; các phiên còn RUNNING được đánh dấu INTERRUPTED khi khởi động lại. Dữ liệu đang chờ ghi có thể mất. Lỗi lưu được thông báo cho quản lý.

---

## 4. Trạng thái kiểm chứng và đo sai số thực tế

### 4.1. Phần đã kiểm tra và phần còn cần đo

Kiểm thử phần mềm kiểm tra công thức, xử lý trạng thái, giao thức TLS, lưu lịch sử và quyền truy cập. Kiểm thử đọc Wi-Fi đã nhận được thông tin kết nối trên card Intel(R) Wi-Fi 6 AX201 160MHz qua JNA. Đây là bằng chứng tích hợp API hoạt động trên thiết bị đó.

**Chưa có bộ số liệu khoảng cách thực tế đã được xác minh.** Các giá trị RSSI dùng trong kiểm thử tự động là dữ liệu giả lập, không phải kết quả đặt laptop tại các mốc trong lớp học. Không dùng chúng để kết luận sai số hay độ chính xác của sản phẩm. Bảng số liệu từng ghi trong phiên bản trước đã được bỏ vì chưa có bản ghi thực nghiệm đối chiếu.

### 4.2. Mẫu ghi nhận cho buổi thử tại lớp

Ghi ngày đo, phòng, model router/điện thoại, laptop/card Wi-Fi, BSSID, băng tần, vị trí và hướng đặt máy. Dùng thước đo mốc 1 m và 5 m để hiệu chuẩn. Sau đó đo tại các vị trí độc lập, giữ yên ít nhất 30 giây mỗi lượt và lặp lại tối thiểu ba lượt. Giữ bản ghi từng lượt, không chỉ ghi giá trị tốt nhất.

| Tình huống dự kiến | Khoảng cách thực đo bằng thước | RSSI trung vị | Khoảng cách ước lượng | Sai số tuyệt đối | Ngày/lượt đo |
|---|---|---|---|---|---|
| Tầm nhìn thẳng, gần điểm phát | Chưa đo | — | — | — | — |
| Tầm nhìn thẳng, giữa phòng | Chưa đo | — | — | — | — |
| Ngoài vùng hiệu chuẩn 1–5 m | Chưa đo | — | — | — | — |
| Có người hoặc vách ngăn che chắn | Chưa đo | — | — | — | — |
| Xoay hướng laptop tại cùng vị trí | Chưa đo | — | — | — | — |

Sai số tuyệt đối mỗi lượt = `|khoảng cách ước lượng − khoảng cách thực|`. Khi báo cáo, nêu cả sai số trung bình và lớn nhất, điều kiện đo và số lượt. Kiểm tra riêng cảnh báo vượt ngưỡng, phục hồi và mất tín hiệu.

### 4.3. Giới hạn cần thể hiện trong báo cáo

- Vật cản, hướng laptop, phản xạ sóng và thay đổi công suất phát có thể làm RSSI thay đổi dù khoảng cách không đổi. Hiệu chuẩn phải phù hợp thiết bị và môi trường sử dụng.
- Ngoài vùng hiệu chuẩn 1–5 m, kết quả là ngoại suy. Giới hạn giá trị hiển thị hoặc ngưỡng cấu hình không thể hiện phạm vi đo chính xác đã kiểm chứng.
- Cùng tên Wi-Fi chưa chắc cùng BSSID/băng tần. Đổi điểm phát hoặc băng tần cần kết nối lại đúng mốc hoặc hiệu chuẩn lại.
- Driver phải cung cấp RSSI kết nối hiện tại và thông tin băng tần. Ứng dụng báo lỗi khi không lấy được dữ liệu, không dùng RSSI cũ trong danh sách quét làm mẫu đo mới.
- Client gửi RSSI và mẫu hiệu chuẩn lên Server. Kiểm tra phiên, kiểu dữ liệu và giới hạn tần suất không chứng minh tính trung thực của thiết bị gửi; Client bị sửa có thể gửi số liệu giả. Tính năng hỗ trợ giám sát, không phải cơ chế chống gian lận hay xác định tọa độ tuyệt đối.
