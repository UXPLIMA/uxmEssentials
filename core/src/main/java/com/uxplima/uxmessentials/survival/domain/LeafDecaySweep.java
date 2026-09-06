package com.uxplima.uxmessentials.survival.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The pure fast-leaf-decay algorithm: given the coordinate of a just-broken log, decide which nearby leaves have lost
 * their support and should decay now rather than waiting for vanilla's random-tick sweep. It walks the cube of
 * half-width {@code radius} around the origin and returns every leaf coordinate that the caller classifies as
 * <em>decayable</em> (a non-persistent leaf) and that has no log within {@code supportDistance}, the block distance
 * within which a standing trunk still holds its leaves, so felling one log of a tree whose trunk still stands does not
 * strip the leaves that trunk supports.
 *
 * <p>Pure: it walks abstract {@link BlockPos} coordinates through two caller-supplied predicates ({@code isLog},
 * {@code isDecayableLeaf}), so it is unit-testable on a plain in-memory grid with no Bukkit in sight. The adapter
 * supplies predicates that resolve each coordinate to a live block and read its material tag and (for a leaf) its
 * persistent flag. The result is bounded by {@code maxLeaves} so a huge canopy cannot return an unbounded list.
 */
public final class LeafDecaySweep {

    private final int radius;
    private final int supportDistance;
    private final int maxLeaves;

    /**
     * @param radius the half-width of the cube around the broken log that is swept for leaves; at least one
     * @param supportDistance the block distance within which a log keeps a leaf alive; at least one
     * @param maxLeaves the most leaf coordinates a single sweep returns; at least one
     */
    public LeafDecaySweep(int radius, int supportDistance, int maxLeaves) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1: " + radius);
        }
        if (supportDistance < 1) {
            throw new IllegalArgumentException("supportDistance must be at least 1: " + supportDistance);
        }
        if (maxLeaves < 1) {
            throw new IllegalArgumentException("maxLeaves must be at least 1: " + maxLeaves);
        }
        this.radius = radius;
        this.supportDistance = supportDistance;
        this.maxLeaves = maxLeaves;
    }

    /**
     * The leaf coordinates within {@code radius} of {@code origin} that should decay now: decayable leaves with no log
     * within {@code supportDistance}.
     *
     * @param origin the coordinate of the log that was just broken
     * @param isLog whether a coordinate currently holds a log
     * @param isDecayableLeaf whether a coordinate holds a leaf that may decay (a non-persistent leaf)
     * @return the unsupported leaf coordinates, at most {@code maxLeaves} of them
     */
    public List<BlockPos> unsupportedLeaves(
            BlockPos origin, Predicate<BlockPos> isLog, Predicate<BlockPos> isDecayableLeaf) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(isLog, "isLog");
        Objects.requireNonNull(isDecayableLeaf, "isDecayableLeaf");
        List<BlockPos> decaying = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isDecayableLeaf.test(pos) && !hasLogWithin(pos, isLog)) {
                        decaying.add(pos);
                        if (decaying.size() >= maxLeaves) {
                            return List.copyOf(decaying);
                        }
                    }
                }
            }
        }
        return List.copyOf(decaying);
    }

    /** Whether any log sits within {@code supportDistance} (on each axis) of {@code leaf}. */
    private boolean hasLogWithin(BlockPos leaf, Predicate<BlockPos> isLog) {
        for (int dx = -supportDistance; dx <= supportDistance; dx++) {
            for (int dy = -supportDistance; dy <= supportDistance; dy++) {
                for (int dz = -supportDistance; dz <= supportDistance; dz++) {
                    if (isLog.test(leaf.offset(dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
