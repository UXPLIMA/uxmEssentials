package com.uxplima.uxmessentials.presence.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The pure anti-machine activity filter: given a configured set of {@link ActivityKind}s an operator wants
 * ignored, decides whether a given kind should reset a player's AFK idle clock. An ignored kind does
 * <em>not</em> count as activity, so a machine that only ever produces that kind (an AFK fish farm reeling the
 * rod, a player nudged below the move threshold by a pushing piston) still drifts idle and goes AFK.
 *
 * <p>The default ignore set is empty, so out of the box every recognised kind counts as activity and behaviour
 * is unchanged. An operator opts the filter in by naming kinds in {@code presence.conf}. {@link ActivityKind#MOVE}
 * is always treated as activity even if listed, because a real block hop is the irreducible "the player moved"
 * signal; sub-threshold movement is reported by the adapter as {@link ActivityKind#ROTATE} instead, which the
 * operator may legitimately ignore.
 */
public final class ActivityPolicy {

    private final Set<ActivityKind> ignored;

    private ActivityPolicy(Set<ActivityKind> ignored) {
        this.ignored = EnumSet.copyOf(Objects.requireNonNull(ignored, "ignored"));
        this.ignored.remove(ActivityKind.MOVE);
    }

    /** A policy that ignores exactly the given kinds (a real {@link ActivityKind#MOVE} is never ignored). */
    public static ActivityPolicy ignoring(Set<ActivityKind> ignored) {
        return new ActivityPolicy(ignored);
    }

    /** The default policy: nothing ignored, so every recognised kind resets the idle clock. */
    public static ActivityPolicy countingEverything() {
        return new ActivityPolicy(EnumSet.noneOf(ActivityKind.class));
    }

    /** Whether {@code kind} resets the AFK idle clock: true unless the operator has ignored it. */
    public boolean countsAsActivity(ActivityKind kind) {
        Objects.requireNonNull(kind, "kind");
        return !ignored.contains(kind);
    }

    /** Whether {@code kind} is on the ignore list (the inverse of {@link #countsAsActivity}). */
    public boolean isIgnored(ActivityKind kind) {
        Objects.requireNonNull(kind, "kind");
        return ignored.contains(kind);
    }
}
