# Nguồn mã và bước đánh giá fingerprinting offline

## Phân biệt tham khảo và tái sử dụng

Phần khảo sát/SQLite/giao thức/JavaFX trong đợt A–B là mã tích hợp của QuizBattle. Trước ngày 29/10/2026, các repo fingerprinting được dùng để tham khảo hướng làm, chưa chuyển mã định vị của chúng vào dự án. JNA, SQLite JDBC, Jackson, JavaFX và các phụ thuộc Maven hiện có vốn là các thư viện mã nguồn mở được sử dụng qua dependency.

Sau yêu cầu ưu tiên nguồn GitHub, đã đọc mã cụ thể, tìm nguồn gốc và chọn **Anyplace của DMSL, Đại học Cyprus** cho lõi KNN/WKNN. Đây là tái sử dụng có sửa đổi, không chỉ nêu tên một repo trong tài liệu.

| Nguồn đã kiểm tra | Mức sử dụng trong dự án |
|---|---|
| [dmsl/anyplace — Algorithms.java](https://github.com/dmsl/anyplace/blob/7229551823757cc39174cc693947d9077e770bfb/clients/android/Anyplace/src/com/dmsl/airplace/algorithms/Algorithms.java) | **Chuyển và sửa lõi số học** Euclidean RSSI, KNN, WKNN; giữ bản quyền và MIT. Khóa commit `7229551…`. |
| [Talentica/WifiIndoorPositioning — Algorithms.java](https://github.com/Talentica/WifiIndoorPositioning/blob/bb20128cf9a117ec1a5dbd3bc0b8207de6f0ec80/app/src/main/java/com/talentica/wifiindoorpositioning/wifiindoorpositioning/core/Algorithms.java) | Đã đọc thuật toán và luồng thu tại ReferencePoint. File thuật toán ghi nguồn Anyplace/MIT, nên lấy trực tiếp bản gốc Anyplace làm cơ sở; không sao chép nguyên ứng dụng Android/Realm. |
| [codedpro/DIY-IPS — CollectRSSIDataActivity.java](https://github.com/codedpro/DIY-IPS-Indoor-Positioning-System/blob/27ea76d07cc07d7e38aa8782e1574147457892ed/IndoorPositioningApp_v2/App/src/main/java/com/scslab/indoorpositioning/CollectRSSIDataActivity.java) | Tham khảo luồng tọa độ, hướng cầm, thu/lưu offline rồi upload. Đã đọc cả `IndoorPositioningRSSIModel.java`: dùng `WifiManager`, cảm biến hướng và nhánh nhận diện theo SSID `SCSLAB_AP`; không áp nguyên cách này cho nhiều AP cùng SSID HUTECH E1. Không sao chép mã; chưa thấy giấy phép cấp repo trong tree commit đã kiểm tra. |
| [darryncampbell/WiFi-RTT-Trilateration](https://github.com/darryncampbell/WiFi-RTT-Trilateration#requirements) | Tham khảo điều kiện triển khai. Yêu cầu Android/Wi-Fi RTT 802.11mc và tọa độ AP; chưa phù hợp điều kiện Windows và chưa biết vị trí AP của bài toán này. Không đưa vào ứng dụng. |
| [lemmingapex/trilateration](https://github.com/lemmingapex/trilateration#trilateration) | Thư viện Java nhận tọa độ mốc và khoảng cách. Không tự đọc Wi-Fi; thiếu các đầu vào cần thiết ở trường nên chưa sử dụng. |

## Phần mã được chuyển và phần thay đổi

- `AnyplaceKnn.java`: chuyển từ `calculateEuclideanDistance` (dòng 244), `calculateAverageKDistanceLocations` (329), `calculateWeightedAverageKDistanceLocations` (372) của commit Anyplace đã khóa. Dùng `double`/record Java thay tọa độ dạng chuỗi; tham số K được truyền vào thay hằng số 4.
- Giữ phép tính khoảng cách trong **không gian RSSI**, trung bình tọa độ KNN và trọng số nghịch đảo khoảng cách WKNN. Không diễn giải độ lệch RSSI thành số mét tới AP.
- Mã gốc gán trọng số 100 khi khoảng cách tín hiệu bằng 0. Bản chuyển chỉ dùng các điểm khớp hoàn toàn trong trường hợp này; nếu tín hiệu giống nhau ở nhiều vị trí khác nhau, lớp mô hình trả trạng thái mơ hồ.
- Giữ các điểm bằng nhau tại biên K để kiểm tra mơ hồ, thay vì tùy thứ tự danh sách mà bỏ một điểm. Kiểm tra vector rỗng, độ dài khác nhau, NaN/Infinity và trọng số quá lớn.
- `FingerprintModel.java` là lớp nối của QuizBattle: dùng khóa **BSSID/tần số**, chỉ lấy mẫu mới hợp lệ và lượt đã xác nhận. Tính trung bình từng lượt rồi trung bình các lượt theo điểm, để lượt thu dài hơn không được nhiều phiếu hơn.
- Khi radio vắng mặt, baseline dùng -110 dBm như mặc định RadioMap Anyplace. Véc-tơ ghép theo khóa radio; khoảng cách Euclidean chia căn số chiều tham gia thành RMS dB. Vì xử lý số radio thiếu khác nhau, kết quả mô hình đầy đủ không được tuyên bố giống hệt Anyplace.
- Ngưỡng thử ban đầu: ít nhất 2 radio trùng, Jaccard radio chung/tổng ≥ 0.5, RMS ≤ 20 dB; khoảng cách lớn nhất giữa các điểm hàng xóm ≤ 10 m. Trộn vùng Phòng/Hành lang bị từ chối. Các ngưỡng này nằm trong `Parameters`, **chưa được hiệu chỉnh ở HUTECH**.
- Khoảng cách tới server là `hypot(x-serverX, y-serverY)` trên hệ tọa độ mét. Chỉ là đường thẳng 2D; chưa có bản đồ đa giác tường hoặc chênh lệch độ cao.

Chi tiết nguồn, SHA-256, giấy phép và thay đổi ở [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Giấy phép đi cùng trong JAR, không chỉ có ở README.

## Dùng công cụ đánh giá offline

Đây là bước đầu đợt C, chưa tích hợp vị trí trực tiếp vào phòng thi. Cần xuất một phiên bản khu từ màn khảo sát, có ít nhất 2 điểm huấn luyện và lượt kiểm tra đã xác nhận. Thu các lượt kiểm tra vào thời điểm khác; nếu dùng laptop khác, đặt nhãn thiết bị riêng.

Chọn trước các `id` trong mảng `runs` làm tập kiểm tra. Mỗi ID đại diện **toàn bộ lượt**, không chia những scan gần giống nhau của một lượt sang cả huấn luyện và kiểm tra. Các lượt ACCEPTED còn lại làm huấn luyện; lượt bị loại, gián đoạn hoặc chờ xác nhận không được dùng.

Chạy từ thư mục dự án bằng PowerShell, thay đường dẫn và UUID bằng dữ liệu của nhóm:

```powershell
.\scripts\Evaluate-WifiSurvey.ps1 `
  -SurveyJson 'C:\KhaoSat\tang-5-v1.json' `
  -OutputJson 'C:\KhaoSat\bao-cao-luot-1.json' `
  -HoldoutRunIds @('UUID-luot-kiem-tra-1', 'UUID-luot-kiem-tra-2') `
  -K 3
```

Script build module Java và chạy `FingerprintCli`. Không cần Server; không sửa database. Nếu báo cáo đã tồn tại thì từ chối ghi đè, hãy dùng tên khác. File báo cáo chứa tham số, hash bộ dữ liệu, ID huấn luyện/kiểm tra, kết quả KNN/WKNN theo lượt, cờ thiết bị mới, sai số vị trí và sai số khoảng cách Server riêng, trung vị, P90 (nearest-rank), số lượt/tỷ lệ không định vị được.

Các mức lỗi chỉ tính trên lượt được định vị; phải xem cùng tỷ lệ bị từ chối. Nếu toàn bộ bị từ chối, lỗi trung vị/P90 là `null`, không phải 0 m. Không dùng tập kiểm tra này vừa chọn K/ngưỡng vừa báo sai số cuối cùng; cần tập xác nhận riêng sau khi chọn tham số. Kiểm tra ID/scan/timestamp chỉ phát hiện trùng dữ liệu, không chứng minh hai lượt thu độc lập về thực nghiệm.

## Kiểm chứng nguồn chuyển

Đã chạy **mã Anyplace nguyên bản** tại commit trên bằng Java 21 với RadioMap trong RAM, tạo 12 kết quả chuẩn: hai véc-tơ RSSI × K=1/2/4 × KNN/WKNN. Bản chuyển khớp trong sai số số thực `1e-5` ở trường hợp thông thường. Fixture ghi nguồn và hash tại `nqb-shared/src/test/resources/fingerprint/anyplace-golden.json`; các thay đổi zero-distance/ties có test riêng.

Test bổ sung kiểm tra khoảng cách đường thẳng, bỏ cache, radio khác tần số, mơ hồ khác vùng/cùng fingerprint, hàng xóm quá xa, không có radio chung, lỗi toàn bộ bị từ chối, mẫu kiểm tra trùng huấn luyện dù đổi ID, và bảo vệ file đầu vào/báo cáo đã tồn tại. Chạy `mvn -q -pl nqb-shared test`.

Các fixture là dữ liệu tổng hợp để kiểm tra phép tính. Chưa có bộ pilot đo tại hành lang HUTECH, chưa có sai số thực địa được xác nhận, chưa phát hành EXE mới hoặc bật định vị trực tiếp.
