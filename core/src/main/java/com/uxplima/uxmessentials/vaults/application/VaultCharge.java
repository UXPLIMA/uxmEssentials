package com.uxplima.uxmessentials.vaults.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.domain.VaultError;

/**
 * The economy charge gate for vault actions: resolves the cost for an action, skips the charge when it is
 * free, when no economy provider is wired, or when the player holds the bypass node, and otherwise delegates
 * the withdrawal to {@link VaultEconomy}. Mirrors the homes {@code HomeCharge}.
 *
 * <p>This is where the economy <em>soft coupling</em> is expressed. The provider is an {@link Optional}
 * injected at wiring time: when it is absent a configured cost is ignored and the action proceeds for free
 * (and a configured refund is never paid); when it is present the cost is charged through the narrow
 * {@link VaultEconomy} seam after the amount quota has passed, and the refund is deposited on delete. The
 * vaults context therefore never hard-depends on the economy context.
 */
public final class VaultCharge {

    private static final String BYPASS_NODE = "uxmessentials.vault.free";

    private final Permissions permissions;
    private final Optional<VaultEconomy> economy;
    private final VaultChargeSettings settings;

    public VaultCharge(Permissions permissions, Optional<VaultEconomy> economy, VaultChargeSettings settings) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Charge {@code who} the combined create + per-open fee in a single atomic withdrawal, the first-ever
     * allocation of a vault index. Bundling both fees into one debit means a new vault is paid for in one
     * indivisible move: it can never half-charge (take the create fee then fail to take the open fee, leaving
     * the create fee lost with no vault). Returns success immediately when the combined fee is free, when no
     * economy provider is present, or when the player holds the bypass node; otherwise withdraws the sum and
     * returns {@link VaultError#CANNOT_AFFORD} when the balance was insufficient.
     */
    public Result<Unit, VaultError> chargeAllocate(PlayerRef who) {
        return charge(who, settings.costToCreate().add(settings.costToOpen()));
    }

    /**
     * Charge {@code who} the per-open fee: re-opening a vault index that already exists. Same gating as
     * {@link #chargeAllocate(PlayerRef)}; with a zero {@code cost-to-open} this is always free, so re-opening
     * an existing vault never costs unless configured.
     */
    public Result<Unit, VaultError> chargeOpen(PlayerRef who) {
        return charge(who, settings.costToOpen());
    }

    /**
     * Refund {@code who} the configured delete refund. A no-op (returning {@code true}) when no economy
     * provider is present, when the refund is zero, or when the player holds the bypass node (they were never
     * charged, so nothing is paid back). The deposit is the inverse of the create fee. It makes deleting a
     * paid-for vault recoverable.
     *
     * <p>Returns {@code false} only when an economy refund was attempted but the provider rejected the credit
     * (e.g. a {@code max-balance} clamp), so the caller can surface the dropped refund. {@code VaultCharge} is
     * pure core and intentionally holds no logger; the boolean is the seam for the adapter to log against.
     */
    public boolean refund(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        BigDecimal refund = settings.refundOnDelete();
        if (economy.isEmpty() || refund.signum() <= 0 || permissions.has(who, BYPASS_NODE)) {
            return true;
        }
        return economy.get().deposit(who, refund);
    }

    private Result<Unit, VaultError> charge(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        if (amount.signum() <= 0 || economy.isEmpty() || permissions.has(who, BYPASS_NODE)) {
            return Result.ok();
        }
        // The withdraw is the guarded debit and reports its own success. There is no preceding canAfford
        // gate, so a concurrent spend (or a min-balance edge) that the guard rejects yields CANNOT_AFFORD
        // rather than a vault allocated without payment.
        return economy.get().withdraw(who, amount) ? Result.ok() : Result.err(VaultError.CANNOT_AFFORD);
    }
}
