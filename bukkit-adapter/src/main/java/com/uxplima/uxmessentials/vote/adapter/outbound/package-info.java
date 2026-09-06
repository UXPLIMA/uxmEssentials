/**
 * The vote context's outbound adapters. {@code BukkitRewardApplier} applies a resolved reward grant, console
 * commands, MiniMessage messages and broadcasts, and item grants for an online voter (dropping overflow at the
 * voter's feet), and queued commands for an offline one. {@code BukkitVoteContext} supplies the engine its
 * world, permission, online, and chance-roll seams. {@code BukkitRewardDispatcher} runs reward console commands
 * from the console sender on the global region thread (with the {@code {player}} substitution) for the
 * offline-drain path, and {@code BukkitVoteAudience} snapshots the online players as kernel {@code PlayerRef}s
 * for the party rewards and thank-you broadcast. All translate between Bukkit handles and the kernel value
 * objects behind the vote context's application ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.adapter.outbound;
