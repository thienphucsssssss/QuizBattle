# Ràng buộc 1 máy Client -- 1 tài khoản học sinh

## Mục tiêu

Mỗi máy Client học sinh chỉ được sử dụng **một tài khoản học sinh đã
đăng ký/gán cho máy đó** trong hệ thống QuizBattle. Mục tiêu là hạn chế
việc đổi tài khoản liên tục trên cùng một máy trong buổi thi và giúp
người điều hành xác định đúng học sinh đang tham gia.

**Không nên dùng địa chỉ IP làm định danh duy nhất của máy.** Nhiều máy
trong cùng phòng máy có thể dùng chung IP công khai hoặc cùng mạng NAT;
IP nội bộ cũng có thể thay đổi. Vì vậy, ưu tiên gắn tài khoản với một mã
định danh máy do ứng dụng tạo/lưu cục bộ. IP chỉ nên được ghi nhận để hỗ
trợ kiểm tra và ghi nhật ký. Nếu yêu cầu nghiệp vụ bắt buộc là "mỗi IP
chỉ có một tài khoản", cần xác nhận rõ vì quy tắc này có thể chặn nhầm
nhiều học sinh hợp lệ trong cùng mạng.

## Phạm vi thay đổi

-   Chỉ thay đổi phần Client học sinh và phần Server cần thiết để xác
    thực/ràng buộc phiên đăng nhập.
-   Không thay đổi thuật toán chấm điểm, nội dung câu hỏi, cách tính thứ
    hạng hoặc giao thức khác không liên quan.
-   Không áp dụng quy tắc này cho tài khoản quản trị viên/giảng viên.
-   Không tự tạo, sửa hoặc xóa tài khoản học sinh hiện có.
-   Không lưu mật khẩu học sinh ở dạng văn bản thuần trên máy Client.

## Quy tắc nghiệp vụ

-   Một máy Client đã được gán với tài khoản học sinh A thì chỉ tài
    khoản A được phép đăng nhập trên máy đó.
-   Khi tài khoản A đăng nhập thành công, Server kiểm tra ràng buộc
    máy--tài khoản trước khi cho phép tham gia phòng.
-   Tài khoản B đăng nhập trên cùng máy phải bị từ chối nếu máy đã được
    gán cho A, trừ khi người có quyền quản lý thực hiện quy trình đổi/gỡ
    liên kết.
-   Một tài khoản học sinh không được đồng thời sử dụng nhiều máy nếu
    chính sách triển khai chọn chế độ "một tài khoản -- một máy". Nếu
    không cần giới hạn này, phải ghi rõ rằng tài khoản chỉ bị khóa với
    một máy tại một thời điểm hay được phép dùng lại trên máy khác sau
    khi đăng xuất.
-   Đăng xuất không tự động xóa liên kết máy--tài khoản. Nếu đăng xuất
    tự xóa liên kết thì học sinh có thể đổi tài khoản liên tục trên cùng
    máy.
-   Khi mất kết nối hoặc Client khởi động lại, liên kết máy--tài khoản
    vẫn được giữ theo chính sách đã cấu hình.
-   Chỉ quản trị viên/giảng viên được phân quyền mới có thể gỡ hoặc
    chuyển liên kết; thao tác phải được ghi nhật ký.
-   Không được dùng ràng buộc máy--tài khoản để thay đổi điểm, thứ hạng
    hoặc kết quả ván chơi.

## Luồng hoạt động

1.  Client khởi động và lấy mã định danh máy đã được tạo/lưu từ lần chạy
    đầu tiên.
2.  Học sinh nhập thông tin đăng nhập.
3.  Client gửi yêu cầu xác thực cùng mã định danh máy tới Server qua kết
    nối hiện có.
4.  Server xác thực tài khoản và kiểm tra trạng thái liên kết máy--tài
    khoản.
5.  Nếu chưa có liên kết, Server áp dụng chính sách triển khai:
    -   **Chế độ tự gán:** liên kết máy với tài khoản đầu tiên đăng nhập
        hợp lệ; hoặc
    -   **Chế độ quản lý gán trước:** chỉ cho phép tài khoản đã được
        quản trị viên/giảng viên gán cho máy.
6.  Nếu liên kết khớp, Server cho phép đăng nhập và tiếp tục luồng vào
    phòng.
7.  Nếu liên kết không khớp, Server từ chối và hiển thị thông báo rõ
    ràng, không tiết lộ thông tin nhạy cảm của tài khoản đã liên kết.
8.  Khi quản lý gỡ/chuyển liên kết, Server kiểm tra quyền, ghi nhật ký
    và cập nhật trạng thái liên kết.

## Dữ liệu và bảo mật

-   Mã định danh máy phải được tạo ngẫu nhiên, không chứa tên học sinh,
    mật khẩu hoặc thông tin cá nhân.
-   Server là nơi quyết định liên kết nào hợp lệ; không tin vào kiểm tra
    chỉ thực hiện ở Client.
-   Mã định danh máy không được xem là bằng chứng bảo mật tuyệt đối vì
    có thể bị sao chép hoặc xóa dữ liệu cục bộ.
-   Nếu mã máy bị mất hoặc Client được cài lại, cần có quy trình khôi
    phục/gán lại do người có quyền thực hiện.
-   Không ghi mật khẩu, token phiên hoặc dữ liệu xác thực nhạy cảm vào
    log.
-   Chỉ lưu thông tin cần thiết, chẳng hạn mã máy, mã tài khoản, thời
    điểm tạo liên kết, người thực hiện và lịch sử thay đổi.
-   Nếu vẫn ghi nhận IP, chỉ dùng như dữ liệu hỗ trợ kiểm tra; không coi
    IP là định danh ổn định của máy.
-   Các thao tác gán, gỡ và chuyển liên kết cần được kiểm tra quyền ở
    Server.

## Thông báo cho người dùng

-   Khi máy chưa được gán: "Máy này chưa được liên kết với tài khoản học
    sinh."
-   Khi liên kết thành công: "Máy đã được liên kết với tài khoản của
    bạn."
-   Khi tài khoản không khớp: "Tài khoản này không được phép sử dụng
    trên máy hiện tại. Vui lòng liên hệ giảng viên/quản trị viên."
-   Khi liên kết đã bị khóa hoặc cần xử lý: hiển thị hướng dẫn liên hệ
    người quản lý, không hiển thị thông tin cá nhân của tài khoản khác.
-   Khi mất kết nối: thông báo lỗi kết nối thông thường; không tự động
    xóa liên kết.

## Kiểm thử phát triển

### `DeviceAccountBindingTest`

-   Máy chưa liên kết và tài khoản hợp lệ: xử lý theo đúng chế độ tự gán
    hoặc gán trước.
-   Máy đã liên kết với A, A đăng nhập: cho phép.
-   Máy đã liên kết với A, B đăng nhập: từ chối.
-   Đăng xuất rồi đăng nhập lại A: vẫn cho phép và không tạo liên kết
    trùng.
-   Đăng xuất rồi đăng nhập B: vẫn từ chối nếu chưa được người có quyền
    chuyển liên kết.
-   Tài khoản không hợp lệ: từ chối và không tạo liên kết.
-   Người không có quyền cố gỡ/chuyển liên kết: từ chối.
-   Người có quyền gỡ/chuyển liên kết: thành công và có nhật ký.
-   Client khởi động lại hoặc mất kết nối: liên kết không bị xóa ngoài ý
    muốn.
-   Hai yêu cầu liên kết đồng thời: không thể khiến một máy bị gán cho
    hai tài khoản.
-   Mã định danh máy bị thiếu/hỏng: xử lý an toàn, yêu cầu quy trình
    khôi phục phù hợp.
-   IP thay đổi nhưng mã máy không đổi: vẫn nhận diện cùng máy.
-   Hai máy khác nhau dùng chung IP công khai: không bị nhầm là cùng một
    máy nếu dùng mã định danh máy.

### Kiểm thử giao diện

-   Thông báo lỗi dễ hiểu, không làm lộ nickname hoặc dữ liệu tài khoản
    khác.
-   Nút đăng nhập/đăng xuất không bị treo khi Server từ chối ràng buộc.
-   Không ảnh hưởng màn hình phòng chơi, điểm, bảng xếp hạng và màn vinh
    danh.
-   Kiểm tra trường hợp cửa sổ nhỏ và khi mất kết nối trong lúc đăng
    nhập.

### Kiểm thử tích hợp LAN

-   Một Server và nhiều Client học sinh kết nối đồng thời.
-   Các Client khác nhau trong cùng mạng có thể đăng nhập bằng tài khoản
    được gán tương ứng.
-   Cố đăng nhập tài khoản khác trên máy đã liên kết phải bị từ chối.
-   Xác nhận luồng khám phá LAN, TLS, tạo/vào phòng, chấm điểm, lưu lịch
    sử và đóng phòng không bị ảnh hưởng.
-   Kiểm thử cùng máy không thay thế cho nghiệm thu trên nhiều máy vật
    lý trong mạng LAN thực tế.

## Điều kiện nghiệm thu

-   Server là nơi kiểm tra ràng buộc máy--tài khoản.
-   Một máy không thể sử dụng tài khoản học sinh khác khi liên kết chưa
    được gỡ/chuyển hợp lệ.
-   Quy trình gán, gỡ và chuyển liên kết có kiểm tra quyền và nhật ký.
-   Không làm thay đổi giao thức, chấm điểm hoặc dữ liệu ván chơi ngoài
    phần cần thiết cho ràng buộc.
-   Các bài kiểm thử đơn vị, giao diện và tích hợp liên quan đều đạt.
-   Không dùng IP làm định danh duy nhất nếu môi trường có nhiều máy
    dùng chung IP/NAT.

## Triển khai và cập nhật

-   Ưu tiên triển khai thử trên dữ liệu kiểm thử, không thử bằng tài
    khoản hoặc database cá nhân đang sử dụng.
-   Sao lưu cấu hình/dữ liệu theo quy trình hiện có trước khi cập nhật.
-   Chỉ cập nhật Server nếu thay đổi giao thức hoặc logic phía Server
    thực sự cần thiết; không tự ý ghi đè database, cấu hình TLS, chứng
    chỉ hoặc dữ liệu cá nhân.
-   Nếu thay đổi giao thức Client--Server, phải kiểm tra tương thích
    phiên bản và cập nhật các Client cần thiết.
-   Ghi rõ phiên bản phát hành, kết quả kiểm thử và cách khôi phục bản
    trước khi triển khai rộng.

## Quyết định cần chốt trước khi lập trình

1.  **Cách gán máy:** tự gán cho tài khoản đầu tiên đăng nhập hợp lệ hay
    giảng viên gán trước?
2.  **Phạm vi giới hạn:** chỉ một tài khoản trên mỗi máy, hay mỗi tài
    khoản cũng chỉ được dùng trên một máy?
3.  **Đổi máy/cài lại:** ai được quyền gỡ hoặc chuyển liên kết?
4.  **Vai trò của IP:** chỉ ghi log hỗ trợ kiểm tra (khuyến nghị), hay
    thực sự dùng IP làm điều kiện chặn? Nếu chọn IP, cần xác nhận cách
    xử lý phòng máy có nhiều Client dùng chung IP công khai.

Mặc định đề xuất: **mỗi máy Client được liên kết với một tài khoản học
sinh bằng mã định danh máy; Server xác thực ràng buộc; IP chỉ dùng hỗ
trợ kiểm tra.**
