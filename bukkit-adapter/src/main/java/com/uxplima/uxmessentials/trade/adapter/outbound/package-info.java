/**
 * The trade context's outbound adapters:
 * {@link com.uxplima.uxmessentials.trade.adapter.outbound.LoggingTradeAudit}, the completed-trade audit trail written
 * to the shared {@code com.uxplima.uxmessentials.audit} channel (the same channel the optional Discord bridge listens
 * on), mirroring the moderation, economy, and vaults audits. No player-facing {@code MessageKey} appears here; audit
 * lines are operator-facing and go through the {@link com.uxplima.uxmessentials.shared.application.port.Logger} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.adapter.outbound;
