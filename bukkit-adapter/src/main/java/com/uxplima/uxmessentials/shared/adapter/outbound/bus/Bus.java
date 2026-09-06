package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The handle the context wirings receive to opt into cross-server sync. It exposes the two seams a context
 * needs. The {@link BusPublisher} to announce its local mutations and the {@link RemoteSyncRegistry} to
 * register its cache-invalidation listener: without handing out the {@link BusCore} itself. A context
 * wires its sync identically whether the bus is enabled or not: when disabled, the publisher is a no-op and
 * the registered listeners are never invoked, so the broadcasting decorator and listener are always safe to
 * wire and the single-server path is unchanged.
 *
 * @param publisher the outbound seam contexts announce mutations through
 * @param registry the inbound seam contexts register their remote-change listeners with
 */
@NullMarked
public record Bus(BusPublisher publisher, RemoteSyncRegistry registry) {

    public Bus {
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(registry, "registry");
    }

    /** A bus whose publisher discards every frame and whose registry is never read: for a disabled cluster. */
    public static Bus disabled(String serverId) {
        return new Bus(new NoOpBusPublisher(serverId), new RemoteSyncRegistry());
    }

    /** The publisher that drops every frame; used when the backend opts out of network sync. */
    private record NoOpBusPublisher(String serverId) implements BusPublisher {

        private NoOpBusPublisher {
            Objects.requireNonNull(serverId, "serverId");
        }

        @Override
        public void publish(NetworkMessage message) {
            // Intentionally discarded: with the bus disabled there are no peers to notify.
        }
    }
}
