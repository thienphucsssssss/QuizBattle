package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.List;
import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/** Uses the existing monitoring UI and server-owned, persistent AP catalog. */
final class AccessPointViews {
    private AccessPointViews() { }

    static String apLabel(JsonNode player, long extraAgeMs) {
        return fresh(player, extraAgeMs) ? player.path("access_point").path("ap_label").asText("Chưa có dữ liệu AP")
            : "Chưa có dữ liệu AP mới";
    }

    static String directionLabel(JsonNode player, long extraAgeMs) {
        var ap = player.path("access_point");
        long directionAge = ap.path("direction_age_ms").asLong(-1);
        if (directionAge >= 0 && directionAge + extraAgeMs > 30000) return "Chưa xác định hướng di chuyển";
        return fresh(player, extraAgeMs) ? ap.path("direction_label").asText("Chưa xác định hướng di chuyển")
            : "Chưa xác định hướng di chuyển";
    }

    private static boolean fresh(JsonNode player, long extraAgeMs) {
        long age = player.path("measurement_age_ms").asLong(-1);
        return player.path("connected").asBoolean() && age >= 0 && age + extraAgeMs <= 15000;
    }

    static void openConfiguration(ArcadeApplication app) {
        app.call("AP_CONFIG_GET", object(), config -> showConfiguration(app, config));
    }

    static void showConfiguration(ArcadeApplication app, JsonNode config) {
        var dialog = new Dialog<ButtonType>();
        dialog.initOwner(app.ownerStage());
        app.trackProximityDialog(dialog);
        styleDialog(dialog);
        dialog.setTitle("Bản đồ AP trên hành lang");
        dialog.setHeaderText("Gán BSSID cho AP và thứ tự trên hành lang");
        dialog.getDialogPane().setId("ap-config-dialog");
        var explanation = text("Mỗi dòng là một BSSID. Các băng tần của cùng AP dùng chung tên AP, hành lang và thứ tự. "
            + "Ví dụ: HUTECH E1 · AP1 · Tầng 3 / hành lang A · 1. Nhập theo vị trí đã khảo sát tại trường.", "muted");
        var grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);
        String[] headings = {"Tên Wi-Fi (SSID)", "BSSID", "Tên AP", "Tầng / hành lang", "Thứ tự"};
        double[] widths = {20, 24, 12, 22, 9, 13};
        for (double width : widths) { var c = new ColumnConstraints(); c.setPercentWidth(width); grid.getColumnConstraints().add(c); }
        List<TextField[]> rows = new ArrayList<>();
        Runnable render = () -> {
            grid.getChildren().clear();
            for (int i = 0; i < headings.length; i++) grid.add(text(headings[i], "caption"), i, 0);
            for (int r = 0; r < rows.size(); r++) {
                var fields = rows.get(r);
                for (int i = 0; i < fields.length; i++) grid.add(fields[i], i, r + 1);
            }
        };
        Runnable[] redraw = {null};
        redraw[0] = () -> {
            render.run();
            for (int r = 0; r < rows.size(); r++) {
                var fields = rows.get(r);
                var remove = button("Xóa", "link-button", () -> { rows.remove(fields); redraw[0].run(); });
                remove.setMinWidth(Region.USE_PREF_SIZE);
                remove.setAccessibleText("Xóa dòng BSSID " + fields[1].getText());
                grid.add(remove, 5, r + 1);
            }
        };
        java.util.function.Consumer<JsonNode> add = value -> {
            if (rows.size() >= 64) return;
            String[] keys = {"ssid", "bssid", "name", "corridor", "position"};
            var fields = new TextField[5];
            for (int i = 0; i < fields.length; i++) {
                fields[i] = new TextField(value.path(keys[i]).asText(""));
                fields[i].setMinWidth(0); fields[i].setMaxWidth(Double.MAX_VALUE);
                fields[i].setAccessibleText(headings[i]);
            }
            rows.add(fields); redraw[0].run();
        };
        config.path("access_points").forEach(add);
        var error = text("", "feedback", "error"); error.setId("ap-config-error");
        error.setVisible(false); error.setManaged(false);
        var addButton = button("Thêm BSSID", "quiet", () -> {
            if (rows.size() >= 64) { error.setText("Tối đa 64 BSSID."); error.setVisible(true); error.setManaged(true); return; }
            add.accept(object().put("ssid", "HUTECH E1"));
        });
        addButton.setId("ap-add-row");
        var observed = new ComboBox<ProximityViews.AnchorOption>(); observed.setMaxWidth(Double.MAX_VALUE);
        for (var ap : app.latestProximitySnapshot().path("candidate_anchors"))
            observed.getItems().add(new ProximityViews.AnchorOption(ap.path("ssid").asText() + " · " + ap.path("bssid").asText(),
                ap.path("ssid").asText(), ap.path("bssid").asText(), ap.path("band").asText()));
        observed.setPromptText("BSSID client đã báo về"); observed.setAccessibleText("BSSID đã nhận từ client");
        var useObserved = button("Thêm AP đã thấy", "quiet", () -> {
            var selected = observed.getValue();
            if (selected != null && rows.stream().noneMatch(r -> r[1].getText().strip().equalsIgnoreCase(selected.bssid())))
                add.accept(object().put("ssid", selected.ssid()).put("bssid", selected.bssid()));
        });
        useObserved.disableProperty().bind(observed.valueProperty().isNull());
        HBox.setHgrow(observed, Priority.ALWAYS);
        var scroller = scroll(grid); scroller.setPrefViewportHeight(260);
        var content = column(12, explanation, row(8, addButton, observed, useObserved), scroller,
            text("Thứ tự tăng: 1 → 2 → 3; thứ tự giảm: 3 → 2 → 1. Khác hành lang hoặc chưa gán AP: chưa xác định hướng. "
                + "Lưu trên server; lưu bản đồ sẽ bắt đầu lại lịch sử theo dõi hiện tại.", "muted"), error);
        dialog.getDialogPane().setContent(content); dialog.getDialogPane().setPrefWidth(940);
        var saveType = new ButtonType("Lưu bản đồ AP", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE));
        var save = (Button) dialog.getDialogPane().lookupButton(saveType);
        save.setId("ap-save-config"); save.getStyleClass().add("primary");
        save.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            var payload = object().put("revision", config.path("revision").asInt());
            var entries = payload.putArray("access_points");
            try {
                for (var fields : rows) entries.add(object().put("ssid", fields[0].getText().strip())
                    .put("bssid", fields[1].getText().strip()).put("name", fields[2].getText().strip())
                    .put("corridor", fields[3].getText().strip()).put("position", Integer.parseInt(fields[4].getText().strip())));
            } catch (NumberFormatException invalid) {
                error.setText("Thứ tự AP phải là số nguyên từ 1 đến 10000."); error.setVisible(true); error.setManaged(true); return;
            }
            save.setDisable(true); content.setDisable(true);
            app.call("AP_CONFIGURE", payload, result -> {
                dialog.setResult(saveType); dialog.close(); app.refreshProximityMonitor();
                app.notice("Đã lưu bản đồ AP trên server.", false);
            }, failure -> {
                if (!dialog.isShowing()) return;
                save.setDisable(false); content.setDisable(false);
                error.setText(ArcadeApplication.message(failure)); error.setVisible(true); error.setManaged(true);
            });
        });
        redraw[0].run(); dialog.show();
    }
}
