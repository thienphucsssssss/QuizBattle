package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import vn.edu.hutech.nqb.client.ProximityCalibrationStore;
import vn.edu.hutech.nqb.client.ProximityClientService;
import vn.edu.hutech.nqb.client.WifiSignalProvider;
import vn.edu.hutech.nqb.client.WindowsWifiPermission;
import vn.edu.hutech.nqb.shared.ProximityRules;

import java.awt.Desktop;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static vn.edu.hutech.nqb.admin.FxKit.*;
import static vn.edu.hutech.nqb.client.QuizGateway.object;

/**
 * UI components for Wi-Fi proximity monitoring:
 * - Permission & device diagnostic dialog
 * - Two-step (1 m and 5 m, 30s each) student calibration wizard + local profile reuse confirmation
 * - Teacher/Admin standalone live monitoring window (persists across question transitions)
 * - Proximity history & alert timeline dialog
 */
final class ProximityViews {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    public record AnchorOption(String label, String ssid, String bssid, String band) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final ArcadeApplication app;
    private final Supplier<CompletableFuture<WindowsWifiPermission.Result>> requestPermission;
    private final Consumer<Consumer<WifiSignalProvider.Reading>> verifyPermission;
    private final Supplier<Boolean> openPermissionSettings;

    ProximityViews(ArcadeApplication app) {
        this(app,()->WindowsWifiPermission.requestAccessAsync(action->{if(Platform.isFxApplicationThread())action.run();else Platform.runLater(action);}),complete->app.proximityClient().readNowAsync().whenComplete((reading,error)->Platform.runLater(()->{
            if(error==null)complete.accept(reading);
            else complete.accept(WifiSignalProvider.Reading.failure(WifiSignalProvider.Status.API_ERROR,"Không đọc được tín hiệu Wi-Fi.",System.currentTimeMillis()));
        })),ProximityViews::openWindowsLocationSettings);
    }

    ProximityViews(ArcadeApplication app,Supplier<CompletableFuture<WindowsWifiPermission.Result>> requestPermission,
                   Consumer<Consumer<WifiSignalProvider.Reading>> verifyPermission,Supplier<Boolean> openPermissionSettings) {
        this.app = app;
        this.requestPermission=requestPermission;this.verifyPermission=verifyPermission;this.openPermissionSettings=openPermissionSettings;
    }

    static String formatTimestamp(String isoUtc) {
        if (isoUtc == null || isoUtc.isBlank()) {
            return "Chưa cập nhật";
        }
        try {
            return TIME_FMT.format(Instant.parse(isoUtc));
        } catch (Exception ignored) {
            return isoUtc;
        }
    }

    static String formatDurationMs(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        if (sec < 60L) {
            return sec + " giây";
        }
        return (sec / 60L) + " phút " + (sec % 60L) + " giây";
    }

    /**
     * Shows the permission or Wi-Fi state diagnostic dialog when a student cannot measure.
     * Includes the three required buttons for permission denial: Mở cài đặt Windows, Thử lại, Để sau.
     */
    void showDiagnosticDialog(
        WifiSignalProvider.Reading reading,
        String anchorSsid,
        String anchorBssid,
        String anchorBand,
        Runnable onRetry
    ) {
        if(reading.status()==WifiSignalProvider.Status.PERMISSION_DENIED){showWifiPermissionDialog(onRetry);return;}
        var dialog = new Dialog<ButtonType>();
        dialog.initOwner(app.ownerStage());
        app.trackProximityDialog(dialog);
        styleDialog(dialog);

        dialog.setTitle("Kiểm tra kết nối Wi-Fi");
        dialog.setHeaderText("Chưa đủ điều kiện đo tín hiệu Wi-Fi");

        String message = reading.userMessage(anchorBssid, anchorBand, anchorSsid);
        Label bodyLabel = text(message, "subtitle");
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(560);
        bodyLabel.setId("proximity-diagnostic-message");

        VBox content = column(14, bodyLabel);
        content.setPrefWidth(560);
        if (!anchorBssid.isBlank()) {
            String anchorDesc = (anchorSsid == null || anchorSsid.isBlank() ? anchorBssid : anchorSsid + " (" + anchorBssid + ")")
                + " · " + anchorBand;
            content.getChildren().add(text("Điểm phát làm mốc của phòng: " + anchorDesc, "muted"));
        }
        if (reading.status() == WifiSignalProvider.Status.CONNECTED) {
            String currentDesc = (reading.ssid().isBlank() ? reading.bssid() : reading.ssid() + " (" + reading.bssid() + ")")
                + " · " + reading.band();
            content.getChildren().add(text("Đang kết nối: " + currentDesc, "muted"));
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(620);

        ButtonType retryBtn = new ButtonType("Thử lại", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterBtn = new ButtonType("Để sau", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(retryBtn, laterBtn);

        Button retryNode = (Button) dialog.getDialogPane().lookupButton(retryBtn);
        retryNode.setId("proximity-retry");
        retryNode.getStyleClass().add("primary");
        ButtonBar.setButtonUniformSize(retryNode, false);
        retryNode.setMinWidth(Region.USE_PREF_SIZE);

        Button laterNode = (Button) dialog.getDialogPane().lookupButton(laterBtn);
        laterNode.setId("proximity-later");
        ButtonBar.setButtonUniformSize(laterNode, false);
        laterNode.setMinWidth(Region.USE_PREF_SIZE);

        dialog.setResultConverter(b -> b);
        dialog.setOnHidden(e -> {
            if (dialog.getResult() == retryBtn && onRetry != null) {
                onRetry.run();
            }
        });
        dialog.show();
    }

    private void showWifiPermissionDialog(Runnable onGranted) {
        var dialog=new Dialog<ButtonType>();dialog.initOwner(app.ownerStage());dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        app.trackProximityDialog(dialog);styleDialog(dialog);
        dialog.setTitle("Cho phép đo Wi-Fi");dialog.setHeaderText("Cho phép QuizBattle đọc tín hiệu Wi-Fi");
        var explanation=text("QuizBattle cần quyền vị trí của Windows để đọc tín hiệu Wi-Fi và ước lượng khoảng cách tới điểm phát.","subtitle");
        explanation.setWrapText(true);explanation.setMaxWidth(540);explanation.setMinHeight(Region.USE_PREF_SIZE);explanation.setId("proximity-diagnostic-message");
        var status=text("Nhấn Cho phép, rồi đồng ý nếu Windows hiện yêu cầu.","muted");status.setWrapText(true);status.setMaxWidth(540);status.setMinHeight(Region.USE_PREF_SIZE);status.setId("proximity-permission-status");
        var content=column(14,explanation,status);content.setPrefWidth(540);
        dialog.getDialogPane().setContent(content);dialog.getDialogPane().setPrefWidth(600);
        status.textProperty().addListener((observable,before,after)->Platform.runLater(()->{
            var scene=dialog.getDialogPane().getScene();
            if(dialog.isShowing()&&scene!=null){scene.getRoot().applyCss();scene.getWindow().sizeToScene();}
        }));
        var requestType=new ButtonType("Cho phép",ButtonBar.ButtonData.OK_DONE);
        var settingsType=new ButtonType("Mở cài đặt và cấp quyền",ButtonBar.ButtonData.LEFT);
        var checkType=new ButtonType("Kiểm tra lại",ButtonBar.ButtonData.OTHER);
        var laterType=new ButtonType("Để sau",ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(settingsType,checkType,requestType,laterType);
        var request=(Button)dialog.getDialogPane().lookupButton(requestType);request.setId("proximity-request-access");request.getStyleClass().add("primary");
        var settings=(Button)dialog.getDialogPane().lookupButton(settingsType);settings.setId("proximity-open-settings");
        var retry=(Button)dialog.getDialogPane().lookupButton(checkType);retry.setId("proximity-retry");
        var later=(Button)dialog.getDialogPane().lookupButton(laterType);later.setId("proximity-later");
        for(var button:List.of(request,settings,retry,later)){ButtonBar.setButtonUniformSize(button,false);button.setMinWidth(Region.USE_PREF_SIZE);}
        settings.setVisible(false);settings.setManaged(false);retry.setVisible(false);retry.setManaged(false);
        var pending=new AtomicReference<CompletableFuture<WindowsWifiPermission.Result>>();
        boolean[] checking={false},openedSettings={false},wasAway={false};
        Consumer<String> blocked=message->{
            status.setText(message);request.setVisible(false);request.setManaged(false);
            settings.setVisible(true);settings.setManaged(true);settings.setDisable(false);if(!settings.getStyleClass().contains("primary"))settings.getStyleClass().add("primary");
            retry.setVisible(true);retry.setManaged(true);retry.setDisable(false);
        };
        Runnable verify=()->{
            if(!dialog.isShowing()||checking[0])return;checking[0]=true;
            settings.setDisable(true);retry.setDisable(true);status.setText("Đang kiểm tra quyền đọc Wi-Fi…");
            verifyPermission.accept(reading->{
                if(!dialog.isShowing())return;checking[0]=false;
                if(reading.status()!=WifiSignalProvider.Status.PERMISSION_DENIED){dialog.close();if(onGranted!=null)onGranted.run();return;}
                blocked.accept("Windows chưa cho phép đọc Wi-Fi. Nếu đã từ chối hoặc tắt dịch vụ vị trí, hãy bật lại trong Cài đặt. QuizBattle sẽ tự kiểm tra khi bạn quay lại.");
            });
        };
        request.addEventFilter(javafx.event.ActionEvent.ACTION,event->{
            event.consume();if(request.isDisabled())return;request.setDisable(true);status.setText("Đang chờ Windows cấp quyền…");
            try {
                var operation=requestPermission.get();pending.set(operation);
                operation.whenComplete((result,error)->Platform.runLater(()->{
                    if(!dialog.isShowing())return;
                    // Verify real WLAN access; a consent response is not a Wi-Fi measurement.
                    verify.run();
                }));
            } catch(RuntimeException error){blocked.accept("Chưa mở được yêu cầu quyền. Bạn có thể cấp quyền trong Cài đặt Windows.");}
        });
        settings.addEventFilter(javafx.event.ActionEvent.ACTION,event->{
            event.consume();openedSettings[0]=true;wasAway[0]=false;settings.setDisable(true);
            status.setText("Bật Dịch vụ vị trí và cho phép ứng dụng truy cập vị trí. Sau đó quay lại QuizBattle để tiếp tục.");
            CompletableFuture.supplyAsync(openPermissionSettings).whenComplete((opened,error)->Platform.runLater(()->{
                if(!dialog.isShowing())return;settings.setDisable(false);
                if(error!=null||!Boolean.TRUE.equals(opened))status.setText("Không mở được Cài đặt Windows. Hãy mở Cài đặt → Quyền riêng tư → Vị trí, rồi quay lại QuizBattle.");
            }));
        });
        retry.addEventFilter(javafx.event.ActionEvent.ACTION,event->{event.consume();verify.run();});
        javafx.beans.value.ChangeListener<Boolean> focus=(observable,before,focused)->{
            if(!openedSettings[0])return;if(!focused)wasAway[0]=true;
            else if(wasAway[0]){wasAway[0]=false;verify.run();}
        };
        var permissionWindow=new AtomicReference<javafx.stage.Window>();
        dialog.setOnShown(event->{var window=dialog.getDialogPane().getScene().getWindow();permissionWindow.set(window);window.focusedProperty().addListener(focus);});
        dialog.setOnHidden(event->{
            var operation=pending.get();if(operation!=null)operation.cancel(false);
            var window=permissionWindow.getAndSet(null);if(window!=null)window.focusedProperty().removeListener(focus);
        });
        dialog.show();
    }

    static boolean openWindowsLocationSettings() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI("ms-settings:privacy-location"));
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            new ProcessBuilder("cmd.exe", "/c", "start", "", "ms-settings:privacy-location").start();
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Opens the local profile reuse confirmation dialog if a matching saved profile exists,
     * or launches the 2-step calibration wizard.
     */
    void openCalibrationFlow(
        ProximityClientService service,
        JsonNode proximityNode,
        Runnable onCalibrationCompleted
    ) {
        String anchorSsid = proximityNode.path("anchor_ssid").asText("");
        String anchorBssid = proximityNode.path("anchor_bssid").asText("");
        String anchorBand = proximityNode.path("anchor_band").asText("");
        int configEpoch = proximityNode.path("config_epoch").asInt(1);
        String recalibratedAt = proximityNode.path("recalibrated_at_utc").asText("");
        boolean requireFresh = proximityNode.path("require_fresh_calibration").asBoolean(false);

        if (anchorBssid.isBlank() || anchorBand.isBlank()) {
            app.notice("Chủ phòng chưa chọn điểm phát Wi-Fi làm mốc.", true);
            return;
        }

        app.readWifi(current -> {
        if (!current.isConnected() || !current.matchesAnchor(anchorBssid, anchorBand)) {
            showDiagnosticDialog(current, anchorSsid, anchorBssid, anchorBand,
                () -> openCalibrationFlow(service, proximityNode, onCalibrationCompleted));
            return;
        }

        if (!requireFresh) {
            Optional<ProximityCalibrationStore.Profile> saved = service.calibrationStore()
                .find(current.adapterId(), anchorBssid, anchorBand, recalibratedAt);
            if (saved.isPresent()) {
                showReuseProfileDialog(service, saved.get(), configEpoch, anchorSsid, onCalibrationCompleted,
                    () -> showCalibrationWizard(service, current, anchorSsid, anchorBssid, anchorBand, configEpoch, onCalibrationCompleted));
                return;
            }
        }

        showCalibrationWizard(service, current, anchorSsid, anchorBssid, anchorBand, configEpoch, onCalibrationCompleted);
        });
    }

    private void showReuseProfileDialog(
        ProximityClientService service,
        ProximityCalibrationStore.Profile profile,
        int configEpoch,
        String anchorSsid,
        Runnable onCalibrationCompleted,
        Runnable onMeasureNew
    ) {
        var dialog = new Dialog<ButtonType>();
        dialog.initOwner(app.ownerStage());
        app.trackProximityDialog(dialog);
        styleDialog(dialog);
        dialog.setTitle("Hồ sơ hiệu chuẩn đã lưu");
        dialog.setHeaderText("Xác nhận vị trí điểm phát trước khi dùng lại");

        CheckBox confirmSamePlace = new CheckBox("Tôi xác nhận điểm phát Wi-Fi vẫn đặt ở cùng vị trí như lần hiệu chuẩn trước");
        confirmSamePlace.setId("proximity-confirm-reuse");

        Label errorLabel = text("", "feedback", "error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        String apLabel = (anchorSsid.isBlank() ? profile.ssid() : anchorSsid) + " (" + profile.bssid() + ") · " + profile.band();
        VBox content = column(
            12,
            text("Tìm thấy hồ sơ hiệu chuẩn trên máy này:", "subtitle"),
            text("Điểm phát: " + apLabel, "muted"),
            text("Card Wi-Fi: " + (profile.adapterName().isBlank() ? profile.adapterId() : profile.adapterName()), "muted"),
            text(String.format(Locale.ROOT, "Mức 1 m: %.0f dBm · Mức 5 m: %.0f dBm · Hệ số suy hao n = %.2f",
                profile.rssi1m(), profile.rssi5m(), profile.pathLossN()), "muted"),
            confirmSamePlace,
            errorLabel
        );
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);

        ButtonType useSavedBtn = new ButtonType("Dùng hồ sơ này", ButtonBar.ButtonData.OK_DONE);
        ButtonType recalibrateBtn = new ButtonType("Đo hiệu chuẩn mới", ButtonBar.ButtonData.OTHER);
        ButtonType cancelBtn = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(useSavedBtn, recalibrateBtn, cancelBtn);

        Button useBtnNode = (Button) dialog.getDialogPane().lookupButton(useSavedBtn);
        useBtnNode.getStyleClass().add("primary");
        useBtnNode.setId("proximity-use-saved");

        useBtnNode.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            if (!confirmSamePlace.isSelected()) {
                errorLabel.setText("Hãy đánh dấu xác nhận điểm phát vẫn ở cùng vị trí trước khi tiếp tục.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                return;
            }
            useBtnNode.setDisable(true);
            ObjectNode payload = buildCalibrationPayload(
                configEpoch,
                profile.adapterId(),
                profile.adapterName(),
                profile.bssid(),
                profile.band(),
                profile.samples1m(),
                profile.samples5m(),
                true,
                true
            );
            app.call("PROXIMITY_CALIBRATION", payload, res -> {
                if (!dialog.isShowing()) return;
                dialog.setResult(useSavedBtn);
                dialog.close();
                if (onCalibrationCompleted != null) {
                    onCalibrationCompleted.run();
                }
            }, err -> {
                useBtnNode.setDisable(false);
                errorLabel.setText(ArcadeApplication.message(err));
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            });
        });

        Button recalNode = (Button) dialog.getDialogPane().lookupButton(recalibrateBtn);
        recalNode.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            dialog.setResult(recalibrateBtn);
            dialog.close();
            if (onMeasureNew != null) {
                onMeasureNew.run();
            }
        });

        dialog.show();
    }

    /**
     * Two-step calibration wizard:
     * Step 1: Place laptop 1 m from access point (30s)
     * Step 2: Place laptop 5 m from access point (30s)
     */
    void showCalibrationWizard(
        ProximityClientService service,
        WifiSignalProvider.Reading initialReading,
        String anchorSsid,
        String anchorBssid,
        String anchorBand,
        int configEpoch,
        Runnable onCalibrationCompleted
    ) {
        var dialog = new Dialog<ButtonType>();
        dialog.initOwner(app.ownerStage());
        app.trackProximityDialog(dialog);
        styleDialog(dialog);
        dialog.setTitle("Hiệu chuẩn khoảng cách Wi-Fi");
        dialog.setHeaderText("Hiệu chuẩn hai bước (1 m và 5 m)");

        String apDesc = (anchorSsid.isBlank() ? initialReading.ssid() : anchorSsid) + " (" + anchorBssid + ") · " + anchorBand;
        Label apInfo = text("Điểm phát làm mốc: " + apDesc + " · Card: " + initialReading.adapterName(), "muted");

        ProgressBar bar1m = new ProgressBar(0);
        bar1m.setMaxWidth(Double.MAX_VALUE);
        Label status1m = text("Chưa đo. Đặt laptop cách điểm phát khoảng 1 m rồi bấm Đo bước 1 m.", "muted");
        status1m.setId("calibration-status-1m");
        Button btn1m = button("Đo bước 1 m (30 giây)", "primary", null);
        btn1m.setId("calibration-start-1m");

        ProgressBar bar5m = new ProgressBar(0);
        bar5m.setMaxWidth(Double.MAX_VALUE);
        Label status5m = text("Chưa đo. Hoàn tất bước 1 m trước khi đo ở khoảng cách 5 m.", "muted");
        status5m.setId("calibration-status-5m");
        Button btn5m = button("Đo bước 5 m (30 giây)", "quiet", null);
        btn5m.setId("calibration-start-5m");
        btn5m.setDisable(true);

        Label overallStatus = text("Hoàn tất cả hai bước đạt yêu cầu để lưu hồ sơ hiệu chuẩn.", "feedback");
        overallStatus.setId("calibration-overall-status");

        @SuppressWarnings("unchecked")
        List<Integer>[] samplesHolder = new List[]{List.of(), List.of()};
        boolean[] stepValid = {false, false};

        ButtonType finishType = new ButtonType("Hoàn tất hiệu chuẩn", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(finishType, closeType);

        Button finishBtn = (Button) dialog.getDialogPane().lookupButton(finishType);
        finishBtn.getStyleClass().add("ink-button");
        finishBtn.setId("calibration-submit");
        finishBtn.setDisable(true);
        ButtonBar.setButtonUniformSize(finishBtn, false);
        finishBtn.setMinWidth(Region.USE_PREF_SIZE);

        Button closeBtn = (Button) dialog.getDialogPane().lookupButton(closeType);
        ButtonBar.setButtonUniformSize(closeBtn, false);
        closeBtn.setMinWidth(Region.USE_PREF_SIZE);

        Runnable updateOverall = () -> {
            if (stepValid[0] && stepValid[1]) {
                var eval = ProximityRules.evaluateCalibration(samplesHolder[0], samplesHolder[1]);
                overallStatus.setText(eval.message());
                overallStatus.getStyleClass().remove("error");
                if (!eval.valid()) {
                    overallStatus.getStyleClass().add("error");
                }
                finishBtn.setDisable(!eval.valid());
            } else {
                finishBtn.setDisable(true);
            }
        };

        btn1m.setOnAction(e -> {
            btn1m.setDisable(true);
            btn5m.setDisable(true);
            finishBtn.setDisable(true);
            bar1m.setProgress(0);
            status1m.setText("Đang thu mẫu ở khoảng cách 1 m… Giữ máy cố định.");
            service.startCalibrationStep(
                1,
                initialReading.adapterId(),
                anchorBssid,
                anchorBand,
                prog -> Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    bar1m.setProgress((double) prog.elapsedSeconds() / Math.max(1, prog.totalSeconds()));
                    status1m.setText(String.format(
                        Locale.ROOT,
                        "Bước 1 m: %d/%d giây · %d mẫu hợp lệ · Trung vị %.0f dBm · Độ lệch %.1f dB",
                        prog.elapsedSeconds(),
                        prog.totalSeconds(),
                        prog.validSamples(),
                        prog.currentMedianDbm(),
                        prog.currentMadDb()
                    ));
                }),
                result -> Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    btn1m.setDisable(false);
                    btn1m.setText("Đo lại bước 1 m");
                    samplesHolder[0] = result.validSamples();
                    stepValid[0] = result.evaluation().valid();
                    status1m.setText(result.evaluation().message());
                    btn5m.setDisable(!stepValid[0]);
                    if (stepValid[0] && !stepValid[1]) {
                        btn5m.getStyleClass().removeAll("quiet");
                        if (!btn5m.getStyleClass().contains("primary")) btn5m.getStyleClass().add("primary");
                    }
                    updateOverall.run();
                })
            );
        });

        btn5m.setOnAction(e -> {
            btn1m.setDisable(true);
            btn5m.setDisable(true);
            finishBtn.setDisable(true);
            bar5m.setProgress(0);
            status5m.setText("Đang thu mẫu ở khoảng cách 5 m… Giữ máy cố định.");
            service.startCalibrationStep(
                5,
                initialReading.adapterId(),
                anchorBssid,
                anchorBand,
                prog -> Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    bar5m.setProgress((double) prog.elapsedSeconds() / Math.max(1, prog.totalSeconds()));
                    status5m.setText(String.format(
                        Locale.ROOT,
                        "Bước 5 m: %d/%d giây · %d mẫu hợp lệ · Trung vị %.0f dBm · Độ lệch %.1f dB",
                        prog.elapsedSeconds(),
                        prog.totalSeconds(),
                        prog.validSamples(),
                        prog.currentMedianDbm(),
                        prog.currentMadDb()
                    ));
                }),
                result -> Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    btn1m.setDisable(false);
                    btn5m.setDisable(false);
                    btn5m.setText("Đo lại bước 5 m");
                    samplesHolder[1] = result.validSamples();
                    stepValid[1] = result.evaluation().valid();
                    status5m.setText(result.evaluation().message());
                    updateOverall.run();
                })
            );
        });

        finishBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume();
            finishBtn.setDisable(true);
            ObjectNode payload = buildCalibrationPayload(
                configEpoch,
                initialReading.adapterId(),
                initialReading.adapterName(),
                anchorBssid,
                anchorBand,
                samplesHolder[0],
                samplesHolder[1],
                false,
                true
            );
            app.call("PROXIMITY_CALIBRATION", payload, res -> {
                if (!dialog.isShowing()) return;
                try {
                    service.calibrationStore().save(
                        initialReading.adapterId(),
                        initialReading.adapterName(),
                        anchorSsid.isBlank() ? initialReading.ssid() : anchorSsid,
                        anchorBssid,
                        anchorBand,
                        samplesHolder[0],
                        samplesHolder[1]
                    );
                } catch (Exception saveError) {
                    app.notice(ArcadeApplication.message(saveError),true);
                }
                dialog.setResult(finishType);
                dialog.close();
                if (onCalibrationCompleted != null) {
                    onCalibrationCompleted.run();
                }
            }, err -> {
                finishBtn.setDisable(false);
                overallStatus.setText(ArcadeApplication.message(err));
                overallStatus.getStyleClass().remove("error");
                overallStatus.getStyleClass().add("error");
            });
        });

        dialog.setOnHidden(e -> service.cancelCalibrationStep());

        VBox step1Box = column(8, text("BƯỚC 1 · ĐẶT MÁY CÁCH ĐIỂM PHÁT 1 M", "caption"), bar1m, status1m, btn1m);
        step1Box.getStyleClass().add("outlined");
        VBox step5Box = column(8, text("BƯỚC 2 · ĐẶT MÁY CÁCH ĐIỂM PHÁT 5 M", "caption"), bar5m, status5m, btn5m);
        step5Box.getStyleClass().add("outlined");

        VBox body = column(12, apInfo, step1Box, step5Box, overallStatus);
        dialog.getDialogPane().setContent(scroll(body));
        dialog.getDialogPane().setPrefSize(660, 590);
        dialog.show();
    }

    static ObjectNode buildCalibrationPayload(
        int configEpoch,
        String adapterId,
        String adapterName,
        String bssid,
        String band,
        List<Integer> samples1m,
        List<Integer> samples5m,
        boolean reusedLocalProfile,
        boolean positionConfirmed
    ) {
        ObjectNode payload = object()
            .put("config_epoch", configEpoch)
            .put("adapter_id", adapterId)
            .put("adapter_name", adapterName == null ? "" : adapterName)
            .put("bssid", bssid)
            .put("band", band)
            .put("reused_local_profile", reusedLocalProfile)
            .put("position_confirmed", positionConfirmed);
        ArrayNode a1 = payload.putArray("samples_1m");
        samples1m.forEach(a1::add);
        ArrayNode a5 = payload.putArray("samples_5m");
        samples5m.forEach(a5::add);
        return payload;
    }

    /**
     * Collects available anchor options for the host's room configuration dialog:
     * 1. Current room anchor (if already configured)
     * 2. Host's own connected Wi-Fi (if host laptop is on Wi-Fi)
     * 3. Candidate anchors reported by student Clients in the room (if host uses Ethernet)
     */
    List<AnchorOption> collectAnchorOptions(WifiSignalProvider.Reading hostReading, JsonNode roomProx, JsonNode monitorSnapshot) {
        Map<String, AnchorOption> map = new LinkedHashMap<>();

        if (hostReading != null) {
            if (hostReading.isConnected()) {
                String key = hostReading.bssid() + "|" + hostReading.band();
                String ssidText = hostReading.ssid().isBlank() ? "Wi-Fi hiện tại" : hostReading.ssid();
                map.put(key, new AnchorOption(
                    "Máy chủ phòng: " + ssidText + " (" + hostReading.bssid() + " · " + hostReading.band() + ")",
                    hostReading.ssid(),
                    hostReading.bssid(),
                    hostReading.band()
                ));
            }
        }

        String roomBssid = roomProx.path("anchor_bssid").asText("");
        String roomBand = roomProx.path("anchor_band").asText("");
        String roomSsid = roomProx.path("anchor_ssid").asText("");
        if (!roomBssid.isBlank() && !roomBand.isBlank()) {
            String key = roomBssid + "|" + roomBand;
            map.putIfAbsent(key, new AnchorOption(
                "Đang dùng: " + (roomSsid.isBlank() ? roomBssid : roomSsid + " (" + roomBssid + ")") + " · " + roomBand,
                roomSsid,
                roomBssid,
                roomBand
            ));
        }

        if (monitorSnapshot != null && monitorSnapshot.path("candidate_anchors").isArray()) {
            for (JsonNode c : monitorSnapshot.path("candidate_anchors")) {
                String bssid = c.path("bssid").asText("");
                String band = c.path("band").asText("");
                String ssid = c.path("ssid").asText("");
                String by = c.path("reported_by").asText("Client");
                if (!bssid.isBlank() && !band.isBlank()) {
                    String key = bssid + "|" + band;
                    map.putIfAbsent(key, new AnchorOption(
                        "Từ máy " + by + ": " + (ssid.isBlank() ? bssid : ssid + " (" + bssid + ")") + " · " + band,
                        ssid,
                        bssid,
                        band
                    ));
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Standalone live monitoring window for Teacher / Admin.
     * Remains open across question transitions.
     */
    static final class LiveMonitorWindow {
        private final ArcadeApplication app;
        private final Stage stage;
        private final Label anchorSummary = text("", "subtitle");
        private final Label thresholdSummary = text("", "muted");
        private final Label warningBanner = text("", "feedback", "error");
        private final TableView<JsonNode> table = new TableView<>();
        private final TableView<JsonNode> apTable = new TableView<>();
        private final Button apConfigBtn;
        private final VBox detailBox = column(8, text("CHI TIẾT TÍN HIỆU & NGUYÊN NHÂN LỖI", "caption"), text("Chọn một sinh viên trong bảng để xem chi tiết.", "muted"));
        private final Button recalibrateAllBtn;
        private final Button refreshBtn;
        private String selectedPlayerId = "";
        private final LanTopologyView topology;
        private final StackPane views=new StackPane();

        LiveMonitorWindow(ArcadeApplication app) {
            this.app = app;
            topology=new LanTopologyView(false,this::selectPlayer);
            this.stage = new Stage();
            stage.setTitle("Quiz Battle · Giám sát khoảng cách Wi-Fi");
            stage.setMinWidth(820);
            stage.setMinHeight(520);

            warningBanner.setVisible(false);
            warningBanner.setManaged(false);

            table.setId("proximity-monitor-table");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setPlaceholder(text("Chưa có sinh viên trong phòng.", "muted"));

            TableColumn<JsonNode, Node> playerCol = new TableColumn<>("Người chơi");
            playerCol.setCellValueFactory(c -> {
                JsonNode p = c.getValue();
                Node av = QuizlingArt.avatar(p.path("avatar_id").asText("quizling-1"), 32);
                Label nick = text(p.path("nickname").asText(), "subtitle");
                nick.setStyle("-fx-font-size: 15px;");
                HBox box = row(10, av, nick);
                return new ReadOnlyObjectWrapper<>(box);
            });
            playerCol.setPrefWidth(210);
            playerCol.setSortable(false);

            TableColumn<JsonNode, String> distCol = new TableColumn<>("Khoảng cách");
            var distanceInfo=text("ⓘ","caption");distanceInfo.setTooltip(new Tooltip("Khoảng cách được tính từ tín hiệu Wi-Fi thực và hồ sơ hiệu chuẩn; không phải phép đo mét trực tiếp."));distCol.setGraphic(distanceInfo);
            distCol.setCellValueFactory(c -> {
                JsonNode p = c.getValue();
                String label = p.path("distance_label").asText("Không có dữ liệu");
                return new ReadOnlyStringWrapper(label);
            });
            distCol.setPrefWidth(190);
            distCol.setSortable(false);

            TableColumn<JsonNode, String> statusCol = new TableColumn<>("Trạng thái");
            statusCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().path("status_label").asText("Không có dữ liệu")));
            statusCol.setPrefWidth(160);
            statusCol.setSortable(false);

            TableColumn<JsonNode, String> updatedCol = new TableColumn<>("Cập nhật gần nhất");
            updatedCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatTimestamp(c.getValue().path("updated_at_utc").asText(""))));
            updatedCol.setPrefWidth(140);
            updatedCol.setSortable(false);

            var ipCol=col("IP trong LAN",p->p.path("ip_address").asText("—"),130);
            var pingCol=col("Ping",p->p.has("ping_ms")?(p.path("ping_ms").asLong()==0?"<1":p.path("ping_ms").asText())+" ms":"— ms",90);
            table.getColumns().addAll(playerCol,ipCol,pingCol,distCol,statusCol,updatedCol);

            apTable.setId("ap-monitor-table");
            apTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            apTable.setPlaceholder(text("Chưa có client gửi thông tin AP.", "muted"));
            apTable.getColumns().addAll(
                col("Người chơi", p -> p.path("nickname").asText(), 140),
                col("AP đang kết nối", p -> AccessPointViews.apLabel(p, app.measurementSnapshotAgeMs()), 170),
                col("Tầng / hành lang", p -> p.path("access_point").path("corridor").asText("—"), 160),
                col("Hướng suy đoán", p -> AccessPointViews.directionLabel(p, app.measurementSnapshotAgeMs()), 280),
                col("BSSID", p -> p.path("access_point").path("bssid").asText("—"), 155));
            apTable.getSelectionModel().selectedItemProperty().addListener((o, before, selected) -> {
                if (selected != null) { selectedPlayerId = selected.path("player_id").asText(); renderPlayerDetail(selected); }
            });
            var ageTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                apTable.refresh();
                if (!selectedPlayerId.isBlank()) for (var p : apTable.getItems())
                    if (p.path("player_id").asText().equals(selectedPlayerId)) { renderPlayerDetail(p); break; }
            }));
            ageTimer.setCycleCount(javafx.animation.Timeline.INDEFINITE); runWhenShowing(apTable, ageTimer);

            table.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
                if (selected != null) {
                    selectedPlayerId = selected.path("player_id").asText("");
                    renderPlayerDetail(selected);
                }
            });

            detailBox.getStyleClass().add("panel");
            detailBox.setVisible(false);detailBox.setManaged(false);
            detailBox.setId("proximity-monitor-detail");

            recalibrateAllBtn = button("Hiệu chuẩn lại cả phòng", "quiet", () -> {
                JsonNode snap = app.latestProximitySnapshot();
                if (snap == null || snap.path("locked").asBoolean()) return;
                ObjectNode req = object()
                    .put("enabled", snap.path("enabled").asBoolean(true))
                    .put("anchor_ssid", snap.path("anchor_ssid").asText(""))
                    .put("anchor_bssid", snap.path("anchor_bssid").asText(""))
                    .put("anchor_band", snap.path("anchor_band").asText(""))
                    .put("threshold_meters", snap.path("threshold_meters").asInt(10))
                    .put("recalibrate_all", true);
                app.call("PROXIMITY_CONFIGURE", req, value -> {
                    app.notice("Đã yêu cầu cả phòng hiệu chuẩn lại.", false);
                    app.refreshProximityMonitor();
                });
            });
            recalibrateAllBtn.setId("proximity-recalibrate-all");
            recalibrateAllBtn.setMinWidth(Region.USE_PREF_SIZE);

            refreshBtn = new FreshMeasurementButton(app);
            refreshBtn.setId("proximity-monitor-refresh");
            refreshBtn.setMinWidth(Region.USE_PREF_SIZE);

            var titleBox = column(4, text("Giám sát khoảng cách Wi-Fi", "title"), anchorSummary, thresholdSummary);
            HBox.setHgrow(titleBox, Priority.ALWAYS);
            var header = row(12, titleBox, recalibrateAllBtn, refreshBtn);
            apConfigBtn = button("Cấu hình AP", "quiet", () -> AccessPointViews.openConfiguration(app));
            apConfigBtn.setId("ap-open-config");
            var rootBox = new BorderPane();
            rootBox.setPadding(new Insets(20, 24, 20, 24));
            var diagram=new ToggleButton("Sơ đồ");diagram.setId("monitor-topology-mode");var list=new ToggleButton("Danh sách");list.setId("monitor-list-mode");var modes=new ToggleGroup();diagram.setToggleGroup(modes);list.setToggleGroup(modes);diagram.setSelected(true);
            var apMode = new ToggleButton("AP & hướng di chuyển"); apMode.setId("monitor-ap-mode"); apMode.setToggleGroup(modes);
            var graphScroll=scroll(topology);views.getChildren().addAll(graphScroll,table,apTable);table.setVisible(false);table.setManaged(false);apTable.setVisible(false);apTable.setManaged(false);
            modes.selectedToggleProperty().addListener((o,b,a)->{if(a==null){diagram.setSelected(true);return;}boolean graph=a==diagram;graphScroll.setVisible(graph);graphScroll.setManaged(graph);table.setVisible(a==list);table.setManaged(a==list);apTable.setVisible(a==apMode);apTable.setManaged(a==apMode);});
            rootBox.setTop(column(12, header,row(8,diagram,list,apMode,space(),apConfigBtn), warningBanner));
            BorderPane.setMargin(table, new Insets(14, 0, 14, 0));
            rootBox.setCenter(views);
            var detailScroll = scroll(detailBox);
            detailScroll.setPrefViewportHeight(190); detailScroll.setMaxHeight(230);
            detailScroll.visibleProperty().bind(detailBox.visibleProperty());
            detailScroll.managedProperty().bind(detailBox.managedProperty());
            rootBox.setBottom(detailScroll);

            Scene scene = new Scene(rootBox,980,Math.min(720,javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()-80));
            scene.getStylesheets().add(CSS);
            stage.setScene(scene);
        }

        void showAndRefresh() {
            if (!stage.isShowing()) {
                stage.show();
            } else {
                stage.toFront();
            }
            app.refreshProximityMonitor();
        }

        boolean isShowing() {
            return stage.isShowing();
        }
        void selectPlayer(String id){selectedPlayerId=id;for(var row:table.getItems())if(row.path("player_id").asText().equals(id)){table.getSelectionModel().select(row);renderPlayerDetail(row);return;}}

        Stage stage() {
            return stage;
        }

        void close() {
            stage.close();
        }

        void update(JsonNode snap) {
            if (snap == null || snap.isEmpty()) return;
            topology.update(app.managedRoomSnapshot(),snap);
            boolean enabled = snap.path("enabled").asBoolean(false);
            boolean locked = snap.path("locked").asBoolean(false);
            String ssid = snap.path("anchor_ssid").asText("");
            String bssid = snap.path("anchor_bssid").asText("");
            String band = snap.path("anchor_band").asText("");
            int threshold = snap.path("threshold_meters").asInt(10);

            if (!enabled) {
                anchorSummary.setText("Giám sát khoảng cách đang tắt cho phòng này.");
                thresholdSummary.setText("Chủ phòng có thể bật trong Cấu hình phòng trước khi bắt đầu.");
            } else if (bssid.isBlank()) {
                anchorSummary.setText("Chưa chọn điểm phát làm mốc.");
                thresholdSummary.setText("Ngưỡng cảnh báo: " + threshold + " m");
            } else {
                String ap = (ssid.isBlank() ? "Wi-Fi đã chọn" : ssid) + " · " + band;
                anchorSummary.setText("Điểm phát làm mốc: " + ap);
                thresholdSummary.setText("Ngưỡng cảnh báo: " + threshold + " m" + (locked ? " · Đang khóa cấu hình trong lúc thi" : ""));
            }

            recalibrateAllBtn.setDisable(!enabled || locked || bssid.isBlank());
            apConfigBtn.setDisable(locked || !app.managedRoomSnapshot().path("is_owner").asBoolean()
                || !snap.path("phase").asText().equals("WAITING") || !snap.path("ap_config").path("supported").asBoolean());

            String warning = snap.path("persistence_warning").asText("");
            warningBanner.setText(warning);
            warningBanner.setVisible(!warning.isBlank());
            warningBanner.setManaged(!warning.isBlank());

            List<JsonNode> rows = new ArrayList<>();
            snap.path("players").forEach(rows::add);
            table.setItems(FXCollections.observableArrayList(rows));
            apTable.setItems(FXCollections.observableArrayList(rows));

            if (!selectedPlayerId.isBlank()) {
                for (JsonNode p : rows) {
                    if (selectedPlayerId.equals(p.path("player_id").asText())) {
                        table.getSelectionModel().select(p);
                        renderPlayerDetail(p);
                        return;
                    }
                }
            }
            selectedPlayerId="";detailBox.setVisible(false);detailBox.setManaged(false);
        }

        private void renderPlayerDetail(JsonNode p) {
            detailBox.setVisible(true);detailBox.setManaged(true);
            String nick = p.path("nickname").asText("");
            String statusLabel = p.path("status_label").asText("Không có dữ liệu");
            String distLabel = p.path("distance_label").asText("Không có dữ liệu");
            String detail = p.path("detail").asText("");
            String err = p.path("error_reason").asText("");
            boolean extrapolated = p.path("extrapolated").asBoolean(false);

            List<Node> lines = new ArrayList<>();
            lines.add(row(10,text("CHI TIẾT TÍN HIỆU · " + nick.toUpperCase(Locale.ROOT), "caption"),space(),button("Thu gọn","link-button",()->{selectedPlayerId="";table.getSelectionModel().clearSelection();detailBox.setVisible(false);detailBox.setManaged(false);})));
            lines.add(text("Trạng thái: " + statusLabel + "   ·   Khoảng cách: " + distLabel, "subtitle"));
            var ap = p.path("access_point");
            lines.add(text("AP: " + AccessPointViews.apLabel(p, app.measurementSnapshotAgeMs())
                + " · " + AccessPointViews.directionLabel(p, app.measurementSnapshotAgeMs()), "subtitle"));
            if (!ap.path("ssid").asText().isBlank())
                lines.add(text("Wi-Fi: " + ap.path("ssid").asText() + " · BSSID: " + ap.path("bssid").asText()
                    + " · " + ap.path("corridor").asText(), "muted"));
            var history = new ArrayList<String>();
            for (var event : ap.path("history")) history.add((event.path("continuous").asBoolean() ? "→ " : "| ")
                + event.path("ap_name").asText() + " (" + formatTimestamp(event.path("at_utc").asText()) + ")");
            lines.add(text(history.isEmpty() ? "Chưa có AP được xác nhận."
                : "Lịch sử AP: " + String.join("  ", history), "muted"));
            lines.add(text("Hướng chỉ được suy đoán từ chuyển AP đã xác nhận; chưa chuyển AP không có nghĩa là đứng yên. Dấu | bắt đầu đoạn theo dõi mới.", "muted"));
            if (!detail.isBlank()) {
                lines.add(text(detail, "muted"));
            }
            if (extrapolated) {
                lines.add(text("Lưu ý: Khoảng cách nằm ngoài đoạn hiệu chuẩn 1–5 m, kết quả được ngoại suy.", "muted"));
            }
            if (!err.isBlank() && !detail.contains(err)) {
                lines.add(text("Nguyên nhân lỗi: " + err, "feedback", "error"));
            }
            detailBox.getChildren().setAll(lines);
        }
    }

    /**
     * Shows the detailed proximity summary and alert timeline for a historical session.
     */
    void showProximityHistoryDetail(JsonNode detail) {
        var dialog = new Dialog<ButtonType>();
        dialog.initOwner(app.ownerStage());
        app.trackProximityDialog(dialog);
        styleDialog(dialog);
        dialog.getDialogPane().setId("proximity-history-detail");
        dialog.setTitle("Nhật ký giám sát khoảng cách");
        dialog.setHeaderText(detail.path("room_name").asText("Phòng thi") + " · " + detail.path("status_label").asText(""));

        String ap = detail.path("anchor_bssid").asText("").isBlank()
            ? "Không đặt mốc"
            : (detail.path("anchor_ssid").asText("").isBlank() ? detail.path("anchor_bssid").asText() : detail.path("anchor_ssid").asText() + " (" + detail.path("anchor_bssid").asText() + ")")
              + " · " + detail.path("anchor_band").asText("");
        Label meta = text(
            "Giảng viên: " + detail.path("host_name").asText("") +
            "   ·   Điểm phát: " + ap +
            "   ·   Ngưỡng: " + detail.path("threshold_meters").asInt(10) + " m (ước lượng)",
            "muted"
        );

        TableView<JsonNode> playerTable = new TableView<>();
        playerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        playerTable.setPlaceholder(text("Chưa có dữ liệu người chơi.", "muted"));
        playerTable.setPrefHeight(200);

        playerTable.getColumns().addAll(
            col("Người chơi", c -> c.path("nickname").asText(), 150),
            col("Trạng thái cuối", c -> c.path("last_status_label").asText(), 130),
            col("Nhỏ nhất (ước lượng)", c -> c.path("min_distance_label").asText("—"), 130),
            col("Lớn nhất (ước lượng)", c -> c.path("max_distance_label").asText("—"), 130),
            col("Thiếu dữ liệu", c -> formatDurationMs(c.path("missing_data_ms").asLong(0)), 110),
            col("Số cảnh báo", c -> String.valueOf(c.path("alert_count").asInt(0)), 95)
        );
        List<JsonNode> playerRows = new ArrayList<>();
        detail.path("players").forEach(playerRows::add);
        playerTable.setItems(FXCollections.observableArrayList(playerRows));

        TableView<JsonNode> alertTable = new TableView<>();
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        alertTable.setPlaceholder(text("Không có sự kiện cảnh báo nào trong lượt chơi này.", "muted"));
        alertTable.setPrefHeight(190);

        alertTable.getColumns().addAll(
            col("Thời điểm", c -> formatTimestamp(c.path("created_at_utc").asText()), 100),
            col("Người chơi", c -> c.path("nickname").asText(), 130),
            col("Loại cảnh báo", c -> c.path("event_label").asText(), 170),
            col("Khoảng cách", c -> c.path("distance_label").asText("—"), 100),
            col("Chi tiết", c -> c.path("detail").asText(), 280)
        );
        List<JsonNode> alertRows = new ArrayList<>();
        detail.path("alerts").forEach(alertRows::add);
        alertTable.setItems(FXCollections.observableArrayList(alertRows));

        Label alertPageLabel = text("", "muted");
        Button previousAlerts = button("← Trước", "quiet", null);
        Button nextAlerts = button("Sau →", "quiet", null);
        int[] alertPage = {detail.path("alert_page").asInt(1)};
        boolean[] alertsHaveMore = {detail.path("alerts_has_more").asBoolean()};
        java.util.function.Consumer<JsonNode> renderAlerts = page -> {
            alertPage[0] = page.path("alert_page").asInt(1);
            alertsHaveMore[0] = page.path("alerts_has_more").asBoolean();
            List<JsonNode> rows = new ArrayList<>();
            page.path("alerts").forEach(rows::add);
            alertTable.setItems(FXCollections.observableArrayList(rows));
            alertPageLabel.setText(page.path("alert_total").asInt(rows.size()) + " sự kiện · Trang " + alertPage[0]);
            previousAlerts.setDisable(alertPage[0] <= 1);
            nextAlerts.setDisable(!alertsHaveMore[0]);
        };
        java.util.function.IntConsumer loadAlerts = page -> {
            previousAlerts.setDisable(true); nextAlerts.setDisable(true);
            app.call("PROXIMITY_HISTORY_GET", object().put("session_id", detail.path("session_id").asText()).put("alert_page", page),
                value -> { if(dialog.isShowing())renderAlerts.accept(value); },
                error -> { if(dialog.isShowing()){previousAlerts.setDisable(alertPage[0] <= 1); nextAlerts.setDisable(!alertsHaveMore[0]); app.notice(ArcadeApplication.message(error),true);} });
        };
        previousAlerts.setOnAction(e -> loadAlerts.accept(alertPage[0] - 1));
        nextAlerts.setOnAction(e -> loadAlerts.accept(alertPage[0] + 1));
        renderAlerts.accept(detail);

        VBox content = column(
            12,
            meta,
            text("TÓM TẮT TỪNG NGƯỜI CHƠI", "caption"),
            playerTable,
            text("SỰ KIỆN CẢNH BÁO", "caption"),
            alertTable,
            row(12, alertPageLabel, space(), previousAlerts, nextAlerts)
        );

        if (detail.path("has_scoreboard").asBoolean() && detail.path("ranking").isArray() && detail.path("ranking").size() > 0) {
            VBox ranks = column(6, text("BẢNG ĐIỂM LIÊN KẾT", "caption"));
            for (JsonNode r : detail.path("ranking")) {
                ranks.getChildren().add(row(12,
                    text("#" + r.path("rank").asInt(), "subtitle"),
                    text(r.path("nickname").asText()),
                    space(),
                    text(r.path("score").asInt() + " điểm", "subtitle")
                ));
            }
            content.getChildren().add(ranks);
        } else {
            content.getChildren().add(text("Lượt chơi này không có bảng điểm hoàn tất (đã hủy hoặc gián đoạn).", "muted"));
        }

        dialog.getDialogPane().setContent(scroll(content));
        dialog.getDialogPane().setPrefSize(840, 620);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
        dialog.show();
    }

    private static TableColumn<JsonNode, String> col(String title, java.util.function.Function<JsonNode, String> mapper, double width) {
        TableColumn<JsonNode, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        c.setPrefWidth(width);
        c.setSortable(false);
        return c;
    }
}
