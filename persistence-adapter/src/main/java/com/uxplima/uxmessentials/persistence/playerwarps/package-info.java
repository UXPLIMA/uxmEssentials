/**
 * The player-warps context's outbound persistence adapter: the jOOQ {@code PlayerWarpRepository} over the
 * generated DSL and its Caffeine read-cache decorator keyed by owner uuid. Every warp fact is a first-class
 * column in the {@code player_warps} table (owner, name, world uid + name, coordinates, public flag, creation
 * time), there is no opaque JSON blob, so a {@code PlayerWarp} is rebuilt from queryable rows. Player-warps
 * are owned per player and keyed by {@code (owner, name)}, and each owner's small set is cached whole. SQL is
 * issued only through the typed jOOQ DSL, never string concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.playerwarps;
