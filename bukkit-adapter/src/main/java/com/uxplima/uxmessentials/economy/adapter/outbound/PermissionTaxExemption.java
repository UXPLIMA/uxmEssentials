package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.TaxExemption;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The permission-driven {@link TaxExemption}: a payer's {@code /pay} is untaxed when they hold the configured
 * {@code pay.tax.bypass-permission}: staff, shops, automated systems. Permission-based like
 * {@link PermissionBaltopExemption}, so it survives a UUID change and needs no per-account flag.
 */
@NullMarked
public final class PermissionTaxExemption implements TaxExemption {

    private final Permissions permissions;
    private final String node;

    public PermissionTaxExemption(Permissions permissions, String node) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.node = Objects.requireNonNull(node, "node");
    }

    @Override
    public boolean isExempt(PlayerRef payer) {
        return permissions.has(Objects.requireNonNull(payer, "payer"), node);
    }
}
