package com.uxplima.uxmessentials.trade.application;

/**
 * The lifecycle phase of one side's escrowed goods in a cross-server trade, held as the {@code phase} column of that
 * side's escrow row. Each side owns exactly one row, and the row advances through these phases by single guarded
 * transitions so a crash or a duplicate bus signal can never move a side's goods to two places.
 *
 * <ul>
 *   <li>{@link #HELD}: the owner escrowed: their money is debited and their items removed, both held in the row. A
 *       side may still {@code refund} (back out) only while {@code HELD}.
 *   <li>{@link #COMMITTED}. Both sides were {@code HELD} and the point of no return was crossed atomically (both
 *       rows flip together in one transaction), so neither side can refund any more.
 *   <li>{@link #CLAIMED}: the counterparty has taken these goods and delivered them to its own player. A row reaches
 *       {@code CLAIMED} exactly once (the guarded {@code COMMITTED → CLAIMED} transition is the delivery gate).
 * </ul>
 */
public enum TradeEscrowPhase {
    HELD,
    COMMITTED,
    CLAIMED
}
