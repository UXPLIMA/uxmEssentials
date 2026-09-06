/**
 * The ranks context's outbound ports. Phase 1 owns one: the
 * {@link com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository} contract for the DB-backed rank
 * pointer (the raw rank id and prestige a player carries), implemented by the jOOQ adapter in
 * {@code persistence-adapter}. The rank pointer is DB-backed and survives a world rollback, never PDC, the same
 * hard invariant the economy ledger holds, so the store is the authority, not any live permission state.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.application.port;
