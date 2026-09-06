package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The pure decision guard for the namespace-bypass block. Exercises the strip helper across the namespace forms a
 * client can send ({@code minecraft:gamemode}, {@code /bukkit:pl}, mixed case), its edge cases (no colon, empty
 * namespace, empty label), and the deny decision itself. A namespaced form is blocked exactly when its bare form is
 * denied, a bypass holder is never blocked, and the whole block is a no-op when switched off.
 */
class NamespaceBypassRuleTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";

    private static RuleSet blacklist(String... denied) {
        return RuleSet.of(RuleMode.BLACKLIST, List.of(denied), Map.of(), BYPASS);
    }

    private static PlayerFacts noBypass() {
        return facts(false);
    }

    private static PlayerFacts bypassHolder() {
        return facts(true);
    }

    private static PlayerFacts facts(boolean bypass) {
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return Optional.empty();
            }

            @Override
            public boolean hasPermission(String node) {
                return bypass && node.equals(BYPASS);
            }
        };
    }

    @Test
    void bareRootStripsASingleNamespacePrefix() {
        assertThat(NamespaceBypassRule.bareRoot("minecraft:gamemode")).contains("gamemode");
        assertThat(NamespaceBypassRule.bareRoot("/bukkit:pl")).contains("pl");
        assertThat(NamespaceBypassRule.bareRoot("Plugin:Cmd")).contains("cmd");
    }

    @Test
    void bareRootIsEmptyForANonNamespacedOrMalformedRoot() {
        // No colon: a plain command has no namespace to strip.
        assertThat(NamespaceBypassRule.bareRoot("gamemode")).isEmpty();
        // Empty namespace or empty label are not usable namespace forms.
        assertThat(NamespaceBypassRule.bareRoot(":gamemode")).isEmpty();
        assertThat(NamespaceBypassRule.bareRoot("minecraft:")).isEmpty();
        assertThat(NamespaceBypassRule.bareRoot("")).isEmpty();
    }

    @Test
    void aNamespacedFormIsDeniedExactlyWhenItsBareFormIs() {
        NamespaceBypassRule rule = NamespaceBypassRule.of(blacklist("gamemode"), true);

        // The bare form is blacklisted, so the namespaced escape is blocked whatever the namespace.
        assertThat(rule.deniesNamespacedForm("minecraft:gamemode", noBypass())).isTrue();
        assertThat(rule.deniesNamespacedForm("bukkit:gamemode", noBypass())).isTrue();
        // A namespaced form whose bare root is allowed is not blocked.
        assertThat(rule.deniesNamespacedForm("minecraft:home", noBypass())).isFalse();
        // A plain (non-namespaced) root is left to the gate's primary decision, never blocked by this rule.
        assertThat(rule.deniesNamespacedForm("gamemode", noBypass())).isFalse();
    }

    @Test
    void aBypassHolderIsNeverBlocked() {
        NamespaceBypassRule rule = NamespaceBypassRule.of(blacklist("gamemode"), true);

        assertThat(rule.deniesNamespacedForm("minecraft:gamemode", bypassHolder()))
                .isFalse();
    }

    @Test
    void theBlockIsANoOpWhenSwitchedOff() {
        NamespaceBypassRule rule = NamespaceBypassRule.of(blacklist("gamemode"), false);

        assertThat(rule.deniesNamespacedForm("minecraft:gamemode", noBypass())).isFalse();
    }
}
