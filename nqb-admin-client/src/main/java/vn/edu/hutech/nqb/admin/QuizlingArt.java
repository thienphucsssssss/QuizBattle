package vn.edu.hutech.nqb.admin;

import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Scale;
import vn.edu.hutech.nqb.shared.AvatarCatalog;

/** Original vector characters, not emoji or downloaded Kahoot assets. */
final class QuizlingArt {
    private static final String[] COLORS = {"#b8f350", "#ff9366", "#a6a0ff", "#63dcdc", "#ffd66f", "#ff9ac5"};
    static Pane avatar(String id, double size) {
        int v;
        try { v = AvatarCatalog.find(id).variant(); } catch (IllegalArgumentException missing) { v = 0; }
        Color body = Color.web(COLORS[v % COLORS.length]), ink = Color.web("#202146"), light = Color.web("#fffceb");
        var art = new Group();
        art.getChildren().add(ellipse(60, 108, 34, 5, Color.web("#101331", .16)));
        // Different silhouettes and appendages, not just a palette swap.
        if (v % 4 == 0) {
            art.getChildren().addAll(ellipse(41, 27, 10, 24, body), ellipse(78, 27, 10, 24, body));
        } else if (v % 4 == 1) {
            art.getChildren().addAll(poly(body, 24,56, 24,12, 49,37), poly(body, 72,37, 98,12, 97,61));
        } else if (v % 4 == 2) {
            art.getChildren().addAll(ellipse(28, 38, 17, 17, body), ellipse(91, 38, 17, 17, body));
        } else {
            var stem = new Line(60, 20, 60, 38); stem.setStroke(body); stem.setStrokeWidth(7);
            art.getChildren().addAll(stem, ellipse(60, 17, 9, 9, body), poly(body, 26,58, 9,42, 13,76), poly(body, 96,58, 112,42, 107,76));
        }
        art.getChildren().addAll(ellipse(40, 101, 14, 8, body.darker()), ellipse(80, 101, 14, 8, body.darker()));
        var torso = new Rectangle(24, 31, 72, 72); torso.setArcWidth(v % 3 == 0 ? 66 : 36); torso.setArcHeight(v % 3 == 0 ? 66 : 36); torso.setFill(body);
        art.getChildren().add(torso);
        // Six forehead marks distinguish even equal-colour silhouettes.
        if (v / 4 % 2 == 0) art.getChildren().add(poly(light, 60,35, 64,42, 60,49, 56,42));
        else { art.getChildren().addAll(ellipse(54, 41, 3, 4, light), ellipse(65, 41, 3, 4, light)); }
        art.getChildren().addAll(ellipse(44, 65, 12, 15, light), ellipse(77, 65, 12, 15, light));
        double gaze = v % 3 - 1;
        art.getChildren().addAll(ellipse(46 + gaze * 2, 67, 5, 8, ink), ellipse(79 + gaze * 2, 67, 5, 8, ink), ellipse(47 + gaze * 2, 64, 1.7, 2.1, Color.WHITE), ellipse(80 + gaze * 2, 64, 1.7, 2.1, Color.WHITE));
        var smile = new Arc(60, 81, 10, 8, 190, 160); smile.setType(ArcType.OPEN); smile.setFill(null); smile.setStroke(ink); smile.setStrokeWidth(3); smile.setStrokeLineCap(StrokeLineCap.ROUND);
        art.getChildren().addAll(smile, ellipse(31, 79, 5, 3, Color.web("#e65f8a", .55)), ellipse(90, 79, 5, 3, Color.web("#e65f8a", .55)));
        if (v / 6 == 1) {
            var scarf = new Rectangle(27, 91, 66, 9); scarf.setArcWidth(9); scarf.setArcHeight(9); scarf.setFill(ink);
            art.getChildren().addAll(scarf, poly(ink, 77,94, 90,94, 95,111, 83,108));
        } else if (v / 6 == 2) {
            var band = new Arc(60, 57, 39, 28, 0, 180); band.setType(ArcType.OPEN); band.setFill(null); band.setStroke(ink); band.setStrokeWidth(5);
            art.getChildren().addAll(band, ellipse(23,64,7,14,ink), ellipse(97,64,7,14,ink));
        } else if (v / 6 == 3) {
            for (int x : new int[]{44,77}) { var glass = new Circle(x,65,15,Color.TRANSPARENT); glass.setStroke(ink); glass.setStrokeWidth(3); art.getChildren().add(glass); }
            var bridge = new Line(59,65,62,65); bridge.setStroke(ink); bridge.setStrokeWidth(3); art.getChildren().add(bridge);
        }
        art.getTransforms().add(new Scale(size / 120, size / 120));
        var frame = new Pane(art); frame.setMinSize(size, size); frame.setPrefSize(size, size); frame.setMaxSize(size, size); frame.setMouseTransparent(true);
        frame.setAccessibleText(AvatarCatalog.ALL.get(v).name()); return frame;
    }
    private static Ellipse ellipse(double x, double y, double rx, double ry, Color color) { var e = new Ellipse(x, y, rx, ry); e.setFill(color); return e; }
    private static Polygon poly(Color color, double... coordinates) { var p = new Polygon(coordinates); p.setFill(color); return p; }
}
