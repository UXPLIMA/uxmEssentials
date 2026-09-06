package com.uxplima.uxmessentials.economy.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.EconomyError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A wallet change refused before it could apply. A debit short of funds, or a credit that would breach the
 * currency's maximum. A first-class event rather than a thrown exception, so consumers (audit, the
 * requesting context's UX) observe the refusal the same way they observe success. Carries the requested
 * amount, the balance available at the time, and the {@link EconomyError} that classifies the refusal. Maps
 * to one {@code event=economy_rejected} audit line.
 *
 * @param owner the wallet whose change was refused
 * @param requested the {@link Money} the change asked for, carrying its currency
 * @param available the owner's balance in that currency at the moment of refusal
 * @param reason the modelled cause of the refusal
 * @param occurredAt when the refusal happened
 */
public record WalletRejected(PlayerRef owner, Money requested, Money available, EconomyError reason, Instant occurredAt)
        implements EconomyEvent {

    public WalletRejected {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
