package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an owner's player-warp limit through the shared {@code Permissions} quota reducer. The limit is the
 * highest {@code uxmessentials.pwarp.limit.<n>} node the owner holds (optionally scoped to the warp's world
 * via {@code uxmessentials.pwarp.limit.<world>.<n>}), folded together with any LuckPerms meta and the
 * per-context config default; the {@code -1} unlimited sentinel short-circuits to "no limit". The result is
 * handed to the aggregate as a {@link PlayerWarpLimit} value object, so the {@code max}/sentinel semantics
 * live entirely in the one shared reducer (docs/permissions.md, the numbered-node convention).
 */
public final class PlayerWarpQuota {

    /** The quota family for player-warp limits: the {@code MAX}-direction reducer over the numbered nodes. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota("uxmessentials.pwarp.limit");

    private final Permissions permissions;
    private final int configDefault;

    public PlayerWarpQuota(Permissions permissions, int configDefault) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < 0) {
            throw new IllegalArgumentException("default player warp limit must not be negative: " + configDefault);
        }
        this.configDefault = configDefault;
    }

    /**
     * Resolve {@code owner}'s player-warp limit, scoped to {@code world} so the world-scoped node form folds
     * in. Pass the world the new warp would live in; a {@code null} world resolves the unscoped family only.
     */
    public PlayerWarpLimit resolve(PlayerRef owner, @Nullable WorldRef world) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, FAMILY, world, configDefault);
        if (resolved.isUnlimited()) {
            return PlayerWarpLimit.noLimit();
        }
        long value = resolved.orElse(configDefault);
        return PlayerWarpLimit.of(Math.toIntExact(Math.max(0, value)));
    }
}
