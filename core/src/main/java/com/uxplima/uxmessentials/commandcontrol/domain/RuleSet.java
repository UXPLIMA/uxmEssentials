package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The pure allow/deny decision behind the command whitelist / blacklist. Built once from the module's config, it
 * holds the {@link RuleMode}, a {@code default} command list, a per-group command list map, and the {@code .bypass}
 * permission node; it decides {@link Decision#ALLOW} or {@link Decision#DENY} for one command root and one player's
 * {@link PlayerFacts}. Nothing here is Bukkit-aware. The event handling, the command-root extraction, and the
 * group/permission lookups are all adapter-side, so the decision is a plain function that unit-tests exactly.
 *
 * <p>Resolution: a {@code .bypass} holder is always allowed. Otherwise the player's group selects its command list
 * (falling back to the {@code default} list when the group has none or is absent), and the mode reads that list
 * {@link RuleMode#WHITELIST} allows only a listed root, {@link RuleMode#BLACKLIST} denies only a listed root. Console
 * is never modelled here: the adapter's listener fires only for player commands, so a console command is never gated.
 *
 * <p>Every command root and every configured entry is normalised to a lowercase, slash-stripped label, so
 * {@code /Home}, {@code home}, and a config entry of {@code "/HOME"} all compare equal.
 */
public final class RuleSet {

    /** The outcome of {@link #decide}: the command may run, or it is blocked. */
    public enum Decision {
        ALLOW,
        DENY
    }

    private final RuleMode mode;
    private final Set<String> defaultCommands;
    private final Map<String, Set<String>> groupCommands;
    private final String bypassPermission;

    private RuleSet(
            RuleMode mode,
            Set<String> defaultCommands,
            Map<String, Set<String>> groupCommands,
            String bypassPermission) {
        this.mode = mode;
        this.defaultCommands = defaultCommands;
        this.groupCommands = groupCommands;
        this.bypassPermission = bypassPermission;
    }

    /**
     * Build a rule set, normalising every group key and command entry to a lowercase, slash-stripped label.
     *
     * @param mode whether the lists are read as a whitelist or a blacklist
     * @param defaultCommands the command list applied to a player whose group has no list of its own
     * @param groupCommands the per-group command lists, keyed by permission group name
     * @param bypassPermission the node that always allows a command through (never blank)
     */
    public static RuleSet of(
            RuleMode mode,
            List<String> defaultCommands,
            Map<String, List<String>> groupCommands,
            String bypassPermission) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(defaultCommands, "defaultCommands");
        Objects.requireNonNull(groupCommands, "groupCommands");
        if (bypassPermission == null || bypassPermission.isBlank()) {
            throw new IllegalArgumentException("bypassPermission must be non-blank");
        }
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        groupCommands.forEach((group, list) -> {
            Objects.requireNonNull(group, "group name");
            groups.put(normalize(group), normalizeAll(list));
        });
        return new RuleSet(mode, normalizeAll(defaultCommands), Map.copyOf(groups), bypassPermission);
    }

    /**
     * Decide whether {@code commandRoot} may run for {@code facts}. A {@code .bypass} holder is always allowed;
     * otherwise the player's group selects its list (or the default list) and the mode reads it.
     */
    public Decision decide(String commandRoot, PlayerFacts facts) {
        return explain(commandRoot, facts).decision();
    }

    /**
     * The same decision {@link #decide} makes, with what produced it: the mode that was read, whether the root was
     * listed, and whose list it was. For anything that has to explain the outcome rather than act on it, so an
     * explanation runs the resolution rather than restating it and can never drift from the gate.
     */
    public RuleVerdict explain(String commandRoot, PlayerFacts facts) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        Objects.requireNonNull(facts, "facts");
        String root = normalize(commandRoot);
        if (facts.hasPermission(bypassPermission)) {
            return new RuleVerdict(root, mode, Decision.ALLOW, RuleVerdict.Reason.BYPASS, Optional.empty());
        }
        Optional<String> group = facts.group();
        boolean listed = listFor(group).contains(root);
        Decision decision =
                switch (mode) {
                    case WHITELIST -> listed ? Decision.ALLOW : Decision.DENY;
                    case BLACKLIST -> listed ? Decision.DENY : Decision.ALLOW;
                };
        RuleVerdict.Reason reason = listed ? RuleVerdict.Reason.LISTED : RuleVerdict.Reason.UNLISTED;
        return new RuleVerdict(root, mode, decision, reason, listOwner(group));
    }

    /**
     * True when this rule set can never deny any command: a blacklist whose every list is empty. The adapter reads
     * this to short-circuit its listener before any per-command work, so an operator who leaves the lists blank pays
     * nothing. A whitelist is never inert: an empty whitelist denies everything, which is a deliberate lock-down.
     */
    public boolean isInert() {
        return mode == RuleMode.BLACKLIST
                && defaultCommands.isEmpty()
                && groupCommands.values().stream().allMatch(Set::isEmpty);
    }

    /** The group whose own list governs it, or empty when the {@code default} list does. */
    private Optional<String> listOwner(Optional<String> group) {
        return group.map(RuleSet::normalize).filter(groupCommands::containsKey);
    }

    /** The list that governs {@code group}: the group's own list when it has one, else the {@code default} list. */
    private Set<String> listFor(Optional<String> group) {
        Objects.requireNonNull(group, "group");
        return group.map(name -> groupCommands.getOrDefault(normalize(name), defaultCommands))
                .orElse(defaultCommands);
    }

    /** Lowercase, trim, and strip a leading slash from every non-blank entry, deduplicating. */
    private static Set<String> normalizeAll(List<String> raw) {
        Objects.requireNonNull(raw, "raw");
        Set<String> out = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry != null && !entry.isBlank()) {
                out.add(normalize(entry));
            }
        }
        return Set.copyOf(out);
    }

    /** Lowercase, trim, and strip a single leading slash so labels compare uniformly. */
    private static String normalize(String label) {
        String trimmed = label.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
