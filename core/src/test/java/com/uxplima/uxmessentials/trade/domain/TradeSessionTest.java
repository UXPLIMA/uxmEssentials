package com.uxplima.uxmessentials.trade.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link TradeSession} state machine: a fresh session opens empty and unconfirmed, any offer change
 * clears BOTH confirmations (the anti-scam invariant), both-confirm arms the session to {@code READY}, a change after
 * arming un-readies it, {@code commit()} runs only from {@code READY}, {@code cancel()} runs from any non-terminal
 * state, and every illegal transition is rejected.
 */
class TradeSessionTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private static TradeSession open() {
        return TradeSession.open(TradeId.newId(), ALICE, BOB);
    }

    private static TradeOffer offerOf(String handle, int amount) {
        return new TradeOffer(List.of(new OfferedItem(handle, amount)), Map.of());
    }

    @Test
    void opensEmptyUnconfirmedAndInOpenState() {
        TradeSession session = open();

        assertThat(session.state()).isEqualTo(TradeState.OPEN);
        assertThat(session.offer(TradeSide.INITIATOR)).isEqualTo(TradeOffer.empty());
        assertThat(session.offer(TradeSide.PARTNER)).isEqualTo(TradeOffer.empty());
        assertThat(session.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(session.confirmed(TradeSide.PARTNER)).isFalse();
        assertThat(session.participant(TradeSide.INITIATOR)).isEqualTo(ALICE);
        assertThat(session.participant(TradeSide.PARTNER)).isEqualTo(BOB);
        assertThat(session.sideOf(ALICE)).contains(TradeSide.INITIATOR);
        assertThat(session.sideOf(BOB)).contains(TradeSide.PARTNER);
        assertThat(session.sideOf(new PlayerRef(UUID.randomUUID(), "Carol"))).isEmpty();
    }

    @Test
    void rejectsATradeWithYourself() {
        PlayerRef sameAccount = new PlayerRef(ALICE.uuid(), "Alias");

        assertThatIllegalArgumentException().isThrownBy(() -> TradeSession.open(TradeId.newId(), ALICE, sameAccount));
    }

    @Test
    void changingAnOfferClearsBothConfirmFlags() {
        TradeSession session = open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER);
        assertThat(session.bothConfirmed()).isTrue();

        TradeSession changed = session.withOffer(TradeSide.INITIATOR, offerOf("diamond", 3));

        assertThat(changed.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(changed.confirmed(TradeSide.PARTNER)).isFalse();
        assertThat(changed.state()).isEqualTo(TradeState.OPEN);
        assertThat(changed.offer(TradeSide.INITIATOR)).isEqualTo(offerOf("diamond", 3));
    }

    @Test
    void changingTheOtherSidesOfferAlsoClearsBothConfirmFlags() {
        TradeSession session = open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER);

        TradeSession changed = session.withOffer(TradeSide.PARTNER, offerOf("emerald", 1));

        assertThat(changed.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(changed.confirmed(TradeSide.PARTNER)).isFalse();
    }

    @Test
    void bothConfirmedThenReadyArmsTheSession() {
        TradeSession ready =
                open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        assertThat(ready.state()).isEqualTo(TradeState.READY);
        assertThat(ready.isReady()).isTrue();
    }

    @Test
    void aChangeAfterArmingUnReadiesAndReClearsConfirms() {
        TradeSession ready =
                open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        TradeSession reopened = ready.withOffer(TradeSide.INITIATOR, offerOf("gold_ingot", 5));

        assertThat(reopened.state()).isEqualTo(TradeState.OPEN);
        assertThat(reopened.isReady()).isFalse();
        assertThat(reopened.bothConfirmed()).isFalse();
    }

    @Test
    void readyRequiresBothSidesConfirmed() {
        TradeSession onlyOne = open().confirm(TradeSide.INITIATOR);

        assertThatExceptionOfType(IllegalTradeTransitionException.class).isThrownBy(onlyOne::ready);
    }

    @Test
    void commitRunsOnlyFromReady() {
        TradeSession committed = open().confirm(TradeSide.INITIATOR)
                .confirm(TradeSide.PARTNER)
                .ready()
                .commit();

        assertThat(committed.state()).isEqualTo(TradeState.COMMITTED);
    }

    @Test
    void commitFromOpenIsRejected() {
        TradeSession open = open();

        assertThatExceptionOfType(IllegalTradeTransitionException.class).isThrownBy(open::commit);
    }

    @Test
    void commitFromAnUnconfirmedButNotArmedSessionIsRejected() {
        TradeSession bothConfirmedButNotArmed =
                open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER);

        // Confirmed on both sides, but ready() was never called: the session is still OPEN, so commit is illegal.
        assertThatExceptionOfType(IllegalTradeTransitionException.class).isThrownBy(bothConfirmedButNotArmed::commit);
    }

    @Test
    void cancelIsLegalFromOpen() {
        assertThat(open().cancel().state()).isEqualTo(TradeState.CANCELLED);
    }

    @Test
    void cancelIsLegalFromReady() {
        TradeSession ready =
                open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        assertThat(ready.cancel().state()).isEqualTo(TradeState.CANCELLED);
    }

    @Test
    void noTransitionIsLegalFromATerminalState() {
        TradeSession cancelled = open().cancel();
        TradeSession committed = open().confirm(TradeSide.INITIATOR)
                .confirm(TradeSide.PARTNER)
                .ready()
                .commit();

        assertThatExceptionOfType(IllegalTradeTransitionException.class).isThrownBy(cancelled::cancel);
        assertThatExceptionOfType(IllegalTradeTransitionException.class)
                .isThrownBy(() -> cancelled.confirm(TradeSide.INITIATOR));
        assertThatExceptionOfType(IllegalTradeTransitionException.class)
                .isThrownBy(() -> cancelled.withOffer(TradeSide.INITIATOR, offerOf("stone", 1)));
        assertThatExceptionOfType(IllegalTradeTransitionException.class).isThrownBy(committed::commit);
        assertThatExceptionOfType(IllegalTradeTransitionException.class).isThrownBy(committed::cancel);
        assertThatExceptionOfType(IllegalTradeTransitionException.class)
                .isThrownBy(() -> committed.confirm(TradeSide.PARTNER));
    }

    @Test
    void confirmIsRejectedWhileArmed() {
        TradeSession ready =
                open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        // Once armed the session must be committed or cancelled, not re-confirmed; confirm is an OPEN-only transition.
        assertThatExceptionOfType(IllegalTradeTransitionException.class)
                .isThrownBy(() -> ready.confirm(TradeSide.INITIATOR));
    }

    @Test
    void unconfirmDropsAnArmedSessionBackToOpen() {
        TradeSession ready =
                open().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        TradeSession reopened = ready.unconfirm(TradeSide.PARTNER);

        assertThat(reopened.state()).isEqualTo(TradeState.OPEN);
        assertThat(reopened.confirmed(TradeSide.PARTNER)).isFalse();
        assertThat(reopened.confirmed(TradeSide.INITIATOR)).isTrue();
    }
}
