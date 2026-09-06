package com.uxplima.uxmessentials.survival.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A bounded breadth-first flood-fill over a 3D block grid, shared by tree-feller (the connected logs of a felled
 * tree) and veinminer (the connected blocks of an ore vein). Starting from an origin the caller has already accepted,
 * it visits every neighbour the {@code matches} predicate accepts, transitively, until it runs out of matching
 * neighbours or reaches {@code maxBlocks} visited coordinates: whichever comes first.
 *
 * <p>Connectivity is the full 26-neighbourhood (every one of the twenty-six coordinates that differ by at most one on
 * each axis), so a diagonal branch of a tree or a diagonal step of a vein stays connected. The shape both mechanics
 * expect. The origin is always the first element of the result and is never re-tested against the predicate: the
 * caller only starts a search once it has already decided the origin is a log or a configured ore, so a search with
 * no matching neighbour returns exactly {@code [origin]}.
 *
 * <p>Pure: it walks abstract {@link BlockPos} coordinates through a caller-supplied predicate, so it is unit-testable
 * on a plain in-memory grid with no Bukkit in sight. The adapter supplies a predicate that resolves each coordinate
 * to a live block and compares its material.
 */
public final class ConnectedBlockSearch {

    /** The upper bound on how many coordinates a single search returns, the origin included. */
    private final int maxBlocks;

    /**
     * @param maxBlocks the most coordinates a search returns, counting the origin; must be at least one
     */
    public ConnectedBlockSearch(int maxBlocks) {
        if (maxBlocks < 1) {
            throw new IllegalArgumentException("maxBlocks must be at least 1: " + maxBlocks);
        }
        this.maxBlocks = maxBlocks;
    }

    /**
     * Flood-fill from {@code origin} over every neighbour {@code matches} accepts, bounded by {@code maxBlocks}.
     *
     * @param origin the already-accepted starting coordinate, always first in the result
     * @param matches whether a neighbour coordinate belongs to the same connected group
     * @return the visited coordinates in breadth-first order, origin first, at most {@code maxBlocks} of them
     */
    public List<BlockPos> from(BlockPos origin, Predicate<BlockPos> matches) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(matches, "matches");
        List<BlockPos> visited = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        visited.add(origin);
        seen.add(origin);
        frontier.add(origin);
        while (!frontier.isEmpty() && visited.size() < maxBlocks) {
            expand(frontier.poll(), matches, visited, seen, frontier);
        }
        return List.copyOf(visited);
    }

    /** Test each not-yet-seen neighbour of {@code current}, enqueuing the ones that match until the cap is hit. */
    private void expand(
            BlockPos current,
            Predicate<BlockPos> matches,
            List<BlockPos> visited,
            Set<BlockPos> seen,
            Deque<BlockPos> frontier) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (visited.size() >= maxBlocks) {
                        return;
                    }
                    BlockPos neighbour = current.offset(dx, dy, dz);
                    if (seen.add(neighbour) && matches.test(neighbour)) {
                        visited.add(neighbour);
                        frontier.add(neighbour);
                    }
                }
            }
        }
    }
}
