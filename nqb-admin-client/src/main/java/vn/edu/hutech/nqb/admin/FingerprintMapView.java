package vn.edu.hutech.nqb.admin;

import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import vn.edu.hutech.nqb.shared.WifiSurvey.Area;
import vn.edu.hutech.nqb.shared.fingerprint.FingerprintEvaluation.Row;

/** Measured coordinates, equal scale on both axes. No invented AP positions or walls. */
final class FingerprintMapView extends Pane {
    private Area area;
    private Row row;
    private boolean dirty=true;
    private double previousWidth=-1,previousHeight=-1;
    FingerprintMapView() { getStyleClass().add("fingerprint-map");setMinWidth(0); }
    void update(Area value,Row result) { area=value;row=result;dirty=true;requestLayout(); }
    @Override protected void layoutChildren() {
        if(!dirty&&previousWidth==getWidth()&&previousHeight==getHeight())return;
        dirty=false;previousWidth=getWidth();previousHeight=getHeight();
        getChildren().clear();if(area==null||getWidth()<120||getHeight()<120)return;
        double minX=Math.min(0,area.serverX()),maxX=Math.max(0,area.serverX()),minY=Math.min(0,area.serverY()),maxY=Math.max(0,area.serverY());
        for(var p:area.points()){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());}
        double dx=Math.max(1,maxX-minX),dy=Math.max(1,maxY-minY),scale=Math.min((getWidth()-100)/dx,(getHeight()-80)/dy);
        double left=(getWidth()-dx*scale)/2,bottom=(getHeight()+dy*scale)/2;
        final double x0=minX,y0=minY;java.util.function.DoubleUnaryOperator x=v->left+(v-x0)*scale,y=v->bottom-(v-y0)*scale;
        line(30,y.applyAsDouble(0),getWidth()-25,y.applyAsDouble(0),"fingerprint-axis",false);
        line(x.applyAsDouble(0),20,x.applyAsDouble(0),getHeight()-25,"fingerprint-axis",false);
        label("X (m)",getWidth()-55,y.applyAsDouble(0)-10);label("Y (m)",x.applyAsDouble(0)+8,20);
        label(FingerprintViews.number(minX),x.applyAsDouble(minX),getHeight()-8);label(FingerprintViews.number(maxX),x.applyAsDouble(maxX)-28,getHeight()-8);
        label(FingerprintViews.number(maxY),x.applyAsDouble(0)-38,y.applyAsDouble(maxY)+4);
        for(var p:area.points()){var dot=new Circle(x.applyAsDouble(p.x()),y.applyAsDouble(p.y()),4);dot.getStyleClass().add("fingerprint-point");Tooltip.install(dot,new Tooltip(p.toString()));getChildren().add(dot);}
        if(row!=null){
            var truth=area.points().stream().filter(p->p.id().equals(row.pointId())).findFirst().orElseThrow();var estimate=row.estimate();
            if(estimate.estimated()){
                double ex=x.applyAsDouble(estimate.x()),ey=y.applyAsDouble(estimate.y());
                line(ex,ey,x.applyAsDouble(area.serverX()),y.applyAsDouble(area.serverY()),"fingerprint-distance",false);
                line(ex,ey,x.applyAsDouble(truth.x()),y.applyAsDouble(truth.y()),"fingerprint-error",true);
                line(ex-7,ey-7,ex+7,ey+7,"fingerprint-estimate",false);line(ex-7,ey+7,ex+7,ey-7,"fingerprint-estimate",false);
            }
            var known=new Circle(x.applyAsDouble(truth.x()),y.applyAsDouble(truth.y()),10);known.getStyleClass().add("fingerprint-truth");getChildren().add(known);
        }
        var server=new Rectangle(x.applyAsDouble(area.serverX())-6,y.applyAsDouble(area.serverY())-6,12,12);server.getStyleClass().add("fingerprint-server");getChildren().add(server);
        label("Server",x.applyAsDouble(area.serverX())+12,y.applyAsDouble(area.serverY())-9);
        getChildren().forEach(node->node.setManaged(false));
    }
    private void label(String value,double x,double y){var text=new Text(x,y,value);text.getStyleClass().add("fingerprint-axis-text");getChildren().add(text);}
    private void line(double x1,double y1,double x2,double y2,String style,boolean dashed){var line=new Line(x1,y1,x2,y2);line.getStyleClass().add(style);if(dashed)line.getStrokeDashArray().setAll(5.0,4.0);getChildren().add(line);}
}
