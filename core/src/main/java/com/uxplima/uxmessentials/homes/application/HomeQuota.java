package com.uxplima.uxmessentials.homes.application;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an owner's home limit through the shared {@code Permissions} quota reducer. How multiple
 * {@code uxmessentials.home.limit.<n>} tiers combine is governed by {@code limitMode}: {@code MAX} keeps
 * the highest tier the player holds (the default "highest wins" behavior), {@code STACK} sums all tiers
 * the player holds (additive slots). The world-scoped form
 * ({@code uxmessentials.home.limit.<world>.<n>}) is folded in alongside the unscoped form, LuckPerms meta
 * is treated identically to a node, and the {@code -1} unlimited sentinel short-circuits to "no limit".
 * The result is handed to the aggregate as a {@link HomeLimit} value object, so the reduction semantics
 * live entirely in the one shared reducer (docs/permissions.md, the numbered-node convention).
 */
public final class HomeQuota {

    private static final String NODE = "uxmessentials.home.limit";

    private final Permissions permissions;
    private final QuotaFamily family;
    private final int configDefault;

    /**
     * @param permissions the permission port used to resolve quota nodes
     * @param configDefault the per-server default home count for players with no matching tier node
     * @param limitMode how multiple tier nodes combine. {@link QuotaReduction#MAX} keeps the highest,
     *     {@link QuotaReduction#STACK} sums them all
     */
    public HomeQuota(Permissions permissions, int configDefault, QuotaReduction limitMode) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < 0) {
            throw new IllegalArgumentException("default home limit must not be negative: " + configDefault);
        }
        this.configDefault = configDefault;
        Objects.requireNonNull(limitMode, "limitMode");
        this.family = limitMode == QuotaReduction.STACK ? QuotaFamily.stack(NODE) : QuotaFamily.quota(NODE);
    }

    /**
     * The quota family built for the configured {@code limitMode}. Exposed so placeholders and views can
     * pass the same family to {@link Permissions#resolveQuota} without constructing a second instance.
     */
    public QuotaFamily family() {
        return family;
    }

    /**
     * Resolve {@code owner}'s home limit, scoped to {@code world} so the world-scoped node form folds in.
     * Pass the world the new home would live in; a {@code null} world resolves the unscoped family only.
     */
    public HomeLimit resolve(PlayerRef owner, @Nullable WorldRef world) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, family, world, configDefault);
        if (resolved.isUnlimited()) {
            return HomeLimit.noLimit();
        }
        long value = resolved.orElse(configDefault);
        return HomeLimit.of(Math.toIntExact(Math.max(0, value)));
    }
}
