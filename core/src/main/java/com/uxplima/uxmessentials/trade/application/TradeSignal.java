package com.uxplima.uxmessentials.trade.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;

/**
 * A single cross-server trade control message, carried over the bus between the two backend servers a trade spans. It
 * names the trade, the kind of signal, and the two participants (the sender {@code from} and the addressed {@code to}),
 * plus the backend that produced it. The heavy state, the escrowed items and money, is never in the signal; it lives
 * in the shared escrow table, and the signal only tells the addressed backend to re-evaluate that trade's rows.
 *
 * @param tradeId the trade both backends agree on
 * @param type what happened / what to do
 * @param from the participant whose backend produced the signal
 * @param to the participant the signal is addressed to (the local player on the receiving backend)
 * @param originServer the {@code server-id} of the backend that produced the signal
 */
@NullMarked
public record TradeSignal(TradeId tradeId, TradeSignalType type, PlayerRef from, PlayerRef to, String originServer) {

    public TradeSignal {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(originServer, "originServer");
        if (originServer.isBlank()) {
            throw new IllegalArgumentException("originServer must not be blank");
        }
    }
}
