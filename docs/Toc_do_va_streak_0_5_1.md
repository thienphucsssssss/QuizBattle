# Điểm tốc độ, streak và thời gian trả lời — 0.5.1

Theo yêu cầu mới, Server thưởng thêm cho đáp án đúng, nhanh và chuỗi đúng. Thay quy tắc cố định 100 điểm của bản 0.5.0; giữ luồng câu hỏi, bảng hạng và hoạt ảnh hiện tại.

## Quy tắc

| Thành phần | Điểm |
| --- | --- |
| Trả lời đúng | 100 |
| Tốc độ | `floor(100 × (T − t) / T)`; từ 0 đến 100 |
| Chuỗi đúng | `20 × min(streak − 1, 5)`; từ câu đúng liên tiếp thứ hai |
| Sai, không trả lời hoặc hết hạn | 0, streak về 0 |

`T` là thời lượng câu theo mili giây, `t` là thời gian Server ghi nhận đáp án hợp lệ đầu tiên, tính từ lúc mở câu bằng đồng hồ đơn điệu `System.nanoTime()`. Thời gian bao gồm truyền qua LAN. Không dùng đồng hồ hoặc trường thời gian do Client gửi. Đáp án nhận từ deadline trở đi bị từ chối.

Ví dụ câu 10 giây, trả lời đúng lúc 5 giây: lần đúng đầu 150 điểm; đúng liên tiếp lần hai 170 điểm. Thưởng chuỗi đạt trần 100 từ chuỗi 6; mỗi câu tối đa 300 điểm. Hai đáp án gần nhau có thể bằng điểm do làm tròn về điểm nguyên. Đồng điểm tổng vẫn đồng hạng.

Chỉ chốt điểm/streak khi hết câu. ACK chỉ xác nhận đã nhận và trả thời gian, không lộ đáp án đúng, điểm hoặc streak của câu đang mở. Gửi trùng không thay lựa chọn, thời gian hay số điểm. Sai hoặc bỏ lỡ ngắt chuỗi; ván mới/reset xóa cả chuỗi và thời gian cũ.

## Giao diện

- Sau khi Server nhận đáp án, cạnh nickname có chữ nhỏ dạng `1,23 s` và giữ nguyên tới câu tiếp theo. Không lấy thời gian Client đang chờ ACK làm thời gian trả lời.
- Từ hai câu đúng liên tiếp có nhãn nhỏ **Chuỗi ×2**. Màn đáp án đúng có phần thưởng tốc độ/chuỗi; màn bảng hạng có thời gian nhỏ cạnh tên và chuỗi dưới tên.
- Khi sang câu mới, thời gian cũ ẩn đi; chưa trả lời không hiển thị `0,00 s`. Đăng xuất hoặc reset không để thông tin ván trước còn trên tên.
- Lịch sử tiếp tục lưu tổng điểm và số câu đúng; không tính lại điểm cũ. Thời gian từng câu và chuỗi trực tiếp chưa bổ sung vào database lịch sử. Schema vẫn là 4.

## Đồng bộ và kiểm tra

`ROOM_STATE.scoring_version = SPEED_STREAK_V1`. ACK nhận đáp án hợp lệ có `answer_elapsed_ms`. Các bản tin kết quả có `points`, `speed_bonus`, `streak_bonus`, `streak`, `best_streak`, `answer_elapsed_ms`; ranking trực tiếp có chuỗi và thời gian. `-1` nghĩa chưa trả lời, không phải 0 giây. ROOM_STATE chỉ trả thời gian riêng cho học sinh đó hoặc chủ phòng/Admin; ranking sau câu trả các thời gian công khai.

Các kiểm thử bổ sung bao gồm biên thời gian/công thức, trần thưởng, sai/bỏ lỡ, giả mạo điểm/thời gian/chuỗi, gửi trùng, so sánh hai Client nhanh/chậm, cộng điểm/lưu lịch sử/reset và QA thời gian cạnh nickname ở 1024×640, 1366×768. Bộ cài chính được cập nhật cùng Server trong `dist/QuizBattle/`, không cần thay IP/chứng chỉ.

Ngày 29/10/2026: 148 kiểm thử Maven đạt (0 failures/errors). QA JavaFX toàn luồng đạt ở hai độ phân giải, kiểm tra thêm font nhỏ, xóa thời gian ở câu tiếp theo, chuỗi ×2/thưởng 20 và tương phản chữ trên nhãn. Log `tmp/speed-streak-*.log`, ảnh `output/speed-streak-ui-*`.

Bộ cài 0.5.1 đã cài trên MSI, SHA-256: `890ea96ee2b2bc05a77e0883cf67271e7f560a554a7c73dfea4b6e87420a4d76`. Bản trước được sao lưu tại `backups/QuizBattle-before-0.5.1-20261029-065850/`. Đối chiếu dữ liệu sau cập nhật khớp mọi trường cũ, schema vẫn 4; 6 tệp chứng chỉ/cấu hình giữ nguyên hash. Bộ chạy vẫn duy nhất `dist/QuizBattle/`.
