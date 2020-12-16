package com.menecats.polybool;

import static com.menecats.polybool.helpers.PolyBoolHelper.point;

public class ExperimentalEpsilon extends Epsilon {
    public ExperimentalEpsilon() {
        super();
    }

    public ExperimentalEpsilon(double eps) {
        super(eps);
    }

    @Override
    public boolean pointAboveOrOnLine(double[] pt, double[] left, double[] right) {
        final double Ax = left[0];
        final double Ay = left[1];
        final double Bx = right[0];
        final double By = right[1];
        final double Cx = pt[0];
        final double Cy = pt[1];
        final double ABx = Bx - Ax;
        final double ABy = By - Ay;
        final double AB = Math.sqrt(ABx * ABx + ABy * ABy);
        // algebraic distance of 'pt' to ('left', 'right') line is:
        // [ABx * (Cy - Ay) - ABy * (Cx - Ax)] / AB
        return ABx * (Cy - Ay) - ABy * (Cx - Ax) >= -eps * AB;
    }

    @Override
    public boolean pointBetween(double[] p, double[] left, double[] right) {
        // p must be collinear with left->right
        // returns false if p == left, p == right, or left == right
        if (pointsSame(p, left) || pointsSame(p, right)) return false;
        final double d_py_ly = p[1] - left[1];
        final double d_rx_lx = right[0] - left[0];
        final double d_px_lx = p[0] - left[0];
        final double d_ry_ly = right[1] - left[1];

        double dot = d_px_lx * d_rx_lx + d_py_ly * d_ry_ly;
        // dot < 0 is p is to the left of 'left'
        if (dot < 0) return false;
        final double sqlen = d_rx_lx * d_rx_lx + d_ry_ly * d_ry_ly;
        // dot <= sqlen is p is to the left of 'right'
        return dot <= sqlen;
    }

    @Override
    public boolean pointsCollinear(double[] pt1, double[] pt2, double[] pt3) {
        // does pt1->pt2->pt3 make a straight line?
        // essentially this is just checking to see if the slope(pt1->pt2) === slope(pt2->pt3)
        // if slopes are equal, then they must be collinear, because they share pt2
        final double dx1 = pt1[0] - pt2[0];
        final double dy1 = pt1[1] - pt2[1];
        final double dx2 = pt2[0] - pt3[0];
        final double dy2 = pt2[1] - pt3[1];
        final double n1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
        final double n2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
        // Assuming det(u, v) = 0, we have:
        // |det(u + u_err, v + v_err)| = |det(u + u_err, v + v_err) - det(u,v)|
        // =|det(u, v_err) + det(u_err. v) + det(u_err, v_err)|
        // <= |det(u, v_err)| + |det(u_err, v)| + |det(u_err, v_err)|
        // <= N(u)N(v_err) + N(u_err)N(v) + N(u_err)N(v_err)
        // <= eps * (N(u) + N(v) + eps)
        // We have N(u) ~ N(u + u_err) and N(v) ~ N(v + v_err).
        // Assuming eps << N(u) and eps << N(v), we end with:
        // |det(u + u_err, v + v_err)| <= eps * (N(u + u_err) + N(v + v_err))
        return Math.abs(dx1 * dy2 - dx2 * dy1) <= eps * (n1 + n2);
    }

    @Override
    public EpsilonIntersectionResult linesIntersect(double[] a0, double[] a1, double[] b0, double[] b1) {
        // returns false if the lines are coincident (e.g., parallel or on top of each other)
        //
        // returns an object if the lines intersect:
        //   {
        //     pt: [x, y],    where the intersection point is at
        //     alongA: where intersection point is along A,
        //     alongB: where intersection point is along B
        //   }
        //
        //  alongA and alongB will each be one of: -2, -1, 0, 1, 2
        //
        //  with the following meaning:
        //
        //    -2   intersection point is before segment's first point
        //    -1   intersection point is directly on segment's first point
        //     0   intersection point is between segment's first and second points (exclusive)
        //     1   intersection point is directly on segment's second point
        //     2   intersection point is after segment's second point
        final double adx = a1[0] - a0[0];
        final double ady = a1[1] - a0[1];
        final double bdx = b1[0] - b0[0];
        final double bdy = b1[1] - b0[1];

        final double axb = adx * bdy - ady * bdx;
        final double n1 = Math.sqrt(adx * adx + ady * ady);
        final double n2 = Math.sqrt(bdx * bdx + bdy * bdy);
        if (Math.abs(axb) <= eps * (n1 + n2))
            return null; // lines are coincident

        final double dx = a0[0] - b0[0];
        final double dy = a0[1] - b0[1];

        final double A = (bdx * dy - bdy * dx) / axb;
        final double B = (adx * dy - ady * dx) / axb;
        final double[] pt = point(
                a0[0] + A * adx,
                a0[1] + A * ady
        );

        final EpsilonIntersectionResult ret = new EpsilonIntersectionResult();
        ret.pt = pt;

        // categorize where intersection point is along A and B
        if (pointsSame(pt, a0))
            ret.alongA = -1;
        else if (pointsSame(pt, a1))
            ret.alongA = 1;
        else if (A < 0)
            ret.alongA = -2;
        else if (A > 1)
            ret.alongA = 2;

        if (pointsSame(pt, b0))
            ret.alongB = -1;
        else if (pointsSame(pt, b1))
            ret.alongB = 1;
        else if (B < 0)
            ret.alongB = -2;
        else if (B > 1)
            ret.alongB = 2;

        return ret;
    }
}
