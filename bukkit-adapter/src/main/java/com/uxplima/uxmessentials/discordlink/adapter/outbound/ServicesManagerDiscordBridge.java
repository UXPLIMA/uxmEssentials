package com.uxplima.uxmessentials.discordlink.adapter.outbound;

import java.util.Objects;

import org.bukkit.plugin.ServicesManager;

import com.uxplima.uxmessentials.api.link.DiscordBridgePresence;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the Discord bridge's presence off the Bukkit {@code ServicesManager}: the optional bridge jar registers
 * a {@link DiscordBridgePresence} once its gateway connects and the host's confirmation seam is wired, and
 * unregisters it on shutdown. The bridge is a separate plugin with its own lifecycle, so the registration
 * comes and goes after the host has enabled. The lookup is therefore live on every call, never cached, so a
 * bridge that connects (or drops) after the host started is reflected immediately.
 *
 * <p>This is the only place the discord-link context touches a platform service registry; the use cases see
 * just the {@link DiscordBridge} port. The default deployment ships no bridge, so the lookup returns
 * {@code null} and {@link #available()} answers {@code false}.
 */
@NullMarked
public final class ServicesManagerDiscordBridge implements DiscordBridge {

    private final ServicesManager services;

    public ServicesManagerDiscordBridge(ServicesManager services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public boolean available() {
        return services.getRegistration(DiscordBridgePresence.class) != null;
    }
}
