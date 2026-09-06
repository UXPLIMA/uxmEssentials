package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure fast-leaf-decay sweep: a decayable leaf within the radius of a felled log decays only when no log
 * still supports it, persistent leaves are never returned, leaves beyond the radius are left alone, and the result is
 * capped.
 */
class LeafDecaySweepTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    @Test
    void decayableLeavesWithinRadiusWithNoSupportingLogDecay() {
        // Leaves in a shell around the felled log, no logs left anywhere: every one within radius comes down.
        Set<BlockPos> leaves =
                Set.of(new BlockPos(1, 64, 0), new BlockPos(0, 65, 0), new BlockPos(2, 64, 1), new BlockPos(0, 63, 2));
        LeafDecaySweep sweep = new LeafDecaySweep(3, 6, 256);

        List<BlockPos> decaying = sweep.unsupportedLeaves(ORIGIN, LeafDecaySweepTest::noLogs, leaves::contains);

        assertThat(decaying).containsExactlyInAnyOrderElementsOf(leaves);
    }

    @Test
    void leavesBeyondTheRadiusAreLeftAlone() {
        BlockPos near = new BlockPos(2, 64, 0);
        BlockPos far = new BlockPos(9, 64, 0); // outside a radius-3 cube
        LeafDecaySweep sweep = new LeafDecaySweep(3, 6, 256);

        List<BlockPos> decaying =
                sweep.unsupportedLeaves(ORIGIN, LeafDecaySweepTest::noLogs, Set.of(near, far)::contains);

        assertThat(decaying).containsExactly(near);
    }

    @Test
    void aLeafStillWithinSupportDistanceOfALogSurvives() {
        BlockPos leaf = new BlockPos(1, 64, 0);
        BlockPos standingLog = new BlockPos(3, 64, 0); // two blocks from the leaf, inside supportDistance 6
        LeafDecaySweep sweep = new LeafDecaySweep(4, 6, 256);

        List<BlockPos> decaying = sweep.unsupportedLeaves(ORIGIN, standingLog::equals, leaf::equals);

        assertThat(decaying).isEmpty();
    }

    @Test
    void persistentLeavesAreNeverDecayableAndAreNotReturned() {
        // The adapter's isDecayableLeaf predicate answers false for a persistent leaf; a sweep that sees none decays
        // nothing.
        LeafDecaySweep sweep = new LeafDecaySweep(3, 6, 256);

        List<BlockPos> decaying = sweep.unsupportedLeaves(ORIGIN, LeafDecaySweepTest::noLogs, pos -> false);

        assertThat(decaying).isEmpty();
    }

    @Test
    void theResultIsCappedAtMaxLeaves() {
        LeafDecaySweep sweep = new LeafDecaySweep(3, 6, 5);
        Set<BlockPos> seen = new HashSet<>();

        // Every distinct coordinate is a fresh decayable leaf, so the sweep is bounded only by the maxLeaves cap.
        List<BlockPos> decaying = sweep.unsupportedLeaves(ORIGIN, LeafDecaySweepTest::noLogs, seen::add);

        assertThat(decaying).hasSize(5);
    }

    @Test
    void rejectsNonPositiveDimensions() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LeafDecaySweep(0, 6, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new LeafDecaySweep(1, 0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new LeafDecaySweep(1, 6, 0));
    }

    /** No coordinate holds a log: the felled-tree case where every leaf within radius is unsupported. */
    private static boolean noLogs(BlockPos pos) {
        return false;
    }
}
