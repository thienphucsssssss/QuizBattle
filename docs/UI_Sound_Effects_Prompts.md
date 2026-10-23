# Bộ prompt UI sound effects cho Network Quiz Battle

Mục đích: tạo bộ hiệu ứng giao diện ngắn, đồng nhất cho ứng dụng JavaFX LAN theo phong cách arcade hiện đại đã chọn. Đây là prompt và quy tắc sử dụng dự kiến; chưa tạo âm thanh hoặc tích hợp mã Java.

## Cách sử dụng

1. Mỗi mục bên dưới là một prompt tiếng Anh độc lập. Copy toàn bộ prompt của một mục vào công cụ tạo sound effect, không chọn chế độ tạo bài hát hoặc nhạc nền.
2. Nếu công cụ có ô Duration, nhập thời lượng mục tiêu tương ứng. Prompt chỉ mô tả mong muốn, không bảo đảm công cụ xuất đúng thời lượng/định dạng.
3. Tạo 2–3 biến thể cho mỗi âm, nghe thử ở cùng mức âm lượng rồi chọn một bản. Nếu công cụ hỗ trợ tham chiếu audio, dùng ui_press hoặc ui_success đã chọn làm tham chiếu chất âm cho các mục sau; không bắt buộc.
4. Tải và đặt tên như mục tương ứng. Ưu tiên WAV PCM 16-bit, 44.1 kHz, mono. Có thể gửi file gốc stereo hoặc 48 kHz nếu công cụ không có lựa chọn này; không chỉ đổi đuôi MP3 thành WAV.
5. Giữ chất âm sạch, dễ nghe và gần nhau về độ lớn cảm nhận. Không cố tăng âm cho thật to; không clipping, không khoảng im lặng dài ở đầu, đuôi kết thúc gọn và không bị click do cắt đột ngột.
6. Gom file thành một thư mục/ZIP, kèm tên công cụ và thông tin quyền sử dụng nếu có. Giữ bản gốc; cân âm lượng/cắt khoảng im lặng sẽ thực hiện trên bản sao khi tích hợp.

JavaFX 21 hỗ trợ WAV với mã hóa PCM, và AudioClip phù hợp hiệu ứng ngắn. Tham khảo [định dạng media JavaFX](https://openjfx.io/javadoc/21/javafx.media/javafx/scene/media/package-summary.html) và [AudioClip](https://openjfx.io/javadoc/21/javafx.media/javafx/scene/media/AudioClip.html).

## Chất âm chung

Arcade hiện đại, có cảm giác xúc giác nhẹ: tiếng chạm gọn, synth tròn/ấm, đôi chút vật liệu gỗ hoặc nhựa mờ. Vui nhưng không trẻ con, không kiểu hacker/sci-fi hoặc máy slot casino. Gọn và dễ nghe nhiều lần trong lớp học.

Không dùng voice, lời nói, nhạc nền, drum beat, tiếng đám đông, tiếng báo động chói, reverb dài hoặc âm thanh/logotype sao chép từ Kahoot/Windows/game khác. Những cụm nốt ngắn dưới đây là sound cue cho một sự kiện, không phải yêu cầu tạo bản nhạc.

## A Hiệu ứng giao diện và kết nối

### 01 ui_press.wav

Dùng cho: bấm nút thông thường, mở thao tác; không phát cho mỗi lần hover hoặc gõ phím. Thời lượng mục tiêu: 0.10 giây.

```text
Create one isolated UI button-press sound, approximately 0.10 seconds.
A tiny tactile tap: a muted wooden click layered with a warm rounded synth pluck. Immediate attack, compact body, clean short decay. Modern arcade desktop interface, playful but restrained, comfortable when repeated.
One event only. Dry, centered mono-compatible sound, no leading silence. No voice, music, ambience, piercing beep, long reverb, or recognizable branded sound.
```

### 02 ui_select.wav

Dùng cho: chọn tab, chọn vai trò, bật/tắt tùy chọn; không dùng để báo đáp án đã được Server nhận. Thời lượng: 0.15 giây.

```text
Create one isolated UI selection sound, approximately 0.15 seconds.
A soft rounded pop with a tiny upward pitch bend, slightly brighter than a button click. It should feel like a selector snapping gently into place, not a success celebration. Warm tactile synth, modern arcade desktop interface.
One event only. Dry, centered mono-compatible sound, immediate onset and tidy decay. No voice, music, ambience, harsh treble, long reverb, or imitation of existing app sounds.
```

### 03 ui_transition.wav

Dùng cho: mở panel/dialog hoặc đổi màn quan trọng; không phát khi mọi toast xuất hiện. Thời lượng: 0.25 giây.

```text
Create one isolated UI panel-opening transition sound, approximately 0.25 seconds.
A small soft air sweep ending in a delicate matte-plastic tap. Compact and close, like a lightweight interface panel settling into place. Warm modern arcade character, understated rather than cinematic.
One event only. Dry, centered mono-compatible sound with a clean ending. No voice, music, room ambience, large whoosh, bass impact, long reverb, or branded audio imitation.
```

### 04 ui_success.wav

Dùng cho: đăng nhập, lưu câu hỏi, tạo tài khoản hoặc cập nhật thành công — chỉ sau phản hồi Server. Thời lượng: 0.40 giây.

```text
Create one isolated UI task-success sound, approximately 0.40 seconds.
Two gentle ascending rounded synth notes with a small tactile click at the start. Clear completion and quiet satisfaction, not victory or excitement. Warm, clean modern arcade desktop interface, less energetic than a correct-answer sound.
One event only. Dry, centered mono-compatible sound, immediate onset and short decay. No voice, music bed, percussion beat, glittery cascade, long reverb, or recognizable notification melody.
```

### 05 ui_warning.wav

Dùng cho: cảnh báo câu hỏi trùng, dữ liệu chưa lưu hoặc thao tác cần xác nhận. Thời lượng: 0.35 giây.

```text
Create one isolated gentle UI warning sound, approximately 0.35 seconds.
Two soft mid-register mallet-like taps with a slight downward pitch movement. The feeling is “please check this” rather than danger or failure. Warm rounded tone, modern arcade desktop interface, noticeable without being startling.
One event only. Dry, centered mono-compatible sound and compact decay. No voice, music, siren, alarm, angry buzzer, piercing frequencies, long reverb, or branded notification imitation.
```

### 06 ui_error.wav

Dùng cho: thao tác bị từ chối, validation hoặc lỗi request. Thời lượng: 0.30 giây.

```text
Create one isolated UI action-rejected sound, approximately 0.30 seconds.
A short muted low-mid synth knock followed by a soft downward blip. Clear but polite, like an action gently bouncing back. Warm tactile modern arcade interface. Distinct from a warning and from losing a quiz round, never humiliating or aggressive.
One event only. Dry, centered mono-compatible sound with a clean ending. No voice, music, distortion, loud buzzer, deep cinematic impact, long reverb, or copied system error sound.
```

### 07 connection_up.wav

Dùng cho: kết nối an toàn tới Server đã hoàn tất; không phát ngay khi bắt đầu connect. Thời lượng: 0.45 giây.

```text
Create one isolated secure-connection-established UI sound, approximately 0.45 seconds.
Two small rounded digital pulses that gently rise and settle into one stable warm tone. Suggest a link becoming connected and reliable. Modern arcade desktop interface, calm and welcoming, not futuristic computer chatter.
One event only. Dry, centered mono-compatible sound, immediate onset and short tail. No voice, music, modem noise, electrical crackle, sci-fi sweep, long reverb, or recognizable branded connection sound.
```

### 08 connection_down.wav

Dùng cho: mất kết nối đang hoạt động; chỉ phát một lần cho một lần mất kết nối, không mỗi lần retry. Thời lượng: 0.45 giây.

```text
Create one isolated connection-lost UI sound, approximately 0.45 seconds.
Two soft rounded digital pulses that step downward, ending in a muted tactile tap. Communicate that a link has disconnected, with gentle concern rather than panic. Use the same warm modern arcade tone family as a connection-established cue.
One event only. Dry, centered mono-compatible sound with a clean decay. No voice, music, siren, horror atmosphere, electrical failure noise, loud bass, long reverb, or branded audio imitation.
```

## B Hiệu ứng phòng chờ và vòng thi

### 09 player_join.wav

Dùng cho: một học sinh vừa tham gia phòng chờ; âm phụ của giảng viên, có thể tắt nếu nhiều người vào cùng lúc. Thời lượng: 0.20 giây.

```text
Create one isolated quiz-lobby player-arrival UI sound, approximately 0.20 seconds.
A friendly warm bubble pop with a tiny rounded upward pluck. It should feel like a new participant gently appearing in a room, small and welcoming rather than a celebration. Modern arcade desktop interface, soft and easy to repeat.
One event only. Dry, centered mono-compatible sound, immediate onset and clean ending. No voice, music, crowd, cartoon character noise, excessive sparkle, long reverb, or branded sound imitation.
```

### 10 player_ready.wav

Dùng cho: Server xác nhận học sinh chuyển sang sẵn sàng; không phát cả ui_select và player_ready cho cùng một lần xác nhận. Thời lượng: 0.20 giây.

```text
Create one isolated quiz-player-ready confirmation sound, approximately 0.20 seconds.
A small tactile latch click followed by one firm, warm rounded synth note. The feeling is “locked in and ready,” not answer correctness or victory. Modern arcade desktop interface, slightly more decisive than a soft selection pop.
One event only. Dry, centered mono-compatible sound with no leading silence and short decay. No voice, music, mechanical clank, harsh beep, long reverb, or copied game sound.
```

### 11 countdown_tick.wav

Dùng cho: một nhịp đếm 3–2–1 trước ván; ứng dụng phát lại cùng file, không cần tạo ba file. Thời lượng: 0.12 giây.

```text
Create exactly one countdown tick for a quiz UI, approximately 0.12 seconds.
A clean rounded woodblock-like synth pulse in the mid register, slightly firmer than a button click. Focused and anticipatory without stress. It must remain pleasant when the application plays it once per second for three seconds.
Generate one tick only, not a countdown sequence or loop. Dry and centered mono-compatible. No spoken numbers, voice, music, ticking bed, piercing beep, alarm, long reverb, or branded imitation.
```

### 12 round_open.wav

Dùng cho: câu hỏi bắt đầu, gồm câu đầu sau countdown và câu tiếp theo. Thời lượng: 0.45 giây.

```text
Create one isolated quiz-question-opening UI cue, approximately 0.45 seconds.
A compact two-step upward synth movement ending in a crisp rounded pluck. Energetic but light, signaling “the next question is now open.” Warm modern arcade tone, clear rhythm and a tactile finish, not a full musical intro.
One event only. Dry, centered mono-compatible sound, immediate onset and tidy decay. No voice, music bed, drum beat, cinematic sweep, heavy bass, long reverb, or copied quiz-game audio.
```

### 13 round_close.wav

Dùng cho: Server đóng câu; trung tính, không tiết lộ đúng/sai. Thời lượng: 0.35 giây.

```text
Create one isolated quiz-question-closed UI cue, approximately 0.35 seconds.
A short rounded mid-register tone stepping downward into a soft tactile stop. Communicate “this question is closed,” neutrally, without suggesting a correct or incorrect answer. Warm modern arcade sound, firm but never like an alarm.
One event only. Dry, centered mono-compatible sound with a clean ending. No voice, music, game-show buzzer, ringing bell, harsh treble, long reverb, or recognizable branded audio.
```

### 14 answer_ack.wav

Dùng cho: ANSWER_ACK accepted=true; đây là âm “đã nhận”, không phải âm đúng. Thời lượng: 0.18 giây.

```text
Create one isolated answer-received confirmation UI sound, approximately 0.18 seconds.
A tiny secure-feeling latch click with a neutral rounded synth ping. Communicate only “your answer was received,” with no rising victory gesture and no indication of correctness. Warm tactile modern arcade desktop interface, concise and reassuring.
One event only. Dry, centered mono-compatible sound, immediate onset and short decay. No voice, music, celebratory chord, error buzz, long reverb, or copied app sound.
```

### 15 answer_correct.wav

Dùng cho: kết quả từ Server cho biết trả lời đúng, sau khi câu đã đóng. Thời lượng: 0.65 giây.

```text
Create one isolated correct-answer reward UI sound, approximately 0.65 seconds.
Three clean ascending rounded synth plucks resolving warmly, with a tiny tactile accent on the final note. Bright, satisfying and playful, a modest reward for one correct answer. Modern arcade character, fuller than a task-success notification but smaller than a match-ending celebration.
One event only. Dry, centered mono-compatible sound and compact tail. No voice, music bed, cheering, coin cascade, casino style, piercing sparkle, long reverb, or copied game melody.
```

### 16 answer_wrong.wav

Dùng cho: kết quả sai/bỏ trống; nhẹ nhàng, không làm người học khó chịu. Thời lượng: 0.45 giây.

```text
Create one isolated incorrect-answer feedback UI sound, approximately 0.45 seconds.
Two mellow rounded synth notes gently descending, followed by a soft cushioned tap. Communicate “not this time, keep going,” not punishment or defeat. Warm modern arcade character, friendly and encouraging, less abrupt than a rejected-action error.
One event only. Dry, centered mono-compatible sound with a tidy decay. No voice, music, mocking noise, sad trombone, loud buzzer, distortion, long reverb, or copied quiz-game audio.
```

### 17 match_finish.wav

Dùng cho: ván kết thúc và bảng hạng cuối đã tới; không riêng “thắng” vì nhiều học sinh và đồng hạng. Thời lượng: 1.10 giây.

```text
Create one isolated quiz-match-complete UI celebration cue, approximately 1.10 seconds.
A compact four-note ascending motif of warm rounded synth plucks, ending in a small satisfying chord and a soft tactile accent. Celebrate everyone completing the match, not only a winner. Modern arcade desktop character, upbeat and distinctive but restrained enough for a classroom.
One event only, not a song or loop. Dry, centered mono-compatible sound and short final decay. No voice, music bed, drum beat, applause, orchestral brass, casino jackpot, long reverb, or recognizable branded melody.
```

## Những âm không cần tạo riêng

| Sự kiện | Âm dùng lại |
| --- | --- |
| Bật/tắt âm thanh, chọn vai trò/tab | ui_select; tắt âm thì không phát thêm tiếng sau mute |
| Đăng nhập thành công, lưu câu hỏi, tạo/sửa tài khoản | ui_success sau phản hồi Server |
| Câu hỏi trùng, rời form chưa lưu, xác nhận thao tác nguy hiểm | ui_warning nếu người dùng đã bật âm giao diện |
| Sai mật khẩu, lỗi validation/request, thao tác trái quyền | ui_error; không phát thêm nếu cùng sự kiện đã có connection_down |
| Tạo phòng thành công | ui_success |
| Hủy ván | ui_warning một lần khi Server xác nhận, không fanfare |
| Sửa bộ lọc, phân trang, đóng dialog | Không bắt buộc âm; dùng ui_select/ui_press nếu bật âm giao diện |
| Tải dữ liệu, loading, hover, focus, gõ văn bản | Không âm thanh |
| Bảng hạng đổi vị trí sau mỗi câu | Phản hồi hình ảnh; không cần thêm tiếng đếm từng điểm |

## Quy tắc tích hợp để âm không gây ồn

- Bộ 17 file là bộ tài nguyên đầy đủ, không có nghĩa phải bật mọi âm cho mọi thao tác.
- Tách hai nhóm tùy chọn: hiệu ứng giao diện và hiệu ứng quiz. Giảng viên mặc định bật hiệu ứng quiz ở mức vừa phải; hiệu ứng giao diện có thể bật riêng. Học sinh mặc định mute, bật khi muốn dùng tai nghe.
- ui_press/ui_select không chồng với âm ngữ nghĩa cho cùng một thao tác. Không phát đồng thời ba tiếng click/success/dialog chỉ vì một lần bấm nút.
- player_join/player_ready là cue phụ, giới hạn tần suất khi nhiều người đổi trạng thái. Trong ván không phát tiếng arrival của người không được JOIN.
- countdown_tick là một file, phát ở ba nhịp trước ván. Không phát ticking suốt câu hỏi hoặc âm cảnh báo mỗi giây.
- round_close không báo đúng/sai. answer_ack chỉ sau ACK accepted. answer_correct/answer_wrong chỉ sau QUESTION_RESULT của đúng ván/câu. Không phát tiếng đúng ngay khi người dùng bấm lựa chọn.
- Cửa sổ điều hành và trình chiếu dùng chung một AudioService để không phát hai lần. Mute có hiệu lực ngay, không làm thay đổi trạng thái Server.
- Nếu QUESTION_RESULT vừa đóng câu vừa báo đúng/sai trên máy học sinh, ưu tiên âm đúng/sai, không chồng thêm round_close. GAME_OVER ưu tiên match_finish, bỏ các cue chuyển chặng còn chờ.
- Dedupe theo ván/câu/sự kiện; không phát lại kết quả cũ khi mở cửa sổ hoặc kết nối lại. Không tự retry phát hàng loạt âm sau mất kết nối.
- Lỗi nạp asset/thiết bị âm thanh không ngăn login, nộp đáp án hoặc hiển thị kết quả. Mọi thông tin quan trọng luôn có phản hồi bằng chữ/biểu tượng, không dựa chỉ vào audio.

## Kiểm tra trước khi gửi file

- Một file là một sound effect, không chứa 3 biến thể nối nhau, lời nói hoặc nhạc nền.
- Nghe ui_press và countdown_tick lặp vài lần; nếu chói hoặc mệt tai, chọn bản mềm hơn.
- answer_ack không giống answer_correct; round_close không giống answer_wrong; ui_warning không giống báo động.
- match_finish ngắn hơn khoảng 1.5 giây, không thành bài nhạc hoặc màn intro dài.
- Nghe cả bộ liên tiếp ở cùng âm lượng; cùng chất âm, không có một file to vượt hẳn các file còn lại.
- Kiểm tra đúng định dạng bằng thông tin file, không đổi tên phần mở rộng để giả chuyển mã.
- Lưu thông tin quyền sử dụng/giấy phép của công cụ; không suy ra quyền phân phối chỉ vì file được tạo bằng AI.
