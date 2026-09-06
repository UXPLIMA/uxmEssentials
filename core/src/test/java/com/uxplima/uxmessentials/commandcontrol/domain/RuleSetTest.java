package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The pure decision guard for the command whitelist / blacklist. Exercises the four ways the {@link RuleSet} reads
 * its lists. Whitelist allows only the listed roots, blacklist denies only the listed roots, a group selects its own
 * list, and an unmatched group falls through to the default list. Plus the two always-allow escapes ({@code .bypass}
 * and the inert short-circuit) and the case/slash normalisation the adapter relies on.
 */
class RuleSetTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";

    /** A player in {@code group} with no bypass node: the common case the mode rules are tested against. */
    private static PlayerFacts inGroup(String group) {
        return facts(Optional.of(group), false);
    }

    private static PlayerFacts noGroup() {
        return facts(Optional.empty(), false);
    }

    private static PlayerFacts facts(Optional<String> group, boolean bypass) {
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return group;
            }

            @Override
            public boolean hasPermission(String node) {
                return bypass && node.equals(BYPASS);
            }
        };
    }

    @Test
    void whitelistAllowsOnlyListedAndDeniesTheRest() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("home", "spawn"), Map.of(), BYPASS);

        assertThat(rules.decide("home", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("spawn", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("op", noGroup())).isEqualTo(RuleSet.Decision.DENY);
    }

    @Test
    void blacklistDeniesListedAndAllowsTheRest() {
        RuleSet rules = RuleSet.of(RuleMode.BLACKLIST, List.of("op", "stop"), Map.of(), BYPASS);

        assertThat(rules.decide("op", noGroup())).isEqualTo(RuleSet.Decision.DENY);
        assertThat(rules.decide("stop", noGroup())).isEqualTo(RuleSet.Decision.DENY);
        assertThat(rules.decide("home", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
    }

    @Test
    void aGroupSelectsItsOwnListOverTheDefault() {
        RuleSet rules = RuleSet.of(
                RuleMode.WHITELIST,
                List.of("spawn"),
                Map.of("vip", List.of("fly", "hat"), "default-abusing-name", List.of("ignored")),
                BYPASS);

        // The vip group's own whitelist governs a vip player: fly is allowed, the default-only spawn is not.
        assertThat(rules.decide("fly", inGroup("vip"))).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("spawn", inGroup("vip"))).isEqualTo(RuleSet.Decision.DENY);
    }

    @Test
    void anUnmatchedGroupFallsThroughToTheDefaultList() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("spawn"), Map.of("vip", List.of("fly")), BYPASS);

        // A player in a group with no configured list uses the default list.
        assertThat(rules.decide("spawn", inGroup("member"))).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("fly", inGroup("member"))).isEqualTo(RuleSet.Decision.DENY);
        // A player in no group at all also uses the default list.
        assertThat(rules.decide("spawn", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
    }

    @Test
    void bypassHolderIsAlwaysAllowedEvenUnderAStrictWhitelist() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of(), Map.of(), BYPASS);

        // An empty whitelist denies everyone, but a bypass holder runs anything.
        assertThat(rules.decide("op", facts(Optional.of("vip"), true))).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(rules.decide("op", noGroup())).isEqualTo(RuleSet.Decision.DENY);
    }

    @Test
    void caseAndLeadingSlashAreNormalisedOnBothSides() {
        RuleSet rules =
                RuleSet.of(RuleMode.BLACKLIST, List.of("/OP", " Stop "), Map.of("Staff", List.of("Gamemode")), BYPASS);

        assertThat(rules.decide("op", noGroup())).isEqualTo(RuleSet.Decision.DENY);
        assertThat(rules.decide("STOP", noGroup())).isEqualTo(RuleSet.Decision.DENY);
        // The group key and its entry are matched case-insensitively too.
        assertThat(rules.decide("gamemode", inGroup("staff"))).isEqualTo(RuleSet.Decision.DENY);
    }

    @Test
    void anEmptyBlacklistIsInertButAnEmptyWhitelistIsNot() {
        assertThat(RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), BYPASS).isInert())
                .isTrue();
        assertThat(RuleSet.of(RuleMode.BLACKLIST, List.of("op"), Map.of(), BYPASS)
                        .isInert())
                .isFalse();
        // A blacklist with a populated group list is not inert.
        assertThat(RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of("vip", List.of("op")), BYPASS)
                        .isInert())
                .isFalse();
        // An empty whitelist actively denies, so it is never inert.
        assertThat(RuleSet.of(RuleMode.WHITELIST, List.of(), Map.of(), BYPASS).isInert())
                .isFalse();
    }

    @Test
    void aBlankBypassPermissionIsRejected() {
        assertThatThrownBy(() -> RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modeParsingIsCaseInsensitiveAndFallsBackOnAnUnknownValue() {
        assertThat(RuleMode.fromConfig("WhiteList", RuleMode.BLACKLIST)).isEqualTo(RuleMode.WHITELIST);
        assertThat(RuleMode.fromConfig("blacklist", RuleMode.WHITELIST)).isEqualTo(RuleMode.BLACKLIST);
        assertThat(RuleMode.fromConfig("nonsense", RuleMode.BLACKLIST)).isEqualTo(RuleMode.BLACKLIST);
        assertThat(RuleMode.fromConfig(null, RuleMode.WHITELIST)).isEqualTo(RuleMode.WHITELIST);
    }

    @Test
    void configuredEntriesAreImmutableFromTheCallerSListView() {
        // A caller mutating its source list after construction must not change the rule set.
        var mutable = new java.util.ArrayList<>(List.of("op"));
        RuleSet rules = RuleSet.of(RuleMode.BLACKLIST, mutable, Map.of(), BYPASS);
        mutable.add("home");
        assertThat(rules.decide("home", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
    }
}
