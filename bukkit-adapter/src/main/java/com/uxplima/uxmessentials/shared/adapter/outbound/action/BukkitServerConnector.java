package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-backed {@link ServerConnector}: it registers the standard proxy plugin-message channel
 * ({@code BungeeCord}, which Velocity also honours) once on construction and sends a {@code Connect} subchannel
 * frame through the clicking player's own connection to move them to another backend. A plugin message rides a
 * player connection, so the {@code viewer} carries the frame directly. With no proxy in front of the server the
 * frame is simply discarded by the vanilla handler, a harmless no-op, which is the degraded single-server
 * behaviour.
 */
@NullMarked
public final class BukkitServerConnector implements ServerConnector {

    /** Bukkit's well-known proxy channel; Velocity listens on the same legacy name for {@code Connect}. */
    private static final String CHANNEL = "BungeeCord";

    private static final String CONNECT_SUBCHANNEL = "Connect";

    private final Plugin plugin;
    private final Logger log;

    public BukkitServerConnector(Plugin plugin, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.log = Objects.requireNonNull(log, "log");
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL);
    }

    @Override
    public void connect(Player player, String server) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        if (!isAvailable()) {
            log.warn("event=click_connect_skipped reason=channel_unregistered server={}", server);
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, connectFrame(server));
    }

    /**
     * Stop hook, intentionally a no-op. The {@code BungeeCord} channel is registered against the plugin, not this
     * connector, and is shared by every module that connects players ({@code npc} and {@code holograms} both build
     * their own connector over the same channel). Unregistering here would pull the channel out from under a
     * sibling module that is still enabled, so we leave it: Paper unregisters all of a plugin's outgoing channels
     * automatically on disable, which is the only point at which the channel should actually go away.
     */
    public void close() {
        // No per-module teardown, see the method contract above.
    }

    // Package-private rather than private so the connector's own test can decode and assert the exact
    // "Connect" + server frame the proxy expects, without sending a real plugin message.
    static byte[] connectFrame(String server) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(CONNECT_SUBCHANNEL);
            out.writeUTF(server);
        } catch (IOException impossible) {
            // A ByteArrayOutputStream never does real I/O, so this branch cannot run in practice.
            throw new UncheckedIOException("encoding a BungeeCord Connect frame", impossible);
        }
        return bytes.toByteArray();
    }
}
