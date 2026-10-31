# Ước lượng Client → AP và Server → AP tự động

Mỗi Client đọc RSSI và BSSID của **AP đang kết nối**, gửi về Server khoảng 5 giây/lần. Không cần nhập AP hoặc đứng ở mốc 1 m / 5 m để có số ước lượng. LAN, VPN hay Internet chỉ là đường truyền báo cáo; Server không thể suy ra RSSI của Client từ địa chỉ IP hoặc ping.

Server đọc card Wi-Fi **trên máy chạy tiến trình Server**, khoảng 1 giây/lần. Số Server → AP không lấy từ máy giảng viên. Máy chỉ dùng Ethernet và không kết nối Wi-Fi, không có card Wi-Fi, bị từ chối quyền vị trí, hoặc không đọc được RSSI sẽ hiện trạng thái tương ứng và không có số mét. Nếu có nhiều card Wi-Fi đang kết nối, provider hiện tại chọn một card theo thứ tự hệ điều hành trả về; BSSID/card thực sự dùng được đính kèm số đo.

## Cách dùng

1. Chạy bản Server và Client mới, cho từng máy kết nối Wi-Fi và tham gia phòng.
2. Client tự gửi mẫu ngay khi vào phòng; chờ tối đa một chu kỳ báo cáo để thấy số. Nếu Windows chặn đọc Wi-Fi, mở **Đang đo Wi-Fi / Bật đo Wi-Fi** để xem chẩn đoán và cấp quyền vị trí trên chính máy đó.
3. Xem **Client → AP** và **Server → AP** trong cửa sổ giám sát; bảng danh sách và chi tiết cho biết phương pháp. Trong bản đồ, chế độ **Theo dõi Wi-Fi** cũng hiển thị hai số AP riêng biệt.
4. Di chuyển Client hoặc chuyển AP: ứng dụng dùng RSSI mới và bỏ mẫu của AP/card/băng tần cũ. Mặc định, việc này không hủy trạng thái sẵn sàng hay tự đẩy Client khỏi phòng. Khả năng nối lại khi roaming mất mạng vẫn dùng cơ chế giữ phiên hiện có.

Hiệu chuẩn hai mốc vẫn nằm trong **Cấu hình phòng → Giám sát theo mốc đã hiệu chuẩn (nâng cao)**. Chỉ khi chủ phòng chủ động chọn **Bắt buộc hiệu chuẩn trước khi sẵn sàng** thì điều kiện AP/hiệu chuẩn/mẫu mới mới chặn nút sẵn sàng. Hồ sơ hợp lệ được ưu tiên cho đúng card, BSSID và băng tần; chuyển AP sẽ quay về mô hình mặc định. Cảnh báo và lịch sử khoảng cách cũ chỉ ghi số đã hiệu chuẩn, không tự trộn số mặc định vào đó.

## Mô hình và giới hạn

Dùng lại phép đảo mô hình log-distance trong `ProximityRules`:

`d = 10 ^ ((RSSI_1m - median(RSSI)) / (10 * n))`

| Tham số giả định để demo | Giá trị |
| --- | --- |
| RSSI tại 1 m, băng 2.4 GHz | −40 dBm |
| RSSI tại 1 m, băng 5 GHz | −46 dBm |
| RSSI tại 1 m, băng 6 GHz | −48 dBm |
| Hệ số suy hao n | 3 |

Đây là **tham số quy ước chưa được đo tại HUTECH**, không phải dữ liệu hiệu chuẩn hoặc thông số đã xác minh của router. Trung vị tối đa 5 mẫu giảm dao động; dữ liệu quá 15 giây không được hiện thành số đo còn mới. Miền hiển thị được chặn ở 0.2–200 m, đánh dấu giới hạn nếu bị chặn. Thông tin phương pháp, tham số, độ lệch MAD và BSSID đi cùng số mét trong `ap_distance` / `server_ap_distance`.

Tường, người, công suất phát, anten và thiết bị nhận đều ảnh hưởng RSSI. Số có thể đổi khi đứng yên, giữ nguyên khi di chuyển hoặc đổi đột ngột lúc roaming. Chưa có kiểm chứng sai số thực địa. Giao diện luôn ghi **Ước lượng thô · chưa hiệu chuẩn** khi dùng mô hình mặc định.

Hai khoảng cách Client → AP và Server → AP **không đủ để suy ra vị trí Client hoặc khoảng cách Client → Server**, kể cả cùng một AP. Số đường thẳng trên bản đồ vẫn lấy từ tọa độ fingerprint với khảo sát chung, hoặc từ chế độ mô phỏng được ghi nhãn rõ. Không tự cộng/trừ hai số AP thành khoảng cách giữa hai máy.

## Tham khảo và tái sử dụng

- [Mô hình log-distance của ns-3](https://www.nsnam.org/docs/models/html/propagation.html#logdistancepropagationlossmodel): tham khảo công thức; không nhập mã nguồn hay phụ thuộc ns-3.
- [Microsoft Native Wi-Fi: WLAN_BSS_ENTRY](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/ns-wlanapi-wlan_bss_entry): trường BSSID/RSSI/tần số.
- [Microsoft: thay đổi quyền truy cập Wi-Fi và vị trí](https://learn.microsoft.com/en-us/windows/win32/nativewifi/wi-fi-access-location-changes).

Server tái sử dụng `WindowsWifiSignalProvider` và JNA từ module `nqb-client-core` hiện có; module này không có JavaFX. Không thêm thư viện đo khoảng cách mới. Khi phát hành cần đóng gói lại **cả Server và Client** để có tính năng này; EXE/ZIP 0.6.4 trước đó chưa được cập nhật.

## Kiểm chứng trong đợt triển khai

- 230 kiểm thử đạt: mô hình RSSI, đổi AP/card/băng tần, loại số đo cũ, lỗi quyền, nguồn đo riêng trên Server, TLS và chế độ hiệu chuẩn nâng cao.
- Kiểm tra JavaFX ở 1024 × 640 và 1366 × 768: vào phòng không hiệu chuẩn, RSSI Client giả lập thay đổi/roaming, giữ Sẵn sàng, mất quyền thì ẩn số mét; giám sát hiển thị Client → AP và Server → AP riêng.
- Kiểm tra lại bản đồ: mô phỏng không lẫn sang dữ liệu thật, mẫu vị trí hết hạn không còn hiện chấm Client.
- RSSI Client trong bài kiểm tra giao diện là dữ liệu giả lập có nhãn QA; Server đọc Wi-Fi thật của máy chạy kiểm tra. Những kết quả này xác nhận hành vi phần mềm, chưa chứng minh sai số khoảng cách tại trường.
