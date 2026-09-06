/**
 * The command-control context's Bukkit listeners.
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener} gates every player
 * command on the {@link com.uxplima.uxmessentials.commandcontrol.domain.RuleSet} whitelist / blacklist, cancelling a
 * denied dispatch and sending the configured deny line;
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandVisibilityListener} keeps disallowed
 * and hidden commands out of what a client sees. Scrubbing the sent command list, filtering tab completion, and
 * blocking the plugin-listing / help commands from executing (scrub-help);
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.outbound.BukkitPlayerFacts} adapts the live
 * {@code Player} to the domain's Bukkit-free player-facts view with a lazy group lookup.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;
