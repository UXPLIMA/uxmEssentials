/**
 * The player-warps context's outbound ports: {@code PlayerWarpRepository} for durable, per-owner player-warp
 * storage and {@code PlayerWarpTeleporter} for delegating the actual teleport to the teleport context, the
 * player-warps context never re-implements movement. The application depends only on these interfaces; the
 * jOOQ repository and the teleport-delegating adapter implement them in the persistence and bukkit adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.application.port;
