package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The mutable coordinator shared by the two views of one trade: it owns the current {@link TradeSession}, the latest
 * offered-item snapshot each side placed, and the two {@link TradeHolder}s the views render into. Both participants'
 * region threads reach the same instance, so every read and write of the session and the snapshots runs under this
 * object's monitor; the offered stacks themselves are read and delivered outside the lock (no blocking I/O is held).
 *
 * <p>The {@code settled} flag is the single-winner gate: whichever path, a both-confirm commit, a window close, a
 * disconnect, a world change, or a plugin stop. First flips it owns the settlement and returns or swaps the items;
 * every later path sees the flag already set and does nothing, so no stack is delivered twice.
 */
@NullMarked
final class TradeExchange {

    /** The result of a confirm click, telling the view whether to re-render or run the swap. */
    enum Outcome {
        CHANGED,
        COMMITTED,
        IGNORED
    }

    private final TradeHolder initiatorHolder;
    private final TradeHolder partnerHolder;
    private final AtomicBoolean settled = new AtomicBoolean(false);

    private TradeSession session;
    private @Nullable ItemStack @Nullable [] initiatorOffer;
    private @Nullable ItemStack @Nullable [] partnerOffer;
    private final Map<String, BigDecimal> initiatorMoney = new HashMap<>();
    private final Map<String, BigDecimal> partnerMoney = new HashMap<>();
    private long initiatorExperience;
    private long partnerExperience;

    TradeExchange(TradeSession session, TradeHolder initiatorHolder, TradeHolder partnerHolder) {
        this.session = Objects.requireNonNull(session, "session");
        this.initiatorHolder = Objects.requireNonNull(initiatorHolder, "initiatorHolder");
        this.partnerHolder = Objects.requireNonNull(partnerHolder, "partnerHolder");
    }

    TradeId id() {
        return initiatorHolder.tradeId();
    }

    TradeHolder holder(TradeSide side) {
        return side == TradeSide.INITIATOR ? initiatorHolder : partnerHolder;
    }

    PlayerRef participant(TradeSide side) {
        return holder(side).viewer();
    }

    /** Store {@code side}'s freshly-read offer and re-stake it, which clears both confirmations. A no-op if settled. */
    synchronized void applyOffer(TradeSide side, @Nullable ItemStack[] offer) {
        if (session.state().isTerminal()) {
            return;
        }
        if (side == TradeSide.INITIATOR) {
            initiatorOffer = offer;
        } else {
            partnerOffer = offer;
        }
        restake(side);
    }

    /**
     * Set {@code side}'s staked money for {@code currencyId} (a non-positive amount clears that currency) and re-stake
     * the whole offer, which clears both confirmations: the same anti-scam invariant an item change triggers. A no-op
     * once the trade has settled.
     */
    synchronized void setMoney(TradeSide side, String currencyId, BigDecimal amount) {
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");
        if (session.state().isTerminal()) {
            return;
        }
        Map<String, BigDecimal> money = side == TradeSide.INITIATOR ? initiatorMoney : partnerMoney;
        if (amount.signum() <= 0) {
            money.remove(currencyId);
        } else {
            money.put(currencyId, amount);
        }
        restake(side);
    }

    /** {@code side}'s currently-staked money per currency id, a defensive copy for rendering. */
    synchronized Map<String, BigDecimal> money(TradeSide side) {
        return Map.copyOf(side == TradeSide.INITIATOR ? initiatorMoney : partnerMoney);
    }

    /**
     * Set {@code side}'s staked experience points (a non-positive amount clears it) and re-stake the whole offer, which
     * clears both confirmations, the same anti-scam invariant an item or money change triggers. A no-op once settled.
     */
    synchronized void setExperience(TradeSide side, long points) {
        if (session.state().isTerminal()) {
            return;
        }
        long clamped = Math.max(points, 0L);
        if (side == TradeSide.INITIATOR) {
            initiatorExperience = clamped;
        } else {
            partnerExperience = clamped;
        }
        restake(side);
    }

    /** {@code side}'s currently-staked experience points, for rendering. */
    synchronized long experience(TradeSide side) {
        return side == TradeSide.INITIATOR ? initiatorExperience : partnerExperience;
    }

    /**
     * Overwrite {@code side}'s positional item snapshot with a fresh live read <em>without</em> touching the domain
     * session: the commit path's two-hop live read (the Folia sub-tick fix). Because the session has already reached
     * its terminal {@code COMMITTED} state when this runs, the money is settled from the confirmed session while the
     * items delivered are exactly what physically sits in the window at freeze time, so a stack placed in the same tick
     * as the counterpart's confirm is delivered rather than discarded.
     */
    synchronized void captureItems(TradeSide side, @Nullable ItemStack[] offer) {
        if (side == TradeSide.INITIATOR) {
            initiatorOffer = offer;
        } else {
            partnerOffer = offer;
        }
    }

    /** Rebuild {@code side}'s whole offer from its latest item array, staked money, and staked experience, re-staking. */
    private void restake(TradeSide side) {
        @Nullable ItemStack[] items = side == TradeSide.INITIATOR ? initiatorOffer : partnerOffer;
        Map<String, BigDecimal> money = side == TradeSide.INITIATOR ? initiatorMoney : partnerMoney;
        long experience = side == TradeSide.INITIATOR ? initiatorExperience : partnerExperience;
        session = session.withOffer(side, new TradeOffer(TradeItemCodec.items(items), Map.copyOf(money), experience));
    }

    /** Mark {@code side} confirmed; when both sides agree, win the settle race and arm the swap. */
    synchronized Outcome confirm(TradeSide side) {
        if (session.state().isTerminal()) {
            return Outcome.IGNORED;
        }
        session = session.confirm(side);
        if (!session.bothConfirmed()) {
            return Outcome.CHANGED;
        }
        if (!settled.compareAndSet(false, true)) {
            return Outcome.IGNORED;
        }
        session = session.ready().commit();
        return Outcome.COMMITTED;
    }

    /** Claim the settlement for a cancel path; only the first caller wins, so items are returned exactly once. */
    boolean beginCancel() {
        return settled.compareAndSet(false, true);
    }

    /** Move the session to {@code CANCELLED} once a cancel path has won {@link #beginCancel()}. */
    synchronized void markCancelled() {
        if (!session.state().isTerminal()) {
            session = session.cancel();
        }
    }

    synchronized TradeSession session() {
        return session;
    }

    synchronized boolean confirmed(TradeSide side) {
        return session.confirmed(side);
    }

    synchronized @Nullable ItemStack @Nullable [] offer(TradeSide side) {
        return side == TradeSide.INITIATOR ? initiatorOffer : partnerOffer;
    }
}
