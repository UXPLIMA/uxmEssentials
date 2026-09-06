package com.uxplima.uxmessentials.trade.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.trade.application.TradeEscrow;
import com.uxplima.uxmessentials.trade.domain.TradeId;

/**
 * The durable escrow of the two sides of a cross-server trade, backing the two-phase commit. It is the source of truth
 * a crashed or rejoining backend reconciles from (the bus signal is only a poke to re-read it), so every state change
 * is a single guarded transition that either wins or is a no-op, a duplicate signal, a double region hop, or a
 * rejoin can never move a side's goods twice.
 *
 * <p>Both participants' rows live in one shared network database (SQLite is single-server, so cross-server trading runs
 * on a network backend), keyed by {@code (tradeId, owner)}. A side moves only its own row's phase, except the atomic
 * {@link #commitBoth} that flips both rows together and the {@link #claim} a counterparty runs to take a committed
 * row's goods.
 */
public interface TradeEscrowStore {

    /** Persist a fresh {@code HELD} stake, replacing any stale row for the same {@code (tradeId, owner)}. */
    void escrow(TradeEscrow escrow);

    /** The stake a player escrowed for a trade, or empty when they hold none (never staked, or already resolved). */
    Optional<TradeEscrow> find(TradeId tradeId, UUID owner);

    /** Every stake a player currently owns across all trades: the set a rejoining player reconciles. */
    List<TradeEscrow> findByOwner(UUID owner);

    /**
     * Atomically cross the point of no return for a trade: if <em>both</em> participants' rows are {@code HELD}, flip
     * them together to {@code COMMITTED} in one transaction and return {@code true}; otherwise change nothing and
     * return {@code false}. Because both rows flip together, a side can never be {@code COMMITTED} while its
     * counterpart is still refundable, which is what makes the two-sided commit safe against a concurrent back-out.
     */
    boolean commitBoth(TradeId tradeId, UUID a, UUID b);

    /**
     * Take a committed row's goods for delivery: the guarded {@code COMMITTED → CLAIMED} transition on the
     * {@code owner}'s row, run by the counterparty. Returns {@code true} exactly once per row, so the counterpart's
     * goods are delivered to the recipient exactly once even under a duplicate signal or a rejoin.
     */
    boolean claim(TradeId tradeId, UUID owner);

    /**
     * Begin a refund of a still-refundable stake: the guarded delete of the {@code owner}'s row while it is
     * {@code HELD}. Returns {@code true} when this call won the row (the caller then returns the goods to the owner)
     * and {@code false} when the row was already committed or already refunded (nothing to return).
     */
    boolean beginRefund(TradeId tradeId, UUID owner);

    /** Unconditionally drop a resolved row: cleanup once both sides of a trade have been claimed. */
    void clear(TradeId tradeId, UUID owner);
}
