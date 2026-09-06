/**
 * The warps context's use cases and outbound ports. The use cases orchestrate the {@code Warp} aggregate
 * through the {@code WarpRepository} port, gate {@code /warp} through {@code WarpAccess} (the per-warp
 * permission, the warp's optional extra permission, and, only when an economy provider is present, the
 * per-warp cost), render feedback through the {@code Messages}/{@code MessageSink} pair, and delegate the
 * actual teleport to the teleport context through the {@code WarpTeleporter} port, warps never
 * re-implements movement. The economy coupling is <em>soft</em>: the {@code WarpEconomy} seam is injected as
 * an {@code Optional}, so a warp's cost is charged when economy is wired (P3) and ignored gracefully when it
 * is not. The {@code WarpsModule} declares the context's commands and enable gate. No Bukkit, Paper, Kyori,
 * or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.application;
