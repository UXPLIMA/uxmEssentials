package com.uxplima.uxmessentials.economy.domain;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;

/**
 * The modelled reasons a raw {@code /pay} or {@code /eco} amount fails to parse into a usable {@link Money}.
 * Each value carries the {@link EconomyMessageKey} the command adapter renders, mirroring {@link EconomyError}
 * so the failure reason and its localized text never drift apart. A parser returns a
 * {@code Result.err(AmountParseError.X)} and the caller renders {@code X.messageKey()} without re-deriving it.
 *
 * <p>{@link #NOT_A_NUMBER} covers anything that is not a recognisable figure (an unknown suffix, stray
 * letters, an empty token); {@link #NOT_POSITIVE} covers a zero or negative figure where the command requires
 * a strictly positive amount. Both are first-class values the {@link AmountParser} produces, never thrown.
 */
public enum AmountParseError {

    /** The token is not a recognisable number: bad digits, an unknown suffix, or empty input. */
    NOT_A_NUMBER(EconomyMessageKey.PAY_INVALID_AMOUNT),

    /** The figure parsed but is not strictly positive, which the amount commands require. */
    NOT_POSITIVE(EconomyMessageKey.PAY_INVALID_AMOUNT);

    private final EconomyMessageKey messageKey;

    AmountParseError(EconomyMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public EconomyMessageKey messageKey() {
        return messageKey;
    }
}
