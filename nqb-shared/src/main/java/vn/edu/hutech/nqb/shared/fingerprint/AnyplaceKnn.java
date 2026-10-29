/*
 * Derived from Anyplace Algorithms.java, commit 7229551823757cc39174cc693947d9077e770bfb.
 * https://github.com/dmsl/anyplace/blob/7229551823757cc39174cc693947d9077e770bfb/clients/android/Anyplace/src/com/dmsl/airplace/algorithms/Algorithms.java
 * Authors: C. Laoudias, G. Larkou, G. Constantinou, M. Constantinides, S. Nicolaou.
 * Supervisor: Demetrios Zeinalipour-Yazti.
 * Copyright (c) 2015, Data Management Systems Lab (DMSL), University of Cyprus.
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in the
 * Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * QuizBattle adaptation (2026): retain Euclidean RSSI distance, KNN arithmetic
 * mean and WKNN inverse-distance mean. Replace string coordinates/floats with
 * typed doubles; validate inputs; handle exact matches without the original
 * arbitrary weight 100; stable ordering and inclusive boundary ties. The caller
 * must reject ambiguous geometry before interpolation. No Android/Realm code.
 */
package vn.edu.hutech.nqb.shared.fingerprint;

import java.util.*;

/** Numerical core adapted from the three distance/averaging methods in Anyplace. */
public final class AnyplaceKnn {
    private AnyplaceKnn() { }

    public record Neighbor(String id,double distance,double x,double y) {
        public Neighbor {
            if(id==null||id.isBlank()||!Double.isFinite(distance)||distance<0||!Double.isFinite(x)||!Double.isFinite(y))
                throw new IllegalArgumentException("Invalid neighbor");
        }
    }
    public record Position(double x,double y) { }

    /** From calculateEuclideanDistance; malformed vectors fail explicitly. */
    public static double euclidean(double[] reference,double[] observed) {
        if(reference.length==0||reference.length!=observed.length)throw new IllegalArgumentException("RSSI vectors must have equal nonzero length");
        double sum=0;
        for(int i=0;i<reference.length;i++){
            if(!Double.isFinite(reference[i])||!Double.isFinite(observed[i]))throw new IllegalArgumentException("Nonfinite RSSI");
            double difference=reference[i]-observed[i];
            sum+=difference*difference;
        }
        if(!Double.isFinite(sum))throw new IllegalArgumentException("RSSI distance overflow");
        return Math.sqrt(sum);
    }

    /** Deterministic K selection. Ties at K are retained for the caller's ambiguity check. */
    public static List<Neighbor> nearest(List<Neighbor> values,int k) {
        if(k<1||values.isEmpty())throw new IllegalArgumentException("K and neighbor count must be positive");
        var sorted=values.stream().sorted(Comparator.comparingDouble(Neighbor::distance).thenComparing(Neighbor::id)).toList();
        double cutoff=sorted.get(Math.min(k,sorted.size())-1).distance();
        if(sorted.getFirst().distance()==0)cutoff=0;
        double boundary=cutoff;
        return sorted.stream().filter(n->n.distance()<=boundary).toList();
    }

    /** From calculateAverageKDistanceLocations / calculateWeightedAverageKDistanceLocations. */
    public static Position interpolate(List<Neighbor> selected,boolean weighted) {
        if(selected.isEmpty())throw new IllegalArgumentException("No selected neighbors");
        double minimum=selected.stream().mapToDouble(Neighbor::distance).min().orElseThrow();
        // An exact fingerprint is not diluted by farther neighbors. Multiple exact matches
        // remain visible to the caller, who rejects conflicting physical positions.
        var used=minimum==0?selected.stream().filter(n->n.distance()==0).toList():selected;
        double sumWeights=0,sumX=0,sumY=0;
        for(var neighbor:used){
            // Common scaling factor avoids overflow for tiny distances; same weighted mean as 1/d.
            double weight=weighted&&minimum>0?minimum/neighbor.distance():1;
            sumWeights+=weight;
            sumX+=weight*neighbor.x();
            sumY+=weight*neighbor.y();
        }
        double x=sumX/sumWeights,y=sumY/sumWeights;
        if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Coordinate overflow");
        return new Position(x,y);
    }
}
