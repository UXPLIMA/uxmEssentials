package com.uxplima.uxmessentials.villagers.domain;

/**
 * The follow-range rule: given whether a following villager and its owner are in the same world and the squared
 * distance between them, decide whether the villager should pathfind toward the owner this tick or hold still. This
 * is the whole of the follow-movement decision, the live {@code Villager}, its pathfinder, and the owner's live
 * {@code Location} are adapter concerns; the domain owns only the range comparison so it can be unit-tested without
 * Bukkit.
 *
 * <p>The villager moves only while it is in the owner's world and within {@code range} blocks of them; once the
 * owner steps beyond the range (or into another world) the villager stops rather than sprinting after them across
 * the map, and it resumes on the next tick that finds the owner back in range. The comparison works in squared
 * distance so the adapter never takes a square root. {@link #rangeSquared()} is compared against the squared
 * distance Bukkit hands back from {@code Location#distanceSquared}.
 *
 * @param range how far, in blocks, the owner may be from the villager before it stops following; strictly positive
 */
public record FollowRange(double range) {

    public FollowRange {
        if (!(range > 0) || !Double.isFinite(range)) {
            throw new IllegalArgumentException("follow range must be a strictly positive, finite number: " + range);
        }
    }

    /** The follow range squared, for comparison against a {@code Location#distanceSquared} without a square root. */
    public double rangeSquared() {
        return range * range;
    }

    /**
     * Whether a following villager should pathfind toward its owner this tick. It moves only when the two are in the
     * same world ({@code sameWorld}) and no more than {@link #range} blocks apart; otherwise it holds still.
     *
     * @param sameWorld whether the villager and its owner are currently in the same world
     * @param distanceSquared the squared distance between them; must not be negative
     */
    public boolean shouldMove(boolean sameWorld, double distanceSquared) {
        if (distanceSquared < 0) {
            throw new IllegalArgumentException("distanceSquared must not be negative: " + distanceSquared);
        }
        return sameWorld && distanceSquared <= rangeSquared();
    }
}
