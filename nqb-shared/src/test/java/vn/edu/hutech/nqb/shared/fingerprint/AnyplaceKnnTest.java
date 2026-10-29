package vn.edu.hutech.nqb.shared.fingerprint;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnyplaceKnnTest {
    @Test void euclideanAndInverseDistanceMeanMatchKnownValues(){
        assertEquals(5,AnyplaceKnn.euclidean(new double[]{-50,-60},new double[]{-53,-64}),1e-12);
        var values=List.of(new AnyplaceKnn.Neighbor("a",2,0,0),new AnyplaceKnn.Neighbor("b",6,8,4));
        var plain=AnyplaceKnn.interpolate(values,false);var weighted=AnyplaceKnn.interpolate(values,true);
        assertEquals(new AnyplaceKnn.Position(4,2),plain);assertEquals(2,weighted.x(),1e-12);assertEquals(1,weighted.y(),1e-12);
    }
    @Test void exactMatchesExcludeFartherNeighborsAndBoundaryTiesAreNotSilentlyDropped(){
        var exact=new AnyplaceKnn.Neighbor("a",0,3,4);var farther=new AnyplaceKnn.Neighbor("b",2,100,100);
        assertEquals(List.of(exact),AnyplaceKnn.nearest(List.of(farther,exact),2));assertEquals(new AnyplaceKnn.Position(3,4),AnyplaceKnn.interpolate(List.of(exact,farther),true));
        var tie=new AnyplaceKnn.Neighbor("c",2,10,0);assertEquals(List.of(farther,tie),AnyplaceKnn.nearest(List.of(tie,farther),1));
    }
    @Test void invalidInputsFailAndNumericalScalingAvoidsTinyDistanceOverflow(){
        assertThrows(IllegalArgumentException.class,()->AnyplaceKnn.euclidean(new double[0],new double[0]));
        assertThrows(IllegalArgumentException.class,()->AnyplaceKnn.euclidean(new double[]{Double.NaN},new double[]{0}));
        assertThrows(IllegalArgumentException.class,()->AnyplaceKnn.euclidean(new double[]{1},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->AnyplaceKnn.interpolate(List.of(),true));
        assertThrows(IllegalArgumentException.class,()->new AnyplaceKnn.Neighbor("a",-1,0,0));
        var p=AnyplaceKnn.interpolate(List.of(new AnyplaceKnn.Neighbor("a",Double.MIN_VALUE,4,3),new AnyplaceKnn.Neighbor("b",Double.MIN_VALUE,8,5)),true);
        assertEquals(new AnyplaceKnn.Position(6,4),p);
    }
    @Test void originalAnyplaceGoldenResultsAgreeForNonzeroUntiedDistances()throws Exception{
        try(var input=getClass().getResourceAsStream("/fingerprint/anyplace-golden.json")){
            assertNotNull(input);var data=vn.edu.hutech.nqb.shared.WifiSurvey.JSON.readTree(input);
            for(var sample:data.path("cases")){
                var neighbors=new ArrayList<AnyplaceKnn.Neighbor>();double[] query=new double[3];for(int i=0;i<3;i++)query[i]=sample.path("query").get(i).asDouble();
                for(var reference:data.path("references")){double[] values=new double[3];for(int i=0;i<3;i++)values[i]=reference.path("signal").get(i).asDouble();neighbors.add(new AnyplaceKnn.Neighbor(reference.path("id").asText(),AnyplaceKnn.euclidean(values,query),reference.path("x").asDouble(),reference.path("y").asDouble()));}
                var result=AnyplaceKnn.interpolate(AnyplaceKnn.nearest(neighbors,sample.path("k").asInt()),sample.path("weighted").asBoolean());
                assertEquals(sample.path("x").asDouble(),result.x(),1e-5);assertEquals(sample.path("y").asDouble(),result.y(),1e-5);
            }
        }
    }
}
