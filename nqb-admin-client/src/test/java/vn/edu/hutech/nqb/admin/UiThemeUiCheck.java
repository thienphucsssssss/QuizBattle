package vn.edu.hutech.nqb.admin;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;

/** Opt-in native UI behavior check; no Server, credentials or production data. */
public final class UiThemeUiCheck {
    private static Path output;
    public static void main(String[] args) {
        output = Path.of(args.length == 0 ? "../output/ui-system-theme" : args[0]);
        ArcadeApplication.qaDriver = app -> Thread.ofPlatform().daemon().start(() -> {
            try {
                Files.createDirectories(output);
                fx(() -> check(app));
                System.out.println("UI_THEME_PASS: live form retention, light/dark contrast, visible/new dialogs, QA preference isolation");
            } catch (Throwable error) { ArcadeApplication.qaFailed(error); error.printStackTrace(); }
            finally { Platform.runLater(Platform::exit); }
        });
        try { ArcadeApplication.launchFor("MANAGE", new String[]{"--width=1024", "--height=640"}); }
        finally { ArcadeApplication.qaDriver = null; }
    }
    @SuppressWarnings("unchecked")
    private static void check(ArcadeApplication app) {
        var prefs = Preferences.userNodeForPackage(ArcadeApplication.class);
        String saved = prefs.get("darkTheme", null);
        app.qaAuth();
        app.qaScene().getRoot().applyCss(); app.qaScene().getRoot().layout();
        TextField identity = (TextField) app.qaScene().lookup("#identity");
        identity.setText("giu-noi-dung-dang-nhap");
        app.qaSettings();
        Parent options = Window.getWindows().stream().filter(w -> w != app.qaScene().getWindow())
                .map(w -> w.getScene().getRoot()).filter(r -> r.lookup("#appearance-theme") != null).findFirst().orElse(null);
        check(options != null, "Options must expose the Sáng/Tối appearance selector");
        ComboBox<String> selector = (ComboBox<String>) options.lookup("#appearance-theme");
        for (String value : new String[]{"Tối", "Sáng", "Tối"}) {
            selector.setValue(value);
            boolean dark = value.equals("Tối");
            Parent root = app.qaScene().getRoot();
            root.applyCss(); root.layout(); options.applyCss(); options.layout();
            check(root.getStyleClass().contains("theme-dark") == dark, "Application theme did not update");
            check(options.getStyleClass().contains("theme-dark") == dark, "Visible options dialog did not update");
            check(app.qaScene().lookup("#identity") == identity && identity.getText().equals("giu-noi-dung-dang-nhap"), "Theme switch rebuilt or cleared the form");
            Label heading = (Label) root.lookup(".title");
            Color background = (Color) ((Region) root).getBackground().getFills().getFirst().getFill();
            check(contrast((Color) heading.getTextFill(), background) >= 4.5, "Heading contrast is insufficient");
            var nested = new Dialog<ButtonType>(); nested.initOwner(options.getScene().getWindow()); FxKit.styleDialog(nested);
            nested.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            check(nested.getDialogPane().getStyleClass().contains("theme-dark") == dark, "New dialog lost the active theme");
            snapshot(root, "auth-" + (dark ? "dark" : "light"));
            snapshot(options, "options-" + (dark ? "dark" : "light"));
            selector.show();
            Parent popup = Window.getWindows().stream().filter(w -> w instanceof javafx.stage.PopupWindow)
                    .map(w -> w.getScene().getRoot()).findFirst().orElseThrow();
            popup.applyCss(); popup.layout();
            check(popup.getStyleClass().contains("theme-dark") == dark, "Popup lost the active theme");
            snapshot(popup, "theme-popup-" + (dark ? "dark" : "light"));
            selector.hide();
            checkArcade(app, dark);
        }
        check(java.util.Objects.equals(saved, prefs.get("darkTheme", null)), "QA changed the user's saved theme");
        app.qaCloseAuxiliary();
    }
    private static void checkArcade(ArcadeApplication app, boolean dark) {
        var label = FxKit.text("Đáp án dài vẫn dễ đọc", "subtitle");
        var answer = FxKit.button("", "answer", () -> {}); answer.getStyleClass().add("a"); answer.setGraphic(label);
        var game = FxKit.column(16, FxKit.text("Câu hỏi trên sân đấu", "game-title"), answer);
        game.getStyleClass().add("game"); game.setPadding(new javafx.geometry.Insets(24));
        var scene = new javafx.scene.Scene(game, 650, 240); scene.getStylesheets().add(FxKit.CSS);
        var window = new javafx.stage.Stage(); window.initOwner(app.qaScene().getWindow()); window.setScene(scene); window.show();
        try {
            game.applyCss(); game.layout();
            Color fill = (Color) answer.getBackground().getFills().getFirst().getFill();
            check(contrast((Color)label.getTextFill(), fill) >= 4.5, "Appearance made pastel answer text unreadable");
            snapshot(game, "arcade-" + (dark ? "dark" : "light"));
        } finally { window.close(); }
    }
    private static double luminance(Color c) {
        return .2126 * linear(c.getRed()) + .7152 * linear(c.getGreen()) + .0722 * linear(c.getBlue());
    }
    private static double linear(double c) { return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
    private static double contrast(Color a, Color b) { return (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05); }
    private static void snapshot(Node node, String name) {
        try {
            WritableImage image = node.snapshot(null, null);
            var buffered = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y=0;y<buffered.getHeight();y++) for (int x=0;x<buffered.getWidth();x++) buffered.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            ImageIO.write(buffered,"png",output.resolve(name+".png").toFile());
        } catch(Exception error) { throw new IllegalStateException(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(() -> { action.run(); return null; }); Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }
}
