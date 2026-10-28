package vn.edu.hutech.nqb.admin;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import static vn.edu.hutech.nqb.admin.FxKit.*;

/** Stable management navigation using the existing product identity. */
final class ManagementSidebar extends VBox {
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Label network = text("LAN · Đã kết nối", "sidebar-network"), ping = text("Ping: đang đo…", "sidebar-network");

    ManagementSidebar(String role, String displayName, Consumer<String> navigate) {
        super(8); getStyleClass().add("sidebar"); setMinWidth(248); setPrefWidth(248); setMaxWidth(248);
        sceneProperty().addListener((o,before,scene)-> {
            if (scene != null) {
                prefWidthProperty().bind(javafx.beans.binding.Bindings.when(scene.widthProperty().lessThan(1200)).then(232).otherwise(248));
                minWidthProperty().bind(prefWidthProperty()); maxWidthProperty().bind(prefWidthProperty());
            } else { prefWidthProperty().unbind(); minWidthProperty().unbind(); maxWidthProperty().unbind(); }
        });
        var connection = column(3, network, ping); connection.getStyleClass().add("sidebar-connection"); connection.setId("sidebar-connection");
        var menu = column(2);
        add(menu, "Tổng quan", "Tổng quan", "M3 3H8V8H3Z M12 3H17V8H12Z M3 12H8V17H3Z M12 12H17V17H12Z", navigate);
        add(menu, "Ngân hàng", "Ngân hàng câu hỏi", "M6 3H17V15H6Z M3 6V18H14 M9 7H14 M9 11H13", navigate);
        add(menu, "Phòng thi", "Phòng thi", "M3 17H17 M5 17V3H15V17 M8 6H12 M8 9H12 M8 12H12", navigate);
        add(menu, "Giám sát thi đấu", "Giám sát thi đấu", "M3 17V12 M8 17V9 M13 17V6 M18 17V2 M3 8L8 5L12 7L18 2", navigate);
        add(menu, "Đo tầm kết nối LAN", "Đo tầm kết nối LAN", "M2 7H18V14H2Z M5 7V10 M8 7V12 M11 7V10 M14 7V12", navigate);
        add(menu, "Lịch sử", "Lịch sử & Bảng xếp hạng", "M3 17V10H7V17Z M8 17V3H12V17Z M13 17V7H17V17Z", navigate);
        if (role.equals("ADMIN")) add(menu, "Người dùng", "Người dùng", "M13 6A3 3 0 1 1 7 6A3 3 0 1 1 13 6 M4 17V15Q4 11 10 11Q16 11 16 15V17", navigate);
        add(menu, "Cài đặt", "Cài đặt", "M3 5H17 M3 10H17 M3 15H17 M7 3V7 M13 8V12 M8 13V17", navigate);
        var navigation = scroll(menu); navigation.getStyleClass().add("sidebar-scroll"); navigation.setFitToHeight(false); VBox.setVgrow(navigation, Priority.ALWAYS);
        var name = text(displayName, "sidebar-name"); name.setWrapText(false); name.setMaxWidth(Double.MAX_VALUE); name.setTooltip(new Tooltip(displayName));
        var profile = row(10, mark(30), column(3, name, text(role.equals("ADMIN") ? "Quản trị viên" : "Giảng viên", "caption")));
        HBox.setHgrow(profile.getChildren().get(1), Priority.ALWAYS);
        var account = column(4, profile); account.getStyleClass().add("sidebar-account");
        add(account, "Mật khẩu", "Đổi mật khẩu", "M6 9V6A4 4 0 0 1 14 6V9 M4 9H16V18H4Z M10 12V15", navigate);
        add(account, "Đăng xuất", "Đăng xuất", "M8 3H3V17H8 M8 10H18 M14 6L18 10L14 14", navigate);
        VBox.setMargin(profile, new Insets(0, 4, 8, 4));
        getChildren().addAll(brand(), connection, navigation, account);
    }
    private void add(VBox target, String route, String label, String path, Consumer<String> navigate) {
        var button = button(label, "nav", () -> navigate.accept(route)); button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(true); button.setMinWidth(0);
        button.setGraphic(icon(path)); button.setGraphicTextGap(10); button.setTooltip(new Tooltip(label));
        buttons.put(route, button); target.getChildren().add(button);
    }
    private static Node icon(String content) {
        var path = new SVGPath(); path.setContent(content); path.getStyleClass().add("nav-icon"); path.setMouseTransparent(true);
        var frame = new StackPane(path); frame.setMinSize(20,20); frame.setPrefSize(20,20); frame.setMaxSize(20,20); frame.setMouseTransparent(true); return frame;
    }
    void select(String route) {
        String selected = route.equals("GAME") ? "Phòng thi" : route;
        buttons.forEach((name, button) -> {
            button.getStyleClass().remove("selected");
            if (name.equals(selected)) button.getStyleClass().add("selected");
            button.setAccessibleHelp(name.equals(selected) ? "Trang đang mở" : null);
        });
    }
    void connection(boolean connected, Long millis) {
        network.setText(connected ? "LAN · Đã kết nối" : "LAN · Mất kết nối");
        ping.setText(!connected ? "Ping: —" : millis == null ? "Ping: chưa đo được" : "Ping: " + millis + " ms");
        getStyleClass().remove("offline"); if (!connected) getStyleClass().add("offline");
    }
}
