package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The pure decision guard for the plugin-hide feature. Exercises the namespace/slash/case fold that makes the hide
 * effective against the {@code /bukkit:pl} escape, the view-permission reveal, the disabled and empty-list no-ops, and
 * the {@link HidePolicy#isActive()} short-circuit the adapter relies on.
 */
class HidePolicyTest {

    private static final String VIEW = "uxmessentials.commandcontrol.viewplugins";

    private static final List<String> HIDDEN = List.of("plugins", "pl", "?", "help", "ver", "version", "icanhasbukkit");

    /** A player with no view permission: the common case the hide rules are tested against. */
    private static PlayerFacts noView() {
        return facts(false);
    }

    private static PlayerFacts withView() {
        return facts(true);
    }

    private static PlayerFacts facts(boolean view) {
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return Optional.empty();
            }

            @Override
            public boolean hasPermission(String node) {
                return view && node.equals(VIEW);
            }
        };
    }

    @Test
    void hidesAListedCommandFromAPlayerWithoutTheViewPermission() {
        HidePolicy policy = HidePolicy.of(true, HIDDEN, VIEW);

        assertThat(policy.shouldHide("plugins", noView())).isTrue();
        assertThat(policy.shouldHide("help", noView())).isTrue();
        // A command that is not listed is never hidden.
        assertThat(policy.shouldHide("home", noView())).isFalse();
    }

    @Test
    void theViewPermissionRevealsEveryHiddenCommand() {
        HidePolicy policy = HidePolicy.of(true, HIDDEN, VIEW);

        assertThat(policy.shouldHide("plugins", withView())).isFalse();
        assertThat(policy.shouldHide("icanhasbukkit", withView())).isFalse();
    }

    @Test
    void namespacedSlashedAndMixedCaseVariantsAreFolded() {
        HidePolicy policy = HidePolicy.of(true, List.of("plugins"), VIEW);

        assertThat(policy.isHiddenCommand("plugins")).isTrue();
        assertThat(policy.isHiddenCommand("/plugins")).isTrue();
        assertThat(policy.isHiddenCommand("Plugins")).isTrue();
        assertThat(policy.isHiddenCommand("bukkit:plugins")).isTrue();
        assertThat(policy.isHiddenCommand("minecraft:plugins")).isTrue();
        assertThat(policy.isHiddenCommand("paper:PLUGINS")).isTrue();
        // A config entry that itself carries a namespace is folded to its bare root too.
        assertThat(HidePolicy.of(true, List.of("bukkit:pl"), VIEW).isHiddenCommand("pl"))
                .isTrue();
    }

    @Test
    void aDisabledPolicyHidesNothingAndIsInactive() {
        HidePolicy policy = HidePolicy.of(false, HIDDEN, VIEW);

        assertThat(policy.shouldHide("plugins", noView())).isFalse();
        assertThat(policy.isActive()).isFalse();
    }

    @Test
    void anEmptyListIsInactiveEvenWhenEnabled() {
        HidePolicy policy = HidePolicy.of(true, List.of(), VIEW);

        assertThat(policy.isActive()).isFalse();
        assertThat(policy.shouldHide("plugins", noView())).isFalse();
    }

    @Test
    void anEnabledPopulatedPolicyIsActive() {
        assertThat(HidePolicy.of(true, HIDDEN, VIEW).isActive()).isTrue();
    }

    @Test
    void blankOrNullEntriesAreDroppedAndABlankViewPermissionIsRejected() {
        // Blank and null entries are skipped rather than becoming a phantom hidden root.
        HidePolicy policy = HidePolicy.of(true, java.util.Arrays.asList("plugins", "", "  ", null), VIEW);
        assertThat(policy.isHiddenCommand("plugins")).isTrue();
        assertThat(policy.isHiddenCommand("")).isFalse();

        assertThatThrownBy(() -> HidePolicy.of(true, List.of("plugins"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHiddenSetIsImmutableFromTheCallerSListView() {
        var mutable = new java.util.ArrayList<>(Set.of("plugins"));
        HidePolicy policy = HidePolicy.of(true, mutable, VIEW);
        mutable.add("help");
        assertThat(policy.isHiddenCommand("help")).isFalse();
    }
}
