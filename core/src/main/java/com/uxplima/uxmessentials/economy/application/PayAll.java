package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /payall <amount> [currency]}: pay {@code amountEach} to every online recipient from the sender's own
 * wallet: the player-funded counterpart to the admin {@code /eco giveall} free credit. Each leg is delegated
 * to the {@link Pay} use case, so every per-recipient gate (self-pay, {@code min-pay}, the target's
 * {@code /paytoggle}, the per-currency confirm-threshold) and the atomic, DB-backed move stay exactly as
 * {@code /pay} runs them: there is no second money path. The sender is skipped, never paying themselves.
 *
 * <p>Per-recipient outcomes are tallied through {@link PayOutcome}: a {@link PayOutcome.Kind#SENT} counts as a
 * success, anything else (a rejection, or a staged transfer above the confirm-threshold that did not move) as a
 * skip. After the fan-out the sender gets one {@link EconomyMessageKey#PAYALL_SENT} summary; the per-leg
 * rejection notices the recipients (and the sender) see are {@link Pay}'s own.
 */
public final class PayAll {

    private final Pay pay;
    private final EconomyNotifier notifier;

    public PayAll(Pay pay, EconomyNotifier notifier) {
        this.pay = Objects.requireNonNull(pay, "pay");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Pay {@code amountEach} from {@code from} to every recipient (the sender excluded), then summarise. */
    public void payAll(PlayerRef from, List<PlayerRef> recipients, Money amountEach) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(amountEach, "amountEach");
        int sent = 0;
        for (PlayerRef recipient : recipients) {
            if (payOne(from, recipient, amountEach)) {
                sent++;
            }
        }
        notifier.send(
                from,
                EconomyMessageKey.PAYALL_SENT,
                Map.of("count", Integer.toString(sent), "amount", notifier.amount(amountEach)));
    }

    private boolean payOne(PlayerRef from, PlayerRef recipient, Money amountEach) {
        if (from.equals(recipient)) {
            return false;
        }
        return pay.pay(from, recipient, amountEach).kind() == PayOutcome.Kind.SENT;
    }
}
