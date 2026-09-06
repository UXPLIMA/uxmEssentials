package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The pure decision behind the namespace-bypass block. A player can dodge the command filter by prefixing a command
 * with its namespace, {@code /minecraft:gamemode}, {@code /bukkit:pl}, {@code /plugin:cmd} all reach the same handler
 * as the bare command yet read as a different root, so the {@link RuleSet} (which matches on the raw root) would let
 * them through. This rule closes that hole: given a command root, it strips a leading {@code namespace:} prefix and
 * re-asks the {@link RuleSet} about the bare form, so {@code /minecraft:gamemode} is denied exactly when {@code gamemode}
 * is denied.
 *
 * <p>It is a thin, config-built wrapper over the same {@link RuleSet} the gate already holds; the {@code .bypass}
 * escape is not modelled here because the wrapped {@link RuleSet} short-circuits a bypass holder to
 * {@link RuleSet.Decision#ALLOW} before any list read, so a bypass holder may still use a namespaced form. When the
 * block is switched off ({@code block-namespace-bypass = false}) it denies nothing. The plugin-hide half of the
 * namespace escape needs no companion here: {@link HidePolicy} already folds the {@code namespace:} prefix when it
 * matches a hidden root, so {@code /bukkit:pl} is caught by the hide path directly.
 *
 * <p>Nothing here is Bukkit-aware. The adapter extracts the raw command root and supplies the {@link PlayerFacts}; the
 * strip and the deny decision are a plain function that unit-tests exactly.
 */
public final class NamespaceBypassRule {

    private final RuleSet rules;
    private final boolean enabled;

    private NamespaceBypassRule(RuleSet rules, boolean enabled) {
        this.rules = rules;
        this.enabled = enabled;
    }

    /**
     * Build the rule over {@code rules}, blocking namespaced forms only when {@code enabled}.
     *
     * @param rules the same rule set the command gate evaluates the bare form against
     * @param enabled whether the namespace-bypass block is switched on ({@code block-namespace-bypass})
     */
    public static NamespaceBypassRule of(RuleSet rules, boolean enabled) {
        Objects.requireNonNull(rules, "rules");
        return new NamespaceBypassRule(rules, enabled);
    }

    /**
     * True when the block is on, {@code commandRoot} carries a {@code namespace:} prefix, and the bare form (the label
     * after the prefix) is denied by the rule set for {@code facts}. A root with no namespace, or a bare form the rule
     * set allows, yields {@code false}: the gate then treats the command on its own merits.
     */
    public boolean deniesNamespacedForm(String commandRoot, PlayerFacts facts) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        Objects.requireNonNull(facts, "facts");
        if (!enabled) {
            return false;
        }
        Optional<String> bare = bareRoot(commandRoot);
        return bare.isPresent() && rules.decide(bare.get(), facts) == RuleSet.Decision.DENY;
    }

    /**
     * The bare command label of a namespaced root. The part after a single leading {@code namespace:} prefix, lowercased
     * and with any leading slash removed, or {@link Optional#empty()} when the root carries no usable namespace prefix
     * (no colon, an empty namespace like {@code :cmd}, or an empty label like {@code minecraft:}).
     */
    public static Optional<String> bareRoot(String commandRoot) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        String trimmed = commandRoot.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            return Optional.empty();
        }
        String label = trimmed.substring(colon + 1);
        return label.isBlank() ? Optional.empty() : Optional.of(label);
    }
}
