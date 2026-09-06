package com.uxplima.uxmessentials.trade.adapter.outbound;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.TradeSignalFrame;
import com.uxplima.uxmessentials.trade.application.TradeSignal;
import com.uxplima.uxmessentials.trade.application.TradeSignalType;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the trade context's {@link TradeBus} port to the shared cross-server bus: a {@link TradeSignal} is published
 * as a {@link TradeSignalFrame} through the {@link BusPublisher}, and every inbound frame the bus dispatches to
 * {@link #onFrame} is translated back to a {@link TradeSignal} and handed to the coordinator. The wiring registers
 * {@link #onFrame} as a {@code RemoteSyncListener}, so this runs on the bus's off-tick dispatch thread, the coordinator
 * it feeds does its own region hops for any Bukkit touch, matching the {@code RemoteSyncListener} contract.
 */
@NullMarked
public final class BusTradeBus implements TradeBus {

    private final BusPublisher publisher;
    private final boolean enabled;
    private final Logger log;
    private @Nullable Consumer<TradeSignal> handler;

    public BusTradeBus(BusPublisher publisher, boolean enabled, Logger log) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.enabled = enabled;
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void send(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        publisher.publish(new TradeSignalFrame(
                signal.originServer(),
                signal.tradeId().value(),
                signal.type().name(),
                signal.from().uuid(),
                signal.from().name(),
                signal.to().uuid(),
                signal.to().name()));
    }

    @Override
    public void subscribe(Consumer<TradeSignal> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public String localServer() {
        return publisher.serverId();
    }

    @Override
    public boolean healthy() {
        return enabled;
    }

    /** Register this with the bus registry: translate a trade frame and hand it to the subscribed coordinator. */
    public void onFrame(NetworkMessage message) {
        if (!(message instanceof TradeSignalFrame frame) || handler == null) {
            return;
        }
        TradeSignalType type;
        try {
            type = TradeSignalType.valueOf(frame.signal());
        } catch (IllegalArgumentException unknown) {
            log.warn("dropping cross-server trade frame with unknown signal {}", frame.signal());
            return;
        }
        handler.accept(new TradeSignal(
                new TradeId(frame.tradeId()),
                type,
                new PlayerRef(frame.fromUuid(), frame.fromName()),
                new PlayerRef(frame.toUuid(), frame.toName()),
                frame.originServer()));
    }
}
