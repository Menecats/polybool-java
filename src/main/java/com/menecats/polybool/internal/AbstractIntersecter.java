package com.menecats.polybool.internal;

import com.menecats.polybool.Epsilon;
import com.menecats.polybool.models.Segment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractIntersecter {
    protected static class IntersecterContent {
        boolean isStart;
        double[] pt;
        Segment seg;
        boolean primary;
        LinkedList<IntersecterContent> other;
        LinkedList<LinkedList<IntersecterContent>> status;
    }

    protected final Epsilon eps;

    private final boolean selfIntersection;
    private final LinkedList<IntersecterContent> event_root = LinkedList.create();

    AbstractIntersecter(boolean selfIntersection, Epsilon eps) {
        this.eps = eps;
        this.selfIntersection = selfIntersection;
    }

    protected Segment segmentNew(double[] start, double[] end) {
        return new Segment(start, end);
    }

    protected Segment segmentCopy(double[] start, double[] end, Segment seg) {
        return new Segment(start, end, new Segment.SegmentFill(seg.myFill.above, seg.myFill.below));
    }


    private int eventCompare(boolean p1_isStart, double[] p1_1, double[] p1_2,
                             boolean p2_isStart, double[] p2_1, double[] p2_2) {

        // compare the selected points first
        int comp = this.eps.pointsCompare(p1_1, p2_1);
        if (comp != 0)
            return comp;
        // the selected points are the same

        if (this.eps.pointsSame(p1_2, p2_2)) // if the non-selected points are the same too...
            return 0; // then the segments are equal

        if (p1_isStart != p2_isStart) // if one is a start and the other isn't...
            return p1_isStart ? 1 : -1; // favor the one that isn't the start

        // otherwise, we'll have to calculate which one is below the other manually
        return this.eps.pointAboveOrOnLine(p1_2,
                p2_isStart ? p2_1 : p2_2, // order matters
                p2_isStart ? p2_2 : p2_1
        ) ? 1 : -1;
    }

    private void eventAdd(LinkedList<IntersecterContent> ev, double[] other_pt) {
        this.event_root.insertBefore(ev, (here) -> {
            // should ev be inserted before here?
            int comp = this.eventCompare(
                    ev.getContent().isStart, ev.getContent().pt, other_pt,
                    here.getContent().isStart, here.getContent().pt, here.getContent().other.getContent().pt
            );
            return comp < 0;
        });
    }

    private LinkedList<IntersecterContent> eventAddSegmentStart(Segment seg, boolean primary) {
        IntersecterContent content = new IntersecterContent();
        content.isStart = true;
        content.pt = seg.start;
        content.seg = seg;
        content.primary = primary;

        LinkedList<IntersecterContent> ev_start = LinkedList.node(content);
        this.eventAdd(ev_start, seg.end);
        return ev_start;
    }

    private void eventAddSegmentEnd(LinkedList<IntersecterContent> ev_start, Segment seg, boolean primary) {
        IntersecterContent content = new IntersecterContent();
        content.isStart = false;
        content.pt = seg.end;
        content.seg = seg;
        content.primary = primary;
        content.other = ev_start;

        LinkedList<IntersecterContent> ev_end = LinkedList.node(content);
        ev_start.getContent().other = ev_end;
        this.eventAdd(ev_end, ev_start.getContent().pt);
    }

    protected LinkedList<IntersecterContent> eventAddSegment(Segment seg, boolean primary) {
        LinkedList<IntersecterContent> ev_start = this.eventAddSegmentStart(seg, primary);
        this.eventAddSegmentEnd(ev_start, seg, primary);
        return ev_start;
    }

    private void eventUpdateEnd(LinkedList<IntersecterContent> ev, double[] end) {
        // slides an end backwards
        //   (start)------------(end)    to:
        //   (start)---(end)

        ev.getContent().other.remove();
        ev.getContent().seg.end = end;
        ev.getContent().other.getContent().pt = end;
        this.eventAdd(ev.getContent().other, ev.getContent().pt);
    }

    private LinkedList<IntersecterContent> eventDivide(LinkedList<IntersecterContent> ev, double[] pt) {
        Segment ns = this.segmentCopy(pt, ev.getContent().seg.end, ev.getContent().seg);
        this.eventUpdateEnd(ev, pt);
        return this.eventAddSegment(ns, ev.getContent().primary);
    }

    protected List<Segment> baseCalculate(boolean primaryPolyInverted, boolean secondaryPolyInverted) {
        // if selfIntersection is true then there is no secondary polygon, so that isn't used

        //
        // status logic
        //

        LinkedList<LinkedList<IntersecterContent>> status_root = LinkedList.create();

        BiFunction<LinkedList<IntersecterContent>, LinkedList<IntersecterContent>, Integer> statusCompare = (ev1, ev2) -> {
            double[] a1 = ev1.getContent().seg.start;
            double[] a2 = ev1.getContent().seg.end;
            double[] b1 = ev2.getContent().seg.start;
            double[] b2 = ev2.getContent().seg.end;

            if (this.eps.pointsCollinear(a1, b1, b2)) {
                if (this.eps.pointsCollinear(a2, b1, b2))
                    return 1;//eventCompare(true, a1, a2, true, b1, b2);
                return this.eps.pointAboveOrOnLine(a2, b1, b2) ? 1 : -1;
            }
            return this.eps.pointAboveOrOnLine(a1, b1, b2) ? 1 : -1;
        };

        final Function<LinkedList<IntersecterContent>, LinkedList.TransitionResult<LinkedList<IntersecterContent>>> statusFindSurrounding = (ev) -> status_root
                .findTransition((here) -> {
                    int comp = statusCompare.apply(ev, here.getContent());
                    return comp > 0;
                });

        final BiFunction<LinkedList<IntersecterContent>, LinkedList<IntersecterContent>, LinkedList<IntersecterContent>> checkIntersection = (ev1, ev2) -> {
            // returns the segment equal to ev1, or false if nothing equal

            final Segment seg1 = ev1.getContent().seg;
            final Segment seg2 = ev2.getContent().seg;
            final double[] a1 = seg1.start;
            final double[] a2 = seg1.end;
            final double[] b1 = seg2.start;
            final double[] b2 = seg2.end;

            final Epsilon.EpsilonIntersectionResult i = this.eps.linesIntersect(a1, a2, b1, b2);

            if (i == null) {
                // segments are parallel or coincident

                // if points aren't collinear, then the segments are parallel, so no intersections
                if (!this.eps.pointsCollinear(a1, a2, b1))
                    return null;
                // otherwise, segments are on top of each other somehow (aka coincident)

                if (this.eps.pointsSame(a1, b2) || this.eps.pointsSame(a2, b1))
                    return null; // segments touch at endpoints... no intersection

                final boolean a1_equ_b1 = this.eps.pointsSame(a1, b1);
                final boolean a2_equ_b2 = this.eps.pointsSame(a2, b2);

                if (a1_equ_b1 && a2_equ_b2)
                    return ev2; // segments are exactly equal

                final boolean a1_between = !a1_equ_b1 && this.eps.pointBetween(a1, b1, b2);
                final boolean a2_between = !a2_equ_b2 && this.eps.pointBetween(a2, b1, b2);

                // handy for debugging:
                // buildLog.log({
                //	a1_equ_b1: a1_equ_b1,
                //	a2_equ_b2: a2_equ_b2,
                //	a1_between: a1_between,
                //	a2_between: a2_between
                // });

                if (a1_equ_b1) {
                    if (a2_between) {
                        //  (a1)---(a2)
                        //  (b1)----------(b2)
                        this.eventDivide(ev2, a2);
                    } else {
                        //  (a1)----------(a2)
                        //  (b1)---(b2)
                        this.eventDivide(ev1, b2);
                    }
                    return ev2;
                } else if (a1_between) {
                    if (!a2_equ_b2) {
                        // make a2 equal to b2
                        if (a2_between) {
                            //         (a1)---(a2)
                            //  (b1)-----------------(b2)
                            this.eventDivide(ev2, a2);
                        } else {
                            //         (a1)----------(a2)
                            //  (b1)----------(b2)
                            this.eventDivide(ev1, b2);
                        }
                    }

                    //         (a1)---(a2)
                    //  (b1)----------(b2)
                    this.eventDivide(ev2, a1);
                }
            } else {
                // otherwise, lines intersect at i.pt, which may or may not be between the endpoints

                // is A divided between its endpoints? (exclusive)
                if (i.alongA == 0) {
                    if (i.alongB == -1) // yes, at exactly b1
                        this.eventDivide(ev1, b1);
                    else if (i.alongB == 0) // yes, somewhere between B's endpoints
                        this.eventDivide(ev1, i.pt);
                    else if (i.alongB == 1) // yes, at exactly b2
                        this.eventDivide(ev1, b2);
                }

                // is B divided between its endpoints? (exclusive)
                if (i.alongB == 0) {
                    if (i.alongA == -1) // yes, at exactly a1
                        this.eventDivide(ev2, a1);
                    else if (i.alongA == 0) // yes, somewhere between A's endpoints (exclusive)
                        this.eventDivide(ev2, i.pt);
                    else if (i.alongA == 1) // yes, at exactly a2
                        this.eventDivide(ev2, a2);
                }
            }
            return null;
        };

        //
        // main event loop
        //
        List<Segment> segments = new ArrayList<>();
        while (!this.event_root.isEmpty()) {
            LinkedList<IntersecterContent> ev = this.event_root.getHead();

            if (ev.getContent().isStart) {
                LinkedList.TransitionResult<LinkedList<IntersecterContent>> surrounding = statusFindSurrounding.apply(ev);
                LinkedList<IntersecterContent> above = surrounding.before != null ? surrounding.before.getContent() : null;
                LinkedList<IntersecterContent> below = surrounding.after != null ? surrounding.after.getContent() : null;

                Supplier<LinkedList<IntersecterContent>> checkBothIntersections = () -> {
                    if (above != null) {
                        LinkedList<IntersecterContent> eve = checkIntersection.apply(ev, above);
                        if (eve != null)
                            return eve;
                    }
                    if (below != null)
                        return checkIntersection.apply(ev, below);
                    return null;
                };

                LinkedList<IntersecterContent> eve = checkBothIntersections.get();
                if (eve != null) {
                    // ev and eve are equal
                    // we'll keep eve and throw away ev

                    // merge ev.seg's fill information into eve.seg

                    if (this.selfIntersection) {
                        boolean toggle; // are we a toggling edge?
                        if (ev.getContent().seg.myFill.below == null)
                            toggle = true;
                        else
                            toggle = ev.getContent().seg.myFill.above != ev.getContent().seg.myFill.below;

                        // merge two segments that belong to the same polygon
                        // think of this as sandwiching two segments together, where `eve.seg` is
                        // the bottom -- this will cause the above fill flag to toggle
                        if (toggle)
                            eve.getContent().seg.myFill.above = !eve.getContent().seg.myFill.above;
                    } else {
                        // merge two segments that belong to different polygons
                        // each segment has distinct knowledge, so no special logic is needed
                        // note that this can only happen once per segment in this phase, because we
                        // are guaranteed that all self-intersections are gone
                        eve.getContent().seg.otherFill = ev.getContent().seg.myFill;
                    }

                    ev.getContent().other.remove();
                    ev.remove();
                }

                if (this.event_root.getHead() != ev) {
                    // something was inserted before us in the event queue, so loop back around and
                    // process it before continuing
                    continue;
                }

                //
                // calculate fill flags
                //
                if (this.selfIntersection) {
                    boolean toggle; // are we a toggling edge?
                    if (ev.getContent().seg.myFill.below == null) // if we are a new segment...
                        toggle = true; // then we toggle
                    else // we are a segment that has previous knowledge from a division
                        toggle = ev.getContent().seg.myFill.above != ev.getContent().seg.myFill.below; // calculate toggle

                    // next, calculate whether we are filled below us
                    if (below == null) { // if nothing is below us...
                        // we are filled below us if the polygon is inverted
                        ev.getContent().seg.myFill.below = primaryPolyInverted;
                    } else {
                        // otherwise, we know the answer -- it's the same if whatever is below
                        // us is filled above it
                        ev.getContent().seg.myFill.below = below.getContent().seg.myFill.above;
                    }

                    // since now we know if we're filled below us, we can calculate whether
                    // we're filled above us by applying toggle to whatever is below us
                    if (toggle)
                        ev.getContent().seg.myFill.above = !ev.getContent().seg.myFill.below;
                    else
                        ev.getContent().seg.myFill.above = ev.getContent().seg.myFill.below;
                } else {
                    // now we fill in any missing transition information, since we are all-knowing
                    // at this point

                    if (ev.getContent().seg.otherFill == null) {
                        // if we don't have other information, then we need to figure out if we're
                        // inside the other polygon
                        boolean inside;
                        if (below == null) {
                            // if nothing is below us, then we're inside if the other polygon is
                            // inverted
                            inside = ev.getContent().primary
                                    ? secondaryPolyInverted
                                    : primaryPolyInverted;
                        } else { // otherwise, something is below us
                            // so copy the below segment's other polygon's above
                            if (ev.getContent().primary == below.getContent().primary)
                                inside = below.getContent().seg.otherFill.above;
                            else
                                inside = below.getContent().seg.myFill.above;
                        }
                        ev.getContent().seg.otherFill = new Segment.SegmentFill(inside, inside);
                    }
                }

                // insert the status and remember it for later removal
                ev.getContent().other.getContent().status = surrounding.insert.apply(LinkedList.node(ev));
            } else {
                LinkedList<LinkedList<IntersecterContent>> st = ev.getContent().status;

                if (st == null) {
                    throw new RuntimeException("PolyBool: Zero-length segment detected; your epsilon is probably too small or too large");
                }

                // removing the status will create two new adjacent edges, so we'll need to check
                // for those
                if (status_root.exists(st.getPrev()) && status_root.exists(st.getNext()))
                    checkIntersection.apply(st.getPrev().getContent(), st.getNext().getContent());

                // remove the status
                st.remove();

                // if we've reached this point, we've calculated everything there is to know, so
                // save the segment for reporting
                if (!ev.getContent().primary) {
                    // make sure `seg.myFill` actually points to the primary polygon though
                    Segment.SegmentFill s = ev.getContent().seg.myFill;
                    ev.getContent().seg.myFill = ev.getContent().seg.otherFill;
                    ev.getContent().seg.otherFill = s;
                }
                segments.add(ev.getContent().seg);
            }

            // remove the event and continue
            this.event_root.getHead().remove();
        }

        return segments;
    }
}
