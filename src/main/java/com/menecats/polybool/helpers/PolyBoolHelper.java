package com.menecats.polybool.helpers;

import com.menecats.polybool.Epsilon;
import com.menecats.polybool.ExperimentalEpsilon;
import com.menecats.polybool.models.Polygon;

import java.util.Arrays;
import java.util.List;

public final class PolyBoolHelper {
    public static Epsilon epsilon() {
        return epsilon(false);
    }

    public static Epsilon epsilon(boolean experimental) {
        return experimental
                ? new ExperimentalEpsilon()
                : new Epsilon();
    }

    public static Epsilon epsilon(double epsilon) {
        return epsilon(epsilon, false);
    }

    public static Epsilon epsilon(double epsilon, boolean experimental) {
        return experimental
                ? new ExperimentalEpsilon(epsilon)
                : new Epsilon(epsilon);
    }

    public static double[] point(double x, double y) {
        return new double[]{x, y};
    }

    public static List<double[]> region(double[]... points) {
        return Arrays.asList(points);
    }

    @SafeVarargs
    public static Polygon polygon(List<double[]>... regions) {
        return polygon(false, regions);
    }

    @SafeVarargs
    public static Polygon polygon(boolean inverted, List<double[]>... regions) {
        return new Polygon(Arrays.asList(regions), inverted);
    }

    private PolyBoolHelper() {
    }
}
