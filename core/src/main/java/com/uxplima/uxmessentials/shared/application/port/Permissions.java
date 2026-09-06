package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for permission checks and numbered/world quota resolution.
 *
 * <p>Works against vanilla op plus any permission plugin; numbered quota and tier nodes additionally
 * resolve through LuckPerms meta when present, but meta is never required. The numbered-node form is
 * the canonical contract. Every value-bearing node (home limits, every cooldown and warmup tier,
 * vault amount and size) resolves through the one {@link #resolveQuota} method so the max/min/sentinel
 * and optional {@code <world>} semantics are guaranteed identical across contexts.
 */
public interface Permissions {

    /** True when {@code who} holds {@code node} (vanilla op or any permission plugin). */
    boolean has(PlayerRef who, String node);

    /**
     * Resolve a value-bearing quota for {@code who} under {@code family}, optionally scoped to
     * {@code world}, applying the uniform numeric reducer.
     *
     * <p>The reducer collects every matching node: the unscoped family
     * ({@code uxmessentials.<family>.<value>}), the world-scoped form
     * ({@code uxmessentials.<family>.<world>.<value>}) when {@code world} is present, any LuckPerms
     * meta, and the config default, then reduces by the family's {@link QuotaFamily#direction()}:
     * the {@link QuotaReduction#MAX maximum} for quotas (more is better) or the
     * {@link QuotaReduction#MIN minimum} for cooldowns and warmups (less is better). Across multiple
     * permission groups the same more-generous-wins rule applies a level up, so stacking groups never
     * makes a player worse off. The {@link QuotaResult#unlimited()} sentinel ({@code -1})
     * short-circuits a quota to "no limit"; it is never used for cooldown or warmup families, where
     * {@code 0} means "no wait".
     *
     * @param who the player whose nodes and meta are read
     * @param family the quota family and its reduction direction
     * @param world the world to fold the world-scoped form against, or {@code null} for unscoped only
     * @param configDefault the per-context config fallback when the player holds no matching node
     */
    QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault);

    /**
     * Whether the reducer keeps the largest (quota), smallest (cooldown/warmup), or sums all matching
     * values (stack). {@link #MAX} and {@link #MIN} choose the single most-generous node across all tiers;
     * {@link #STACK} accumulates every tier node the player holds, useful for servers that want players to
     * earn home slots additively across permission groups.
     */
    enum QuotaReduction {
        MAX,
        MIN,
        /** Sum all matching tier nodes; the {@code -1} unlimited sentinel still short-circuits. */
        STACK
    }

    /**
     * A value-bearing node family plus how its matching nodes reduce to one number.
     *
     * @param node the node prefix without the trailing value segment, e.g. {@code uxmessentials.home.limit}
     * @param direction {@link QuotaReduction#MAX} for quotas, {@link QuotaReduction#MIN} for cooldowns/warmups
     */
    record QuotaFamily(String node, QuotaReduction direction) {

        public QuotaFamily {
            if (node == null || node.isBlank()) {
                throw new IllegalArgumentException("node must be non-blank");
            }
            if (direction == null) {
                throw new IllegalArgumentException("direction must be set");
            }
        }

        /** A quota family (more is better): the reducer keeps the maximum. */
        public static QuotaFamily quota(String node) {
            return new QuotaFamily(node, QuotaReduction.MAX);
        }

        /** A cooldown or warmup family (less is better): the reducer keeps the minimum. */
        public static QuotaFamily threshold(String node) {
            return new QuotaFamily(node, QuotaReduction.MIN);
        }

        /** A stacking quota family: the reducer sums all matching tier nodes. */
        public static QuotaFamily stack(String node) {
            return new QuotaFamily(node, QuotaReduction.STACK);
        }
    }

    /**
     * The reduced outcome of {@link #resolveQuota}: either a concrete numeric limit or the unlimited
     * sentinel. Modelling "unlimited" as a distinct case keeps the {@code -1} sentinel from leaking
     * into arithmetic at call sites.
     */
    sealed interface QuotaResult permits QuotaResult.Limited, QuotaResult.Unlimited {

        /** A concrete limit (a home count, a cooldown in seconds). */
        static QuotaResult limited(long value) {
            return new Limited(value);
        }

        /** The {@code -1} sentinel: no limit at all. */
        static QuotaResult unlimited() {
            return Unlimited.INSTANCE;
        }

        boolean isUnlimited();

        /** The numeric value, or {@code fallback} when unlimited. */
        long orElse(long fallback);

        record Limited(long value) implements QuotaResult {
            @Override
            public boolean isUnlimited() {
                return false;
            }

            @Override
            public long orElse(long fallback) {
                return value;
            }
        }

        record Unlimited() implements QuotaResult {

            private static final Unlimited INSTANCE = new Unlimited();

            @Override
            public boolean isUnlimited() {
                return true;
            }

            @Override
            public long orElse(long fallback) {
                return fallback;
            }
        }

        /** The concrete value when limited, else empty. */
        default Optional<Long> asLong() {
            return this instanceof Limited limited ? Optional.of(limited.value()) : Optional.empty();
        }
    }
}
