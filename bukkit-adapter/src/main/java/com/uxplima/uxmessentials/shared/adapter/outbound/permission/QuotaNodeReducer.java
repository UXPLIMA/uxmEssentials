package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.OptionalLong;

import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The pure numeric core of the numbered/world quota reducer (docs/permissions.md "Uniform numeric
 * reducer"). It folds individual matching nodes into one value by the family's direction, the
 * maximum for quotas (more is better), the minimum for cooldowns and warmups (less is better), and
 * tracks the {@code -1} unlimited sentinel for quota families.
 *
 * <p>The Bukkit-specific enumeration of which nodes a player holds lives in {@code BukkitPermissions};
 * this class only parses one node's trailing value segment against a family and accumulates the
 * running winner, so the max/min/sentinel semantics are defined in one testable place.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>caller-confined</b>. A reducer is created per resolution and never shared, so its
 * mutable running state needs no synchronization.
 */
@NullMarked
final class QuotaNodeReducer {

    /** The {@code -1} sentinel meaning "no limit"; only meaningful for {@link QuotaReduction#MAX}. */
    private static final long UNLIMITED = -1L;

    private final QuotaFamily family;
    private final String unscopedPrefix;
    private final @Nullable String worldPrefix;

    private boolean unlimited;
    private boolean sawValue;
    private long running;

    // STACK-only: the config default to use when no tier node was seen, kept separate so it is not
    // added on top of the summed tiers when the player does hold matching nodes.
    private long stackDefault;

    QuotaNodeReducer(QuotaFamily family, @Nullable String worldName) {
        this.family = Objects.requireNonNull(family, "family");
        this.unscopedPrefix = family.node() + ".";
        this.worldPrefix =
                worldName == null ? null : family.node() + "." + worldName.toLowerCase(java.util.Locale.ROOT) + ".";
    }

    /**
     * Seed the fold with the config default so a player with no matching node still resolves.
     *
     * <p>For {@link QuotaReduction#STACK} the default is stored separately and returned only when no
     * tier node was seen: it is never added on top of the summed tiers.
     */
    void seedDefault(long configDefault) {
        if (family.direction() == QuotaReduction.STACK) {
            stackDefault = configDefault;
        } else {
            accept(configDefault);
        }
    }

    /**
     * Offer a held permission node; if it belongs to this family (scoped or unscoped) and ends in a
     * parseable value, fold its value into the running winner.
     */
    void offerNode(String node) {
        Objects.requireNonNull(node, "node");
        OptionalLong value = valueOf(node);
        if (value.isPresent()) {
            accept(value.getAsLong());
        }
    }

    /** Offer a value already resolved from a non-node source (LuckPerms meta). */
    void offerMeta(long value) {
        accept(value);
    }

    /** The reduced outcome: the unlimited sentinel short-circuits, otherwise the running winner. */
    QuotaResult result() {
        if (unlimited) {
            return QuotaResult.unlimited();
        }
        if (family.direction() == QuotaReduction.STACK) {
            // When at least one tier node was seen, return the accumulated sum; otherwise fall back to
            // the config default stored by seedDefault (so a player with no nodes still gets the default).
            return QuotaResult.limited(sawValue ? running : stackDefault);
        }
        return QuotaResult.limited(sawValue ? running : 0L);
    }

    private OptionalLong valueOf(String node) {
        String tail = matchTail(node);
        if (tail == null || tail.isEmpty() || tail.indexOf('.') >= 0) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(tail));
        } catch (NumberFormatException notNumeric) {
            return OptionalLong.empty();
        }
    }

    private @Nullable String matchTail(String node) {
        if (worldPrefix != null && node.startsWith(worldPrefix)) {
            return node.substring(worldPrefix.length());
        }
        if (node.startsWith(unscopedPrefix)) {
            return node.substring(unscopedPrefix.length());
        }
        return null;
    }

    private void accept(long value) {
        if (family.direction() == QuotaReduction.MAX && value == UNLIMITED) {
            unlimited = true;
            return;
        }
        if (family.direction() == QuotaReduction.STACK) {
            if (value == UNLIMITED) {
                unlimited = true;
                return;
            }
            // Seed running from 0 on first STACK value so we accumulate correctly.
            if (!sawValue) {
                running = 0L;
                sawValue = true;
            }
            running += value;
            return;
        }
        if (!sawValue) {
            running = value;
            sawValue = true;
            return;
        }
        running = family.direction() == QuotaReduction.MAX ? Math.max(running, value) : Math.min(running, value);
    }
}
