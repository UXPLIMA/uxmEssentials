package com.uxplima.uxmessentials.trade.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;

/**
 * One side's escrowed stake in a cross-server trade, the durable, crash-safe record of what an owner has put on the
 * table, held in the shared {@code trade_escrow} table so both backends (and a rejoining player) can reconcile the
 * trade from it. The two participants each own one row, keyed by {@code (tradeId, owner)}; the {@code phase} tracks the
 * two-phase commit, and the two servers move only their own row's phase through single guarded transitions.
 *
 * <p>The items are an opaque, adapter-owned string ({@code itemData}. The adapter serialises the real stacks to bytes
 * and encodes them to text, and decodes them back to deliver), so the application never sees a Bukkit stack, mirroring
 * the domain's opaque item handle. Money is carried per currency id (the same shape a
 * {@link com.uxplima.uxmessentials.trade.domain.TradeOffer} stakes) so a debited amount is refunded or delivered in the
 * exact currency it was staked. A blank {@code itemData} and an empty money map mean nothing of that kind was staked.
 *
 * @param tradeId the trade this stake belongs to
 * @param owner the player who escrowed this stake (whose money was debited and items removed)
 * @param ownerServer the {@code server-id} of the backend the owner is on
 * @param counterparty the player on the other backend the owner is trading with
 * @param counterpartyServer the {@code server-id} of the counterparty's backend
 * @param itemData the owner's escrowed item stacks, opaque to the application; blank when no items were staked
 * @param itemCount the total quantity of items staked, for the audit receipt (the application never decodes the data)
 * @param money the owner's escrowed money per currency id; empty when no money was staked
 * @param phase the two-phase-commit lifecycle phase of this row
 * @param createdAt when the stake was escrowed
 */
@NullMarked
public record TradeEscrow(
        TradeId tradeId,
        PlayerRef owner,
        String ownerServer,
        PlayerRef counterparty,
        String counterpartyServer,
        String itemData,
        int itemCount,
        Map<String, BigDecimal> money,
        TradeEscrowPhase phase,
        Instant createdAt) {

    public TradeEscrow {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerServer, "ownerServer");
        Objects.requireNonNull(counterparty, "counterparty");
        Objects.requireNonNull(counterpartyServer, "counterpartyServer");
        Objects.requireNonNull(itemData, "itemData");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(createdAt, "createdAt");
        money = Map.copyOf(money);
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must not be negative: " + itemCount);
        }
    }

    /** A fresh {@code HELD} escrow row for {@code owner}'s freshly-staked side of a trade. */
    public static TradeEscrow held(
            TradeId tradeId,
            PlayerRef owner,
            String ownerServer,
            PlayerRef counterparty,
            String counterpartyServer,
            String itemData,
            int itemCount,
            Map<String, BigDecimal> money,
            Instant createdAt) {
        return new TradeEscrow(
                tradeId,
                owner,
                ownerServer,
                counterparty,
                counterpartyServer,
                itemData,
                itemCount,
                money,
                TradeEscrowPhase.HELD,
                createdAt);
    }

    /** The same stake at a new lifecycle phase: the immutable transition an escrow store applies to a row. */
    public TradeEscrow withPhase(TradeEscrowPhase next) {
        Objects.requireNonNull(next, "next");
        return new TradeEscrow(
                tradeId,
                owner,
                ownerServer,
                counterparty,
                counterpartyServer,
                itemData,
                itemCount,
                money,
                next,
                createdAt);
    }

    /** Whether this stake holds any items: a money-only side may be settled without the recipient being online. */
    public boolean hasItems() {
        return !itemData.isBlank();
    }
}
