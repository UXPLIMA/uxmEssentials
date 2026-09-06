package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The in-memory registry of live same-server trades. Each open trade is one {@link TradeExchange} indexed twice, by
 * its {@link TradeId} (so a view resolves the shared exchange from its holder) and by each participant's UUID (so a
 * disconnect or a {@code /trade} refusal can find the trade a player is already in). A same-server trade holds nothing
 * in the database, so this map is the whole store; a settled trade is removed from both indexes at once.
 *
 * <p>The maps are concurrent because the two participants' region threads touch them independently on Folia; every
 * per-trade state change is still serialised on the {@link TradeExchange} itself.
 */
@NullMarked
public final class TradeSessions {

    private final ConcurrentHashMap<TradeId, TradeExchange> byTrade = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TradeExchange> byPlayer = new ConcurrentHashMap<>();

    /** Index a freshly-opened exchange under its trade id and both participants. */
    void register(TradeExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        byTrade.put(exchange.id(), exchange);
        byPlayer.put(exchange.participant(TradeSide.INITIATOR).uuid(), exchange);
        byPlayer.put(exchange.participant(TradeSide.PARTNER).uuid(), exchange);
    }

    /** The exchange a player is currently in, or {@code null} when they are not trading. */
    @Nullable TradeExchange find(UUID player) {
        Objects.requireNonNull(player, "player");
        return byPlayer.get(player);
    }

    /** The exchange for a trade id, or {@code null} when it has already settled. */
    @Nullable TradeExchange find(TradeId tradeId) {
        Objects.requireNonNull(tradeId, "tradeId");
        return byTrade.get(tradeId);
    }

    /** Whether {@code player} is already in a trade, the {@code /trade} busy check the later phase reads. */
    public boolean isTrading(UUID player) {
        Objects.requireNonNull(player, "player");
        return byPlayer.containsKey(player);
    }

    /** Drop a settled exchange from both indexes. */
    void remove(TradeExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        byTrade.remove(exchange.id());
        byPlayer.remove(exchange.participant(TradeSide.INITIATOR).uuid());
        byPlayer.remove(exchange.participant(TradeSide.PARTNER).uuid());
    }

    /**
     * The live trade a player is in, as the published view of it, or empty when they are not trading.
     *
     * <p>Read straight off the exchange, so it is the state as it stands at the moment of the call. A trade that
     * settles a tick later does not make the answer wrong; it makes it a snapshot, which is all a live window can
     * ever be.
     */
    public Optional<TradeSnapshot> snapshot(UUID player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(byPlayer.get(player)).map(TradeSessions::snapshotOf);
    }

    /** The same snapshot for every trade open on this server. */
    public List<TradeSnapshot> snapshots() {
        return byTrade.values().stream().map(TradeSessions::snapshotOf).toList();
    }

    private static TradeSnapshot snapshotOf(TradeExchange exchange) {
        return new TradeSnapshot(
                exchange.id(),
                exchange.participant(TradeSide.INITIATOR),
                exchange.participant(TradeSide.PARTNER),
                exchange.confirmed(TradeSide.INITIATOR),
                exchange.confirmed(TradeSide.PARTNER));
    }

    /**
     * One open trade, flattened out of the exchange the two windows share.
     *
     * @param id the trade's own id
     * @param initiator the player who opened it
     * @param partner the player who accepted it
     * @param initiatorConfirmed whether the initiator has confirmed the offers as they stand
     * @param partnerConfirmed whether the partner has confirmed the offers as they stand
     */
    public record TradeSnapshot(
            TradeId id, PlayerRef initiator, PlayerRef partner, boolean initiatorConfirmed, boolean partnerConfirmed) {

        public TradeSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(initiator, "initiator");
            Objects.requireNonNull(partner, "partner");
        }
    }

    /** A snapshot of every live exchange, for draining all in-flight trades on module stop. */
    Collection<TradeExchange> all() {
        return List.copyOf(byTrade.values());
    }

    /** The number of live trades, for a doctor line. */
    public int openCount() {
        return byTrade.size();
    }
}
