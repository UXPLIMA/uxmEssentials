package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import com.uxplima.uxmessentials.shared.network.NetworkMessage;

/**
 * The outbound half of the bus a context's repository decorator uses to announce a local mutation to peers.
 * Implemented by {@link BusCore}; a context never holds the core directly, only this narrow publish surface.
 * The publisher stamps the origin and routes the frame. A disabled bus makes every publish a no-op, so a
 * broadcasting decorator is safe to wire unconditionally and the single-server path is unchanged.
 *
 * <p>{@link #serverId()} is exposed so a decorator can stamp the same origin the publisher uses, keeping the
 * frame's origin and the loop sentinel in agreement.
 */
public interface BusPublisher {

    /** Stamp {@code message} with this backend's origin and route it to peers; a no-op when disabled. */
    void publish(NetworkMessage message);

    /** This backend's {@code server-id}, the origin a context stamps into the frames it builds. */
    String serverId();
}
