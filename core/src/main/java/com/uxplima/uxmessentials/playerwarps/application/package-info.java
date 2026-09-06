/**
 * The player-warps context's use cases and outbound ports. The use cases orchestrate the per-owner
 * {@code PlayerWarp} aggregate through the {@code PlayerWarpRepository} port, gate {@code /pwarp} by ownership
 * and the warp's public flag, resolve the per-owner count limit through {@code PlayerWarpQuota}, render
 * feedback through the {@code Messages}/{@code MessageSink} pair, and delegate the actual teleport to the
 * teleport context through the {@code PlayerWarpTeleporter} port — player-warps never re-implements movement.
 * The {@code PlayerwarpsModule} declares the context's commands and enable gate. No Bukkit, Paper, Kyori, or
 * logging type appears here.
 *
 * <p>The bypass nodes are checked here and hold everywhere; the per-verb nodes are not, and do not.
 * {@link com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp} tests the ban, password, whitelist,
 * safety and cost bypasses inside the use case, so every adapter inherits them. The twenty-odd capability
 * nodes {@code /pwarp} gates its verbs on ({@code uxmessentials.pwarp.rename}, {@code .icon}, {@code .price},
 * {@code .sponsor}, {@code .description} and the rest) are checked in the command adapter only, and the editor
 * and manage menus drive rename, icon, price and sponsorship without them. These are the nodes a server grants
 * per rank, so the GUI hands out what the command sells. Warps and kits check their per-item node inside the
 * use case, which is the shape this needs. A known open defect, not a design.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.application;
