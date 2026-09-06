package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.junit.jupiter.api.Test;

/**
 * The {@link AdvancementFilter} decision logic: the enabled gate, the deny-list, the allow-list's exclusivity, the
 * recipe and only-announceable gates, and the per-advancement template lookup with its global fallback: all matched
 * case-insensitively on the advancement key.
 */
class AdvancementFilterTest {

    private static final String DRAGON = "minecraft:end/kill_dragon";
    private static final String RECIPE = "minecraft:recipes/building_blocks/oak_planks";

    @Test
    void aDisabledConfigNeverAnnounces() {
        AdvancementNoticeConfig cfg = config(false, true, true, Set.of(), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, DRAGON, false, true)).isFalse();
    }

    @Test
    void aDenyListedAdvancementIsNotAnnounced() {
        AdvancementNoticeConfig cfg = config(true, true, true, Set.of(), Set.of(DRAGON));

        assertThat(AdvancementFilter.shouldAnnounce(cfg, DRAGON, false, true)).isFalse();
    }

    @Test
    void anAllowListAdmitsOnlyItsMembers() {
        AdvancementNoticeConfig cfg = config(true, true, true, Set.of(DRAGON), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, DRAGON, false, true)).isTrue();
        assertThat(AdvancementFilter.shouldAnnounce(cfg, "minecraft:adventure/root", false, true))
                .isFalse();
    }

    @Test
    void anEmptyAllowListAdmitsEveryAdvancementPassingTheOtherGates() {
        AdvancementNoticeConfig cfg = config(true, true, true, Set.of(), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, DRAGON, false, true)).isTrue();
        assertThat(AdvancementFilter.shouldAnnounce(cfg, "minecraft:adventure/root", false, true))
                .isTrue();
    }

    @Test
    void aRecipeIsSkippedWhenRecipesAreExcluded() {
        AdvancementNoticeConfig cfg = config(true, true, true, Set.of(), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, RECIPE, true, true)).isFalse();
    }

    @Test
    void aRecipeIsAnnouncedWhenRecipesAreNotExcluded() {
        AdvancementNoticeConfig cfg = config(true, false, false, Set.of(), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, RECIPE, true, false)).isTrue();
    }

    @Test
    void aNonAnnounceableAdvancementIsSkippedWhenOnlyAnnounceable() {
        AdvancementNoticeConfig cfg = config(true, true, true, Set.of(), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, DRAGON, false, false)).isFalse();
    }

    @Test
    void aNonAnnounceableAdvancementIsAnnouncedWhenOnlyAnnounceableIsOff() {
        AdvancementNoticeConfig cfg = config(true, true, false, Set.of(), Set.of());

        assertThat(AdvancementFilter.shouldAnnounce(cfg, DRAGON, false, false)).isTrue();
    }

    @Test
    void theKeyIsMatchedCaseInsensitively() {
        AdvancementNoticeConfig cfg = config(true, true, true, Set.of(), Set.of(DRAGON));

        assertThat(AdvancementFilter.shouldAnnounce(cfg, "MINECRAFT:END/KILL_DRAGON", false, true))
                .isFalse();
    }

    @Test
    void aPerAdvancementTemplateOverridesTheGlobalOne() {
        AdvancementNoticeConfig cfg = new AdvancementNoticeConfig(
                true,
                true,
                true,
                Set.of(),
                Set.of(),
                "global",
                Map.of(DRAGON, "the dragon override"),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());

        assertThat(AdvancementFilter.templateFor(cfg, DRAGON)).isEqualTo("the dragon override");
        assertThat(AdvancementFilter.templateFor(cfg, "MINECRAFT:END/KILL_DRAGON"))
                .isEqualTo("the dragon override");
    }

    @Test
    void theGlobalTemplateIsUsedWithoutAnOverride() {
        AdvancementNoticeConfig cfg = new AdvancementNoticeConfig(
                true,
                true,
                true,
                Set.of(),
                Set.of(),
                "global",
                Map.of(DRAGON, "the dragon override"),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());

        assertThat(AdvancementFilter.templateFor(cfg, "minecraft:adventure/root"))
                .isEqualTo("global");
    }

    private static AdvancementNoticeConfig config(
            boolean enabled, boolean excludeRecipes, boolean onlyAnnounceable, Set<String> allow, Set<String> deny) {
        return new AdvancementNoticeConfig(
                enabled,
                excludeRecipes,
                onlyAnnounceable,
                allow,
                deny,
                "{player} earned {title}",
                Map.of(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());
    }
}
