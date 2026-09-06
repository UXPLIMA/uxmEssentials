package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.TransferError;

/**
 * What a {@code /pay} attempt resolved to, distinguishing the three shapes the command adapter must render
 * differently: the transfer was {@link #SENT}, it was {@link #STAGED} for {@code /payconfirm} (above the
 * currency's confirm-threshold: no money moved yet), or it was rejected with a {@link TransferError}. This
 * lets {@link Pay} return one value the command branches on without re-deriving the path.
 *
 * @param kind which of the three resolutions occurred
 * @param error the rejection cause when {@link Kind#REJECTED}, otherwise empty
 */
public record PayOutcome(Kind kind, Optional<TransferError> error) {

    private static final PayOutcome SENT = new PayOutcome(Kind.SENT, Optional.empty());
    private static final PayOutcome STAGED = new PayOutcome(Kind.STAGED, Optional.empty());

    public PayOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(error, "error");
        if ((kind == Kind.REJECTED) != error.isPresent()) {
            throw new IllegalArgumentException("a rejection carries an error; a success carries none");
        }
    }

    /** The transfer applied atomically. */
    public static PayOutcome sent() {
        return SENT;
    }

    /** The transfer was held pending {@code /payconfirm}; no debit happened. */
    public static PayOutcome staged() {
        return STAGED;
    }

    /** The transfer was refused for {@code error} before (or instead of) applying. */
    public static PayOutcome rejected(TransferError error) {
        return new PayOutcome(Kind.REJECTED, Optional.of(error));
    }

    /** The three resolutions a {@code /pay} can take. */
    public enum Kind {
        SENT,
        STAGED,
        REJECTED
    }
}
