/**
 * Pure domain of the trade bounded context: the {@code TradeSession} aggregate (a live, two-party exchange), its
 * identity {@code TradeId}, the immutable {@code TradeOffer} snapshot each side stakes (an opaque {@code OfferedItem}
 * list plus a money amount per currency), the {@code TradeSide} that names the two participants, and the
 * {@code TradeState} the session moves through. The session is a pure model: every transition returns a new session,
 * the anti-scam invariant (any offer change clears BOTH confirmations) is enforced here, and an illegal transition
 * raises {@link com.uxplima.uxmessentials.trade.domain.IllegalTradeTransitionException}. No Bukkit, Paper, Kyori, or
 * economy type appears, the adapter maps an {@code OfferedItem} handle to a real item and a currency id to a real
 * currency at the boundary, and the application layer drives the transitions.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.domain;
