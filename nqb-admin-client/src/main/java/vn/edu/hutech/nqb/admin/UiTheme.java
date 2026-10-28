package vn.edu.hutech.nqb.admin;

import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.stage.Window;
import java.util.List;

/** Appearance only: updates existing scenes, dialogs and popup windows without rebuilding views. */
final class UiTheme {
    private static boolean dark;
    static {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) if (change.wasAdded())
                for (Window window : change.getAddedSubList()) apply(window);
        });
    }
    private UiTheme() { }
    static boolean isDark() { return dark; }
    static void setDark(boolean value) {
        dark = value;
        for (Window window : List.copyOf(Window.getWindows())) apply(window);
    }
    static void apply(Parent root) {
        if (dark) {
            if (!root.getStyleClass().contains("theme-dark")) root.getStyleClass().add("theme-dark");
        } else root.getStyleClass().remove("theme-dark");
    }
    private static void apply(Window window) {
        if (window.getScene() != null) apply(window.getScene().getRoot());
    }
}
