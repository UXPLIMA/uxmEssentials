package com.uxplima.uxmessentials.trade.application;

/**
 * The kind of a cross-server {@link TradeSignal}. A signal is a lightweight poke over the bus that tells the peer
 * "the shared escrow rows for this trade changed. Re-evaluate"; the authoritative state lives in the escrow table,
 * never in the frame (the bus "notification, not source of truth" contract).
 *
 * <ul>
 *   <li>{@link #INVITE} / {@link #ACCEPT} / {@link #DECLINE}. The rendezvous: a {@code /trade} to a player on another
 *       backend, answered by the target's backend, before either side escrows anything.
 *   <li>{@link #READY}. The sender escrowed its side (its row is {@code HELD}); the receiver re-checks whether both
 *       sides are now escrowed and, if so, crosses the atomic commit point.
 *   <li>{@link #COMMIT}. The sender crossed the commit point; the receiver delivers the counterpart's goods to its
 *       own player.
 *   <li>{@link #ABORT}: the sender refunded/cancelled its side; the receiver refunds its own side if still refundable.
 * </ul>
 */
public enum TradeSignalType {
    INVITE,
    ACCEPT,
    DECLINE,
    READY,
    COMMIT,
    ABORT
}
