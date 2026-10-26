# Nhạc nền Client — 0.3.6

## Sử dụng

Mở QuizBattle → Tùy chọn → **Nhạc nền ván đấu**. Âm lượng nhạc nền độc lập với âm thanh giao diện. Lựa chọn được lưu trên máy theo vai trò học sinh/giảng viên (Admin dùng cấu hình giảng viên). Mặc định giảng viên bật ở 20%; học sinh tắt, có thể tự bật. Máy trình chiếu dùng cùng Client và không nhân đôi âm thanh.

Cập nhật 26/10/2026: nhạc nền dừng ngay khi hết ván, không tự phát bản chung cuộc dài khoảng 63 giây. Chỉ còn hiệu ứng kết thúc ngắn khoảng 2 giây nếu bật âm thanh giao diện. Rời/đóng/reset phòng, hủy ván hoặc mất kết nối hủy cả hiệu ứng đang nạp và đang chờ phát.

| Trạng thái | Nhạc |
| --- | --- |
| Đăng nhập, phòng chờ, chưa tham gia ván | Không phát |
| Đếm ngược → câu hỏi → đáp án → bảng hạng từng câu | Network Quiz Battle BGM, lặp liên tục, không khởi động lại giữa các câu |
| Hoàn thành đủ câu → bục vinh danh | Dừng nhạc nền; hiệu ứng kết thúc ngắn nếu bật SFX |
| Hủy ván, reset, đóng/rời phòng, đăng xuất, mất kết nối, đóng Client | Dừng nhạc và giải phóng thiết bị |

Khi tắt/bật lại nhạc giữa ván, bản nhạc hiện tại phát lại từ đầu. Cập nhật trạng thái, chỉnh âm lượng hoặc bật lại nhạc sau khi hết ván không khởi động nhạc. Tài nguyên High Score Glory vẫn được giữ nguyên trong Client nhưng không tự phát khi chung cuộc.

## Tài nguyên và kiến trúc

Hai file gốc tại `E:/_HUTECH/Network_Quiz_Battle_SFX_V2_17_WAV/` được sao chép nguyên byte vào tài nguyên Client:

- `Network_Quiz_Battle_BGM_Seamless_Loop (1).wav` → `/ui/audio/music_game.wav` (~91 giây).
- `High_Score_Glory.wav` → `/ui/audio/music_victory.wav` (~63 giây).

Cả hai là PCM signed 44.1 kHz, 16-bit, stereo. Metadata/SHA256 nằm trong `/ui/audio/music-manifest.json`, tách biệt manifest 17 SFX. Không sinh lại, chuyển đổi, cắt hoặc sửa bản gốc; độ liền mạch tại điểm nối phụ thuộc chính file loop đã cung cấp.

`MusicSlots` dùng một worker daemon riêng và Java Sound Clip. Chỉ giữ một clip nhạc tại một thời điểm, đóng clip cũ trước khi mở clip tiếp theo. Không decode hoặc mở thiết bị trên JavaFX thread. Nạp muộn bị hủy nếu người dùng đã rời phòng/tắt âm; sự kiện trùng không khởi động lại nhạc. Trạng thái và điểm thi vẫn lấy từ Server. Thiết bị âm thanh lỗi chỉ báo tại Tùy chọn, không cản gameplay. Không truyền WAV qua mạng, không thay giao thức/Server/SQLite/TLS.

## Kiểm thử

- `MusicSlotsTest`: vòng lặp/đổi giai đoạn, hết ván dừng nhạc, ván mới, hủy/reset/rời phòng, mute/âm lượng, vai trò và lưu tùy chọn, silent QA, nạp muộn, đóng ứng dụng, lỗi thiết bị.
- `SfxSlotsTest`: hủy hiệu ứng trễ/đang nạp/đang chờ, reset dừng hiệu ứng và cho phép phát lại ở phòng mới, tắt/bật âm khi đang nạp.
- `MusicAssetsTest`: hai asset khớp SHA256 gốc, decode đúng PCM stereo 44.1 kHz/16-bit.
- `MusicDeviceCheck`: opt-in mở/đóng hai clip bằng thiết bị Java Sound thật, không gọi phát âm thanh.
- `LiveLanUiCheck`: ván qua TLS Server/SQLite tạm, kiểm tra nhạc GAME ở từng chặng → NONE khi chung cuộc/reset/đóng phòng và các điều khiển Tùy chọn. Chạy im lặng để không phát nhiều bản nhạc trong QA; không đổi preference âm thanh người dùng.

Việc kiểm thử tự động không thay thế nghe thử cảm nhận về âm lượng và điểm nối loop trong phòng học.

## Kết quả triển khai ngày 22/10/2026

- Build sạch và 95 tests đạt, không lỗi/skip (shared 17, core 32, Server 22, Client 24).
- Bộ cài mới: `dist/QuizBattle-Windows-20261022-121419/QuizBattle-Setup.exe`, phiên bản 0.3.6. Hai WAV nằm trong JAR đã cài, không cần sao chép thủ công.
- Đã cập nhật `C:/Users/starg/AppData/Local/QuizBattle/QuizBattle.exe`; sao lưu Client cũ tại `backups/music-client-20261022-121655/`. Giữ nguyên profile/chứng chỉ công khai và preference có sẵn. Không thay đổi Server cá nhân, tài khoản hoặc ngân hàng câu hỏi.
- `MusicDeviceCheck` đạt với cả hai clip trên runtime đã cài, không phát tiếng trong kiểm thử. `LiveLanUiCheck` đạt bằng runtime đi kèm, khi bỏ Java hệ thống khỏi PATH; kết quả/ảnh tại `output/installed-runtime-music-qa/`, gồm `10-sound-settings.png`.
