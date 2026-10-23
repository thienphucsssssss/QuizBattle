# ĐẶC TẢ KIẾN TRÚC HỆ THỐNG NETWORK QUIZ BATTLE

> **Phiên bản:** 1.0 — Đề xuất triển khai  
> **Tên đề tài:** Network Quiz Battle — Hệ thống thi trắc nghiệm nhiều người trong mạng LAN theo mô hình Client–Server  
> **Loại sản phẩm:** Phần mềm Desktop  
> **Nhóm thực hiện:** 3 sinh viên — trình độ nhập môn Lập trình mạng máy tính  
> **Phạm vi:** MVP (Minimum Viable Product) có thể triển khai và demo thực tế

## 1. Mục tiêu và tổng quan

Xây dựng ứng dụng trắc nghiệm nhiều người chơi trong cùng mạng LAN hoặc Wi-Fi. Một máy đóng vai trò **Host/Server**, cho phép quản lý ngân hàng câu hỏi và tổ chức ván thi. Những máy khác chạy **Player/Client**, tham gia phòng, nhận câu hỏi, gửi đáp án và xem kết quả theo thời gian thực.

**Mục tiêu học phần:** vận dụng TCP Socket, mô hình Client–Server, lập trình xử lý nhiều Client, định dạng/gửi nhận thông điệp, quản lý thời gian trả lời và đồng bộ trạng thái trò chơi.

**Mục tiêu sản phẩm:** có thể chạy trên nhiều máy thật, có giao diện desktop, thực hiện thêm/sửa/xóa câu hỏi và tổ chức được một ván thi từ đầu đến cuối.

### 1.1. Sơ đồ kiến trúc tổng thể

```text
                       MÁY HOST / SERVER
        ┌─────────────────────────────────────────┐
        │ Admin UI                                │
        │ - Quản lý câu hỏi (CRUD)                │
        │ - Xem người tham gia                   │
        │ - Bắt đầu trò chơi                     │
        ├─────────────────────────────────────────┤
        │ TCP Server                              │
        │ - Lắng nghe kết nối                    │
        │ - Xử lý nhiều Client                   │
        │ - Gửi/nhận JSON                        │
        ├─────────────────────────────────────────┤
        │ Game Manager                            │
        │ - Câu hỏi và trạng thái ván             │
        │ - Thời gian trả lời / timeout          │
        │ - Kiểm tra đáp án, tính điểm           │
        ├─────────────────────────────────────────┤
        │ SQLite (quiz.db)                        │
        │ - Ngân hàng câu hỏi                    │
        └───────────────────┬─────────────────────┘
                            │
                    TCP Socket + JSON
                    (mạng LAN / Wi-Fi)
             ┌──────────────┼──────────────┐
             │              │              │
       ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐
       │ Player 1  │  │ Player 2  │  │ Player 3  │
       │ Client UI │  │ Client UI │  │ Client UI │
       └───────────┘  └───────────┘  └───────────┘
```

Server là **nguồn dữ liệu và quyết định chính** (authoritative server): chịu trách nhiệm câu hỏi hiện tại, tính hợp lệ của đáp án, đồng hồ chính và bảng điểm. Client hiển thị dữ liệu nhận được; không tự chấm điểm hay sửa điểm.

Ứng dụng chỉ yêu cầu các máy trong cùng mạng nội bộ, **không cần Internet, Cloud hay dịch vụ đăng nhập bên ngoài**.

## 2. Phân hệ và trách nhiệm

| Thành phần | Vai trò |
|---|---|
| **Admin UI (Host)** | Thêm/xem/sửa/xóa câu hỏi, kiểm tra Server, xem danh sách người chơi, bắt đầu ván |
| **TCP Server** | Mở cổng lắng nghe, quản lý các kết nối TCP và chuyển tiếp dữ liệu |
| **Client Handler** | Nhận thông điệp của một Client, xác thực định dạng, xử lý thoát/mất kết nối |
| **Game Manager** | Quản lý vòng chơi, chọn câu hỏi, timeout, kiểm tra đáp án, chấm điểm, phát kết quả |
| **Database Manager** | Làm việc với SQLite; thực hiện CRUD và truy vấn ngân hàng câu hỏi |
| **TCP Client** | Kết nối đến Server, gửi `JOIN`/`ANSWER`, nhận các message trò chơi |
| **Player UI** | Hiển thị nhập kết nối, lobby, câu hỏi, thời gian còn lại, kết quả và bảng xếp hạng |
| **Shared Protocol** | Quy ước kiểu thông điệp JSON và cách đóng khung dữ liệu trên TCP |

### 2.1. Hai vai trò người dùng

**Host/Admin**
- Khởi động và dừng Server.
- Xem địa chỉ IP và cổng lắng nghe.
- Thêm, hiển thị danh sách, sửa, xóa câu hỏi.
- Xem những người chơi đã tham gia phòng chờ.
- Bắt đầu một ván thi và xem trạng thái/bảng xếp hạng.
- Ở bản MVP, Host thao tác **trực tiếp trên máy Server**, không cần tài khoản quản trị hay giao diện quản trị từ xa.

**Player/Client**
- Nhập IP Server, cổng kết nối và nickname.
- Tham gia lobby và xem danh sách người chơi.
- Nhận câu hỏi, chọn đáp án A/B/C/D, gửi đáp án.
- Xem thời gian còn lại, kết quả từng câu và bảng xếp hạng cuối ván.
- Không có quyền đọc hoặc chỉnh sửa cơ sở dữ liệu câu hỏi.

## 3. Phạm vi chức năng (MVP)

### 3.1. Chức năng bắt buộc

| Mã | Chức năng | Đầu ra mong đợi |
|---|---|---|
| F01 | Khởi động TCP Server | Server lắng nghe IP/port và nhận kết nối |
| F02 | Kết nối từ Client | Player nhập IP/port/nickname và tham gia lobby |
| F03 | Quản lý nhiều Client | Tối thiểu 3 Client chơi đồng thời |
| F04 | Quản lý câu hỏi CRUD | Thêm, xem, sửa, xóa câu hỏi và lưu dữ liệu bền vững |
| F05 | Bắt đầu một ván | Host chọn/bắt đầu bộ câu hỏi, Server phát tới Player |
| F06 | Nhận đáp án | Mỗi Player gửi tối đa 1 đáp án hợp lệ cho mỗi câu |
| F07 | Giới hạn thời gian | Server từ chối câu trả lời quá hạn |
| F08 | Chấm điểm | Server đối chiếu đáp án đúng và cập nhật điểm |
| F09 | Kết quả và bảng xếp hạng | Các Player nhận cùng dữ liệu kết quả từ Server |
| F10 | Xử lý ngắt kết nối | Client rời đi không làm Server bị sập |

### 3.2. Quy tắc nghiệp vụ đề xuất

| Quy tắc | Giá trị MVP |
|---|---|
| Số phòng | 1 phòng thi tại một thời điểm |
| Số câu hỏi mỗi ván | 10 câu (yêu cầu ngân hàng có ít nhất 10 câu hợp lệ) |
| Thời gian trả lời | 15 giây/câu, Host có thể cấu hình |
| Điểm đúng | +100 điểm |
| Điểm sai/không trả lời | 0 điểm |
| Số lần gửi đáp án | Nhận đáp án **đầu tiên hợp lệ** của mỗi Player trong một câu |
| Chọn câu hỏi | Server lấy từ SQLite, có thể trộn thứ tự |
| Sửa/xóa câu hỏi | Chỉ trong trạng thái chờ, không sửa ngân hàng giữa ván |
| Mất kết nối | Player rời phiên; MVP chưa hỗ trợ tiếp tục phiên đã rớt mạng |
| Kết thúc ván | Hết danh sách câu hỏi, Server phát kết quả cuối cùng |

## 4. Kiến trúc giao tiếp mạng

### 4.1. TCP Socket

Ví dụ máy Host sử dụng:

```text
Server IP:   192.168.1.10  (ví dụ; thay theo mạng thực tế)
Server Port: 5000
Protocol:    TCP
```

- Server tạo socket bằng `socket.socket(AF_INET, SOCK_STREAM)`.
- Server `bind()`, `listen()` và liên tục `accept()` các Client mới.
- Client dùng `connect((server_ip, port))` để mở kết nối.
- Dữ liệu được trao đổi hai chiều qua socket bằng chuỗi byte mã hóa UTF-8.
- Chỉ mở cổng cho mạng LAN đáng tin cậy; cấu hình tường lửa nếu hệ điều hành yêu cầu.

TCP cung cấp luồng byte truyền tin có thứ tự và đáng tin cậy **trong khi kết nối còn hoạt động**, nhưng không đảm bảo một lần `recv()` tương đương chính xác một thông điệp. Vì vậy ứng dụng cần thêm cơ chế đóng khung thông điệp.

### 4.2. Multi-client Server

Phương án đơn giản cho nhóm nhập môn:

```text
TCP Server
├── Thread kết nối mới (accept loop)
├── Client Handler Thread 1 → Player Minh
├── Client Handler Thread 2 → Player An
├── Client Handler Thread 3 → Player Huy
└── Game Manager Thread → Điều khiển thời hạn và vòng chơi
```

- Mỗi Client có socket và luồng nhận dữ liệu riêng.
- Danh sách Client, trạng thái ván, câu trả lời và điểm số là dữ liệu dùng chung.
- Sử dụng `threading.Lock` cho những đoạn cập nhật trạng thái dùng chung để tránh tranh chấp.
- Server gửi message tới mọi Client thông qua chức năng `broadcast()`.
- Không giữ khóa trạng thái khi thực hiện thao tác mạng chậm; lấy bản sao trạng thái/người nhận rồi gửi ra ngoài vùng khóa.

### 4.3. Message Protocol

Định dạng: **JSON UTF-8**, mỗi message kết thúc bằng ký tự xuống dòng `\n` (newline-delimited JSON / NDJSON). Các đối tượng phải được serialize trên **một dòng** trước khi gửi.

**Nguyên tắc:** Sender gửi `json.dumps(message) + "\n"`; Receiver tích lũy dữ liệu vào buffer rồi chỉ parse những dòng đã kết thúc. Có thể nhận một phần hoặc nhiều message trong một lần đọc.

Ví dụ các loại thông điệp:

| Thông điệp | Chiều | Ý nghĩa |
|---|---|---|
| `JOIN` | Client → Server | Xin tham gia phòng |
| `JOIN_OK` | Server → Client | Xác nhận và gửi thông tin Player |
| `PLAYER_LIST` | Server → Client | Cập nhật danh sách phòng |
| `QUESTION` | Server → Client | Phát câu hỏi hiện tại |
| `ANSWER` | Client → Server | Gửi lựa chọn |
| `ANSWER_ACK` | Server → Client | Chấp nhận hoặc từ chối đáp án |
| `QUESTION_RESULT` | Server → Client | Công bố đáp án đúng/điểm hiện tại |
| `GAME_OVER` | Server → Client | Kết thúc và bảng xếp hạng cuối cùng |
| `ERROR` | Server → Client | Báo lỗi dữ liệu hoặc trạng thái |

**Tham gia phòng:**

```json
{"type":"JOIN","nickname":"Minh"}
```

**Phát câu hỏi:**

```json
{
  "type": "QUESTION",
  "question_id": 5,
  "number": 1,
  "total": 10,
  "duration_seconds": 15,
  "content": "TCP thuộc tầng nào?",
  "options": {
    "A": "Application",
    "B": "Transport",
    "C": "Network",
    "D": "Physical"
  }
}
```

> **Không truyền `correct_answer` trong thông điệp `QUESTION`.** Đáp án đúng chỉ nằm ở Server đến lúc công bố kết quả.

**Gửi đáp án:**

```json
{"type":"ANSWER","question_id":5,"answer":"B"}
```

**Xác nhận gửi đáp án:**

```json
{"type":"ANSWER_ACK","question_id":5,"accepted":true}
```

**Kết quả câu hỏi:**

```json
{
  "type": "QUESTION_RESULT",
  "question_id": 5,
  "correct_answer": "B",
  "ranking": [
    {"nickname":"Minh","score":100},
    {"nickname":"An","score":0}
  ]
}
```

**Một số tình huống lỗi cần kiểm tra:** nickname rỗng/trùng, message thiếu trường, đáp án không thuộc A/B/C/D, `question_id` không đúng câu hiện tại, trả lời lặp, hết hạn, gửi dữ liệu khi game chưa bắt đầu. Client nhận `ERROR` hoặc `ANSWER_ACK` bị từ chối thay vì khiến Server lỗi.

## 5. Luồng hoạt động ván thi

```text
[Host khởi động Server]
            │
            ▼
[Server nạp SQLite và mở TCP Socket]
            │
            ▼
[Player kết nối / JOIN vào Lobby]
            │
            ▼
[Server broadcast PLAYER_LIST]
            │
            ▼
[Host nhấn BẮT ĐẦU]
            │
            ▼
[Server lấy 10 câu hỏi từ SQLite]
            │
            ▼
[Server broadcast QUESTION và mở thời hạn]
            │
            ▼
[Client chọn A/B/C/D → gửi ANSWER]
            │
            ▼
[Server kiểm tra: đúng câu? chưa gửi? còn hạn?]
            │
            ▼
[Hết thời gian → khóa câu → tính điểm]
            │
            ▼
[Broadcast QUESTION_RESULT + ranking]
            │
     ┌──────┴────────┐
     │               │
  Còn câu         Hết câu
     │               │
     └→ Câu tiếp     └→ GAME_OVER
```

### 5.1. Đồng bộ trạng thái

Game Manager tổ chức theo các trạng thái:

```text
WAITING
   │ Host Start
   ▼
QUESTION_ACTIVE
   │ Hết thời gian / đóng câu
   ▼
SHOW_RESULT
   │ Hết thời gian hiển thị kết quả
   ├── Còn câu → QUESTION_ACTIVE
   └── Hết câu → FINISHED
```

- `WAITING`: nhận người chơi, cho Host CRUD ngân hàng câu hỏi.
- `QUESTION_ACTIVE`: chỉ chấp nhận `ANSWER` của câu hiện tại trước thời hạn.
- `SHOW_RESULT`: khóa đáp án, phát đáp án đúng và tổng điểm.
- `FINISHED`: phát bảng xếp hạng cuối ván.

Mọi Client chuyển màn hình dựa trên message từ Server; Client không được tự chuyển câu hoặc tự cộng điểm.

### 5.2. Timeout và đồng hồ

- Server dùng `time.monotonic()` để lưu `deadline` mỗi câu, ví dụ sau 15 giây.
- Khi `ANSWER` đến, Server kiểm tra thời điểm nhận so với `deadline`.
- Client hiển thị đồng hồ đếm ngược để người chơi theo dõi, nhưng **Server quyết định câu còn thời gian hay không**.
- Không chấm điểm đáp án đến trễ dù Client vẫn đang hiện nút trả lời do độ trễ hoặc lỗi giao diện.
- Timeout câu hỏi **khác** timeout kết nối mạng; ở MVP chỉ cần xử lý ngắt kết nối/exception cơ bản, không cần cơ chế reconnect nâng cao.

## 6. Thiết kế cơ sở dữ liệu

**SQLite** lưu trên máy Host, file `server/quiz.db`. Không dùng MySQL Server, không cần Cloud.

### 6.1. Bảng `questions`

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | ID duy nhất |
| `content` | TEXT NOT NULL | Nội dung câu hỏi |
| `option_a` | TEXT NOT NULL | Lựa chọn A |
| `option_b` | TEXT NOT NULL | Lựa chọn B |
| `option_c` | TEXT NOT NULL | Lựa chọn C |
| `option_d` | TEXT NOT NULL | Lựa chọn D |
| `correct_answer` | TEXT NOT NULL | Một trong A/B/C/D |
| `is_active` | INTEGER NOT NULL DEFAULT 1 | 1: đang dùng; 0: ẩn |

SQL khởi tạo mẫu:

```sql
CREATE TABLE IF NOT EXISTS questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_answer TEXT NOT NULL
        CHECK (correct_answer IN ('A', 'B', 'C', 'D')),
    is_active INTEGER NOT NULL DEFAULT 1
        CHECK (is_active IN (0, 1))
);
```

### 6.2. CRUD câu hỏi

- **Create:** Host nhập nội dung, 4 lựa chọn và đáp án đúng rồi lưu SQLite.
- **Read:** hiển thị câu hỏi và hỗ trợ chọn hàng trong danh sách.
- **Update:** chọn một câu hỏi, sửa thông tin, lưu thay đổi theo `id`.
- **Delete:** chọn câu hỏi, xác nhận xóa rồi thực hiện `DELETE` theo `id` (hoặc ẩn bằng `is_active = 0`).

**Cần kiểm tra dữ liệu đầu vào:** nội dung không rỗng, đủ 4 lựa chọn, đáp án thuộc A/B/C/D. Sau khi Host nhấn Start, Game Manager tải các câu hỏi đã chọn vào bộ nhớ một lần; dữ liệu câu hỏi không bị thay đổi giữa ván.

### 6.3. Dữ liệu chỉ lưu trong bộ nhớ

Đối với MVP, **chưa cần tạo các bảng** tài khoản, lịch sử trận đấu hoặc trả lời từng lượt. Các dữ liệu sau nằm ở bộ nhớ Server:

- Danh sách Player đang kết nối.
- Điểm của từng Player.
- Câu hỏi hiện tại và bộ câu hỏi của ván.
- Danh sách người đã trả lời câu hiện tại.
- Thời điểm hết hạn và trạng thái ván.

## 7. Giao diện ứng dụng

### 7.1. Host/Admin — 2 màn hình

**Màn hình 1 — Dashboard**
- Khởi động/dừng TCP Server.
- Xem IP, Port, tình trạng lắng nghe.
- Xem danh sách nickname người đang kết nối.
- Chọn số câu / thời gian mỗi câu và nhấn Start khi đủ điều kiện.
- Xem trạng thái vòng chơi và bảng điểm.

**Màn hình 2 — Quản lý câu hỏi**
- Bảng danh sách: ID, tóm tắt nội dung, đáp án đúng, trạng thái.
- Nút thêm/sửa/xóa; form câu hỏi với 4 lựa chọn A/B/C/D.
- Kiểm tra dữ liệu, lưu và thông báo lỗi/thành công.

### 7.2. Player/Client — 3 màn hình

**Màn hình 3 — Kết nối và Lobby**
- Trường IP, Port, nickname; nút Connect.
- Danh sách người chơi đã tham gia; chờ Host bắt đầu.

**Màn hình 4 — Quiz**
- Số thứ tự câu, nội dung, 4 lựa chọn, thời gian còn lại.
- Nút gửi đáp án; thông báo đã gửi; khóa sau khi gửi/hết giờ.

**Màn hình 5 — Results/Leaderboard**
- Đáp án đúng; điểm nhận sau câu; bảng xếp hạng.
- Sau câu cuối hiển thị kết quả chung và trạng thái kết thúc.

## 8. Kiến trúc mã nguồn đề xuất

```text
network-quiz-battle/
│
├── server/
│   ├── main.py             # Khởi động Host App + Server
│   ├── tcp_server.py       # Bind, listen, accept, broadcast
│   ├── client_handler.py   # Đọc/ghi message từng Client
│   ├── game_manager.py     # State machine, timeout, score
│   ├── database.py         # SQLite, CRUD questions
│   ├── admin_ui.py         # Dashboard + question editor
│   └── quiz.db             # CSDL tại Host (sinh lúc chạy)
│
├── client/
│   ├── main.py             # Khởi động Player App
│   ├── network_client.py   # TCP connect, send, receive
│   └── player_ui.py        # Connect, Lobby, Quiz, Result
│
├── shared/
│   └── protocol.py         # Kiểu message, encode/decode
│
├── requirements.txt       # Thư viện ngoài (ví dụ customtkinter)
└── README.md              # Cài đặt, chạy và demo
```

### 8.1. Quy tắc tích hợp kỹ thuật

1. Thống nhất định dạng và tên `type` trong `shared/protocol.py` trước khi chia code.
2. Có thể dùng `socket`, `threading`, `sqlite3`, `json`, `queue`, `time` từ thư viện chuẩn Python.
3. Nếu dùng Tkinter/CustomTkinter, **không cập nhật widget trực tiếp từ thread mạng**. Thread mạng đưa sự kiện vào `queue.Queue`, main thread dùng `after()` để đọc và cập nhật giao diện.
4. Server có thể có nhiều thread; thao tác trên trạng thái chung cần được đồng bộ bằng `Lock`.
5. Ghi SQLite bằng kết nối được quản lý phù hợp theo thread, đơn giản nhất là tập trung thao tác CRUD vào lớp Database Manager/phần Host.
6. Khi gửi JSON qua TCP, bổ sung `\n` và có bộ đệm nhận để không mất/cắt message.
7. Ưu tiên vận hành ổn định trước khi đầu tư hiệu ứng giao diện.

## 9. Phân công nhóm ba người

| Thành viên | Phần chính | Đầu ra |
|---|---|---|
| **Người 1 — Networking** | TCP Server, Socket, multi-client, nhận/gửi JSON, broadcast và phát hiện ngắt kết nối | Server nhiều Client chạy ổn định |
| **Người 2 — Desktop UI** | Player UI, các màn hình connect/lobby/quiz/results, nối UI với TCP Client | Ứng dụng Player hoạt động |
| **Người 3 — Database & Logic** | SQLite, CRUD, Admin UI, Game Manager, chấm điểm, timeout | Quản lý câu hỏi và một vòng thi hoàn chỉnh |

**Việc chung:** thống nhất protocol, tích hợp module, kiểm thử trên 3 máy, viết README, chụp màn hình/sơ đồ, báo cáo và thuyết trình.

## 10. Lộ trình triển khai 5 giai đoạn

| Giai đoạn | Công việc | Mốc nghiệm thu |
|---|---|---|
| 1. Networking Core | TCP Server/Client, kết nối nhiều máy, gửi nhận JSON | 3 Client kết nối và nhận broadcast |
| 2. Quiz Engine | Bộ câu hỏi tạm, nhận đáp án, chấm điểm, timeout | Chạy được một vòng hỏi–đáp không cần UI đẹp |
| 3. Database & Admin | SQLite, CRUD, tải câu hỏi vào game | Quản lý câu hỏi và lưu lại sau khởi động |
| 4. Desktop UI | Màn hình Host và Player, nối logic | Chơi được toàn bộ ván bằng giao diện |
| 5. Tích hợp & Kiểm thử | LAN nhiều máy, lỗi mạng, nhập liệu sai, báo cáo | Demo ổn định và bàn giao mã nguồn |

## 11. Kiểm thử và tiêu chí nghiệm thu

| Test ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| T01 | Khởi động Server, 3 Client cùng kết nối | Cả 3 xuất hiện trong lobby |
| T02 | Một Client đặt nickname trùng | Server từ chối hoặc yêu cầu đổi tên |
| T03 | Host thêm câu hỏi rồi mở lại ứng dụng | Câu hỏi được giữ trong SQLite |
| T04 | Host sửa và xóa câu hỏi | Danh sách và dữ liệu được cập nhật đúng |
| T05 | Host nhấn Start | Cùng một câu hỏi xuất hiện ở mọi Client |
| T06 | Player gửi đáp án đúng | Được +100 điểm |
| T07 | Player gửi đáp án sai | Không cộng điểm |
| T08 | Player gửi ANSWER hai lần cho cùng câu | Chỉ ghi nhận lần hợp lệ đầu tiên |
| T09 | Player trả lời sau 15 giây | Bị từ chối, không tính điểm |
| T10 | 3 Player trả lời gần như đồng thời | Server tính điểm đúng, không lỗi race condition |
| T11 | Một Client ngắt kết nối giữa ván | Server và các Client còn lại tiếp tục hoạt động |
| T12 | Hết 10 câu | Mọi Client xem bảng xếp hạng thống nhất |

### 11.1. Kịch bản demo đề xuất khi bảo vệ

1. Trên máy Host, mở Dashboard và hiển thị IP/Port.
2. Mở Quản lý câu hỏi, thêm một câu mới, sửa rồi xóa một câu thử nghiệm.
3. Trên ba máy Client, nhập IP Server và tham gia phòng bằng các nickname khác nhau.
4. Host nhấn Bắt đầu; cả ba máy cùng thấy Câu 1.
5. Một người trả lời đúng, một người sai, một người để hết giờ.
6. Hiển thị đáp án đúng, điểm số và bảng xếp hạng.
7. Ngắt kết nối một Client ở câu sau, xác nhận Server vẫn hoạt động.
8. Kết thúc ván, hiển thị kết quả cuối cùng trên các máy còn lại.

## 12. Công nghệ sử dụng

| Công nghệ | Mục đích |
|---|---|
| Python | Ngôn ngữ lập trình |
| `socket` | Kết nối TCP Client–Server |
| `threading` | Xử lý nhiều Client và Game Manager |
| `json` | Tuần tự hóa và đọc thông điệp |
| `sqlite3` | Cơ sở dữ liệu câu hỏi |
| Tkinter hoặc CustomTkinter | Giao diện desktop |
| `queue` | Chuyển sự kiện từ thread mạng sang UI |
| `time.monotonic()` | Xác định timeout câu hỏi |
| Git/GitHub | Quản lý phiên bản và làm việc nhóm |

## 13. Những tính năng chưa làm ở phiên bản 1.0

Để giữ đúng mức nhập môn và bảo đảm tiến độ, **không triển khai trong MVP**:

- Nhiều phòng thi đồng thời hoặc matchmaking tự động.
- Tài khoản đăng ký/đăng nhập và phân quyền từ xa phức tạp.
- Triển khai trên Internet/Cloud và cấu hình NAT traversal.
- Web admin, WebSocket, ứng dụng di động.
- Lưu lịch sử ván thi/analytics chi tiết.
- Khôi phục điểm và phiên chơi sau khi mất mạng.
- Cơ chế chống gian lận nâng cao, mã hóa đầu cuối, chống tấn công Internet công khai.

Các chức năng này có thể đề xuất làm **hướng phát triển** sau khi phiên bản cơ bản đã ổn định.

## 14. Kết luận

**Network Quiz Battle — Desktop LAN Edition** sử dụng mô hình **Client–Server tập trung**. Host chạy TCP Server, Game Manager, giao diện quản trị và SQLite. Nhiều Player kết nối qua LAN bằng TCP Socket, trao đổi các message JSON có quy ước rõ ràng. Server chịu trách nhiệm đồng bộ câu hỏi, thời hạn, đáp án và bảng điểm; Player chủ yếu hiển thị và gửi tương tác.

**Ưu tiên kỹ thuật:** xây một luồng **Client kết nối → Server phát câu hỏi → Client gửi đáp án → Server chấm điểm → trả kết quả** chạy ổn định trước. Sau đó mới tích hợp CRUD, giao diện hoàn chỉnh và các cải tiến phụ.

---

*Tài liệu dùng làm bản đặc tả khởi đầu cho nhóm 3 sinh viên và có thể điều chỉnh theo yêu cầu chính thức của giảng viên.*
