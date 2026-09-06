/**
 * The invrollback context's domain: the value objects behind an inventory snapshot, the
 * {@link com.uxplima.uxmessentials.invrollback.domain.Snapshot} a player's inventory was frozen into at a moment
 * of interest, its {@link com.uxplima.uxmessentials.invrollback.domain.SnapshotId} identity, the
 * {@link com.uxplima.uxmessentials.invrollback.domain.SnapshotCause} that triggered it, and the pure
 * {@link com.uxplima.uxmessentials.invrollback.domain.RetentionPolicy} that decides which of a player's snapshots
 * to prune given a per-player cap and a maximum age. The serialized inventory is opaque {@code byte[]} at this
 * boundary, the domain never sees a Bukkit {@code ItemStack}; the adapter maps the two. Pure Java: no Bukkit,
 * Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.invrollback.domain;

import org.jspecify.annotations.NullMarked;
