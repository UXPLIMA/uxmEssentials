/**
 * The trade context's window GUI, a genuine dual item-container leaf, deliberately not a spec-menu (players place real
 * stacks into it), mirroring the allowlisted raw-inventory leaves (the kit item-grid, invsee).
 * {@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions} is the in-memory registry of live
 * same-server trades; {@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeView} opens each participant's own
 * six-row window over the shared {@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeExchange}, the
 * viewer's editable offer on the left, the counterpart's offer mirrored read-only on the right
 * ({@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeLayout}), and a confirm button.
 *
 * <p>{@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeListener}, keyed by the window's
 * {@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeHolder}, routes clicks, drags, closes, disconnects,
 * and world changes: an allowed edit re-reads the offer (through
 * {@link com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeItemCodec}) into the pure {@code TradeSession},
 * clearing both confirmations and re-rendering both views live, and a both-confirm runs the atomic swap. Every terminal
 * path returns or swaps the offered stacks exactly once, so no item is ever lost or duplicated.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.adapter.inbound.gui;
