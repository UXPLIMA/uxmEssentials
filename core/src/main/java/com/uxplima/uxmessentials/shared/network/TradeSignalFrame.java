package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A cross-server trade control frame. The wire form of a trade signal exchanged between the two backends a trade
 * spans (the rendezvous, the {@code READY}/{@code COMMIT}/{@code ABORT} of the two-phase commit). Unlike the other
 * frames it addresses a specific participant ({@link #toUuid()}), because a trade control message concerns exactly the
 * two players in the trade, not a whole-cluster cache invalidation; a backend that does not host {@link #toUuid()}
 * ignores it. The heavy state, the escrowed items and money, is never carried here; it lives in the shared
 * {@code trade_escrow} table, and this frame only tells the addressed backend to re-evaluate that trade's rows, so it
 * stays true to the "notification, not source of truth" contract.
 *
 * @param originServer the backend that produced the signal
 * @param tradeId the trade both backends agree on
 * @param signal the signal kind, as the {@code TradeSignalType} name (kept as text so this shared frame never depends
 *     on a feature context's enum)
 * @param fromUuid the participant whose backend produced the signal
 * @param fromName the sender's display name, for the receiving backend's notifications
 * @param toUuid the addressed participant. The local player on the receiving backend
 * @param toName the addressed participant's display name
 */
public record TradeSignalFrame(
        String originServer, UUID tradeId, String signal, UUID fromUuid, String fromName, UUID toUuid, String toName)
        implements NetworkMessage {

    public TradeSignalFrame {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(fromUuid, "fromUuid");
        Objects.requireNonNull(fromName, "fromName");
        Objects.requireNonNull(toUuid, "toUuid");
        Objects.requireNonNull(toName, "toName");
    }

    @Override
    public MessageType type() {
        return MessageType.TRADE_SIGNAL;
    }
}
