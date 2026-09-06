package com.uxplima.uxmessentials.vanish.application.port;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.vanish.application.VanishSync;

/**
 * The vanish context's narrow view of the cross-server bus: it publishes a local vanish {@link VanishSync change} to
 * the peer backends and delivers inbound changes to a handler, without the vanish application ever naming the shared
 * network frame types or the transport. The adapter bridges this to the shared {@code BusPublisher} /
 * {@code RemoteSyncRegistry}, translating a {@link VanishSync} to and from a wire frame, exactly as {@code TradeBus}
 * bridges the trade context.
 *
 * <p>The bus is fire-and-forget and lossy by design (a peer may miss a frame during a restart); a missed frame only
 * leaves a peer's network-vanish view briefly stale until the next change or the player's next join re-announces it.
 * When the network bus is disabled the vanish module wires {@link #disabled()}, whose publish is a no-op and whose
 * subscription is never invoked, so cross-server is inert and the single-server path is unchanged.
 */
public interface VanishBus {

    /** Publish a local vanish change to the peer backends; the origin backend never receives its own frame. */
    void publish(VanishSync change);

    /** Register the handler that receives every inbound vanish change from a peer backend. */
    void subscribe(Consumer<VanishSync> handler);

    /** Whether the bus is currently able to move frames. Informational: publish is safe regardless. */
    boolean healthy();

    /** A no-op bus for a backend that opts out of cross-server vanish: publish drops, subscribe is never invoked. */
    static VanishBus disabled() {
        return Disabled.INSTANCE;
    }

    /** The inert bus wired when {@code cross-server} is off; every seam is a harmless no-op. */
    enum Disabled implements VanishBus {
        INSTANCE;

        @Override
        public void publish(VanishSync change) {
            // No peers to notify with cross-server off.
        }

        @Override
        public void subscribe(Consumer<VanishSync> handler) {
            // No frames ever arrive, so the handler is never stored.
        }

        @Override
        public boolean healthy() {
            return false;
        }
    }
}
