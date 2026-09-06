package com.uxplima.uxmessentials.trade.domain;

/**
 * Raised when a {@link TradeSession} transition is asked for in a state that does not allow it, confirming a
 * cancelled session, committing one that is not {@code READY}, or arming one whose sides have not both confirmed. It
 * is a programming-error signal for the application layer, not a player-facing message: the use cases guard against
 * these paths and surface the appropriate {@code TradeMessageKey} to the player themselves, so this exception carries
 * a developer-oriented description and is never rendered to a client.
 */
public final class IllegalTradeTransitionException extends IllegalStateException {

    public IllegalTradeTransitionException(String message) {
        super(message);
    }
}
