package vn.edu.hutech.nqb.admin;

import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import java.util.List;

/** Presentation helpers only. No networking, credentials or scoring. */
final class FxKit {
    static final String CSS = FxKit.class.getResource("/ui/arcade.css").toExternalForm();
    private FxKit() { }
    static void runWhenShowing(Node node,javafx.animation.Animation animation){
        var current=new java.util.concurrent.atomic.AtomicReference<javafx.stage.Window>();
        javafx.beans.value.ChangeListener<Boolean> visible=(o,b,a)->{if(a)animation.play();else animation.stop();};
        javafx.beans.value.ChangeListener<javafx.stage.Window> window=(o,b,a)->{
            var old=current.getAndSet(a);if(old!=null)old.showingProperty().removeListener(visible);
            animation.stop();if(a!=null){a.showingProperty().addListener(visible);if(a.isShowing())animation.play();}
        };
        node.sceneProperty().addListener((o,b,a)->{
            if(b!=null)b.windowProperty().removeListener(window);
            window.changed(null,current.get(),null);
            if(a!=null){a.windowProperty().addListener(window);window.changed(null,null,a.getWindow());}
        });
    }
    static void loadFonts() {
        for (String name : List.of("Regular", "SemiBold", "ExtraBold")) {
            try (var input = FxKit.class.getResourceAsStream("/ui/fonts/BeVietnamPro-" + name + ".ttf")) {
                if (input != null) Font.loadFont(input, 16);
            } catch (Exception ignored) { /* System font fallback still keeps the application usable. */ }
        }
    }
    static Label text(String value, String... styles) {
        Label label = new Label(value); label.getStyleClass().addAll(styles); label.setWrapText(true); label.setMinWidth(0); return label;
    }
    static Button button(String name, String style, Runnable action) {
        Button button = new Button(name); button.getStyleClass().add(style); button.setOnAction(event -> action.run()); return button;
    }
    static VBox column(double gap, Node... nodes) { VBox box = new VBox(gap, nodes); box.setMinWidth(0); return box; }
    static HBox row(double gap, Node... nodes) { HBox box = new HBox(gap, nodes); box.setAlignment(Pos.CENTER_LEFT); box.setMinWidth(0); return box; }
    static Region space() { Region region = new Region(); HBox.setHgrow(region, Priority.ALWAYS); VBox.setVgrow(region, Priority.ALWAYS); return region; }
    static ScrollPane scroll(Node node) {
        ScrollPane pane = new ScrollPane(node); pane.setFitToWidth(true); pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); pane.setMinHeight(0); return pane;
    }
    static VBox field(String name, Control control) {
        Label label = text(name, "caption"); label.setLabelFor(control); control.setMaxWidth(Double.MAX_VALUE); return column(7, label, control);
    }
    static Pane mark(double size) {
        Pane pane = new Pane(); pane.setMinSize(size, size); pane.setPrefSize(size, size); pane.setMaxSize(size, size);
        Rectangle bg = new Rectangle(size, size, Color.web("#b7f36b")); bg.setArcWidth(size * .3); bg.setArcHeight(size * .3);
        double k = size / 48;
        Path p = new Path(new MoveTo(12*k, 13*k), new LineTo(32*k, 13*k), new LineTo(32*k, 32*k), new LineTo(13*k, 32*k), new LineTo(13*k, 13*k), new MoveTo(23*k, 24*k), new LineTo(36*k, 37*k));
        p.setStroke(Color.web("#162235")); p.setStrokeWidth(3*k); p.setFill(null); p.setStrokeLineCap(StrokeLineCap.ROUND); p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Circle node = new Circle(36*k, 37*k, 3*k, Color.web("#162235"));
        pane.getChildren().addAll(bg, p, node); pane.setMouseTransparent(true); pane.setAccessibleText("Network Quiz Battle"); return pane;
    }
    static Node brand() { return row(12, mark(40), column(1, text("NETWORK", "caption"), text("QUIZ BATTLE", "brand"))); }
    static void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(CSS); dialog.getDialogPane().setMinWidth(420);
    }
}
