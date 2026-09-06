package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;

/**
 * Resolves an owner's vault-count quota through the shared {@code Permissions} quota reducer. The amount is the
 * highest {@code uxmessentials.vault.amount.<n>} node the owner holds, folded together with any LuckPerms meta
 * and the per-context config default; the {@code -1} unlimited sentinel short-circuits to "no limit". The
 * result is handed to the domain as a {@link VaultAmount} value object, so the {@code max}/sentinel semantics
 * live entirely in the one shared reducer (docs/permissions.md, the numbered-node convention).
 */
public final class VaultAmountQuota {

    /** The quota family for vault counts: the {@code MAX}-direction reducer over the numbered nodes. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota("uxmessentials.vault.amount");

    private final Permissions permissions;
    private final int configDefault;

    public VaultAmountQuota(Permissions permissions, int configDefault) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < 0) {
            throw new IllegalArgumentException("default vault amount must not be negative: " + configDefault);
        }
        this.configDefault = configDefault;
    }

    /** Resolve {@code owner}'s vault-count quota. Vaults are not world-scoped, so the reducer folds the family only. */
    public VaultAmount resolve(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, FAMILY, null, configDefault);
        if (resolved.isUnlimited()) {
            return VaultAmount.noLimit();
        }
        long value = resolved.orElse(configDefault);
        return VaultAmount.of(Math.toIntExact(Math.max(0, value)));
    }
}
