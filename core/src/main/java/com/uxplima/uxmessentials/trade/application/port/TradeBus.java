package com.uxplima.uxmessentials.trade.application.port;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.trade.application.TradeSignal;

/**
 * The trade context's narrow view of the cross-server bus: it publishes a {@link TradeSignal} to the peers and
 * delivers inbound signals to a handler, without the trade application ever naming the shared network frame types or
 * the transport. The adapter bridges this to the shared {@code BusPublisher} / {@code RemoteSyncRegistry}, translating
 * a {@link TradeSignal} to and from a wire frame, exactly as {@code TradeEconomy} bridges to the economy provider.
 *
 * <p>The bus is fire-and-forget and lossy by design (a peer may miss a frame during a restart); the escrow table is
 * the source of truth, so a lost signal only delays a step that a rejoin then reconciles. {@link #localServer()} is
 * the {@code server-id} the trade stamps onto its own escrow rows and signals.
 */
public interface TradeBus {

    /** Publish a signal to the peer backends; the origin backend never receives its own frame. */
    void send(TradeSignal signal);

    /** Register the handler that receives every inbound trade signal from a peer backend. */
    void subscribe(Consumer<TradeSignal> handler);

    /** This backend's {@code server-id}, stamped onto the escrow rows and signals it produces. */
    String localServer();

    /** Whether the bus is currently connected: a {@code /trade} to a remote player needs a healthy bus. */
    boolean healthy();
}
