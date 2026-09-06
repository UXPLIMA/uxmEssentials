package com.uxplima.uxmessentials.communication.domain;

import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;

/**
 * The immutable rotation position of the announcer, the index it last broadcast, or none before the first tick.
 * It owns the no-immediate-repeat rule: given the number of announcements and the {@link Ordering},
 * {@link #advance} returns the next index and a new cursor, never repeating the previous index when more than one
 * announcement is configured.
 *
 * <p>Sequential rotation simply steps to {@code (last + 1) % count}, wrapping after the last entry. Random
 * rotation draws an index through the injected {@code randomBound} function (a bounded RNG passed in by the use
 * case so the domain stays pure and deterministic under test) and, when the draw lands on the previous index and
 * more than one entry exists, nudges it forward by one so the same announcement is never shown twice running. A
 * single-entry config collapses both to "index 0 every tick": there is no other entry to avoid.
 *
 * @param last the index broadcast on the previous tick, empty before the first tick
 */
public record AnnouncerCursor(OptionalInt last) {

    /** The starting cursor: nothing broadcast yet. */
    public static AnnouncerCursor start() {
        return new AnnouncerCursor(OptionalInt.empty());
    }

    /**
     * The next index to broadcast across {@code count} entries under {@code ordering}, paired with the cursor to
     * carry into the following tick. {@code randomBound} is a bounded random source (a function from an exclusive
     * bound to a value in {@code [0, bound)}) consulted only for {@link Ordering#RANDOM}; it is never called for
     * sequential ordering. With fewer than two entries the no-repeat rule is vacuous and the single available
     * index is returned.
     */
    public Selection advance(int count, Ordering ordering, IntUnaryOperator randomBound) {
        if (count <= 0) {
            throw new IllegalArgumentException("cannot advance over an empty selection set");
        }
        if (count == 1) {
            return select(0);
        }
        return ordering == Ordering.SEQUENTIAL ? nextSequential(count) : nextRandom(count, randomBound);
    }

    private Selection nextSequential(int count) {
        int next = last.isPresent() ? (last.getAsInt() + 1) % count : 0;
        return select(next);
    }

    private Selection nextRandom(int count, IntUnaryOperator randomBound) {
        int drawn = randomBound.applyAsInt(count);
        if (last.isPresent() && drawn == last.getAsInt()) {
            drawn = (drawn + 1) % count;
        }
        return select(drawn);
    }

    private Selection select(int index) {
        return new Selection(index, new AnnouncerCursor(OptionalInt.of(index)));
    }

    /**
     * The outcome of advancing the cursor: the chosen {@code index} and the {@code cursor} to retain for the next
     * tick.
     *
     * @param index the entry index to broadcast this tick
     * @param cursor the cursor to carry into the next tick
     */
    public record Selection(int index, AnnouncerCursor cursor) {}
}
