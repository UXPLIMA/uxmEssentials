/**
 * The homes context's inbound Brigadier command handler, the single {@code /home} command and its
 * subcommand tree ({@code visit}, {@code invite}, {@code uninvite}, {@code admin}). The no-arg invocation
 * opens the slot grid; each subcommand maps a command source and its arguments onto exactly one homes
 * use-case call and is gated by its own permission node via {@code requires(...)}. All player-facing feedback
 * flows through the use cases' {@code MessageSink}, and only the players-only rejection a console may see is
 * rendered here, still through the catalog.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.adapter.inbound.command;
