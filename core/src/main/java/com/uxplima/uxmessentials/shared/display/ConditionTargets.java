package com.uxplima.uxmessentials.shared.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Reads and writes the two simple targets the announcement editor exposes, a world and a permission, into and out
 * of the raw display-condition string the {@link ConditionParser} grammar understands. The editor lets an operator
 * set "which world it shows in" and "which permission sees it" without writing the condition DSL by hand; this is
 * the seam that turns those two fields into a {@code permission:X && world:Y} condition and back.
 *
 * <h2>Read</h2>
 * {@link #world(String)} and {@link #permission(String)} pull the first {@code world:} / {@code permission:} atom
 * out of an {@code &&}-joined condition, so the editor's value lore shows the current target. They read the simple
 * form the editor itself writes; a hand-authored condition with {@code ||}, {@code !}, or a {@code %papi%}
 * comparison is left for the operator to edit in the file. The editor surfaces only the world and permission atoms
 * it manages.
 *
 * <h2>Write</h2>
 * {@link #withWorld(String, String)} and {@link #withPermission(String, String)} replace just that one atom in the
 * existing condition, preserving every other atom (so setting a world does not drop a permission already set, and
 * vice versa). A blank target clears that atom. The atoms are recomposed in a stable order
 * ({@code permission} then {@code world}) joined with {@code &&}; an empty result is the unconditional blank string.
 *
 * <p>Pure value logic over the condition string (no Bukkit, no parser state) so it lives in {@code :core}
 * alongside the grammar it round-trips.
 */
public final class ConditionTargets {

    private static final String WORLD_PREFIX = "world:";
    private static final String PERMISSION_PREFIX = "permission:";
    private static final String JOIN = " && ";

    private ConditionTargets() {}

    /** The world the condition targets ({@code world:<name>}), or empty when it sets none. */
    public static Optional<String> world(String condition) {
        return atomValue(condition, WORLD_PREFIX);
    }

    /** The permission the condition targets ({@code permission:<node>}), or empty when it sets none. */
    public static Optional<String> permission(String condition) {
        return atomValue(condition, PERMISSION_PREFIX);
    }

    /**
     * {@code condition} with its world atom set to {@code world} (a blank {@code world} clears it), every other
     * atom preserved. The result is the recomposed {@code permission}/{@code world} condition, blank when neither
     * target remains.
     */
    public static String withWorld(String condition, @Nullable String world) {
        return recompose(permission(condition).orElse(null), normalise(world));
    }

    /**
     * {@code condition} with its permission atom set to {@code permission} (a blank value clears it), every other
     * atom preserved. The result is the recomposed condition, blank when neither target remains.
     */
    public static String withPermission(String condition, @Nullable String permission) {
        return recompose(normalise(permission), world(condition).orElse(null));
    }

    private static Optional<String> atomValue(String condition, String prefix) {
        for (String atom : split(condition)) {
            String trimmed = atom.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private static String recompose(@Nullable String permission, @Nullable String world) {
        List<String> atoms = new ArrayList<>();
        if (permission != null) {
            atoms.add(PERMISSION_PREFIX + permission);
        }
        if (world != null) {
            atoms.add(WORLD_PREFIX + world);
        }
        return String.join(JOIN, atoms);
    }

    private static List<String> split(String condition) {
        List<String> atoms = new ArrayList<>();
        int from = 0;
        int idx;
        while ((idx = condition.indexOf("&&", from)) >= 0) {
            atoms.add(condition.substring(from, idx));
            from = idx + 2;
        }
        atoms.add(condition.substring(from));
        return atoms;
    }

    private static @Nullable String normalise(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
