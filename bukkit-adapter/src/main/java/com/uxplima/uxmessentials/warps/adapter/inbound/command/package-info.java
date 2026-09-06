/**
 * The warps context's inbound Brigadier command handler, the single {@code /warp} command and its
 * subcommand tree ({@code list}, {@code set}, {@code del}, {@code info}, {@code move}, plus the per-warp
 * {@code lock}/{@code password}/{@code rate}/{@code rating}/{@code edit} actions). {@code /warp <name>}
 * teleports; each subcommand maps a command source and its arguments onto exactly one warps use-case call and
 * is gated by its own permission node via {@code requires(...)}. All player-facing feedback flows through the
 * use cases' {@code MessageSink}, and only the players-only rejection a console may see is rendered here,
 * still through the catalog. The base {@code uxmessentials.warp.use} node gates the command itself; the
 * per-warp {@code uxmessentials.warp.use.<warp>} gate and the optional cost are checked inside the
 * {@code UseWarp} use case per destination.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.adapter.inbound.command;
