/**
 * The trade context's outbound ports: {@link com.uxplima.uxmessentials.trade.application.port.TradeEconomy}, the
 * narrow economy seam trade owns so staked money can move without a hard dependency on the economy context (mirroring
 * {@code KitEconomy} / {@code PlayerWarpEconomy}). The application depends only on this interface; the economy bridge
 * that fronts the {@code EconomyProvider} implements it in the bukkit adapter, and is injected only when economy is
 * wired. No Bukkit, Paper, Kyori, or provider-SDK type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.application.port;
