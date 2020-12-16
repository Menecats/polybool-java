package com.menecats.polybool.internal;

import com.menecats.polybool.Epsilon;
import com.menecats.polybool.models.Segment;

import java.util.List;

public class SelfIntersecter extends AbstractIntersecter {
    public SelfIntersecter(Epsilon eps) {
        super(true, eps);
    }

    public void addRegion(List<double[]> region) {
        // regions are a list of points:
        //  [ [0, 0], [100, 0], [50, 100] ]
        // you can add multiple regions before running calculate
        double[] pt1;
        double[] pt2 = region.get(region.size() - 1);
        for (double[] pt : region) {
            pt1 = pt2;
            pt2 = pt;

            int forward = this.eps.pointsCompare(pt1, pt2);
            if (forward == 0) // points are equal, so we have a zero-length segment
                continue; // just skip it

            this.eventAddSegment(
                    this.segmentNew(
                            forward < 0 ? pt1 : pt2,
                            forward < 0 ? pt2 : pt1
                    ),
                    true
            );
        }
    }

    public List<Segment> calculate(boolean inverted) {
        // is the polygon inverted?
        // returns segments
        return this.baseCalculate(inverted, false);
    }
}
