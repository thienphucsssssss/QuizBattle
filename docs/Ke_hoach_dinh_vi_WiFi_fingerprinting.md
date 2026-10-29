# Kế hoạch định vị Wi-Fi và giữ phiên khi roaming

Trạng thái: đã triển khai phần mềm đợt A, luồng khảo sát đợt B, và bước đầu đợt C: baseline KNN/WKNN chuyển từ Anyplace/MIT kèm đánh giá offline theo lượt giữ riêng. Xem [hướng dẫn đợt A](Ket_noi_mang_rieng_va_chan_doan_WiFi.md), [khảo sát đợt B](Khao_sat_WiFi_fingerprinting.md) và [nguồn mã/đánh giá offline](Nguon_mo_va_danh_gia_fingerprinting.md). Kết nối Tailscale giữa hai laptop, roaming và bộ pilot thật còn cần nghiệm thu. Chưa chọn mô hình theo sai số thực địa, chưa triển khai đợt D; test giả lập không thay thế đánh giá tại trường.

## Mục tiêu đã thống nhất

- Server cố định trong phòng; client Windows di chuyển trong phòng và dọc một hành lang thẳng, cùng tầng.
- Client tiếp tục dùng HUTECH E1 để đọc tín hiệu Wi-Fi. Tailscale là đường truyền Client–Server khi mạng trường chặn giao tiếp trực tiếp. LAN hiện có vẫn dùng được.
- Nhận diện BSSID đang kết nối và lịch sử chuyển BSSID; tự lưu các BSSID quan sát được trong đợt khảo sát. Chỉ gộp thành một AP vật lý khi có thông tin xác nhận.
- Khảo sát tại các điểm có tọa độ đã đo để tạo bản đồ tín hiệu, không đòi hỏi nhìn thấy hoặc biết vị trí vật lý của AP.
- Ước lượng tọa độ client rồi tính khoảng cách đường thẳng tới tọa độ server trên mặt bằng 2D, kể cả đường đó đi qua tường. Bản đầu chưa tính chênh lệch độ cao.
- Roaming/mất mạng ngắn không tự làm mất chỗ trong phòng thi; khôi phục phiên khi đường truyền trở lại trong thời gian cho phép.
- Phần thu mẫu, định vị, lưu trữ và giao thức được triển khai bằng Java 21. Tailscale là phần mềm mạng riêng cài ngoài ứng dụng.

### Điều chỉnh so với ý tưởng đo từng khoảng cách tới AP

Khi chưa biết vị trí AP và không tiếp cận được AP để hiệu chuẩn theo khoảng cách thật, các số đo RSSI → mét của Server–AP/Client–AP không đủ cơ sở để cộng trừ thành khoảng cách Client–Server. Bản đầu dùng vị trí đã khảo sát làm mốc. Tính năng đo khoảng cách AP hiện có vẫn là chế độ riêng, chỉ sử dụng khi có hồ sơ hiệu chuẩn hợp lệ.

BSSID thay đổi chỉ xác nhận đổi kết nối, không chứng minh đã ra khỏi vùng phủ sóng AP trước. Hệ thống phân biệt AP đang kết nối, AP quan sát được và vị trí ước lượng. Số BSSID cũng không được trình bày thành số AP vật lý.

## Hiện trạng và các điểm tích hợp

| Thành phần | Hiện có | Phần cần bổ sung |
|---|---|---|
| `WindowsWifiSignalProvider` | Quét native nhiều BSSID, phân biệt timestamp mới/cache, xử lý timeout/quyền | Nghiệm thu thêm driver/laptop và dữ liệu tại các điểm thật |
| `ProximityClientService`, `SurveyCaptureService` | Worker quét nối tiếp, giữ riêng lượt khảo sát offline, không chồng hiệu chuẩn | Điều phối báo cáo định vị trực tiếp ở đợt D |
| `LanDirectory`, `LanEntryPane`, `ClientConnectionProfile` | Dò LAN hoặc kết nối trực tiếp IP/hostname qua TLS, lưu lựa chọn | Nghiệm thu kết nối mạng riêng giữa hai laptop |
| `ResumingQuizGateway`, `QuizServer` | Khôi phục phiên bằng token, giữ chỗ có giới hạn, phục hồi đáp án Server đã nhận | Nghiệm thu roaming/mất mạng thật |
| `AccessPointTracker`, `AccessPointViews` | Danh mục AP vật lý xác nhận thủ công, lịch sử association; BSSID quan sát tự lưu riêng | Kết hợp với kết quả vị trí nhưng giữ hai ý nghĩa độc lập |
| `SurveyRepository`, `SurveyViews` | Khu/điểm/tọa độ có phiên bản, mẫu raw, SQLite, import/export và upload TLS | Thu bộ pilot thật cho mô hình KNN/WKNN |
| `DatabaseSchema` | SQLite schema phiên bản 5, dữ liệu khảo sát và phiên bản tọa độ | Bổ sung model đã đánh giá và kết quả định vị ở đợt C–D |
| `ProximityViews`, màn hình phòng | Giám sát khoảng cách tới AP và lịch sử AP, UI JavaFX hiện tại | Màn xem vị trí theo khảo sát, khoảng cách Client–Server và chất lượng dữ liệu |

Giữ phong cách và luồng chính của UI theo `.cursor/rules/preserve-existing-ui-ux.mdc`. Thêm các lựa chọn vào luồng hiện có, thực hiện scan/ghi dữ liệu/tính toán ngoài JavaFX thread.

## Giai đoạn 1 — Kết nối trực tiếp qua mạng riêng

1. Bổ sung lựa chọn Tự dò LAN / Máy chủ cụ thể; nhập IP Tailscale hoặc hostname hợp lệ và cổng, mặc định 5050. Lưu cấu hình kết nối theo người dùng Windows, không lưu mật khẩu/tokens vào file phát hành.
2. Với máy chủ cụ thể, truy vấn trực tiếp qua TLS, không phụ thuộc UDP broadcast hoặc điều kiện IPv4 private LAN. Giữ xác minh khóa Server, hạn chứng chỉ và các ràng buộc TLS hiện có.
3. Giữ luồng chọn phòng/nhập mã phòng sau khi đã liên lạc được Server. Chỉ mã phòng không thay thế địa chỉ Server.
4. Phân biệt sai địa chỉ, không phân giải DNS, timeout TCP, TLS không khớp và Server chưa mở phòng. Kết quả tìm kiếm cũ không được ghi đè sau khi đổi địa chỉ hoặc đóng màn hình.
5. Hướng dẫn cài Tailscale ngoài ứng dụng, đưa máy vào cùng mạng riêng và chỉ cho phép truy cập Server TCP 5050. Quy tắc firewall cho mạng riêng cần riêng với quy tắc `LocalSubnet` hiện tại; không mở toàn bộ firewall.

**Nghiệm thu:** hai laptop thật kết nối TCP/TLS và tham gia cùng phòng qua mạng riêng khi UDP LAN không tới nhau; danh tính Server sai phải bị từ chối. Kết nối LAN cũ vẫn hoạt động. Tailscale có thể dùng relay; phải thử tại HUTECH E1, không cam kết mạng trường luôn cho phép dịch vụ này.

## Giai đoạn 2 — Khôi phục phiên thi sau gián đoạn

1. Thêm capability/version cho phục hồi phiên. Khi JOIN thành công, Server cấp token ngẫu nhiên đủ mạnh; Server giữ hash, Client giữ token trong RAM. Không khôi phục bằng nickname hoặc IP.
2. Đề xuất thời gian giữ chỗ mặc định 60 giây, cấu hình được. Mất mạng chuyển sang Đang kết nối lại; thử lại có backoff, giới hạn tần suất, dừng khi người dùng chủ động rời phòng hoặc hết thời gian.
3. Khôi phục đúng player_id, linh vật, điểm và câu trả lời đã ghi nhận; chỉ một kết nối được sở hữu player tại một thời điểm. Token được luân chuyển khi khôi phục thành công, và bị vô hiệu khi rời/đóng/reset phòng hoặc hết thời gian. Token chỉ có hiệu lực trong tiến trình Server và đúng thế hệ phòng.
4. Đồng hồ trận vẫn chạy trên Server. Không tự phát lại đáp án đang gửi dở; sau khi phục hồi phải hỏi trạng thái đáp án, chỉ cho thao tác phù hợp với câu hiện hành và thời hạn. Chống gửi trùng, đáp án của câu/phiên cũ và giả mạo token.
5. Điều chỉnh `detach`, tick và logic hủy ván: không hủy ngay khi tất cả mất socket nếu còn player được giữ chỗ; vẫn có giới hạn hết hạn rõ ràng. Mất kết nối ở phòng chờ phải cập nhật Ready theo chế độ giám sát đang dùng.
6. Tạm mất mạng làm dữ liệu vị trí hết hạn; không hiển thị vị trí cũ như dữ liệu trực tiếp. Chủ phòng/Admin được xem trạng thái giữ chỗ và khôi phục.

**Nghiệm thu:** ngắt mạng rồi nối lại trong giới hạn không mất chỗ/điểm; đồng hồ không dừng; token sai/cũ/khác phòng bị từ chối; rời chủ động không tự tham gia lại. Không hứa duy trì liên tục khi không có đường truyền hoặc Server khởi động lại.

## Giai đoạn 3 — Quét nhiều BSSID trên Windows

1. Thêm abstraction `WifiScanProvider` và kết quả `WifiScanSnapshot`/`WifiObservation`: adapter, scan_id, thời gian/tuổi dữ liệu, trạng thái, SSID, BSSID, RSSI, tần số/kênh và association hiện tại.
2. Dùng Native Wi-Fi API qua JNA: yêu cầu scan bằng `WlanScan`, theo dõi hoàn tất/thất bại có timeout rồi lấy `WlanGetNetworkBssList`. Tái sử dụng xử lý quyền/adapter hiện có; kết quả cache không được tính là scan mới độc lập.
3. Kiểm tra kích thước bộ đệm/native struct, số entry, giải phóng bộ nhớ và callback khi hủy/đóng. Không gọi scan dồn dập hoặc chặn UI; điều phối scan với đọc RSSI và hiệu chuẩn hiện tại.
4. Chọn đúng card Wi-Fi HUTECH E1; không dùng adapter Tailscale làm nguồn tín hiệu. Không hardcode tên trường: cho phép chọn SSID khảo sát; khóa BSSID/tần số giúp phân biệt các radio.
5. Tự ghi nhận các BSSID quan sát được, first_seen/last_seen và metadata; gán tên tạm cho BSSID, không suy ra tọa độ hay gộp AP vật lý từ thứ tự phát hiện.
6. Giới hạn số entry/kích thước bản tin, ví dụ tối đa 64 quan sát/scan sau khi lọc. Giới hạn cuối cùng phải kiểm tra theo frame 64 KiB hiện có. Snapshot giám sát không phát toàn bộ mẫu Wi-Fi thô của mọi client.

**Mốc kiểm tra bắt buộc trước khảo sát:** dùng laptop thật tại trường để xác nhận đọc được nhiều BSSID, phân biệt scan mới/cũ, độ trễ thực và ảnh hưởng tới kết nối. Nếu không đủ dữ liệu mới hoặc driver không hỗ trợ phù hợp, xử lý khả năng thu mẫu trước khi xây mô hình. Không giả định luôn thấy đủ 7 AP.

## Giai đoạn 4 — Thu mẫu khảo sát và bản đồ tín hiệu

1. Thêm khu khảo sát có chủ sở hữu, SSID, đơn vị mét, vị trí cửa gốc (0,0), trục hành lang và tọa độ server. Phòng là vùng 2D riêng; không ép mọi điểm trong phòng lên trục hành lang.
2. Thêm các điểm tham chiếu có tên, vùng phòng/hành lang và tọa độ do người khảo sát đo. Bắt đầu khoảng 10–15 điểm; số điểm/khoảng cách sẽ điều chỉnh theo mặt bằng và kết quả thử.
3. Màn Khảo sát: chọn khu/điểm → thu mẫu → xem tiến độ, số scan mới hợp lệ và số BSSID → lưu. Khoảng 20–30 giây là thời lượng thử ban đầu, không tự coi đủ mẫu chỉ vì đếm đủ giây.
4. Lưu từng đợt thu gồm dữ liệu thô, tuổi mẫu, adapter/device profile, hướng cầm do người dùng ghi nếu cần và phiên bản bố trí. Lấy lặp lại ở hai hướng đi; không tuyên bố đo hướng la bàn từ laptop.
5. Cho phép lưu cục bộ khi chưa có Server và đồng bộ lại có xác nhận/idempotency; không làm thất lạc mẫu khi mạng mất giữa lượt. Đợt lấy mẫu bị di chuyển hoặc lỗi phải hủy/đánh dấu, không âm thầm gán vào điểm.
6. Lưu trên SQLite qua worker riêng có queue giới hạn. Migration từ schema 4 phải bảo toàn tài khoản, ngân hàng, lịch sử và hiệu chuẩn cũ. Bảng dự kiến: khu khảo sát, điểm tham chiếu, đợt lấy mẫu, scan, observations, danh mục BSSID và model revision.
7. Export/import JSON có schema version, đơn vị và kiểm tra toàn bộ trước khi ghi. Phân trang/chia lô theo giới hạn bản tin; khóa phiên bản bản đồ đang dùng trong ván, sửa khảo sát tạo phiên bản mới.

**Nghiệm thu:** thu mẫu thật, đóng/mở ứng dụng vẫn còn; mẫu cache/khác điểm không bị đếm nhầm; import lỗi không ghi dở. Đợt khảo sát đầu dùng cùng laptop với đợt kiểm tra đầu; thử các laptop khác ở bước đánh giá khả năng chuyển đổi thiết bị.

## Giai đoạn 5 — Định vị bằng Java và đánh giá độc lập

Đã có baseline offline chuyển từ Anyplace/MIT và báo cáo theo toàn bộ lượt giữ riêng. Nguồn mã, thay đổi, kết quả đối chiếu và cách chạy nằm ở [tài liệu đánh giá](Nguon_mo_va_danh_gia_fingerprinting.md). Phần chuyển động, chọn tham số bằng dữ liệu thật và tích hợp trực tiếp vẫn chưa hoàn tất.

1. Thêm bộ định vị Java thuần trong `nqb-shared` hoặc module core phù hợp, không phụ thuộc JavaFX/JNA. KNN làm baseline, WKNN là phương án so sánh, không mặc định WKNN luôn tốt hơn.
2. Chuẩn hóa vector theo BSSID/tần số trong đúng khu/model revision. Lọc nhiễu, dùng thống kê RSSI theo điểm; xử lý BSSID thiếu, mức độ trùng mẫu và tín hiệu mới. Không đổi vector RSSI thành mét tới AP trước khi so khớp.
3. Tính tọa độ từ điểm gần trong không gian tín hiệu; thiết lập quy tắc từ chối khi mẫu cũ, thiếu trùng khớp hoặc không phân biệt được vị trí. Chỉ báo chất lượng/độ phù hợp; không gắn phần trăm tin cậy khi chưa được hiệu chuẩn.
4. Phân biệt khu phòng/hành lang và ràng buộc hình học phù hợp. Không trung bình mù qua hai phía tường hoặc ép một kết quả mơ hồ về hành lang.
5. Tính khoảng cách 2D `hypot(clientX-serverX, clientY-serverY)`. Hướng tăng/giảm trục hoặc tiến gần/ra xa Server suy ra từ chuỗi vị trí đủ ổn định, có ngưỡng chống dao động; dữ liệu cũ không suy ra chuyển động hiện tại.
6. Chọn tham số bằng dữ liệu kiểm tra riêng theo đợt; không trộn các scan gần trùng của cùng một lượt thu vào cả train và test. Đánh giá ở các điểm/đợt chưa dùng huấn luyện, cả đứng yên và đi hai chiều.
7. Báo cáo sai số vị trí và sai số khoảng cách Client–Server riêng: trung vị, P90, tỷ lệ không định vị được, độ trễ cập nhật và ảnh hưởng giữa laptop. Mục tiêu sai số chấp nhận được được chốt sau khảo sát pilot, chưa hứa chính xác 1–2 m.

**Nghiệm thu:** có dữ liệu và báo cáo đo thật để chọn mô hình. Nếu không đạt mức hữu ích, tăng/đổi điểm khảo sát hoặc chỉ hiển thị vùng vị trí, không xuất số mét thiếu cơ sở.

## Giai đoạn 6 — Tích hợp giám sát và phát hành

1. Thêm chế độ Giám sát vị trí theo khảo sát, tách khỏi chế độ khoảng cách tới một AP. Trong chế độ vị trí, chuyển AP không kích hoạt quy tắc sai mốc/hiệu chuẩn AP1 của chế độ cũ và không tự đẩy người chơi khỏi phòng.
2. Server nhận scan được xác thực, kiểm tra sequence/session/model revision, giới hạn tần suất/kích thước rồi tính vị trí từ model đã chọn. Công việc nặng không chạy trên game worker; kết quả trễ của cấu hình/phiên cũ bị loại bỏ.
3. Client hiển thị trạng thái của mình. Chủ phòng/Admin xem sơ đồ tầng, vị trí ước lượng, khoảng cách đường thẳng, BSSID kết nối, tuổi dữ liệu và lịch sử. Giữ ping mạng riêng biệt với khoảng cách vật lý.
4. Không đưa Wi-Fi thô, token hoặc vị trí chi tiết của người khác vào danh sách phòng/máy chiếu. Chức năng nhập/chỉnh/export bản đồ tuân theo quyền chủ sở hữu.
5. Hiển thị rõ Đang kết nối lại, Chưa khảo sát, Mẫu đã cũ, Chưa đủ dữ liệu, Ước lượng vị trí và AP đã đổi. Không tự suy ra Ra khỏi vùng AP1 chỉ từ roaming.
6. Test JavaFX ở 1024×640 và 1366×768; kiểm tra thu mẫu bị hủy, lỗi quyền, mô hình chưa có, mẫu cũ và đường truyền gián đoạn. Chạy hồi quy trận thi, chấm điểm, lịch sử và nhập đề.
7. Đóng gói đồng bộ Server/Client Windows có Java runtime, version/capability rõ ràng; kèm hướng dẫn Tailscale, khảo sát, backup và báo cáo giới hạn. Không phát hành khóa riêng, database cá nhân hoặc token mạng riêng.

**Nghiệm thu cuối:** thực hiện tại HUTECH E1: tham gia phòng qua Tailscale → đi từ phòng ra hành lang → quan sát đổi BSSID và vị trí → gián đoạn ngắn rồi phục hồi phiên → quay lại phòng; đối chiếu số mét với tọa độ đã đo. Không dùng test giả lập làm bằng chứng sai số thực địa.

## Thứ tự bàn giao

| Đợt | Sản phẩm có thể dùng thử | Điều kiện để tiếp tục |
|---|---|---|
| A | Kết nối máy chủ cụ thể, phục hồi phiên, chẩn đoán quét nhiều BSSID | TCP/TLS qua mạng riêng và scan thật trên laptop hoạt động |
| B | Chế độ Khảo sát, lưu/export/import mẫu và tọa độ | Người dùng thu được bộ dữ liệu pilot thật |
| C | KNN/WKNN, báo cáo kiểm chứng, khoảng cách tới server | Mức sai số được đánh giá và lựa chọn cách hiển thị |
| D | Giám sát trực tiếp, hồi quy và bộ cài đồng bộ | Nghiệm thu đi thực tế, roaming và phục hồi phiên |

Trong lúc chưa có mẫu thực địa, có thể hoàn thành luồng phần mềm và kiểm thử tổng hợp; chưa coi việc định vị đạt yêu cầu. Không cài hoặc đăng nhập Tailscale thay người dùng trong bước lập kế hoạch này.

Đợt B hiện gửi dữ liệu lên Server theo tài khoản; tải/trao đổi khu giữa laptop dùng file JSON. Phiên bản tọa độ bất biến, chỉnh sửa tạo bản mới; chưa có model để chọn hoặc khóa trong ván thi (thuộc đợt C–D).

## Nguồn tham khảo

- [Microsoft WlanScan](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/nf-wlanapi-wlanscan): yêu cầu scan và vòng đời bất đồng bộ.
- [Microsoft WlanGetNetworkBssList](https://learn.microsoft.com/en-us/windows/win32/api/wlanapi/nf-wlanapi-wlangetnetworkbsslist): dữ liệu BSS trên card Wi-Fi.
- [Tailscale connection types](https://tailscale.com/docs/reference/connection-types): kết nối trực tiếp/relay và giới hạn theo mạng.
- [DMSL/Anyplace](https://github.com/dmsl/anyplace/blob/7229551823757cc39174cc693947d9077e770bfb/clients/android/Anyplace/src/com/dmsl/airplace/algorithms/Algorithms.java): đã chuyển có sửa đổi lõi Euclidean/KNN/WKNN, giữ thông báo MIT; đối chiếu 12 kết quả chạy bản gốc.
- [Talentica/WifiIndoorPositioning](https://github.com/Talentica/WifiIndoorPositioning): đã đọc luồng fingerprinting và `Algorithms.java`; file thuật toán có nguồn gốc Anyplace nên dùng trực tiếp upstream Anyplace cho bản chuyển.
- [codedpro/DIY-IPS-Indoor-Positioning-System](https://github.com/codedpro/DIY-IPS-Indoor-Positioning-System): quy trình thu mẫu nhiều hướng và kiểm tra sai số độc lập; không nhập phần Android/PDR/Firebase/Python vào ứng dụng Windows.
