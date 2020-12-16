package com.menecats.polybool.internal;

import com.menecats.polybool.Epsilon;
import com.menecats.polybool.models.Segment;

import java.util.List;

public class NonSelfIntersecter extends AbstractIntersecter {
    public NonSelfIntersecter(Epsilon eps) {
        super(false, eps);
    }

    public List<Segment> calculate(List<Segment> segments1, boolean inverted1, List<Segment> segments2, boolean inverted2) {
        // segmentsX come from the self-intersection API, or this API
        // invertedX is whether we treat that list of segments as an inverted polygon or not
        // returns segments that can be used for further operations
        for (Segment seg : segments1) {
            this.eventAddSegment(this.segmentCopy(seg.start, seg.end, seg), true);
        }
        for (Segment seg : segments2) {
            this.eventAddSegment(this.segmentCopy(seg.start, seg.end, seg), false);
        }
        return this.baseCalculate(inverted1, inverted2);
    }
}
