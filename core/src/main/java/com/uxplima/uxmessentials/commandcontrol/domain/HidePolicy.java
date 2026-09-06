package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The pure decision behind the plugin-hide feature: given a command root and a player's {@link PlayerFacts}, decide
 * whether that root is one of the plugin-listing / help commands (e.g. {@code plugins}, {@code pl}, {@code help},
 * {@code icanhasbukkit}) that should be hidden from a player who cannot see the plugin list. Built once from config, it
 * holds the enable gate, the normalised set of hidden roots, and the {@code view} permission that reveals them.
 *
 * <p>Every root is normalised the way the adapter presents it. Lowercased, a leading slash stripped, and a leading
 * {@code namespace:} prefix removed, so a config entry of {@code "plugins"} matches {@code /plugins}, {@code plugins},
 * {@code bukkit:plugins}, {@code minecraft:plugins}, and {@code paper:plugins} alike. That namespace fold is what makes
 * the hide effective against the {@code /bukkit:pl} escape without the domain ever seeing a live command.
 *
 * <p>Nothing here is Bukkit-aware: the {@code PlayerCommandSendEvent} scrub of the sent command list, the
 * {@code AsyncTabCompleteEvent} suggestion filter, and the scrub-help execution block all live in the adapter and call
 * back into this decision. The {@code .bypass} escape is not modelled here. The adapter short-circuits a bypass holder
 * before any hide work, so a bypass holder is never asked about.
 */
public final class HidePolicy {

    private final boolean enabled;
    private final Set<String> hiddenRoots;
    private final String viewPermission;

    private HidePolicy(boolean enabled, Set<String> hiddenRoots, String viewPermission) {
        this.enabled = enabled;
        this.hiddenRoots = hiddenRoots;
        this.viewPermission = viewPermission;
    }

    /**
     * Build a hide policy, normalising every configured command to a lowercase, slash- and namespace-stripped label.
     *
     * @param enabled whether the plugin-hide feature is switched on
     * @param hiddenCommands the plugin-listing / help commands to hide from a player without the view permission
     * @param viewPermission the node that reveals the hidden commands (never blank)
     */
    public static HidePolicy of(boolean enabled, List<String> hiddenCommands, String viewPermission) {
        Objects.requireNonNull(hiddenCommands, "hiddenCommands");
        if (viewPermission == null || viewPermission.isBlank()) {
            throw new IllegalArgumentException("viewPermission must be non-blank");
        }
        Set<String> roots = new LinkedHashSet<>();
        for (String entry : hiddenCommands) {
            if (entry != null && !entry.isBlank()) {
                roots.add(normalize(entry));
            }
        }
        return new HidePolicy(enabled, Set.copyOf(roots), viewPermission);
    }

    /**
     * True when {@code commandRoot} is a hidden plugin-listing command and {@code facts} may not see it, the feature
     * is enabled and the player lacks the view permission. A player holding the view permission is never hidden from.
     */
    public boolean shouldHide(String commandRoot, PlayerFacts facts) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        Objects.requireNonNull(facts, "facts");
        if (!enabled || facts.hasPermission(viewPermission)) {
            return false;
        }
        return isHiddenCommand(commandRoot);
    }

    /** True when {@code commandRoot} (namespace and slash stripped, lowercased) is one of the configured hidden roots. */
    public boolean isHiddenCommand(String commandRoot) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        return hiddenRoots.contains(normalize(commandRoot));
    }

    /**
     * True when the policy can hide anything: the feature is enabled and at least one command is listed. The adapter
     * reads this to skip its per-command work when plugin-hide is off or its list is empty.
     */
    public boolean isActive() {
        return enabled && !hiddenRoots.isEmpty();
    }

    /** Lowercase, trim, and strip a single leading slash and a leading {@code namespace:} prefix. */
    private static String normalize(String label) {
        String trimmed = label.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int colon = trimmed.indexOf(':');
        return colon < 0 ? trimmed : trimmed.substring(colon + 1);
    }
}
