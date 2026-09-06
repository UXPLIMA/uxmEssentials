/**
 * The ranks context's outbound persistence adapter: the jOOQ {@link
 * com.uxplima.uxmessentials.persistence.ranks.JooqPlayerRankRepository} over the generated V74 {@code player_ranks}
 * table, implementing the {@code PlayerRankRepository} port with the raw rank id and prestige as first-class
 * columns. The rank pointer is DB-backed and survives a world rollback, never PDC, the same hard invariant the
 * economy ledger holds. The bukkit-adapter wires the repository from here without naming a jOOQ type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.ranks;
