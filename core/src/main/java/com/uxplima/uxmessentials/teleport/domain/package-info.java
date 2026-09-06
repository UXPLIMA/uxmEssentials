/**
 * Pure domain of the teleport bounded context: the {@code TeleportRequest} aggregate, the
 * {@code /back} capture, the random-teleport safe-location search policy, the warmup/cooldown decision
 * rules (including the move-cancels-warmup invariant and the cooldown-start-phase semantics), and the
 * per-world respawn chain. No Bukkit, Paper, Kyori, or logging type appears here. The model is value
 * objects, sealed states, and the cross-cutting kernel primitives ({@code PlayerRef}, {@code WorldRef},
 * {@code Position}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.teleport.domain;
