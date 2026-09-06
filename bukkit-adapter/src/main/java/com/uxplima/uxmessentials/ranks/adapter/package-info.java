/**
 * The ranks context's Bukkit adapter. Phase 1 holds the {@link com.uxplima.uxmessentials.ranks.adapter.RanksWiring}
 * that assembles the context's use cases over the injected kernel ports and the shared persistence DSL: the parsed
 * ladder, the DB-backed {@code PlayerRankRepository} (through the persistence factory, never a jOOQ type) and the
 * {@code CurrentRank} read use case. The inbound command / GUI adapters and the outbound economy / permission /
 * playtime seams land under this package as the later phases do.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.adapter;
