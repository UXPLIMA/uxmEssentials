package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link TeleportFee} bound to the resolved {@link EconomyProvider}, resolving its backing provider and
 * currency lazily on each call. The economy module is enabled after teleport, so at teleport-wire time the
 * provider and currency are not yet available; the composition root captures them onto its cross-context
 * links and hands their suppliers here. While economy is disabled (the supplier stays null) every request is
 * free ({@link #canAfford} is always {@code true} and {@link #charge} does nothing) so the teleport context
 * never branches on economy presence and the wiring stays order-independent.
 *
 * <p>{@code canAfford} is a plain balance read taken on the command thread before the search. {@code charge}
 * is the guarded single-sided debit, and because it is reached only once a teleport has landed (on the
 * player's region thread), it is dispatched to the async scheduler so no DB write ever runs on a tick thread;
 * the affordability was already gated up front, so a rare debit failure (the balance changed between check and
 * landing) is logged rather than surfaced.
 */
@NullMarked
public final class LinkedTeleportFee implements TeleportFee {

    private final Supplier<@Nullable EconomyProvider> provider;
    private final Supplier<@Nullable Currency> currency;
    private final Scheduler scheduler;
    private final Logger log;
    private final Optional<ChargeReceipts> receipts;

    public LinkedTeleportFee(
            Supplier<@Nullable EconomyProvider> provider,
            Supplier<@Nullable Currency> currency,
            Scheduler scheduler,
            Logger log,
            Optional<ChargeReceipts> receipts) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        EconomyProvider live = provider.get();
        Currency unit = currency.get();
        if (live == null || unit == null) {
            return true; // economy disabled. The cost is free
        }
        return !live.balance(who, unit).isLessThan(Money.of(unit, amount));
    }

    @Override
    public void charge(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        EconomyProvider live = provider.get();
        Currency unit = currency.get();
        if (live == null || unit == null) {
            return; // economy disabled: nothing to withdraw
        }
        scheduler.async(() -> debit(live, unit, who, amount));
    }

    private void debit(EconomyProvider live, Currency unit, PlayerRef who, BigDecimal amount) {
        Money charge = Money.of(unit, amount);
        if (live.debit(who, charge).isErr()) {
            log.warn("rtp charge of {} failed for {} after landing", amount.toPlainString(), who.name());
            return;
        }
        // The fee is taken after the teleport has landed, when the player is looking at the new place and not at
        // their balance, so the receipt is the only thing that connects the two.
        receipts.ifPresent(receipt -> receipt.charged(who, charge, EconomyMessageKey.CHARGE_TELEPORT));
    }
}
