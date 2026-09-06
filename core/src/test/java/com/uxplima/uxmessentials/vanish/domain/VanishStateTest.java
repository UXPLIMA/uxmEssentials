package com.uxplima.uxmessentials.vanish.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The pure vanish visibility rule: a player always sees themselves; a non-vanished target is seen by everyone; a
 * vanished target is seen only when the viewer's <em>see level</em> is at least the target's <em>use level</em>
 * ({@link VanishLevels#sees}). The simple case (layered permissions off) collapses to see level 1 vs use level 1;
 * the layered cases exercise the {@code see-level ≥ use-level} comparison directly. Exercised on {@link VanishState}
 * with no Bukkit or store.
 */
class VanishStateTest {

    private static final int SEE_LEVEL_1 = 1;
    private static final int NO_SEE = VanishLevels.NO_SEE_LEVEL;

    private final UUID viewer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void everyoneSeesANonVanishedTarget() {
        VanishState state = VanishState.empty();

        assertThat(state.isVanished(target)).isFalse();
        assertThat(state.canSee(viewer, target, NO_SEE)).isTrue();
    }

    @Test
    void aVanishedTargetIsHiddenFromAViewerWithNoSeeLevel() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.isVanished(target)).isTrue();
        assertThat(state.canSee(viewer, target, NO_SEE)).isFalse();
    }

    @Test
    void aVanishedTargetIsVisibleToAViewerAtTheSameSeeLevel() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.canSee(viewer, target, SEE_LEVEL_1)).isTrue();
    }

    @Test
    void aPlayerAlwaysSeesThemselvesEvenWhileVanished() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.canSee(target, target, NO_SEE)).isTrue();
    }

    @Test
    void aHigherUseLevelHidesFromALowerSeeLevel() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.of(2));

        // See level 1 does not clear a use level of 2: the target stays hidden.
        assertThat(state.canSee(viewer, target, SEE_LEVEL_1)).isFalse();
    }

    @Test
    void anEqualOrGreaterSeeLevelRevealsAHigherUseLevel() {
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.of(2));

        assertThat(state.canSee(viewer, target, 2)).isTrue(); // equal clears the bar
        assertThat(state.canSee(viewer, target, 3)).isTrue(); // greater clears the bar
    }

    @Test
    void aNoLevelNodeVanishedTargetIsLevelOneSoASeeLevelOneViewerReveals() {
        // A plainly-vanished target (no numbered node) sits at level 1 (DEFAULT), so a viewer resolved to see
        // level 1 (the plain .see node) reveals them: the flat Phase-1 behaviour preserved under the level rule.
        VanishState state = VanishState.empty().withVanished(target, VanishLevel.DEFAULT);

        assertThat(state.levelOf(target)).contains(VanishLevel.DEFAULT);
        assertThat(state.canSee(viewer, target, SEE_LEVEL_1)).isTrue();
    }

    @Test
    void revealingDropsTheTargetFromTheVanishedSet() {
        VanishState state =
                VanishState.empty().withVanished(target, VanishLevel.DEFAULT).withoutVanished(target);

        assertThat(state.isVanished(target)).isFalse();
        assertThat(state.vanishedIds()).isEmpty();
    }
}
