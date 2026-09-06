package com.uxplima.uxmessentials.economy.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@code /pay} above the currency's confirm-threshold, staged and awaiting {@code /payconfirm}. Staging
 * does <strong>not</strong> debit (no money has moved while a pending pay is held) and it is keyed by
 * {@code (payer, target, amount)} so confirming a different transfer than the one prompted cannot happen
 * ({@code docs/11-economy-integration.md} §9.2). On {@code /payconfirm} the use case re-validates funds and
 * the threshold from scratch and only then runs the guarded transfer; confirmation re-checks, it never
 * trusts the staged amount blindly.
 *
 * @param payer who initiated the pay
 * @param target who would receive it
 * @param amount the {@link Money} staged, carrying its currency
 * @param stagedAt when the prompt was issued (the expiry clock starts here)
 */
public record PendingPay(PlayerRef payer, PlayerRef target, Money amount, Instant stagedAt) {

    public PendingPay {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(stagedAt, "stagedAt");
    }

    /** True when {@code (target, amount)} match what was staged: the guard a {@code /payconfirm} must pass. */
    public boolean matches(PlayerRef target, Money amount) {
        return this.target.equals(Objects.requireNonNull(target, "target"))
                && this.amount.equals(Objects.requireNonNull(amount, "amount"));
    }
}
