/**
 * The worlds context's Bukkit listeners: {@code ForceGamemodeListener} switches a player to the
 * {@code force-gamemode} configured for the world they enter on join and world-change, unless they hold the
 * bypass node. The world's forced mode is read from the Caffeine-warmed {@code WorldRepository} snapshot
 * (tick-safe) and the gamemode mutation is routed through the {@code Scheduler} port so it lands on the
 * player's owning entity thread, valid on Folia.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;
