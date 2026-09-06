/**
 * The trade context's inbound command surface:
 * {@link com.uxplima.uxmessentials.trade.adapter.inbound.command.TradeCommand}, the Brigadier {@code /trade <player>}
 * request verb and its {@code accept|deny [player]} answers. The command orchestrates only; the pending-request expiry
 * and the per-player cooldown are the pure {@code TradeRequests} / {@code TradeCooldown} application classes' decisions,
 * and the window mechanics belong to the {@code gui} view the accept opens.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.adapter.inbound.command;
