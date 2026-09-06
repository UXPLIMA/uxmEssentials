package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the vaults context's narrow {@link VaultEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-action vault fee can be charged, and a delete refund paid, in the server's default currency without
 * the vaults context ever importing an economy type (docs/11-economy-integration.md §4.2). This adapter is
 * constructed only when economy is wired <em>and</em> the vaults config opts in; when economy is absent the
 * wiring hands {@link VaultEconomy#NONE} to {@code VaultCharge} instead, so no charge ever fires on a server
 * without economy. Mirrors the homes {@code ProviderHomeEconomy}.
 *
 * <p>A vault cost is a bare {@link BigDecimal} in vaults' own terms; this adapter denominates it in the
 * configured default {@link Currency} before charging. {@code canAfford} is a balance read, {@code withdraw}
 * is the guarded debit whose {@code isOk()} reports whether the funds sufficed (no separate {@code canAfford}
 * gate precedes it), and {@code deposit} is the single-sided credit that pays the delete refund back, whose
 * {@code isOk()} reports whether the refund landed.
 */
@NullMarked
public final class ProviderVaultEconomy implements VaultEconomy {

    private final EconomyProvider economy;
    private final Currency currency;
    private final Optional<ChargeReceipts> receipts;

    public ProviderVaultEconomy(EconomyProvider economy, Currency currency, Optional<ChargeReceipts> receipts) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return !economy.balance(who, currency).isLessThan(Money.of(currency, amount));
    }

    @Override
    public boolean withdraw(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        // The guarded debit is itself the gate: it rejects rather than going negative, so its result reports
        // whether the funds sufficed. VaultCharge trusts this result instead of a preceding canAfford, which
        // closes the check-then-charge double-spend window.
        Money charge = Money.of(currency, amount);
        if (economy.debit(who, charge).isErr()) {
            return false;
        }
        receipts.ifPresent(receipt -> receipt.charged(who, charge, EconomyMessageKey.CHARGE_VAULT));
        return true;
    }

    @Override
    public boolean deposit(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return economy.credit(who, Money.of(currency, amount)).isOk();
    }
}
