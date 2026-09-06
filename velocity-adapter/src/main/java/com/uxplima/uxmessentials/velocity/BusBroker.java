package com.uxplima.uxmessentials.velocity;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The fan-out core of the proxy broker, separated from the Velocity-lifecycle shell so the relay decision is
 * plain Java. It takes a {@link PluginMessageEvent} that already matched the bus channel, decodes the frame
 * to read its origin server id, then sends the same bytes to every backend <em>except</em> the origin.
 *
 * <p>Two things make the relay safe against replication loops ({@code docs/02-concurrency.md}):
 *
 * <ol>
 *   <li>It only relays frames whose source is a {@link ServerConnection}, a backend → proxy message. A
 *       frame the proxy itself sent to a backend arrives with a different source type and is never
 *       re-relayed, so the broker cannot bounce its own output.
 *   <li>It skips the origin backend in the fan-out (origin read from the decoded frame), and additionally
 *       skips the connection's own server. The receiving backend's bus client also drops any frame whose
 *       origin equals its own id, belt and suspenders, so a mutation reaches each peer exactly once.
 * </ol>
 *
 * <p>Every relayed frame is marked {@link PluginMessageEvent.ForwardResult#handled() handled} so the proxy
 * does not also forward it onward through its default plugin-message routing. A frame that fails to decode
 * (a truncated or version-incompatible payload) is dropped with a logged warning rather than relayed blind.
 */
@NullMarked
final class BusBroker {

    private final ProxyServer proxy;
    private final Logger logger;
    private final MinecraftChannelIdentifier channel;

    BusBroker(ProxyServer proxy, Logger logger) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.channel = MinecraftChannelIdentifier.create(BusChannel.NAMESPACE, BusChannel.NAME);
    }

    /** Relay {@code event}'s frame to every backend but its origin, then mark it handled. */
    void relay(PluginMessageEvent event) {
        Objects.requireNonNull(event, "event");
        // Only relay backend → proxy frames; mark every bus frame handled so Velocity does not also route it.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection sourceConnection)) {
            return;
        }
        String origin = decodeOrigin(event.getData());
        if (origin == null) {
            return;
        }
        fanOut(event.getData(), origin, sourceConnection.getServerInfo().getName());
    }

    private void fanOut(byte[] frame, String origin, String sourceServerName) {
        int delivered = 0;
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            // The loop sentinel: never echo a frame back to the backend that produced it. We skip both the
            // declared origin id and the physical source server (in case the two names diverge in config).
            if (name.equals(origin) || name.equals(sourceServerName)) {
                continue;
            }
            if (server.sendPluginMessage(channel, frame)) {
                delivered++;
            }
        }
        logger.debug("relayed bus frame from {} to {} peer(s)", origin, delivered);
    }

    private @org.jspecify.annotations.Nullable String decodeOrigin(byte[] frame) {
        try {
            NetworkMessage message = NetworkMessageCodec.decode(frame);
            return message.originServer();
        } catch (IllegalArgumentException malformed) {
            logger.warn("dropping malformed bus frame ({} bytes): {}", frame.length, malformed.getMessage());
            return null;
        }
    }
}
