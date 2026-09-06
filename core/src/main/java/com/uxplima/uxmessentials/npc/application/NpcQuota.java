package com.uxplima.uxmessentials.npc.application;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.domain.NpcLimit;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Resolves an owner's NPC-creation limit through the shared {@code Permissions} quota reducer, the highest
 * matching {@code uxmessentials.npc.limit.<n>} tier the player holds (LuckPerms meta treated identically),
 * falling back to the module's configured default. The {@code -1} unlimited sentinel, from a node, meta, or the
 * config default, short-circuits to "no limit". An NPC quota is server-wide, so the world-scoped node form does
 * not apply (the world is passed as {@code null}). The result is handed out as an {@link NpcLimit} value object,
 * so the reduction semantics live entirely in the one shared reducer (docs/permissions.md, the numbered-node
 * convention). Mirrors {@code homes.application.HomeQuota}.
 */
public final class NpcQuota {

    private static final String NODE = "uxmessentials.npc.limit";

    private final Permissions permissions;
    private final QuotaFamily family;
    private final int configDefault;

    /**
     * @param permissions the permission port used to resolve quota nodes
     * @param configDefault the per-server default NPC limit for a player with no matching tier node; {@code -1}
     *     means unlimited (the backward-compatible default, since NPCs were previously uncapped)
     */
    public NpcQuota(Permissions permissions, int configDefault) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < -1) {
            throw new IllegalArgumentException(
                    "default npc limit must be -1 (unlimited) or non-negative: " + configDefault);
        }
        this.configDefault = configDefault;
        this.family = QuotaFamily.quota(NODE);
    }

    /** Resolve {@code owner}'s NPC limit; an unlimited node, meta, or config default yields {@link NpcLimit#noLimit()}. */
    public NpcLimit resolve(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, family, null, configDefault);
        if (resolved.isUnlimited()) {
            return NpcLimit.noLimit();
        }
        return NpcLimit.of(Math.toIntExact(Math.max(0, resolved.orElse(configDefault))));
    }
}
