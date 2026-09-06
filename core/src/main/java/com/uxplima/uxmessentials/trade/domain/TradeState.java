package com.uxplima.uxmessentials.trade.domain;

/**
 * The lifecycle stage of a {@link TradeSession}.
 *
 * <ul>
 *   <li>{@code OPEN}. The working state: either side may stake or withdraw items and money and toggle its own
 *       confirmation. This is where a session spends almost all of its life.
 *   <li>{@code READY}. Both sides have confirmed the current offers and nothing has changed since; the exchange is
 *       armed and may be committed. Any further change drops it back to {@code OPEN} (the anti-scam invariant).
 *   <li>{@code COMMITTED}: a terminal, successful state: the swap has been applied. No further transition is legal.
 *   <li>{@code CANCELLED}: a terminal, aborted state: someone cancelled, disconnected, or the window closed. No
 *       further transition is legal; the adapter returns every staked item and refunds every escrowed amount.
 * </ul>
 */
public enum TradeState {
    OPEN,
    READY,
    COMMITTED,
    CANCELLED;

    /** True for a finished session ({@code COMMITTED} or {@code CANCELLED}) that accepts no further transition. */
    public boolean isTerminal() {
        return this == COMMITTED || this == CANCELLED;
    }
}
