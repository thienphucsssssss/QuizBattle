package vn.edu.hutech.nqb.admin;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

/** Uses available content width, independent of window decorations/sidebar. */
final class AdaptiveColumns extends Pane {
    private final Node left,right;private final double ratio;
    AdaptiveColumns(Node left,Node right,double ratio){this.left=left;this.right=right;this.ratio=ratio;getChildren().addAll(left,right);setMinWidth(0);setMaxWidth(Double.MAX_VALUE);widthProperty().addListener((o,b,a)->{requestLayout();if(getParent()!=null)getParent().requestLayout();});}
    boolean wide(){return getWidth()>=1050;}
    @Override protected double computePrefWidth(double height){return 1100;}
    @Override protected double computePrefHeight(double width){double w=width<0?1100:width;return w>=1050?Math.max(left.prefHeight((w-24)*ratio),right.prefHeight((w-24)*(1-ratio))):left.prefHeight(w)+20+right.prefHeight(w);}
    @Override protected void layoutChildren(){double w=getWidth();if(w>=1050){double a=(w-24)*ratio;left.resizeRelocate(0,0,a,left.prefHeight(a));right.resizeRelocate(a+24,0,w-a-24,right.prefHeight(w-a-24));}else{double h=left.prefHeight(w);left.resizeRelocate(0,0,w,h);right.resizeRelocate(0,h+20,w,right.prefHeight(w));}}
}
