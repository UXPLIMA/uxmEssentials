package com.uxplima.uxmessentials.trade.domain;

/**
 * Which of a trade's two participants a transition addresses. A {@link TradeSession} holds exactly two sides, the
 * {@code INITIATOR} who ran {@code /trade <player>} and the {@code PARTNER} who accepted, and every offer, confirm,
 * and unconfirm transition names the side it applies to. The model is symmetric: neither side has more authority than
 * the other, the naming only records who started the exchange for rendering and audit attribution.
 */
public enum TradeSide {
    INITIATOR,
    PARTNER;

    /** The opposite side: used to reach across the session (e.g. to show the counterpart's read-only offer). */
    public TradeSide other() {
        return this == INITIATOR ? PARTNER : INITIATOR;
    }
}
