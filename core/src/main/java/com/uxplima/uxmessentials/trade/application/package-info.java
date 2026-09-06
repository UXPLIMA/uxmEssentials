/**
 * Application layer of the trade bounded context: the {@link com.uxplima.uxmessentials.trade.application.TradeModule}
 * feature-module identity and enable gate, the typed {@link com.uxplima.uxmessentials.trade.application.TradeConfig}
 * view of {@code modules/trade/config.conf}, and the
 * {@link com.uxplima.uxmessentials.trade.application.TradeMessageKey} catalog of player-facing notices. Phase 1 stands
 * up the module skeleton and the pure state machine only; the trade-window use cases, the request/accept flow, and
 * the cross-server escrow orchestration land in the later phases behind this same module. No Bukkit, Paper, Kyori, or
 * infrastructure type appears here: the layer speaks only the domain and the shared kernel ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.application;
