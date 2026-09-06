package com.uxplima.uxmessentials.playerstate.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The pure rule behind the per-world command blocker: an operator names, per world, the command labels a
 * player may not run while standing in that world ({@code world-command-blocks.<world> = ["tpa", "warp"]}),
 * with a {@code "*"} key whose labels apply in every world. This policy answers "may a player in world
 * {@code world} run the command {@code label}?" against that configured map, and the adapter cancels the
 * dispatch when the answer is no and the player lacks the bypass node.
 *
 * <p>The match is on the bare command label only. The leading slash, any {@code uxmessentials:} namespace
 * prefix, and the arguments are stripped by the caller, and is case-insensitive on both the world name and
 * the label, since Bukkit world names are case-sensitive on disk but operators reasonably expect a
 * case-insensitive config match here. The domain never reads the live world or the wall clock; the adapter
 * passes in the player's current world name and the typed label, so the rule stays a pure function of its
 * configured map. When the map is empty the adapter skips the lookup entirely.
 */
public final class WorldCommandPolicy {

    /** The config key whose blocked labels apply in every world. */
    public static final String WILDCARD_WORLD = "*";

    private final Map<String, Set<String>> blockedByWorld;
    private final Set<String> blockedEverywhere;

    /**
     * @param blockedByWorld a per-world map of blocked command labels; the {@code "*"} key (if present)
     *     applies in every world. World names and labels are case-folded; a leading slash on a label is
     *     tolerated and stripped so {@code "/tpa"} and {@code "tpa"} are equivalent
     */
    public WorldCommandPolicy(Map<String, ? extends Collection<String>> blockedByWorld) {
        Objects.requireNonNull(blockedByWorld, "blockedByWorld");
        Map<String, Set<String>> byWorld = new HashMap<>();
        Set<String> everywhere = Set.of();
        for (Map.Entry<String, ? extends Collection<String>> entry : blockedByWorld.entrySet()) {
            String world = entry.getKey();
            if (world == null) {
                continue;
            }
            Set<String> labels = normalizeLabels(entry.getValue());
            if (labels.isEmpty()) {
                continue;
            }
            if (WILDCARD_WORLD.equals(world.trim())) {
                everywhere = labels;
            } else {
                byWorld.put(world.toLowerCase(Locale.ROOT), labels);
            }
        }
        this.blockedByWorld = Map.copyOf(byWorld);
        this.blockedEverywhere = everywhere;
    }

    /** True when a player standing in {@code world} is forbidden to run the command {@code label}. */
    public boolean isBlocked(String world, String label) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(label, "label");
        String wanted = normalizeLabel(label);
        if (wanted.isEmpty()) {
            return false;
        }
        if (blockedEverywhere.contains(wanted)) {
            return true;
        }
        Set<String> here = blockedByWorld.get(world.toLowerCase(Locale.ROOT));
        return here != null && here.contains(wanted);
    }

    /** True when no world (nor the wildcard) blocks any command, so the adapter can skip the lookup. */
    public boolean isEmpty() {
        return blockedByWorld.isEmpty() && blockedEverywhere.isEmpty();
    }

    private static Set<String> normalizeLabels(Collection<String> labels) {
        if (labels == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String normalized = normalizeLabel(label);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return Set.copyOf(out);
    }

    private static String normalizeLabel(String label) {
        String trimmed = label.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            trimmed = trimmed.substring(colon + 1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
