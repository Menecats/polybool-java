package com.menecats.polybool.internal;

import com.menecats.polybool.Epsilon;
import com.menecats.polybool.models.Polygon;
import com.menecats.polybool.models.geojson.Geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.menecats.polybool.PolyBool.*;
import static com.menecats.polybool.helpers.PolyBoolHelper.point;

public final class GeoJSON {
    private static class Node {
        private final List<double[]> region;
        private final List<Node> children;

        private Node(List<double[]> region) {
            this.region = region;
            this.children = new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    public static Polygon toPolygon(final Epsilon epsilon,
                                    final Geometry<?> geojson) {

        final Function<List<List<double[]>>, Segments> geoPoly = (coords) -> {
            // check for empty coords
            if (coords.isEmpty()) {
                return segments(epsilon, new Polygon());
            }

            final Function<List<double[]>, Segments> lineString = ls -> {
                List<double[]> region = new ArrayList<>(ls);

                region.remove(region.size() - 1);

                return segments(epsilon, new Polygon(Collections.singletonList(region)));
            };

            Segments out = lineString.apply(coords.get(0));

            for (int i = 1; i < coords.size(); ++i) {
                out = selectDifference(combine(epsilon, out, lineString.apply(coords.get(i))));
            }

            return out;
        };

        if ("Polygon".equals(geojson.getType())) {
            final List<List<double[]>> coordinates = (List<List<double[]>>) geojson.getCoordinates();

            return polygon(epsilon, geoPoly.apply(coordinates));
        }

        if ("MultiPolygon".equals(geojson.getType())) {
            final List<List<List<double[]>>> coordinates = (List<List<List<double[]>>>) geojson.getCoordinates();

            Segments out = segments(epsilon, new Polygon());
            for (List<List<double[]>> coordinate : coordinates) {
                out = selectUnion(combine(epsilon, out, geoPoly.apply(coordinate)));
            }
            return polygon(epsilon, out);
        }

        throw new IllegalArgumentException("PolyBool: Cannot convert GeoJSON object to PolyBool polygon");
    }

    public static Geometry<?> fromPolygon(final Epsilon epsilon,
                                          Polygon poly) {

        // make sure out polygon is clean
        poly = polygon(epsilon, segments(epsilon, poly));

        // calculate inside heirarchy
        //
        //  _____________________   _______    roots -> A       -> F
        // |          A          | |   F   |            |          |
        // |  _______   _______  | |  ___  |            +-- B      +-- G
        // | |   B   | |   C   | | | |   | |            |   |
        // | |  ___  | |  ___  | | | |   | |            |   +-- D
        // | | | D | | | | E | | | | | G | |            |
        // | | |___| | | |___| | | | |   | |            +-- C
        // | |_______| |_______| | | |___| |                |
        // |_____________________| |_______|                +-- E


        final Node roots = new Node(null);

        // add all regions to the root
        for (int i = 0; i < poly.getRegions().size(); i++) {
            List<double[]> region = poly.getRegions().get(i);
            if (region.size() < 3) // regions must have at least 3 points (sanity check)
                continue;
            fromPolygon_addChild(epsilon, roots, region);
        }

        final List<List<List<double[]>>> geopolys = new ArrayList<>();

        // root nodes are exterior
        for (int i = 0; i < roots.children.size(); i++)
            fromPolygon_addExterior(geopolys, roots.children.get(i));

        // lastly, construct the approrpriate GeoJSON object

        if (geopolys.isEmpty()) // empty GeoJSON Polygon
            return new Geometry.PolygonGeometry();
        if (geopolys.size() == 1) // use a GeoJSON Polygon
            return new Geometry.PolygonGeometry(geopolys.get(0));

        // otherwise, use a GeoJSON MultiPolygon
        return new Geometry.MultiPolygonGeometry(geopolys);
    }

    // test if r1 is inside r2
    private static boolean fromPolygon_regionInsideRegion(final Epsilon epsilon,
                                                          final List<double[]> r1,
                                                          final List<double[]> r2) {

        // we're guaranteed no lines intersect (because the polygon is clean), but a vertex
        // could be on the edge -- so we just average pt[0] and pt[1] to produce a point on the
        // edge of the first line, which cannot be on an edge
        return epsilon.pointInsideRegion(point(
                (r1.get(0)[0] + r1.get(1)[0]) * 0.5,
                (r1.get(0)[1] + r1.get(1)[1]) * 0.5
        ), r2);
    }

    private static void fromPolygon_addChild(final Epsilon epsilon,
                                             final Node root,
                                             final List<double[]> region) {

        // first check if we're inside any children
        for (int i = 0; i < root.children.size(); i++) {
            final Node child = root.children.get(i);
            if (fromPolygon_regionInsideRegion(epsilon, region, child.region)) {
                // we are, so insert inside them instead
                fromPolygon_addChild(epsilon, child, region);
                return;
            }
        }

        // not inside any children, so check to see if any children are inside us
        final Node node = new Node(region);
        for (int i = 0; i < root.children.size(); i++) {
            final Node child = root.children.get(i);
            if (fromPolygon_regionInsideRegion(epsilon, child.region, region)) {
                // oops... move the child beneath us, and remove them from root
                node.children.add(child);
                root.children.remove(i);
                i--;
            }
        }

        // now we can add ourselves
        root.children.add(node);
    }

    // with our heirarchy, we can distinguish between exterior borders, and interior holes
    // the root nodes are exterior, children are interior, children's children are exterior,
    // children's children's children are interior, etc

    // while we're at it, exteriors are counter-clockwise, and interiors are clockwise
    private static List<double[]> fromPolygon_forceWinding(final List<double[]> region,
                                                           final boolean clockwise) {

        // first, see if we're clockwise or counter-clockwise
        // https://en.wikipedia.org/wiki/Shoelace_formula
        int winding = 0;
        double last_x = region.get(region.size() - 1)[0];
        double last_y = region.get(region.size() - 1)[1];
        final List<double[]> copy = new ArrayList<>();
        for (double[] point : region) {
            double curr_x = point[0];
            double curr_y = point[1];
            copy.add(point(curr_x, curr_y)); // create a copy while we're at it
            winding += curr_y * last_x - curr_x * last_y;
            last_x = curr_x;
            last_y = curr_y;
        }
        // this assumes Cartesian coordinates (Y is positive going up)
        boolean isclockwise = winding < 0;
        if (isclockwise != clockwise)
            Collections.reverse(copy);

        // while we're here, the last point must be the first point...
        copy.add(point(copy.get(0)[0], copy.get(0)[1]));
        return copy;
    }

    private static void fromPolygon_addExterior(final List<List<List<double[]>>> geopolys,
                                                final Node node) {
        final List<List<double[]>> p = new ArrayList<>();
        p.add(fromPolygon_forceWinding(node.region, false));

        geopolys.add(p);

        for (int i = 0; i < node.children.size(); ++i) {
            p.add(fromPolygon_getInterior(geopolys, node.children.get(i)));
        }
    }

    private static List<double[]> fromPolygon_getInterior(final List<List<List<double[]>>> geopolys,
                                                          final Node node) {
        for (int i = 0; i < node.children.size(); ++i) {
            fromPolygon_addExterior(geopolys, node.children.get(i));
        }

        return fromPolygon_forceWinding(node.region, true);
    }

    private GeoJSON() {
    }
}
