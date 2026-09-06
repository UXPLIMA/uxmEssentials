package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;

/**
 * One side of a cross-server trade, carried as the subject of that side's trade window, so every binding on the
 * window (its title, its confirm button, the rules of its item region) reaches the trade it belongs to. Unlike the
 * two-player local {@code TradeHolder}, only one of the two participants is on this backend, so the holder carries
 * the local player, the remote counterparty, the counterparty's backend id, and the trade id both backends agree on.
 * The {@code escrowed} flag is the single-winner gate that guarantees the local player's items are removed into
 * escrow exactly once: a confirm and a close can both fire, and only the first flips it.
 */
@NullMarked
final class CrossTradeHolder {

    private final TradeId tradeId;
    private final PlayerRef local;
    private final PlayerRef remote;
    private final String remoteServer;
    private final AtomicBoolean escrowed = new AtomicBoolean(false);

    CrossTradeHolder(TradeId tradeId, PlayerRef local, PlayerRef remote, String remoteServer) {
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.local = Objects.requireNonNull(local, "local");
        this.remote = Objects.requireNonNull(remote, "remote");
        this.remoteServer = Objects.requireNonNull(remoteServer, "remoteServer");
    }

    TradeId tradeId() {
        return tradeId;
    }

    PlayerRef local() {
        return local;
    }

    PlayerRef remote() {
        return remote;
    }

    String remoteServer() {
        return remoteServer;
    }

    /** Claim the escrow for this side; only the first caller wins, so the items are staked exactly once. */
    boolean beginEscrow() {
        return escrowed.compareAndSet(false, true);
    }

    /** Whether this side has already been staked or returned, a plain read, unlike {@link #beginEscrow()}. */
    boolean escrowed() {
        return escrowed.get();
    }
}
