package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.jspecify.annotations.NullMarked;

/**
 * The transport-agnostic heart of the cross-server bus. Everything that does not depend on <em>how</em> bytes
 * are moved lives here: encoding an outbound {@link NetworkMessage} to its wire frame and handing it to the
 * {@link BusTransport}, decoding an inbound frame, dropping the ones this backend originated (the replication
 * loop sentinel), and dispatching the rest to the {@link RemoteSyncRegistry}. The "how bytes are moved" half
 * the plugin-messaging carrier flush today, Redis pub/sub tomorrow, sits behind the {@link BusTransport} seam.
 *
 * <p>It implements {@link BusPublisher}, the narrow outbound surface a context's broadcasting decorator holds:
 * a {@link #publish} encodes the frame (origin already stamped into the message by the caller) and forwards
 * the bytes to {@link BusTransport#send}. A disabled or unhealthy transport discards the bytes, so a publish
 * stays a safe no-op and the single-server path is unchanged.
 *
 * <h2>Loop sentinel</h2>
 * Every frame carries its origin {@code server-id}. On receipt {@link #onFrame} drops any frame whose origin
 * equals {@link #serverId()}, this backend's own mutation echoed back, so a write is applied on every peer
 * once and never bounces around the cluster ({@code docs/02-concurrency.md}).
 */
@NullMarked
final class BusCore implements BusPublisher {

    private final BusTransport transport;
    private final String serverId;
    private final RemoteSyncRegistry registry;
    private final Logger log;

    BusCore(BusTransport transport, String serverId, RemoteSyncRegistry registry, Logger log) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Start the underlying transport, routing every received frame's bytes through {@link #onFrame}. */
    void start() {
        transport.start(this::onFrame);
    }

    /** Stop the underlying transport and release its resources. Idempotent. */
    void stop() {
        transport.stop();
    }

    /** A live read of whether the underlying transport can currently deliver frames. Cheap; never blocks. */
    boolean healthy() {
        return transport.healthy();
    }

    /** This backend's {@code server-id}: the origin stamped into outbound frames and the loop sentinel. */
    @Override
    public String serverId() {
        return serverId;
    }

    /**
     * Encode {@code message} (its origin already stamped by the caller) and hand the bytes to the transport
     * for delivery to peers. Fire-and-forget: a disabled or unhealthy transport simply discards the frame.
     */
    @Override
    public void publish(NetworkMessage message) {
        Objects.requireNonNull(message, "message");
        transport.send(NetworkMessageCodec.encode(message));
    }

    /**
     * Decode one inbound frame, drop it if this backend originated it, otherwise dispatch it to every
     * registered listener. A malformed frame is logged and dropped, never half-applied.
     */
    void onFrame(byte[] frame) {
        NetworkMessage message;
        try {
            message = NetworkMessageCodec.decode(frame);
        } catch (IllegalArgumentException malformed) {
            log.warn("dropping malformed inbound bus frame: {}", String.valueOf(malformed.getMessage()));
            return;
        }
        if (serverId.equals(message.originServer())) {
            // The loop sentinel: our own mutation echoed back. Drop it.
            return;
        }
        registry.dispatch(message, this::logListenerFailure);
    }

    private void logListenerFailure(NetworkMessage message, RuntimeException failure) {
        // One listener failing must not starve the others or wedge the dispatch loop.
        log.error("remote sync listener failed for " + message.type(), failure);
    }
}
