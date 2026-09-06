package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link ConnectedBlockSearch} on a plain in-memory grid, no Bukkit, over the three behaviours both
 * tree-feller and veinminer lean on: a connected group is visited whole, the visit is capped at {@code maxBlocks},
 * and an origin with no matching neighbour returns just itself. Connectivity includes the diagonal (26-neighbour)
 * steps a branch or a diagonal vein needs.
 */
class ConnectedBlockSearchTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    @Test
    void floodsAWholeConnectedColumnInBreadthFirstOrder() {
        // A vertical column of five matching blocks stacked on the origin.
        Set<BlockPos> group = Set.of(
                ORIGIN, new BlockPos(0, 1, 0), new BlockPos(0, 2, 0), new BlockPos(0, 3, 0), new BlockPos(0, 4, 0));

        List<BlockPos> visited = new ConnectedBlockSearch(64).from(ORIGIN, group::contains);

        assertThat(visited).containsExactlyInAnyOrderElementsOf(group);
        assertThat(visited).first().isEqualTo(ORIGIN); // origin is always visited first
    }

    @Test
    void followsDiagonalNeighboursSoABranchStaysConnected() {
        // A staircase that only stays connected through diagonal (corner-touching) steps.
        Set<BlockPos> branch = Set.of(ORIGIN, new BlockPos(1, 1, 1), new BlockPos(2, 2, 2), new BlockPos(3, 3, 3));

        List<BlockPos> visited = new ConnectedBlockSearch(64).from(ORIGIN, branch::contains);

        assertThat(visited).containsExactlyInAnyOrderElementsOf(branch);
    }

    @Test
    void stopsAtMaxBlocksEvenWhenMoreWouldMatch() {
        // Everything on the y axis matches, but the cap holds the visit to three coordinates.
        Predicate<BlockPos> wholeColumn = pos -> pos.x() == 0 && pos.z() == 0;

        List<BlockPos> visited = new ConnectedBlockSearch(3).from(ORIGIN, wholeColumn);

        assertThat(visited).hasSize(3);
        assertThat(visited).first().isEqualTo(ORIGIN);
    }

    @Test
    void returnsJustTheOriginWhenNothingNeighbouringMatches() {
        // An isolated block: the predicate accepts only the origin, so no neighbour is added.
        List<BlockPos> visited = new ConnectedBlockSearch(64).from(ORIGIN, ORIGIN::equals);

        assertThat(visited).containsExactly(ORIGIN);
    }

    @Test
    void aMaxOfOneReturnsOnlyTheOriginEvenInAFullGroup() {
        Predicate<BlockPos> everything = pos -> true;

        List<BlockPos> visited = new ConnectedBlockSearch(1).from(ORIGIN, everything);

        assertThat(visited).containsExactly(ORIGIN);
    }

    @Test
    void rejectsANonPositiveMaxBlocks() {
        assertThatThrownBy(() -> new ConnectedBlockSearch(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
