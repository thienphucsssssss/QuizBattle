package vn.edu.hutech.nqb.admin;

import org.junit.jupiter.api.Test;
import vn.edu.hutech.nqb.client.GatewayException;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

class ServerCompatibilityTest {
    @Test void unsupportedRequestExplainsThatTheServerMustBeUpdatedAndRestarted() {
        String message=ArcadeApplication.message(new CompletionException(new GatewayException("UNKNOWN_TYPE","Yêu cầu không được hỗ trợ.",object())));
        assertTrue(message.contains("Server"),message);
        assertTrue(message.contains("bản cập nhật"),message);
        assertTrue(message.contains("mở lại")||message.contains("khởi động lại"),message);
    }
    @Test void ordinaryFailuresKeepTheirSpecificRecoveryMessage() {
        var error=new GatewayException("TIMEOUT","Máy chủ chưa phản hồi. Kiểm tra kết nối trước khi thử lại.",object());
        assertEquals(error.getMessage(),ArcadeApplication.message(new CompletionException(error)));
    }
}
