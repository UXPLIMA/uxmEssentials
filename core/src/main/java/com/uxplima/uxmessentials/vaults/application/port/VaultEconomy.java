package com.uxplima.uxmessentials.vaults.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the vaults context owns so a per-action cost can be charged, and a refund paid,
 * <em>without</em> a hard dependency on the economy context. It is the entire economy surface vaults needs,
 * expressed in vaults' own terms: a balance check, a withdrawal (the create/open fee) and a deposit (the
 * refund paid back when a vault is deleted). The economy context supplies an adapter that bridges this to its
 * {@code EconomyProvider}/{@code Wallet}; the vaults context never imports an economy type. This mirrors the
 * homes {@code HomeEconomy} seam.
 *
 * <p>Soft coupling: this port is injected as a {@link java.util.Optional} into {@code VaultCharge}. When no
 * provider is present a configured cost is simply ignored at use time (vault actions are free, as if no cost
 * were configured) and a configured refund is never paid. When a provider is present {@code VaultCharge}
 * withdraws through it before a vault is allocated and deposits through it when a vault is deleted. The
 * {@code withdraw} is itself the guarded debit: it returns {@code false} when the funds were insufficient (or
 * the guarded provider otherwise rejected), so there is no separate affordability gate before it and no
 * check-then-charge window in which a concurrent spend could let a vault be allocated without payment. The
 * charge fires only once every other guard (the amount quota) has passed.
 */
public interface VaultEconomy {

    /**
     * A no-op economy: affording is always true and a withdraw/deposit always "succeeds" without moving money.
     * Wired when economy is absent or disabled so a configured cost degrades to "free" without a special case
     * at every call site.
     */
    VaultEconomy NONE = new VaultEconomy() {
        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount) {
            // No economy backing: vault actions are free, so the withdraw trivially "succeeds".
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount) {
            // No economy backing: there was nothing charged, so the refund trivially "succeeds".
            return true;
        }
    };

    /**
     * True when {@code who} can afford {@code amount} in the server's default currency. This is a pre-check
     * only (e.g. {@code /vault info}); it must never gate a {@link #withdraw}. The withdraw is the guarded
     * debit and reports its own success, so a check-then-charge sequence would reintroduce a double-spend
     * window.
     */
    boolean canAfford(PlayerRef who, BigDecimal amount);

    /**
     * Withdraw {@code amount} from {@code who}'s balance. The create or open fee, charged after the quota
     * gate. Returns {@code true} when the funds were debited and {@code false} when the balance was
     * insufficient (or the guarded provider otherwise rejected). The result is the gate, not a preceding
     * {@code canAfford}.
     */
    boolean withdraw(PlayerRef who, BigDecimal amount);

    /**
     * Deposit {@code amount} into {@code who}'s balance: the refund paid back when their vault is deleted.
     * Returns {@code true} when the funds were credited and {@code false} when the credit was rejected (e.g. a
     * {@code max-balance} clamp), so a dropped refund can be surfaced rather than silently lost.
     */
    boolean deposit(PlayerRef who, BigDecimal amount);
}
