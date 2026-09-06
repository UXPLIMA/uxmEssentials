package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.permission.Permission;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The real {@link PermissionQuery}, backed by the Vault {@link Permission} registered in the
 * {@code ServicesManager}. This is the only class in the hooks package that imports
 * {@code net.milkbowl.vault.permission}; it is reached solely from {@link VaultPermissionHook#whenPresent},
 * past {@code Hooks}' present-guard, so it loads only on a server that actually has Vault installed, the no-op
 * {@link PermissionQuery#ABSENT} carries none of these types.
 *
 * <p>The permission service can be absent even when Vault is present: the registration is resolved once in the
 * constructor and may be {@code null}, in which case every operation is a no-op and {@link #available()}
 * reports false. The {@code null} world passed to Vault means the global (non-world-scoped) context. Vault
 * routes changes through the provider on the calling thread, so callers invoke this on the viewer's entity
 * thread (see {@link PermissionQuery}).
 */
@NullMarked
final class VaultPermissionService implements PermissionQuery {

    private final Server server;

    @Nullable private final Permission permission;

    VaultPermissionService(Server server) {
        this.server = Objects.requireNonNull(server, "server");
        RegisteredServiceProvider<Permission> registration =
                server.getServicesManager().getRegistration(Permission.class);
        this.permission = registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean available() {
        return active() != null;
    }

    @Override
    public boolean inGroup(UUID player, String group) {
        Objects.requireNonNull(group, "group");
        Permission active = active();
        return active != null && active.playerInGroup(null, offline(player), group);
    }

    @Override
    public boolean has(UUID player, String node) {
        Objects.requireNonNull(node, "node");
        Permission active = active();
        return active != null && active.playerHas(null, offline(player), node);
    }

    @Override
    public boolean add(UUID player, String node) {
        Objects.requireNonNull(node, "node");
        Permission active = active();
        return active != null && active.playerAdd(null, offline(player), node);
    }

    @Override
    public boolean remove(UUID player, String node) {
        Objects.requireNonNull(node, "node");
        Permission active = active();
        return active != null && active.playerRemove(null, offline(player), node);
    }

    @Override
    public String primaryGroup(UUID player) {
        Permission active = active();
        if (active == null) {
            return "";
        }
        String group = active.getPrimaryGroup(null, offline(player));
        return group == null ? "" : group;
    }

    private @Nullable Permission active() {
        Permission resolved = this.permission;
        return resolved != null && resolved.isEnabled() ? resolved : null;
    }

    private OfflinePlayer offline(UUID player) {
        return server.getOfflinePlayer(Objects.requireNonNull(player, "player"));
    }
}
