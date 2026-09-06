/**
 * Pure domain of the ranks bounded context: the {@link com.uxplima.uxmessentials.ranks.domain.RankId} identity, the
 * {@link com.uxplima.uxmessentials.ranks.domain.Rank} value (its order, display name, cost and the still-opaque
 * requirement/action strings later phases parse), the ordered {@link com.uxplima.uxmessentials.ranks.domain.RankLadder}
 * that answers first / next / contains, the {@link com.uxplima.uxmessentials.ranks.domain.Prestige} level, and the two
 * pointers a player carries. The stored {@link com.uxplima.uxmessentials.ranks.domain.PlayerRank} (raw rank id +
 * prestige) and the resolved {@link com.uxplima.uxmessentials.ranks.domain.RankStanding} (the ladder rank + prestige).
 * The model owns the ladder math; the application resolves a stored pointer against the ladder and the adapters
 * persist it. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.domain;
