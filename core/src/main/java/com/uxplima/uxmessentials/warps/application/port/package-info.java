/**
 * The warps context's outbound ports: {@code WarpRepository} for durable, server-wide warp storage,
 * {@code WarpTeleporter} for delegating the actual teleport to the teleport context, and {@code WarpEconomy}
 *. The narrow economy seam warps owns so a per-warp cost can be charged without a hard dependency on the
 * economy context. The application depends only on these interfaces; the jOOQ repository, the
 * teleport-delegating adapter, and (in P3) the economy bridge implement them in the persistence and bukkit
 * adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.application.port;
