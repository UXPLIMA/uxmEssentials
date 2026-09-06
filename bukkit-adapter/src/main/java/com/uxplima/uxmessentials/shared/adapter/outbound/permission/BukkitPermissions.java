package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.OptionalLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Permissions} implementation backed by Bukkit's permission system, with an optional
 * LuckPerms meta path layered on top.
 *
 * <p>Boolean checks ({@link #has}) delegate to the online {@link Player}'s {@code hasPermission},
 * which already folds in vanilla op and whatever permission plugin is installed. Value-bearing nodes
 * ({@link #resolveQuota}) are read by enumerating the player's effective permissions and folding the
 * numbered (and optionally world-scoped) nodes through {@link QuotaNodeReducer}; a LuckPerms meta
 * value, when present, is one more input to the same fold. An offline player cannot be
 * permission-checked, so {@code has} returns {@code false} and {@code resolveQuota} returns the config
 * default, the canonical conservative behaviour.
 */
@NullMarked
public final class BukkitPermissions implements Permissions {

    private final MetaSource meta;

    /** Build with no meta source, pure Bukkit-permission resolution. */
    public BukkitPermissions() {
        this(MetaSource.none());
    }

    /** Build with a meta source (the LuckPerms-backed one when LuckPerms is present). */
    public BukkitPermissions(MetaSource meta) {
        this.meta = Objects.requireNonNull(meta, "meta");
    }

    @Override
    public boolean has(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        Player player = Bukkit.getPlayer(who.uuid());
        return player != null && player.hasPermission(node);
    }

    @Override
    public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(family, "family");
        QuotaNodeReducer reducer = new QuotaNodeReducer(family, world == null ? null : world.name());
        reducer.seedDefault(configDefault);
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return reducer.result();
        }
        foldEffectiveNodes(player, reducer);
        foldMeta(who, family, world, reducer);
        return reducer.result();
    }

    private static void foldEffectiveNodes(Player player, QuotaNodeReducer reducer) {
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (info.getValue()) {
                reducer.offerNode(info.getPermission());
            }
        }
    }

    private void foldMeta(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, QuotaNodeReducer reducer) {
        OptionalLong unscoped = meta.metaValue(who.uuid(), family.node());
        if (unscoped.isPresent()) {
            reducer.offerMeta(unscoped.getAsLong());
        }
        if (world != null) {
            OptionalLong scoped = meta.metaValue(who.uuid(), family.node() + "." + world.name());
            scoped.ifPresent(reducer::offerMeta);
        }
    }
}
