package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.BaltopExemption;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The permission-driven {@link BaltopExemption} ({@code docs/11-economy-integration.md} §9.3): an owner is
 * excluded from every leaderboard when they hold the configured {@code economy.baltop.exempt-permission}
 * admin float accounts, NPC/shop banks, the server account. Because exemption is permission-based it survives
 * a UUID change and needs no per-account flag. The check runs where the per-currency snapshot is built, not
 * per render, so the hot {@code /baltop} path never re-checks the node.
 */
@NullMarked
public final class PermissionBaltopExemption implements BaltopExemption {

    private final Permissions permissions;
    private final String node;

    public PermissionBaltopExemption(Permissions permissions, String node) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.node = Objects.requireNonNull(node, "node");
    }

    @Override
    public boolean isExempt(PlayerRef owner) {
        return permissions.has(Objects.requireNonNull(owner, "owner"), node);
    }
}
