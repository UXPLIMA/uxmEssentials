/**
 * The survival context's domain: the pure value objects and algorithms behind the opt-in gameplay mechanics. The
 * headline is {@link com.uxplima.uxmessentials.survival.domain.ConnectedBlockSearch}, a bounded flood-fill over an
 * abstract {@link com.uxplima.uxmessentials.survival.domain.BlockPos} grid shared by tree-feller (connected logs) and
 * veinminer (a connected ore vein). Pure Java: no Bukkit, Paper, Kyori, or SLF4J. The Bukkit blocks and items live
 * behind the adapter listeners that feed a neighbour predicate into the search.
 */
@NullMarked
package com.uxplima.uxmessentials.survival.domain;

import org.jspecify.annotations.NullMarked;
