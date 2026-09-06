package com.uxplima.uxmessentials.communication.domain;

/**
 * How a selector walks a list of templates or announcer lines.
 *
 * <p>{@link #SEQUENTIAL} returns the lines in author order, wrapping back to the first after the last, the
 * predictable rotation an operator uses for an ordered set of tips. {@link #RANDOM} picks a line at random; the
 * announcer pairs this with a no-immediate-repeat rule so the same line is never shown twice in a row when more
 * than one line is configured. A single-element list collapses both orderings to "always that one line".
 */
public enum Ordering {

    /** Walk the list in author order, wrapping after the last element. */
    SEQUENTIAL,

    /** Pick at random; the announcer additionally forbids an immediate repeat. */
    RANDOM
}
