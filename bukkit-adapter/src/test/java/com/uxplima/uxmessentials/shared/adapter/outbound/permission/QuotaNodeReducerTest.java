package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import org.junit.jupiter.api.Test;

/**
 * The uniform numbered/world quota reducer (docs/permissions.md "Uniform numeric reducer"): quotas keep
 * the maximum matching node, cooldown/warmup thresholds keep the minimum, the stack mode sums all
 * matching nodes, the {@code -1} sentinel short-circuits a quota/stack to unlimited, the optional
 * {@code <world>} segment folds in alongside the unscoped form, and a player with no matching node falls
 * back to the seeded config default. This is the pure numeric core every context's quota resolution
 * goes through.
 */
class QuotaNodeReducerTest {

    private static final QuotaFamily WARMUP = QuotaFamily.threshold("uxmessentials.tp.warmup");
    private static final QuotaFamily HOME_LIMIT = QuotaFamily.quota("uxmessentials.home.limit");
    private static final QuotaFamily HOME_STACK = QuotaFamily.stack("uxmessentials.home.limit");

    @Test
    void cooldownAndWarmupThresholdsKeepTheSmallestNode() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.5");
        reducer.offerNode("uxmessentials.tp.warmup.1");

        assertThat(reducer.result().orElse(99)).isEqualTo(1L); // lowest tier wins: the most privileged
    }

    @Test
    void warmupZeroRemovesTheWarmup() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.0");

        assertThat(reducer.result().orElse(99)).isZero();
    }

    @Test
    void quotaFamiliesKeepTheLargestNode() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_LIMIT, null);
        reducer.seedDefault(1);
        reducer.offerNode("uxmessentials.home.limit.5");
        reducer.offerNode("uxmessentials.home.limit.10");

        assertThat(reducer.result().orElse(0)).isEqualTo(10L); // more is better for a quota
    }

    @Test
    void minusOneShortCircuitsAQuotaToUnlimited() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_LIMIT, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.home.limit.-1");
        reducer.offerNode("uxmessentials.home.limit.5");

        QuotaResult result = reducer.result();
        assertThat(result.isUnlimited()).isTrue();
        assertThat(result.orElse(7)).isEqualTo(7L); // unlimited yields the caller's fallback in arithmetic
    }

    @Test
    void theWorldScopedFormFoldsInAlongsideTheUnscopedForm() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, "nether");
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.4"); // unscoped
        reducer.offerNode("uxmessentials.tp.warmup.nether.0"); // world-scoped, more privileged

        assertThat(reducer.result().orElse(99)).isZero();
    }

    @Test
    void aNonMatchingWorldNodeIsIgnored() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, "nether");
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.creative.0"); // different world, not folded

        assertThat(reducer.result().orElse(99)).isEqualTo(3L); // only the default remains
    }

    @Test
    void noMatchingNodeFallsBackToTheSeededDefault() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.bypass"); // non-numeric tail, ignored
        reducer.offerNode("uxmessentials.other.node.1"); // wrong family, ignored

        assertThat(reducer.result().orElse(99)).isEqualTo(3L);
    }

    @Test
    void luckPermsMetaFoldsInLikeANode() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerMeta(1);

        assertThat(reducer.result().orElse(99)).isEqualTo(1L);
    }

    // --- STACK mode ---

    @Test
    void stackSumsAllMatchingTierNodes() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_STACK, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.home.limit.2");
        reducer.offerNode("uxmessentials.home.limit.3");

        // tiers sum to 5; config default is not added on top
        assertThat(reducer.result().orElse(0)).isEqualTo(5L);
    }

    @Test
    void stackWithUnlimitedTierShortCircuitsToUnlimited() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_STACK, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.home.limit.2");
        reducer.offerNode("uxmessentials.home.limit.-1");

        assertThat(reducer.result().isUnlimited()).isTrue();
    }

    @Test
    void stackWithNoTierNodeFallsBackToConfigDefault() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_STACK, null);
        reducer.seedDefault(5);
        // no tier nodes offered
        reducer.offerNode("uxmessentials.other.family.10"); // wrong family, ignored

        assertThat(reducer.result().orElse(0)).isEqualTo(5L);
    }

    @Test
    void maxStillReturnsBiggestNodeAfterStackAdded() {
        // regression: MAX behaviour must be unaffected by the STACK code path
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_LIMIT, null);
        reducer.seedDefault(1);
        reducer.offerNode("uxmessentials.home.limit.5");
        reducer.offerNode("uxmessentials.home.limit.2");

        assertThat(reducer.result().orElse(0)).isEqualTo(5L);
    }

    @Test
    void minStillReturnsSmallestNodeAfterStackAdded() {
        // regression: MIN behaviour must be unaffected by the STACK code path
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(10);
        reducer.offerNode("uxmessentials.tp.warmup.7");
        reducer.offerNode("uxmessentials.tp.warmup.3");

        assertThat(reducer.result().orElse(99)).isEqualTo(3L);
    }
}
