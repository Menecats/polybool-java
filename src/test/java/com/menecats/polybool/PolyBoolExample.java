package com.menecats.polybool;

import com.menecats.polybool.models.Polygon;

import static com.menecats.polybool.helpers.PolyBoolHelper.*;

public class PolyBoolExample {
    public static void main(String[] args) {
        Epsilon eps = epsilon();

        Polygon intersection = PolyBool.intersect(
                eps,
                polygon(
                        region(
                                point(50, 50),
                                point(150, 150),
                                point(190, 50)
                        ),
                        region(
                                point(130, 50),
                                point(290, 150),
                                point(290, 50)
                        )
                ),
                polygon(
                        region(
                                point(110, 20),
                                point(110, 110),
                                point(20, 20)
                        ),
                        region(
                                point(130, 170),
                                point(130, 20),
                                point(260, 20),
                                point(260, 170)
                        )
                )
        );

        System.out.println(intersection);
        // Polygon { inverted: false, regions: [
        //     [[50.0, 50.0], [110.0, 50.0], [110.0, 110.0]],
        //     [[178.0, 80.0], [130.0, 50.0], [130.0, 130.0], [150.0, 150.0]],
        //     [[178.0, 80.0], [190.0, 50.0], [260.0, 50.0], [260.0, 131.25]]
        // ]}
    }
}
