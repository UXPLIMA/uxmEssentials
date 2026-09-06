package com.uxplima.uxmessentials.trade.domain;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A live, two-party exchange as a pure, immutable state machine. The session holds its two participants (the
 * {@code INITIATOR} who opened it and the {@code PARTNER} who accepted), the {@link TradeOffer} each has staked, each
 * side's confirmation flag, and the {@link TradeState}. Every transition returns a new {@code TradeSession}; the
 * adapter keeps the latest instance and re-renders both views from it.
 *
 * <p>The anti-scam invariant lives here: {@link #withOffer}, the only way an offer changes, clears BOTH
 * confirmations and returns the session to {@code OPEN}, so no one can confirm a fair offer and then swap it for a
 * worse one. A trade only reaches {@code READY} through {@link #ready()} once both sides have confirmed the current
 * offers, and only a {@code READY} session may {@link #commit()}. An illegal transition raises
 * {@link IllegalTradeTransitionException} rather than silently doing the wrong thing.
 */
public final class TradeSession {

    private final TradeId id;
    private final PlayerRef initiator;
    private final PlayerRef partner;
    private final TradeOffer initiatorOffer;
    private final TradeOffer partnerOffer;
    private final boolean initiatorConfirmed;
    private final boolean partnerConfirmed;
    private final TradeState state;

    private TradeSession(
            TradeId id,
            PlayerRef initiator,
            PlayerRef partner,
            TradeOffer initiatorOffer,
            TradeOffer partnerOffer,
            boolean initiatorConfirmed,
            boolean partnerConfirmed,
            TradeState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
        this.partner = Objects.requireNonNull(partner, "partner");
        this.initiatorOffer = Objects.requireNonNull(initiatorOffer, "initiatorOffer");
        this.partnerOffer = Objects.requireNonNull(partnerOffer, "partnerOffer");
        this.initiatorConfirmed = initiatorConfirmed;
        this.partnerConfirmed = partnerConfirmed;
        this.state = Objects.requireNonNull(state, "state");
    }

    /** A fresh {@code OPEN} session between two distinct players, both offers empty and unconfirmed. */
    public static TradeSession open(TradeId id, PlayerRef initiator, PlayerRef partner) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(partner, "partner");
        if (initiator.equals(partner)) {
            throw new IllegalArgumentException("a player cannot trade with themselves");
        }
        return new TradeSession(
                id, initiator, partner, TradeOffer.empty(), TradeOffer.empty(), false, false, TradeState.OPEN);
    }

    /**
     * Replace {@code side}'s staked offer wholesale and clear BOTH confirmations, returning the session to
     * {@code OPEN}, the anti-scam invariant. Legal from any non-terminal state.
     */
    public TradeSession withOffer(TradeSide side, TradeOffer offer) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(offer, "offer");
        requireActive("stake an offer on");
        TradeOffer nextInitiator = side == TradeSide.INITIATOR ? offer : initiatorOffer;
        TradeOffer nextPartner = side == TradeSide.PARTNER ? offer : partnerOffer;
        return new TradeSession(id, initiator, partner, nextInitiator, nextPartner, false, false, TradeState.OPEN);
    }

    /** Mark {@code side} confirmed. Legal only while {@code OPEN}; the session is armed later via {@link #ready()}. */
    public TradeSession confirm(TradeSide side) {
        Objects.requireNonNull(side, "side");
        requireState(TradeState.OPEN, "confirm");
        return withConfirmation(side, true, TradeState.OPEN);
    }

    /** Withdraw {@code side}'s confirmation, dropping an armed session back to {@code OPEN}. */
    public TradeSession unconfirm(TradeSide side) {
        Objects.requireNonNull(side, "side");
        requireActive("unconfirm");
        return withConfirmation(side, false, TradeState.OPEN);
    }

    /** Arm the exchange: {@code OPEN} with both sides confirmed becomes {@code READY}. Illegal otherwise. */
    public TradeSession ready() {
        requireState(TradeState.OPEN, "arm");
        if (!bothConfirmed()) {
            throw new IllegalTradeTransitionException("both sides must confirm before a trade can be armed");
        }
        return new TradeSession(id, initiator, partner, initiatorOffer, partnerOffer, true, true, TradeState.READY);
    }

    /** Apply the swap: a {@code READY} session becomes {@code COMMITTED}. Legal only from {@code READY}. */
    public TradeSession commit() {
        requireState(TradeState.READY, "commit");
        return new TradeSession(id, initiator, partner, initiatorOffer, partnerOffer, true, true, TradeState.COMMITTED);
    }

    /** Abort the exchange from any non-terminal state, returning items and refunding money is the adapter's job. */
    public TradeSession cancel() {
        requireActive("cancel");
        return new TradeSession(
                id,
                initiator,
                partner,
                initiatorOffer,
                partnerOffer,
                initiatorConfirmed,
                partnerConfirmed,
                TradeState.CANCELLED);
    }

    public TradeId id() {
        return id;
    }

    public TradeState state() {
        return state;
    }

    /** The player on {@code side}. */
    public PlayerRef participant(TradeSide side) {
        Objects.requireNonNull(side, "side");
        return side == TradeSide.INITIATOR ? initiator : partner;
    }

    /** The offer {@code side} has currently staked. */
    public TradeOffer offer(TradeSide side) {
        Objects.requireNonNull(side, "side");
        return side == TradeSide.INITIATOR ? initiatorOffer : partnerOffer;
    }

    /** Whether {@code side} has confirmed the current offers. */
    public boolean confirmed(TradeSide side) {
        Objects.requireNonNull(side, "side");
        return side == TradeSide.INITIATOR ? initiatorConfirmed : partnerConfirmed;
    }

    /** True once both sides have confirmed: the precondition {@link #ready()} checks. */
    public boolean bothConfirmed() {
        return initiatorConfirmed && partnerConfirmed;
    }

    /** True while the session is armed and awaiting {@link #commit()}. */
    public boolean isReady() {
        return state == TradeState.READY;
    }

    /** Which side {@code player} is on, or empty when they are not a participant. */
    public Optional<TradeSide> sideOf(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        if (initiator.equals(player)) {
            return Optional.of(TradeSide.INITIATOR);
        }
        if (partner.equals(player)) {
            return Optional.of(TradeSide.PARTNER);
        }
        return Optional.empty();
    }

    private TradeSession withConfirmation(TradeSide side, boolean value, TradeState nextState) {
        boolean nextInitiator = side == TradeSide.INITIATOR ? value : initiatorConfirmed;
        boolean nextPartner = side == TradeSide.PARTNER ? value : partnerConfirmed;
        return new TradeSession(
                id, initiator, partner, initiatorOffer, partnerOffer, nextInitiator, nextPartner, nextState);
    }

    private void requireActive(String action) {
        if (state.isTerminal()) {
            throw new IllegalTradeTransitionException("cannot " + action + " a " + state + " trade");
        }
    }

    private void requireState(TradeState required, String action) {
        if (state != required) {
            throw new IllegalTradeTransitionException(
                    "cannot " + action + " a trade in state " + state + " (requires " + required + ")");
        }
    }
}
