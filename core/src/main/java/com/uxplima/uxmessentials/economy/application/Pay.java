package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /pay <player> <amount> [currency]} and {@code /payconfirm}: move value between two players atomically.
 * The gates run in order before any leg. Self-pay, the currency's {@code min-pay}, the target's
 * {@code /paytoggle} flag, then the per-currency confirm-threshold ({@code docs/11-economy-integration.md}
 * §6.2, §9.1 to §9.2). Below the threshold the transfer is immediate; above it the pay is staged (no debit) and
 * a {@code /payconfirm} re-validates from scratch and only then transfers. Confirmation re-checks, it never
 * trusts the staged amount blindly.
 *
 * <p>The actual move is one {@link EconomyProvider#transfer} call, guarded and atomic at the database, so
 * the double-spend invariant is the provider's to keep; this use case only orchestrates the gates and the
 * confirm flow around it.
 */
public final class Pay {

    private final EconomyProvider economy;
    private final PayPreferences preferences;
    private final PendingPayRegistry pending;
    private final EconomyNotifier notifier;
    private final PayTaxation taxation;
    private final Clock clock;

    public Pay(
            EconomyProvider economy,
            PayPreferences preferences,
            PendingPayRegistry pending,
            EconomyNotifier notifier,
            PayTaxation taxation,
            Clock clock) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.taxation = Objects.requireNonNull(taxation, "taxation");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Attempt {@code from} paying {@code amount} to {@code to}, running every gate and the confirm flow. */
    public PayOutcome pay(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        Optional<TransferError> gate = gate(from, to, amount);
        if (gate.isPresent()) {
            return reject(from, gate.get(), amount);
        }
        if (amount.currency().requiresConfirm(amount.amount())) {
            return stage(from, to, amount);
        }
        return settle(from, to, amount);
    }

    /**
     * {@code /payconfirm}: re-validate and run the payer's staged transfer. Returns the resolution when one
     * was outstanding, or empty (after telling the payer there is nothing to confirm) when none was staged
     * or it had already expired.
     */
    public Optional<PayOutcome> confirm(PlayerRef payer) {
        Objects.requireNonNull(payer, "payer");
        Optional<PendingPay> staged = pending.take(payer);
        if (staged.isEmpty()) {
            notifier.send(payer, EconomyMessageKey.PAY_CONFIRM_NONE);
            return Optional.empty();
        }
        return Optional.of(revalidate(payer, staged.get()));
    }

    private Optional<TransferError> gate(PlayerRef from, PlayerRef to, Money amount) {
        if (from.equals(to)) {
            return Optional.of(TransferError.SELF_TRANSFER);
        }
        if (!amount.currency().transferAllowed()) {
            return Optional.of(TransferError.CURRENCY_TRANSFER_DISABLED);
        }
        if (amount.amount().compareTo(amount.currency().minPay()) < 0) {
            return Optional.of(TransferError.BELOW_MINIMUM);
        }
        if (!economy.currencies().contains(amount.currency())) {
            return Optional.of(TransferError.CURRENCY_UNSUPPORTED);
        }
        if (!preferences.acceptsPay(to)) {
            return Optional.of(TransferError.TARGET_DISABLED);
        }
        return Optional.empty();
    }

    private PayOutcome stage(PlayerRef from, PlayerRef to, Money amount) {
        pending.stage(new PendingPay(from, to, amount, clock.instant()));
        notifier.send(
                from,
                EconomyMessageKey.PAY_CONFIRM_PROMPT,
                Map.of("player", to.name(), "amount", notifier.amount(amount)));
        return PayOutcome.staged();
    }

    private PayOutcome revalidate(PlayerRef payer, PendingPay staged) {
        Optional<TransferError> gate = gate(payer, staged.target(), staged.amount());
        if (gate.isPresent()) {
            return reject(payer, gate.get(), staged.amount());
        }
        return settle(payer, staged.target(), staged.amount());
    }

    private PayOutcome settle(PlayerRef from, PlayerRef to, Money amount) {
        TransferResult result = economy.transfer(from, to, amount);
        return switch (result) {
            case TransferResult.Allow ignored -> sent(from, to, amount);
            case TransferResult.InsufficientFunds ignored -> reject(from, TransferError.INSUFFICIENT_FUNDS, amount);
            case TransferResult.DenyWith deny -> deny(from, deny);
        };
    }

    private PayOutcome sent(PlayerRef from, PlayerRef to, Money amount) {
        // The provider applied both legs and emitted the WalletDebited/WalletCredited events at the source.
        // The tax (if any) is then taken out of the receiver, who was just credited the gross, so the sink's
        // guarded move always succeeds, and the receiver is told the net they actually kept.
        Money tax = taxation.collect(from, to, amount);
        Money net = amount.minus(tax);
        notifier.send(from, EconomyMessageKey.PAY_SENT, Map.of("player", to.name(), "amount", notifier.amount(amount)));
        if (!tax.isZero()) {
            notifier.send(from, EconomyMessageKey.PAY_TAX_APPLIED, Map.of("amount", notifier.amount(tax)));
        }
        notifier.send(
                to, EconomyMessageKey.PAY_RECEIVED, Map.of("player", from.name(), "amount", notifier.amount(net)));
        return PayOutcome.sent();
    }

    private PayOutcome deny(PlayerRef from, TransferResult.DenyWith deny) {
        notifier.send(from, deny.reason());
        return PayOutcome.rejected(TransferError.CURRENCY_UNSUPPORTED);
    }

    private PayOutcome reject(PlayerRef from, TransferError error, Money amount) {
        notifier.send(from, error.messageKey(), Map.of("amount", notifier.amount(amount)));
        return PayOutcome.rejected(error);
    }

    /** The audit reason every {@code /pay} mutation carries. */
    public static EconomyReason reason() {
        return EconomyReason.PAY;
    }
}
