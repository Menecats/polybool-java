package com.menecats.polybool.models;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Polygon {
    private List<List<double[]>> regions;
    private boolean inverted;

    public Polygon() {
        this(new ArrayList<>());
    }

    public Polygon(List<List<double[]>> regions) {
        this(regions, false);
    }

    public Polygon(List<List<double[]>> regions, boolean inverted) {
        this.regions = regions;
        this.inverted = inverted;
    }

    public List<List<double[]>> getRegions() {
        return regions;
    }

    public void setRegions(List<List<double[]>> regions) {
        this.regions = regions;
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    @Override
    public String toString() {
        return String.format(
                "Polygon { inverted: %s, regions: [\n\t%s\n]}",
                inverted,
                regions
                        .stream()
                        .map(region -> "[" + region
                                .stream()
                                .map(point -> String.format(
                                        "[%s, %s]",
                                        point[0],
                                        point[1]
                                ))
                                .collect(Collectors.joining(", ")) + "]"
                        )
                        .collect(Collectors.joining(",\n\t"))
        );
    }
}
