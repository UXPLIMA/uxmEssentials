package com.uxplima.uxmessentials.trade.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.application.port.TradeItemDelivery;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;

/**
 * The cross-server trade coordinator. The pure two-phase-commit engine that moves two players' escrowed goods between
 * two backend servers with no loss or duplication on any path (commit, refund, decline, timeout, or a crash mid-flight
 * reconciled on rejoin). It owns no state of its own; the durable state is the shared {@link TradeEscrowStore}, and
 * every decision is a single guarded transition on an escrow row, so a duplicate bus signal, a double region hop, or a
 * rejoin is always a no-op past the first.
 *
 * <p>The protocol per side: {@link #escrow} debits the owner's money and stores their items as a {@code HELD} row, then
 * signals {@code READY}. When both sides are {@code HELD}, {@link TradeEscrowStore#commitBoth} atomically flips both to
 * {@code COMMITTED} (the point of no return. Neither can refund after), and each backend {@code claim}s the
 * counterpart's committed row to deliver its goods to its own local player. A back-out before commit refunds the
 * still-{@code HELD} side and signals {@code ABORT} so the peer refunds too. Item delivery hops to the recipient's
 * region in the adapter; a claim whose recipient is offline is deferred to {@link #reconcile} on their next join.
 */
@NullMarked
public final class CrossServerTrade {

    private final TradeEscrowStore escrows;
    private final TradeEconomy economy;
    private final TradeBus bus;
    private final TradeItemDelivery delivery;
    private final Clock clock;
    private final Logger log;

    public CrossServerTrade(
            TradeEscrowStore escrows,
            TradeEconomy economy,
            TradeBus bus,
            TradeItemDelivery delivery,
            Clock clock,
            Logger log) {
        this.escrows = Objects.requireNonNull(escrows, "escrows");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Escrow {@code owner}'s side: guardedly debit their staked money (double-spend-safe), then hold their items and
     * money in a {@code HELD} row and signal {@code READY}. Returns {@code false}, escrowing nothing, when the money
     * could not be debited, so the caller keeps the player's items and aborts the open; the items are removed from the
     * player by the caller only after a {@code true} return.
     */
    public boolean escrow(
            TradeId id,
            PlayerRef owner,
            String counterpartyServer,
            PlayerRef counterparty,
            String itemData,
            int itemCount,
            Map<String, BigDecimal> money) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(counterparty, "counterparty");
        if (!debitAll(owner, money)) {
            return false;
        }
        escrows.escrow(TradeEscrow.held(
                id,
                owner,
                bus.localServer(),
                counterparty,
                counterpartyServer,
                itemData,
                itemCount,
                money,
                clock.instant()));
        bus.send(new TradeSignal(id, TradeSignalType.READY, owner, counterparty, bus.localServer()));
        progress(id, owner.uuid(), counterparty.uuid());
        return true;
    }

    /** Route an inbound peer signal to the matching half of the protocol; {@code to} is the local player. */
    public void onSignal(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        UUID me = signal.to().uuid();
        UUID peer = signal.from().uuid();
        switch (signal.type()) {
            case READY, COMMIT -> progress(signal.tradeId(), me, peer);
            case ABORT -> refund(signal.tradeId(), me, false);
            case INVITE, ACCEPT, DECLINE -> {
                // The rendezvous signals are handled by the request flow, not the escrow engine.
            }
        }
    }

    /** Back out {@code owner}'s still-{@code HELD} side (a window close or decline) and tell the peer to refund too. */
    public void cancel(TradeId id, UUID owner) {
        refund(id, owner, true);
    }

    /** On join, resolve every escrow row the player owns: settle a both-escrowed trade, or refund a stranded one. */
    public void reconcile(UUID player) {
        Objects.requireNonNull(player, "player");
        for (TradeEscrow row : escrows.findByOwner(player)) {
            UUID peer = row.counterparty().uuid();
            if (row.phase() == TradeEscrowPhase.HELD
                    && escrows.find(row.tradeId(), peer).isEmpty()) {
                refund(row.tradeId(), player, true);
            } else {
                progress(row.tradeId(), player, peer);
            }
        }
    }

    /** Advance {@code me}'s side toward settlement: commit when both are held, then claim the counterpart's goods. */
    private void progress(TradeId id, UUID me, UUID peer) {
        Optional<TradeEscrow> mine = escrows.find(id, me);
        if (mine.isEmpty()) {
            return;
        }
        if (mine.get().phase() == TradeEscrowPhase.HELD) {
            tryCommit(id, me, peer);
        }
        deliverCounterpart(id, me, peer);
    }

    /** Cross the atomic commit point when both sides are held; announce it so the peer delivers too. */
    private void tryCommit(TradeId id, UUID me, UUID peer) {
        Optional<TradeEscrow> peerRow = escrows.find(id, peer);
        if (peerRow.isEmpty() || peerRow.get().phase() != TradeEscrowPhase.HELD) {
            return;
        }
        if (escrows.commitBoth(id, me, peer)) {
            TradeEscrow mine = escrows.find(id, me).orElseThrow();
            bus.send(new TradeSignal(id, TradeSignalType.COMMIT, mine.owner(), mine.counterparty(), bus.localServer()));
        }
    }

    /** Take the counterpart's committed goods and hand them to the local player, exactly once, when they are online. */
    private void deliverCounterpart(TradeId id, UUID me, UUID peer) {
        Optional<TradeEscrow> peerRow = escrows.find(id, peer);
        if (peerRow.isEmpty() || peerRow.get().phase() != TradeEscrowPhase.COMMITTED) {
            maybeCleanup(id, me, peer);
            return;
        }
        TradeEscrow counterpart = peerRow.get();
        PlayerRef recipient = counterpart.counterparty();
        if (counterpart.hasItems() && !delivery.isOnline(recipient)) {
            return;
        }
        if (escrows.claim(id, peer)) {
            depositAll(recipient, counterpart.money());
            delivery.deliver(recipient, counterpart.itemData());
            log.debug("cross-server trade {} delivered to {}", id, recipient.name());
        }
        maybeCleanup(id, me, peer);
    }

    /** Refund {@code owner}'s still-{@code HELD} side: return their money and items; a committed trade is untouched. */
    private void refund(TradeId id, UUID owner, boolean announce) {
        Optional<TradeEscrow> mine = escrows.find(id, owner);
        if (mine.isEmpty() || mine.get().phase() != TradeEscrowPhase.HELD) {
            return;
        }
        TradeEscrow row = mine.get();
        if (row.hasItems() && !delivery.isOnline(row.owner())) {
            return;
        }
        if (escrows.beginRefund(id, owner)) {
            depositAll(row.owner(), row.money());
            delivery.deliver(row.owner(), row.itemData());
            if (announce) {
                bus.send(
                        new TradeSignal(id, TradeSignalType.ABORT, row.owner(), row.counterparty(), bus.localServer()));
            }
            log.debug("cross-server trade {} refunded to {}", id, row.owner().name());
        }
    }

    /** Drop both rows once both sides have been claimed; harmless if a leftover row lingers for a later rejoin. */
    private void maybeCleanup(TradeId id, UUID me, UUID peer) {
        boolean mineDone = escrows.find(id, me)
                .map(r -> r.phase() == TradeEscrowPhase.CLAIMED)
                .orElse(true);
        boolean peerDone = escrows.find(id, peer)
                .map(r -> r.phase() == TradeEscrowPhase.CLAIMED)
                .orElse(true);
        if (mineDone && peerDone) {
            escrows.clear(id, me);
            escrows.clear(id, peer);
        }
    }

    /** Guardedly debit every staked money leg; on the first shortfall, refund the ones already taken and fail. */
    private boolean debitAll(PlayerRef owner, Map<String, BigDecimal> money) {
        var taken = new java.util.ArrayList<Map.Entry<String, BigDecimal>>();
        for (Map.Entry<String, BigDecimal> leg : money.entrySet()) {
            if (leg.getValue().signum() <= 0) {
                continue;
            }
            if (economy.withdraw(owner, leg.getValue(), leg.getKey())) {
                taken.add(leg);
            } else {
                taken.forEach(t -> economy.deposit(owner, t.getValue(), t.getKey()));
                return false;
            }
        }
        return true;
    }

    private void depositAll(PlayerRef who, Map<String, BigDecimal> money) {
        for (Map.Entry<String, BigDecimal> leg : money.entrySet()) {
            if (leg.getValue().signum() > 0) {
                economy.deposit(who, leg.getValue(), leg.getKey());
            }
        }
    }
}
