package vn.edu.hutech.nqb.server;

import static vn.edu.hutech.nqb.server.Wire.obj;

/** Sample questions are test-only and never shipped in the Server. */
public final class ServerTestData {
        static final String[][] QUESTIONS={
            {"Giao thức nào đảm bảo truyền dữ liệu tin cậy?","UDP","TCP","IP","ICMP","B"},
            {"Lớp Java nào lắng nghe kết nối TCP?","Socket","ServerSocket","DatagramPacket","URL","B"},
            {"TCP truyền dữ liệu theo mô hình nào?","Byte stream","Thông điệp riêng","File riêng","Ảnh","A"},
            {"Địa chỉ 127.0.0.1 là gì?","Broadcast","Gateway","Loopback","DNS công cộng","C"},
            {"Phương thức nào ghi dữ liệu vào stream?","read()","write()","accept()","bind()","B"},
            {"TLS bảo vệ điều gì?","CPU","Ổ đĩa","Dữ liệu truyền","Màn hình","C"},
            {"Mật khẩu nên được lưu thế nào?","Văn bản rõ","MD5","Base64","Hash chậm có salt","D"},
            {"Port TCP dùng để làm gì?","Định danh dịch vụ","Tên máy","Subnet mask","Độ dài dây","A"},
            {"UTF-8 hỗ trợ điều gì?","Unicode","Tăng điện áp","Mở firewall","Nén TCP","A"},
            {"read() có luôn nhận đủ một JSON?","Có","Không","Chỉ LAN","Chỉ Java","B"},
            {"request_id dùng để làm gì?","Chấm điểm","Mã hóa","Đối chiếu phản hồi","Thay IP","C"},
            {"Ai quyết định deadline câu hỏi?","Mỗi Client","Server","Wireshark","Router","B"},
            {"PreparedStatement giúp xử lý điều gì?","Tham số SQL an toàn","Mất điện","Màn hình tối","Sai SAN","A"},
            {"Heartbeat dùng để làm gì?","Phát nhạc","Nén ảnh","Đổi port","Theo dõi kết nối","D"},
            {"accept() trả về đối tượng nào?","Socket","String","File","Thread","A"},
            {"Socket timeout tránh điều gì?","Trùng IP","Chờ vô hạn","Unicode","Xếp hạng","B"},
            {"Ai truy cập SQLite trong mô hình này?","Học sinh","Mọi máy","Chỉ Server","Router","C"},
            {"LAN có bắt buộc Internet?","Có","Không","Nếu TLS","Nếu JavaFX","B"},
            {"ANSWER_ACK xác nhận điều gì?","Chắc chắn đúng","Đã nhận đáp án","Ván kết thúc","Thưởng tốc độ","B"},
            {"Quyền sở hữu câu hỏi dựa vào đâu?","Role Client gửi","Avatar","Tên tab","Tài khoản đã xác thực","D"}};
    public static void seedQuestions(QuizStore store,int owner) throws Exception {
        if(store.questions(owner,obj()).path("total").asInt()!=0)throw new IllegalStateException("Test bank must be empty");
        for(var q:QUESTIONS)store.saveQuestion(owner,obj().put("content",q[0]).put("option_a",q[1]).put("option_b",q[2]).put("option_c",q[3]).put("option_d",q[4]).put("correct_answer",q[5]).put("category","Lập trình mạng").put("is_active",true),false);
    }
}
