/**
 * The optional Discord bridge, the {@code uxmessentials-discord} Paper plugin jar (docs/09-deployment.md
 * Path C). It mirrors host-plugin audit events and economy notifications to Discord channels and is a
 * <em>separate</em> plugin: it consumes the host plugin's ports through Bukkit's {@code ServicesManager} and
 * has no compile-time link to {@code :bukkit-adapter}.
 *
 * <p>The bridge is dormant until configured with a token ({@code discord.conf}, {@code enabled = false} on
 * first extract). JDA is the only backend; it is reached through the thin {@link
 * com.uxplima.uxmessentials.discord.DiscordGateway} port so the forwarder can be tested against a fake
 * gateway with no live connection. JDA calls never run on the server's main thread, connection and message
 * dispatch are off-tick. The {@code originServer = "discord"} sentinel marks anything the bridge itself emits
 * so a cluster never replicates a Discord-sourced notification back onto the bus.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discord;
