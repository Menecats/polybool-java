package com.menecats.polybool.models;

public final class Segment {
    public static final class SegmentFill {
        public Boolean above;
        public Boolean below;

        public SegmentFill() {
        }

        public SegmentFill(Boolean above, Boolean below) {
            this.above = above;
            this.below = below;
        }
    }

    public double[] start;
    public double[] end;
    public SegmentFill myFill;
    public SegmentFill otherFill;

    public Segment(double[] start, double[] end) {
        this(start, end, new SegmentFill());
    }

    public Segment(double[] start, double[] end, SegmentFill myFill) {
        this.start = start;
        this.end = end;
        this.myFill = myFill;
    }
}
