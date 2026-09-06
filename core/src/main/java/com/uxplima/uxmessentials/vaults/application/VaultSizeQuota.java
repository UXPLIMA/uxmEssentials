package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;

/**
 * Resolves an owner's per-vault size (in rows) through the shared {@code Permissions} quota reducer. The size
 * is the highest {@code uxmessentials.vault.size.<rows>} node the owner holds, folded with any LuckPerms meta
 * and the per-context config default; the result is clamped into the renderable {@code [1, 6]} range by
 * {@link VaultSize#ofClamped(long)} so a misconfigured node or default never produces an inventory a chest GUI
 * cannot show. The {@code max}/sentinel semantics live entirely in the one shared reducer
 * (docs/permissions.md, the numbered-node convention).
 */
public final class VaultSizeQuota {

    /** The quota family for vault sizes: the {@code MAX}-direction reducer over the numbered nodes. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota("uxmessentials.vault.size");

    private final Permissions permissions;
    private final int configDefault;

    public VaultSizeQuota(Permissions permissions, int configDefault) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.configDefault = clampDefault(configDefault);
    }

    /** Resolve {@code owner}'s per-vault size, clamped to the renderable row range. */
    public VaultSize resolve(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, FAMILY, null, configDefault);
        // An unlimited sentinel makes no sense for a row count a GUI must render, so it folds to the max rows.
        long rows = resolved.isUnlimited() ? VaultSize.MAX_ROWS : resolved.orElse(configDefault);
        return VaultSize.ofClamped(rows);
    }

    private static int clampDefault(int configDefault) {
        return Math.max(VaultSize.MIN_ROWS, Math.min(VaultSize.MAX_ROWS, configDefault));
    }
}
