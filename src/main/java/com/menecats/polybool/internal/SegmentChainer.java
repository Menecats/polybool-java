package com.menecats.polybool.internal;

import com.menecats.polybool.Epsilon;
import com.menecats.polybool.models.Segment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SegmentChainer {
    private static class SegmentChainerMatch {
        int index;
        boolean matches_head;
        boolean matches_pt1;

        public SegmentChainerMatch() {
            this(0, false, false);
        }

        public SegmentChainerMatch(int index, boolean matches_head, boolean matches_pt1) {
            this.index = index;
            this.matches_head = matches_head;
            this.matches_pt1 = matches_pt1;
        }
    }

    private interface TriPredicate<T, U, V> {
        boolean apply(T t, U u, V v);
    }

    public static List<List<double[]>> chain(List<Segment> segments, Epsilon eps) {
        List<List<double[]>> chains = new ArrayList<>();
        List<List<double[]>> regions = new ArrayList<>();

        for (Segment seg : segments) {
            double[] pt1 = seg.start;
            double[] pt2 = seg.end;
            if (eps.pointsSame(pt1, pt2)) {
                System.err.println("PolyBool: Warning: Zero-length segment detected; your epsilon is probably too small or too large");
                return null;
            }

            // search for two chains that this segment matches
            final SegmentChainerMatch first_match = new SegmentChainerMatch();
            final SegmentChainerMatch second_match = new SegmentChainerMatch();

            final SegmentChainerMatch[] next_match = {first_match};

            final TriPredicate<Integer, Boolean, Boolean> setMatch = (index, matches_head, matches_pt1) -> {
                // return true if we've matched twice
                next_match[0].index = index;
                next_match[0].matches_head = matches_head;
                next_match[0].matches_pt1 = matches_pt1;

                if (next_match[0] == first_match) {
                    next_match[0] = second_match;
                    return false;
                }
                next_match[0] = null;
                return true; // we've matched twice, we're done here
            };

            for (int i = 0; i < chains.size(); i++) {
                List<double[]> chain = chains.get(i);
                double[] head = chain.get(0);
                double[] tail = chain.get(chain.size() - 1);

                if (eps.pointsSame(head, pt1)) {
                    if (setMatch.apply(i, true, true))
                        break;
                } else if (eps.pointsSame(head, pt2)) {
                    if (setMatch.apply(i, true, false))
                        break;
                } else if (eps.pointsSame(tail, pt1)) {
                    if (setMatch.apply(i, false, true))
                        break;
                } else if (eps.pointsSame(tail, pt2)) {
                    if (setMatch.apply(i, false, false))
                        break;
                }
            }

            if (next_match[0] == first_match) {
                List<double[]> newChain = new ArrayList<>();
                newChain.add(pt1);
                newChain.add(pt2);

                // we didn't match anything, so create a new chain
                chains.add(newChain);
                continue;
            }

            if (next_match[0] == second_match) {
                // we matched a single chain

                // add the other point to the apporpriate end, and check to see if we've closed the
                // chain into a loop

                int index = first_match.index;
                double[] pt = first_match.matches_pt1 ? pt2 : pt1; // if we matched pt1, then we add pt2, etc
                boolean addToHead = first_match.matches_head; // if we matched at head, then add to the head

                List<double[]> chain = chains.get(index);
                double[] grow = addToHead ? chain.get(0) : chain.get(chain.size() - 1);
                double[] grow2 = addToHead ? chain.get(1) : chain.get(chain.size() - 2);
                double[] oppo = addToHead ? chain.get(chain.size() - 1) : chain.get(0);
                double[] oppo2 = addToHead ? chain.get(chain.size() - 2) : chain.get(1);

                if (eps.pointsCollinear(grow2, grow, pt)) {
                    // grow isn't needed because it's directly between grow2 and pt:
                    // grow2 ---grow---> pt
                    if (addToHead) {
                        chain.remove(0);
                    } else {
                        chain.remove(chain.size() - 1);
                    }
                    grow = grow2; // old grow is gone... new grow is what grow2 was
                }

                if (eps.pointsSame(oppo, pt)) {
                    // we're closing the loop, so remove chain from chains
                    chains.remove(index);

                    if (eps.pointsCollinear(oppo2, oppo, grow)) {
                        // oppo isn't needed because it's directly between oppo2 and grow:
                        // oppo2 ---oppo--->grow
                        if (addToHead) {
                            chain.remove(chain.size() - 1);
                        } else {
                            chain.remove(0);
                        }
                    }

                    // we have a closed chain!
                    regions.add(chain);
                    continue;
                }

                // not closing a loop, so just add it to the apporpriate side
                if (addToHead) {
                    chain.add(0, pt);
                } else {
                    chain.add(pt);
                }
                continue;
            }

            // otherwise, we matched two chains, so we need to combine those chains together

            Consumer<Integer> reverseChain = index -> Collections.reverse(chains.get(index));
            BiConsumer<Integer, Integer> appendChain = (index1, index2) -> {
                // index1 gets index2 appended to it, and index2 is removed
                List<double[]> chain1 = chains.get(index1);
                List<double[]> chain2 = chains.get(index2);
                double[] tail = chain1.get(chain1.size() - 1);
                double[] tail2 = chain1.get(chain1.size() - 2);
                double[] head = chain2.get(0);
                double[] head2 = chain2.get(1);

                if (eps.pointsCollinear(tail2, tail, head)) {
                    // tail isn't needed because it's directly between tail2 and head
                    // tail2 ---tail---> head
                    chain1.remove(chain1.size() - 1);
                    tail = tail2; // old tail is gone... new tail is what tail2 was
                }

                if (eps.pointsCollinear(tail, head, head2)) {
                    // head isn't needed because it's directly between tail and head2
                    // tail ---head---> head2
                    chain2.remove(0);
                }

                List<double[]> concatenated = new ArrayList<>();
                concatenated.addAll(chain1);
                concatenated.addAll(chain2);
                chains.set(index1, concatenated);
                chains.remove((int) index2);
            };

            int F = first_match.index;
            int S = second_match.index;

            boolean reverseF = chains.get(F).size() < chains.get(S).size(); // reverse the shorter chain, if needed
            if (first_match.matches_head) {
                if (second_match.matches_head) {
                    if (reverseF) {
                        // <<<< F <<<< --- >>>> S >>>>
                        reverseChain.accept(F);
                        // >>>> F >>>> --- >>>> S >>>>
                        appendChain.accept(F, S);
                    } else {
                        // <<<< F <<<< --- >>>> S >>>>
                        reverseChain.accept(S);
                        // <<<< F <<<< --- <<<< S <<<<   logically same as:
                        // >>>> S >>>> --- >>>> F >>>>
                        appendChain.accept(S, F);
                    }
                } else {
                    // <<<< F <<<< --- <<<< S <<<<   logically same as:
                    // >>>> S >>>> --- >>>> F >>>>
                    appendChain.accept(S, F);
                }
            } else {
                if (second_match.matches_head) {
                    // >>>> F >>>> --- >>>> S >>>>
                    appendChain.accept(F, S);
                } else {
                    if (reverseF) {
                        // >>>> F >>>> --- <<<< S <<<<
                        reverseChain.accept(F);
                        // <<<< F <<<< --- <<<< S <<<<   logically same as:
                        // >>>> S >>>> --- >>>> F >>>>
                        appendChain.accept(S, F);
                    } else {
                        // >>>> F >>>> --- <<<< S <<<<
                        reverseChain.accept(S);
                        // >>>> F >>>> --- >>>> S >>>>
                        appendChain.accept(F, S);
                    }
                }
            }
        }

        return regions;
    }
}