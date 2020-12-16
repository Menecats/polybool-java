package com.menecats.polybool;
/*
 * @copyright 2016 Sean Connelly (@voidqk), http://syntheti.cc
 * @license MIT
 * @preserve Project Home: https://github.com/voidqk/polybooljs
 */

import com.menecats.polybool.internal.*;
import com.menecats.polybool.models.Polygon;
import com.menecats.polybool.models.Segment;
import com.menecats.polybool.models.geojson.Geometry;

import java.util.List;
import java.util.function.Function;

public final class PolyBool {
    public static final class Segments {
        private final List<Segment> segments;
        private final boolean inverted;

        private Segments(List<Segment> segments, boolean inverted) {
            this.segments = segments;
            this.inverted = inverted;
        }
    }

    public static final class Combined {
        private final List<Segment> combined;
        private final boolean inverted1;
        private final boolean inverted2;

        private Combined(List<Segment> combined, boolean inverted1, boolean inverted2) {
            this.combined = combined;
            this.inverted1 = inverted1;
            this.inverted2 = inverted2;
        }
    }

    // Core API
    public static Segments segments(Epsilon epsilon, Polygon polygon) {
        SelfIntersecter i = new SelfIntersecter(epsilon);

        for (List<double[]> region : polygon.getRegions()) {
            i.addRegion(region);
        }

        return new Segments(
                i.calculate(polygon.isInverted()),
                polygon.isInverted()
        );
    }

    public static Combined combine(Epsilon epsilon, Segments segments1, Segments segments2) {
        NonSelfIntersecter i3 = new NonSelfIntersecter(epsilon);

        return new Combined(
                i3.calculate(
                        segments1.segments, segments1.inverted,
                        segments2.segments, segments2.inverted
                ),
                segments1.inverted,
                segments2.inverted
        );
    }

    public static Segments selectUnion(Combined combined) {
        return new Segments(
                SegmentSelector.union(combined.combined),
                combined.inverted1 || combined.inverted2
        );
    }

    public static Segments selectIntersect(Combined combined) {
        return new Segments(
                SegmentSelector.intersect(combined.combined),
                combined.inverted1 && combined.inverted2
        );
    }

    public static Segments selectDifference(Combined combined) {
        return new Segments(
                SegmentSelector.difference(combined.combined),
                combined.inverted1 && !combined.inverted2
        );
    }

    public static Segments selectDifferenceRev(Combined combined) {
        return new Segments(
                SegmentSelector.differenceRev(combined.combined),
                !combined.inverted1 && combined.inverted2
        );
    }

    public static Segments selectXor(Combined combined) {
        return new Segments(
                SegmentSelector.xor(combined.combined),
                combined.inverted1 != combined.inverted2
        );
    }

    public static Polygon polygon(Epsilon epsilon, Segments segments) {
        return new Polygon(
                SegmentChainer.chain(segments.segments, epsilon),
                segments.inverted
        );
    }

    // Public API
    private static Polygon operate(Epsilon epsilon, Polygon poly1, Polygon poly2, Function<Combined, Segments> selector) {
        Segments seg1 = segments(epsilon, poly1);
        Segments seg2 = segments(epsilon, poly2);
        Combined comb = combine(epsilon, seg1, seg2);
        Segments seg3 = selector.apply(comb);
        return polygon(epsilon, seg3);
    }

    public static Polygon union(Epsilon epsilon, Polygon poly1, Polygon poly2) {
        return operate(epsilon, poly1, poly2, PolyBool::selectUnion);
    }

    public static Polygon intersect(Epsilon epsilon, Polygon poly1, Polygon poly2) {
        return operate(epsilon, poly1, poly2, PolyBool::selectIntersect);
    }

    public static Polygon difference(Epsilon epsilon, Polygon poly1, Polygon poly2) {
        return operate(epsilon, poly1, poly2, PolyBool::selectDifference);
    }

    public static Polygon differenceRev(Epsilon epsilon, Polygon poly1, Polygon poly2) {
        return operate(epsilon, poly1, poly2, PolyBool::selectDifferenceRev);
    }

    public static Polygon xor(Epsilon epsilon, Polygon poly1, Polygon poly2) {
        return operate(epsilon, poly1, poly2, PolyBool::selectXor);
    }

    // Import export
    // GeoJSON converters
    public static Polygon polygonFromGeoJSON(Epsilon epsilon, Geometry<?> geojson) {
        return GeoJSON.toPolygon(epsilon, geojson);
    }

    public static Geometry<?> polygonToGeoJSON(Epsilon epsilon, Polygon poly) {
        return GeoJSON.fromPolygon(epsilon, polygon(epsilon, segments(epsilon, poly)));
    }

    private PolyBool() {
    }
}
