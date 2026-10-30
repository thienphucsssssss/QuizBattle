package vn.edu.hutech.nqb.admin;

import javafx.animation.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.util.*;
import java.util.function.Consumer;
import vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan;
import vn.edu.hutech.nqb.shared.fingerprint.CorridorPlan.Position;

/** Equal-scale XY map; animates only between received positions, never extrapolates motion. */
final class CorridorMapView extends Pane implements AutoCloseable {
    record Client(String id,String name,Position position,String status) { }
    private CorridorPlan plan=CorridorPlan.DEFAULT;
    private Position server=plan.roomCenter(1);
    private List<Client> clients=List.of();
    private final Map<String,Position> displayed=new HashMap<>();
    private String selected="";
    private Timeline transition;
    private boolean smooth=true;
    private Consumer<String> select=id->{};
    private Consumer<Double> distance=v->{};
    CorridorMapView(){getStyleClass().add("corridor-map");setMinSize(250,220);setPrefHeight(320);setAccessibleText("Bản đồ hành lang, phòng học hai bên; chấm tròn là Client, ô vuông là Server.");}
    void onSelect(Consumer<String> action){select=action;}
    void onDistance(Consumer<Double> action){distance=action;}
    void update(CorridorPlan value,Position destination,List<Client> values,String selection,boolean animate){
        if(transition!=null)transition.stop();
        boolean changed=!plan.equals(value)||!server.equals(destination);plan=value;server=destination;clients=List.copyOf(values);selected=selection;smooth=animate;
        if(changed)displayed.clear();
        displayed.keySet().retainAll(clients.stream().filter(c->c.position()!=null).map(Client::id).toList());
        var start=new HashMap<String,Position>();
        for(var c:clients)if(c.position()!=null){if(!smooth)displayed.put(c.id(),c.position());else displayed.putIfAbsent(c.id(),c.position());start.put(c.id(),displayed.get(c.id()));}
        render();
        if(!smooth||clients.stream().filter(c->c.position()!=null).noneMatch(c->c.position().distance(displayed.get(c.id()))>.01))return;
        long began=System.nanoTime();transition=new Timeline(new KeyFrame(Duration.millis(40),event->{
            double t=Math.min(1,(System.nanoTime()-began)/650_000_000.0);double eased=t*t*(3-2*t);
            for(var c:clients)if(c.position()!=null){var from=start.get(c.id());var to=c.position();displayed.put(c.id(),new Position(from.x()+(to.x()-from.x())*eased,from.y()+(to.y()-from.y())*eased));}
            render();if(t>=1)transition.stop();
        }));transition.setCycleCount(Animation.INDEFINITE);transition.play();
    }
    @Override protected void layoutChildren(){render();}
    private void render(){
        getChildren().clear();if(getWidth()<100||getHeight()<100)return;
        double minX=Math.min(0,server.x()),maxX=Math.max(plan.length(),server.x()),minY=Math.min(-plan.halfHeight(),server.y()),maxY=Math.max(plan.halfHeight(),server.y());
        for(var p:displayed.values()){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());}
        double scale=Math.min((getWidth()-64)/(maxX-minX),(getHeight()-70)/(maxY-minY));
        final double x0=minX,y0=maxY,left=(getWidth()-(maxX-minX)*scale)/2,top=(getHeight()-(maxY-minY)*scale)/2;
        java.util.function.DoubleUnaryOperator x=v->left+(v-x0)*scale,y=v->top+(y0-v)*scale;
        rectangle(x.applyAsDouble(0),y.applyAsDouble(plan.corridorWidth()/2),plan.length()*scale,plan.corridorWidth()*scale,"corridor-hall");
        for(int i=0;i<plan.roomsPerSide();i++)for(int side=0;side<2;side++){
            double wx=i*plan.roomWidth(),wy=side==0?plan.halfHeight():-plan.corridorWidth()/2;
            rectangle(x.applyAsDouble(wx),y.applyAsDouble(wy),plan.roomWidth()*scale,plan.roomDepth()*scale,"corridor-room");
            double doorY=y.applyAsDouble(side==0?plan.corridorWidth()/2:-plan.corridorWidth()/2);
            rectangle(x.applyAsDouble(wx+plan.roomWidth()/2-0.65),doorY-2,1.3*scale,4,"corridor-hall");
            label(plan.roomName(i+side*plan.roomsPerSide()),x.applyAsDouble(wx+plan.roomWidth()/2)-10,side==0?y.applyAsDouble(wy)-8:y.applyAsDouble(-plan.halfHeight())+20);
        }
        var chosen=clients.stream().filter(c->c.id().equals(selected)).findFirst().orElse(null);
        var active=chosen==null?null:displayed.get(chosen.id());
        if(active!=null){var line=new Line(x.applyAsDouble(active.x()),y.applyAsDouble(active.y()),x.applyAsDouble(server.x()),y.applyAsDouble(server.y()));line.getStyleClass().add("corridor-link");line.getStrokeDashArray().addAll(6.0,5.0);getChildren().add(line);}
        var destination=rectangle(x.applyAsDouble(server.x())-7,y.applyAsDouble(server.y())-7,14,14,"corridor-server");destination.setAccessibleText("Server cố định");
        for(var c:clients){var p=displayed.get(c.id());if(p==null)continue;
            double px=x.applyAsDouble(p.x()),py=y.applyAsDouble(p.y());
            if(c.id().equals(selected)){var halo=new Circle(px,py,17);halo.getStyleClass().add("corridor-halo");getChildren().add(halo);}
            var dot=new Circle(px,py,c.id().equals(selected)?8:6);dot.getStyleClass().add("corridor-client");dot.setAccessibleText(c.name());dot.setOnMouseClicked(e->select.accept(c.id()));getChildren().add(dot);
            if(c.id().equals(selected)){String name=c.name().length()>18?c.name().substring(0,17)+"…":c.name();var text=label(name,px+13,py-13);double right=px+13+text.getLayoutBounds().getWidth();if(right>getWidth()-8)text.setX(Math.max(8,px-13-text.getLayoutBounds().getWidth()));}
        }
        distance.accept(active==null?null:active.distance(server));
    }
    private Rectangle rectangle(double x,double y,double w,double h,String style){var r=new Rectangle(x,y,w,h);r.getStyleClass().add(style);getChildren().add(r);return r;}
    private Text label(String value,double x,double y){var t=new Text(x,y,value);t.getStyleClass().add("corridor-label");getChildren().add(t);return t;}
    @Override public void close(){if(transition!=null)transition.stop();displayed.clear();}
}
