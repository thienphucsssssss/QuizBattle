# Khảo sát Wi-Fi tại các điểm có tọa độ

Đợt B thêm sổ khảo sát trên Client bằng Java 21/JavaFX và SQLite. Có thể thu mẫu khi chưa kết nối Server. Dữ liệu này dùng cho [baseline KNN/WKNN và công cụ đánh giá offline](Nguon_mo_va_danh_gia_fingerprinting.md); ứng dụng **chưa hiển thị tọa độ client hoặc khoảng cách client–server trực tiếp trong phòng thi**.

Client 0.6.4 thêm nút **Đánh giá định vị** trong **Lượt đã lưu**. Khi chưa có dữ liệu thực địa, có thể vào **Tùy chọn → Đánh giá định vị Wi-Fi → Thử mô phỏng tại nhà**; xem [hướng dẫn thử offline](Danh_gia_WiFi_tai_nha_0_6_4.md).

## Cách dùng

1. Trước hoặc sau ván thi, mở **Tùy chọn → Khảo sát Wi-Fi → Tạo khu**. Không khảo sát trong lúc đang thi.
2. Nhập tên khu, nhãn người khảo sát và đúng SSID, ví dụ `HUTECH E1`. Không cần biết vị trí vật lý của các AP.
3. Chọn cửa phòng làm `(0, 0)`. Quy ước X dọc hành lang thẳng, Y dương hướng vào phòng; ghi rõ chiều X tăng để mọi người dùng cùng quy ước. Đo vị trí server cố định bằng mét và nhập X/Y.
4. Nhập các điểm đo, mỗi dòng theo mẫu `Tên điểm; Phòng hoặc Hành lang; X; Y`. Dùng dấu chấm thập phân. Ví dụ minh họa, **không phải số đo thực tại HUTECH**:

   ```text
   Cửa phòng; Hành lang; 0; 0
   Bàn server; Phòng; 2; 4
   Mốc 5 m; Hành lang; 5; 0
   Mốc 10 m; Hành lang; 10; 0
   ```

   Điểm hành lang có Y = 0; điểm trong phòng giữ cả X và Y. Nên lập trước khoảng 10–15 điểm cho lượt pilot, gồm trong phòng, cửa và hành lang hai phía nếu đi được. Số điểm cần thiết sẽ điều chỉnh sau khi đánh giá dữ liệu.
5. Lưu khu, chọn điểm đang đứng và card Wi-Fi vật lý. Nhập nhãn laptop và hướng nhìn/cầm máy, ví dụ “Nhìn theo X tăng”. Đây là ghi chú do người khảo sát nhập, không phải hướng la bàn đo tự động.
6. Đứng yên đúng điểm, chọn 20/30/60 giây và **Bắt đầu thu mẫu**. Ứng dụng yêu cầu scan, chờ hoàn tất, lưu vào SQLite rồi đợi ít nhất 5 giây trước scan tiếp theo. Card/SSID/điểm của lượt được cố định. Lượt có thể kết thúc chậm hơn thời lượng chọn một ít vì chờ scan đang chạy.
7. Sang **Lượt đã lưu**, chọn lượt và xem số scan mới, BSSID/tần số, nhãn thiết bị, hướng cầm. Chỉ bấm **Xác nhận đúng điểm** nếu đứng yên đúng điểm trong cả lượt. Nếu đã di chuyển, chọn **Loại lượt thu** hoặc hủy khi đang thu.
8. Đi đến điểm tiếp theo rồi tạo lượt mới. Thu thêm lượt với hướng nhìn ngược lại; dùng cùng laptop cho bộ pilot và kiểm tra ban đầu. Giữ các lượt kiểm tra độc lập, không chia scan của cùng một lượt vào cả tập huấn luyện và tập kiểm tra.

Tối thiểu **3 scan có dữ liệu mới và 2 BSSID/tần số** mới cho xác nhận lượt thu. Đây là ngưỡng kiểm tra thu thập ban đầu, không bảo đảm đủ dữ liệu định vị hay sai số 1–2 m. Mẫu cache vẫn được giữ trong dữ liệu thô nhưng không cộng vào số mẫu mới. Nhiều BSSID không tương đương nhiều AP vật lý.

## Lưu trữ, phiên bản và sao lưu

- Client lưu mặc định ở `%USERPROFILE%\.quizbattle\wifi-surveys.db`, độc lập database Server. Có thể đổi bằng thuộc tính JVM `nqb.survey.database` phục vụ kiểm thử hoặc kho mới.
- Mỗi scan được ghi nguyên tử: trạng thái, thời gian, adapter, RSSI, BSSID, tần số, timestamp từ Windows, tuổi mẫu và cờ mới/cũ. Đóng ứng dụng giữa lượt: lần mở sau đánh dấu **Bị gián đoạn**, giữ các scan đã ghi, không tự xác nhận.
- **Phiên bản mới** tạo bản tọa độ mới. Không sửa lượt đã xác nhận và không tự chuyển mẫu cũ sang tọa độ mới; chọn lại phiên bản cũ để xem/xuất dữ liệu đó. Nếu khu có nhiều nhánh phiên bản nhập từ máy khác, tạo tiếp từ phiên bản cao nhất để tránh trùng số.
- **Xuất JSON** xuất khu đang chọn cùng mọi lượt của phiên bản đó, kể cả lượt bị loại/gián đoạn. File có `schemaVersion: 1`, `purpose: wifi_fingerprint_survey`, đơn vị `m`.
- **Nhập JSON** đọc và kiểm tra toàn bộ, hiện số khu/lượt rồi yêu cầu xác nhận. Dữ liệu trùng khớp được bỏ qua; cùng ID nhưng khác nội dung bị từ chối và toàn bộ lần nhập được rollback. Không nhập JSON chẩn đoán một scan vào kho khảo sát.
- Giới hạn: 100 điểm/khu, 200 phiên bản khu và 500 lượt/chủ sở hữu trong một kho, 60 scan/lượt, 64 quan sát/scan, file nhập/xuất tối đa 32 MiB. Bản đầu giữ dữ liệu thay vì tự xóa khi đầy.
- Ưu tiên xuất JSON để trao đổi/sao lưu. Nếu sao lưu SQLite trực tiếp, đóng ứng dụng hoàn toàn trước khi sao chép để không bỏ sót dữ liệu trong WAL. File chứa dấu vết BSSID và tọa độ do nhóm đo, chỉ chia sẻ theo nhu cầu của nhóm.

## Đồng bộ lên Server

Thu offline không cần đăng nhập. Để đồng bộ, đăng nhập **Giảng viên hoặc Admin** vào Server mới rồi mở lại màn khảo sát, chọn khu và **Đồng bộ lên Server**. Hộp xác nhận nêu rõ gửi tọa độ và dữ liệu thô cho tài khoản đang đăng nhập. Lượt còn **Chờ xác nhận** chưa được gửi; các lượt đã xác nhận/loại/gián đoạn được gửi cùng trạng thái.

Server gán quyền sở hữu theo tài khoản đã xác thực, không tin nhãn người khảo sát hay `owner_id` từ file/client. Học sinh không được gọi API đồng bộ. Dữ liệu khảo sát không được phát trong danh sách phòng hoặc trạng thái phòng công khai.

Mỗi scan đi trong một bản tin TLS, dưới giới hạn 64 KiB. Server chỉ công bố lượt sau khi đủ số scan, kiểm tra cấu trúc, thứ tự thời gian, mẫu mới/cũ và SHA-256 toàn bộ lượt. Dấu kiểm này dùng phát hiện sai khác/idempotency; không chứng minh người khảo sát đứng đúng vị trí thực.

Client gửi lần lượt, có khoảng nghỉ giữa các yêu cầu để không vượt giới hạn tốc độ Server khi đồng bộ nhiều điểm. Một `scanId` không được gán cho hai lượt/điểm khác nhau trong cùng kho sở hữu.

Nếu mất mạng: file cục bộ vẫn còn. Đăng nhập lại và bấm đồng bộ lại; các bản ghi trùng khớp không tạo thêm hàng. Upload dở được giữ qua khởi động lại Server, tối đa 4 lượt dở/tài khoản, hết hạn sau 24 giờ khi dọn ở lần bắt đầu upload tiếp theo. Đợt này có gửi lên Server; trao đổi/tải sang laptop khác dùng file JSON, chưa có trình duyệt bản đồ trên Server.

Server tự migration schema 4 → 5 khi chạy bản mới, thêm các bảng `wifi_survey_*`. Việc đọc/ghi khảo sát chạy qua kết nối và worker riêng, hàng đợi 32 tác vụ. Bộ test kiểm tra dữ liệu tài khoản/câu hỏi/lịch sử/giám sát cũ còn nguyên. Sao lưu database trước khi nâng phiên bản vận hành như thường lệ.

## Nghiệm thu thực địa còn cần làm

Kết nối Tailscale giữa hai laptop và roaming thật vẫn cần thử theo [hướng dẫn đợt A](Ket_noi_mang_rieng_va_chan_doan_WiFi.md). Màn khảo sát không phụ thuộc việc hoàn tất phép thử đó.

Đầu tiên thu thử 2–3 điểm trên HUTECH E1, xem có ít nhất 3 scan mới và nhiều BSSID/tần số mỗi lượt hay không. Nếu thiếu, xem chẩn đoán Wi-Fi/quyền Location, thử thời lượng dài hơn và kiểm tra driver; không đánh dấu mẫu cache thành mới để đủ số lượng. Sau đó mới thu toàn bộ khu. Cần dữ liệu thật và tọa độ đo độc lập trước khi chọn KNN/WKNN, đánh giá sai số trung vị/P90, và hiển thị khoảng cách tới Server.

Các bài test giả lập kiểm tra logic thu/lưu/đồng bộ và UI; chúng không phải bằng chứng độ chính xác định vị thực tế. Chưa cập nhật bộ cài EXE trong đợt thay đổi mã nguồn này.

## Kiểm chứng phần mềm ngày 29/10/2026

- Kiểm thử SQLite: đóng/mở lại, phục hồi lượt gián đoạn, import nguyên tử/idempotency, tọa độ và timestamp sai, mẫu cache giả mới, một scan bị gán sang điểm khác, upload dở qua khởi động lại.
- Kiểm thử thu mẫu: tiến độ, xác nhận thủ công, hủy scan đang chạy, giải phóng card; mất phiên Server không hủy lượt khảo sát offline.
- TLS thật: học sinh bị từ chối, không giả mạo chủ sở hữu, tài khoản khác không chèn vào upload; gửi 13 lượt/39 scan, đăng nhập lại và đồng bộ lặp không nhân bản. Trạng thái phòng không lộ khảo sát.
- Migration database v4 và các dạng v2/v3 cũ, hồi quy bộ tính năng hiện có.
- JavaFX: tạo khu, thu 20 giây bằng fixture được ghi rõ là giả lập, xác nhận, mở lại, hủy; ảnh kiểm tra trong `output/phase-b-ui-1024/` và `output/phase-b-ui-1366/` (không đưa vào Git).
- Card Wi-Fi thật trên mạng đang dùng: thu thử 20 giây, lưu được 3 scan mới, 4 cặp BSSID/tần số. Tọa độ chỉ là giả định cho kiểm thử, dữ liệu nằm ở database tạm và bị đánh dấu loại bỏ. Không dùng kết quả này làm bộ pilot HUTECH E1.

Chạy bộ test bằng `mvn -q test`. Kiểm tra native riêng khi cần: `mvn -q -pl nqb-client-core -am '-Dtest=SurveyCaptureServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnqb.native.survey=true' test` (PowerShell). Phép thử native cần Windows, card Wi-Fi đang kết nối và quyền đọc Wi-Fi.
