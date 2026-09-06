package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VanishStateChanged;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the vanish context's {@link VanishBus} port to the shared cross-server bus: a local {@link VanishSync} is
 * published as a {@link VanishStateChanged} frame through the {@link BusPublisher}, and every inbound frame the bus
 * dispatches to {@link #onFrame} is translated back to a {@link VanishSync} and handed to the subscribed handler. The
 * wiring registers {@link #onFrame} as a {@code RemoteSyncListener}, so this runs on the bus's off-tick dispatch
 * thread. The handler it feeds ({@link VanishNetworkApplier}) does its own region hop for any Bukkit touch, matching
 * the {@code RemoteSyncListener} contract. This is {@code BusTradeBus} copied for the vanish frame.
 *
 * <p>The publisher stamps this backend's {@code server-id} as the frame origin, so the shared loop sentinel drops the
 * echo on the origin backend. With the network bus disabled the injected publisher is the no-op one, so publishing is
 * silently discarded and {@link #healthy()} reports false.
 */
@NullMarked
public final class BusVanishBus implements VanishBus {

    private final BusPublisher publisher;
    private final boolean enabled;
    private @Nullable Consumer<VanishSync> handler;

    public BusVanishBus(BusPublisher publisher, boolean enabled) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.enabled = enabled;
    }

    @Override
    public void publish(VanishSync change) {
        Objects.requireNonNull(change, "change");
        publisher.publish(new VanishStateChanged(
                publisher.serverId(),
                change.player(),
                change.playerName(),
                change.vanished(),
                change.level().level()));
    }

    @Override
    public void subscribe(Consumer<VanishSync> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public boolean healthy() {
        return enabled;
    }

    /** Register this with the bus registry: translate a vanish frame and hand it to the subscribed applier. */
    public void onFrame(NetworkMessage message) {
        if (!(message instanceof VanishStateChanged frame) || handler == null) {
            return;
        }
        handler.accept(
                new VanishSync(frame.player(), frame.playerName(), frame.vanished(), VanishLevel.of(frame.level())));
    }
}
