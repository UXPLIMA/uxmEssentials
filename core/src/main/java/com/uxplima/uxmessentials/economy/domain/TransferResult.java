package com.uxplima.uxmessentials.economy.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The sealed outcome of an attempted value movement (a {@code /pay} or a {@code WarpCost} charge). Three
 * cases mirror the moderation-verdict shape so the pattern is recognisable across contexts: {@link Allow}
 * applied both legs atomically; {@link InsufficientFunds} means the debit leg would have driven the balance
 * negative and nothing applied; {@link DenyWith} means a policy gate (self-pay, a disabled target, an
 * unsupported currency) refused before any leg. A transfer is atomic, either both legs commit or neither
 * does (the economy GLOSSARY, the double-spend invariant in {@code docs/02-concurrency.md}).
 *
 * <p>This is the return type of {@code EconomyProvider.transfer}; a foreign Treasury/Vault provider produces
 * the same three shapes, so a consumer pattern-matches one result type regardless of which economy is in
 * play.
 */
public sealed interface TransferResult
        permits TransferResult.Allow, TransferResult.InsufficientFunds, TransferResult.DenyWith {

    /** True only for {@link Allow}: both legs committed. Lets a caller branch without a full match. */
    boolean isOk();

    /** Both legs of the transfer applied atomically. */
    static Allow allow(Transaction debit, Transaction credit) {
        return new Allow(debit, credit);
    }

    /** The debit leg would have over-drawn the sender; nothing applied. */
    static InsufficientFunds insufficientFunds(Money required, Money available) {
        return new InsufficientFunds(required, available);
    }

    /** A policy gate refused the transfer before any leg ran. */
    static DenyWith denyWith(MessageKey reason) {
        return new DenyWith(reason);
    }

    /**
     * The success case: the debit applied to the sender and the credit applied to the recipient, both
     * inside one transaction.
     *
     * @param debit the change minted against the sender
     * @param credit the change minted in favour of the recipient
     */
    record Allow(Transaction debit, Transaction credit) implements TransferResult {

        public Allow {
            Objects.requireNonNull(debit, "debit");
            Objects.requireNonNull(credit, "credit");
        }

        @Override
        public boolean isOk() {
            return true;
        }
    }

    /**
     * The funds case: the sender could not cover the amount, so the guarded debit changed no rows and
     * nothing applied.
     *
     * @param required the amount the transfer asked for
     * @param available the sender's balance at the moment of refusal
     */
    record InsufficientFunds(Money required, Money available) implements TransferResult {

        public InsufficientFunds {
            Objects.requireNonNull(required, "required");
            Objects.requireNonNull(available, "available");
        }

        @Override
        public boolean isOk() {
            return false;
        }
    }

    /**
     * The policy case: a gate refused before any leg ran, carrying the {@link MessageKey} the adapter
     * renders (self-pay, a disabled target, an unsupported currency).
     *
     * @param reason the catalog key explaining the refusal
     */
    record DenyWith(MessageKey reason) implements TransferResult {

        public DenyWith {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public boolean isOk() {
            return false;
        }
    }
}
