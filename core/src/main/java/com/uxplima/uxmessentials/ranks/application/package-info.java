/**
 * The ranks context's application layer: the {@link com.uxplima.uxmessentials.ranks.application.RanksModule}
 * feature-module identity and enable gate, the typed {@link com.uxplima.uxmessentials.ranks.application.RanksConfig}
 * over {@code modules/ranks/config.conf} (the module toggle and the prestige/autorank switches later phases read),
 * the {@link com.uxplima.uxmessentials.ranks.application.RankLadders} parser that reads {@code modules/ranks/ranks.conf}
 * into a {@link com.uxplima.uxmessentials.ranks.domain.RankLadder}, the
 * {@link com.uxplima.uxmessentials.ranks.application.RanksMessageKey} catalog handles, and the
 * {@link com.uxplima.uxmessentials.ranks.application.CurrentRank} read use case that resolves a stored pointer
 * against the ladder. Pure application code: no Bukkit, Paper, Kyori, jOOQ, or SLF4J. The rank pointer is reached
 * only through the {@link com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.application;
