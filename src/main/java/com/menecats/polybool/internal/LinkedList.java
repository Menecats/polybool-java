package com.menecats.polybool.internal;

import java.util.function.Function;
import java.util.function.Predicate;

public class LinkedList<T> {
    public static class TransitionResult<T> {
        public final LinkedList<T> before;
        public final LinkedList<T> after;
        public final Function<LinkedList<T>, LinkedList<T>> insert;

        public TransitionResult(final LinkedList<T> before,
                                final LinkedList<T> after,
                                final Function<LinkedList<T>, LinkedList<T>> insert) {

            this.before = before;
            this.after = after;
            this.insert = insert;
        }
    }

    public static <T> LinkedList<T> create() {
        return new LinkedList<>(true, null);
    }

    public static <T> LinkedList<T> node(T content) {
        return new LinkedList<>(false, content);
    }

    private LinkedList<T> prev;
    private LinkedList<T> next;

    private final T content;
    private final boolean root;

    private LinkedList(boolean root, T content) {
        this.root = root;
        this.content = content;
    }

    public boolean exists(LinkedList<T> node) {
        return node != null && node != this;
    }

    public boolean isEmpty() {
        return this.next == null;
    }

    public LinkedList<T> getHead() {
        return this.next;
    }

    public LinkedList<T> getPrev() {
        return prev;
    }

    public LinkedList<T> getNext() {
        return next;
    }

    public void insertBefore(LinkedList<T> node, Predicate<LinkedList<T>> check) {
        LinkedList<T> last = this;
        LinkedList<T> here = this.next;

        while (here != null && !here.root) {
            if (check.test(here)) {
                node.prev = here.prev;
                node.next = here;
                if (here.prev != null)
                    here.prev.next = node;
                here.prev = node;
                return;
            }
            last = here;
            here = here.next;
        }

        last.next = node;
        node.prev = last;
        node.next = null;
    }

    public TransitionResult<T> findTransition(Predicate<LinkedList<T>> check) {
        LinkedList<T> prev = this;
        LinkedList<T> here = this.next;

        while (here != null) {
            if (check.test(here)) break;

            prev = here;
            here = here.next;
        }

        final LinkedList<T> finalPrev = prev;
        final LinkedList<T> finalHere = here;

        return new TransitionResult<>(
                prev == this
                        ? null
                        : prev,
                here,
                node -> {
                    node.prev = finalPrev;
                    node.next = finalHere;
                    finalPrev.next = node;
                    if (finalHere != null)
                        finalHere.prev = node;

                    return node;
                }
        );
    }

    public void remove() {
        if (this.root) return;

        if (this.prev != null)
            this.prev.next = this.next;
        if (this.next != null)
            this.next.prev = this.prev;

        this.prev = null;
        this.next = null;
    }

    public T getContent() {
        return content;
    }
}

