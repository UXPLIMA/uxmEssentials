package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.ExperienceTransfer;
import com.uxplima.uxmessentials.trade.domain.MoneyTransfer;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.Test;

/**
 * Pure coverage of the money-settlement decision and its all-or-nothing execution over a fake {@link TradeEconomy}. The
 * enumeration turns each side's staked money into one leg per non-zero currency entry (including a multi-currency,
 * both-directions offer); {@code settle} moves every leg atomically, and a payer who cannot cover their leg, whether
 * caught by the affordability probe or only by the guarded transfer, blocks the whole settlement with no net money
 * moved.
 */
class TradeSettlementTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void enumeratesOneLegPerNonZeroMoneyEntry() {
        TradeSession session = session(
                money(Map.of("coins", BigDecimal.TEN)),
                money(Map.of("coins", BigDecimal.ZERO, "gems", BigDecimal.ONE)));

        List<MoneyTransfer> legs = TradeSettlement.transfers(session);

        // Alice's coins leg and Bob's gems leg; Bob's zero-coins entry produces no leg.
        assertThat(legs)
                .extracting(MoneyTransfer::payer, MoneyTransfer::currencyId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(TradeSide.INITIATOR, "coins"),
                        org.assertj.core.groups.Tuple.tuple(TradeSide.PARTNER, "gems"));
    }

    @Test
    void settleMovesMoneyBothWaysAtomically() {
        RecordingEconomy economy = new RecordingEconomy();
        TradeSession session =
                session(money(Map.of("coins", BigDecimal.valueOf(100))), money(Map.of("gems", BigDecimal.valueOf(5))));

        boolean settled = new TradeSettlement(economy, new RecordingExperience()).settle(session);

        assertThat(settled).isTrue();
        assertThat(economy.moves)
                .containsExactlyInAnyOrder(
                        new Move(ALICE, BOB, BigDecimal.valueOf(100), "coins"),
                        new Move(BOB, ALICE, BigDecimal.valueOf(5), "gems"));
    }

    @Test
    void aPayerWhoCannotAffordBlocksTheWholeSettlementWithNoNetMove() {
        // Bob cannot afford his gems leg; his transfer fails even though Alice's coins leg already committed.
        RecordingEconomy economy = new RecordingEconomy();
        economy.brokeFor(BOB);
        TradeSession session =
                session(money(Map.of("coins", BigDecimal.valueOf(100))), money(Map.of("gems", BigDecimal.valueOf(5))));

        boolean settled = new TradeSettlement(economy, new RecordingExperience()).settle(session);

        assertThat(settled).isFalse();
        // Either the probe blocked it before any move, or the coins move was reversed, either way, net zero.
        assertThat(economy.net(ALICE, "coins")).isZero();
        assertThat(economy.net(BOB, "coins")).isZero();
    }

    @Test
    void settleWithNoStakedMoneySucceedsWithoutTouchingTheEconomy() {
        RecordingEconomy economy = new RecordingEconomy();
        TradeSession session = session(TradeOffer.empty(), TradeOffer.empty());

        assertThat(new TradeSettlement(economy, new RecordingExperience()).settle(session))
                .isTrue();
        assertThat(economy.moves).isEmpty();
    }

    @Test
    void enumeratesOneExperienceLegPerSideThatStakedExperience() {
        TradeSession session = session(experience(100), experience(0));

        List<ExperienceTransfer> legs = TradeSettlement.experienceTransfers(session);

        // Only Alice staked experience, so only her leg is enumerated; Bob's zero produces no leg.
        assertThat(legs)
                .extracting(ExperienceTransfer::payer, ExperienceTransfer::points)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(TradeSide.INITIATOR, 100L));
    }

    @Test
    void settleMovesExperienceBothWaysAtomically() {
        RecordingExperience xp = new RecordingExperience();
        xp.set(ALICE, 500L);
        xp.set(BOB, 500L);
        TradeSession session = session(experience(100), experience(40));

        boolean settled = new TradeSettlement(new RecordingEconomy(), xp).settle(session);

        assertThat(settled).isTrue();
        // Alice gave 100 and received Bob's 40; Bob gave 40 and received Alice's 100.
        assertThat(xp.balance(ALICE)).isEqualTo(440L);
        assertThat(xp.balance(BOB)).isEqualTo(560L);
    }

    @Test
    void aStakerWhoCannotAffordExperienceBlocksTheSettlementAndRefundsWhatWasHeld() {
        // Alice can cover her 100 experience but Bob has none for his 40, so nothing settles and Alice's held stake
        // returns to her.
        RecordingExperience xp = new RecordingExperience();
        xp.set(ALICE, 500L);
        xp.set(BOB, 0L);
        TradeSession session = session(experience(100), experience(40));

        boolean settled = new TradeSettlement(new RecordingEconomy(), xp).settle(session);

        assertThat(settled).isFalse();
        assertThat(xp.balance(ALICE)).isEqualTo(500L);
        assertThat(xp.balance(BOB)).isZero();
    }

    private static TradeSession session(TradeOffer initiator, TradeOffer partner) {
        return TradeSession.open(TradeId.newId(), ALICE, BOB)
                .withOffer(TradeSide.INITIATOR, initiator)
                .withOffer(TradeSide.PARTNER, partner);
    }

    private static TradeOffer money(Map<String, BigDecimal> amounts) {
        return new TradeOffer(List.of(), amounts);
    }

    private static TradeOffer experience(long points) {
        return new TradeOffer(List.of(), Map.of(), points);
    }

    /** A recorded money movement so the test can assert what settled and compute a per-player net. */
    private record Move(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {}

    /**
     * A fake economy that records every transfer and lets the "coins move" affordability pass while failing any
     * transfer whose payer was marked broke: the mid-settlement failure the reversal must undo.
     */
    private static final class RecordingEconomy implements TradeEconomy {
        private final List<Move> moves = new ArrayList<>();
        private final List<UUID> broke = new ArrayList<>();

        void brokeFor(PlayerRef who) {
            broke.add(who.uuid());
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return !broke.contains(who.uuid());
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
            if (broke.contains(from.uuid())) {
                return false;
            }
            moves.add(new Move(from, to, amount, currencyId));
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("same-server settlement uses transfer");
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("same-server settlement uses transfer");
        }

        BigDecimal net(PlayerRef who, String currencyId) {
            BigDecimal total = BigDecimal.ZERO;
            for (Move move : moves) {
                if (!move.currencyId().equals(currencyId)) {
                    continue;
                }
                if (move.to().uuid().equals(who.uuid())) {
                    total = total.add(move.amount());
                }
                if (move.from().uuid().equals(who.uuid())) {
                    total = total.subtract(move.amount());
                }
            }
            return total;
        }
    }

    /** A fake experience seam over an in-memory per-player balance: a guarded withdraw and an unconditional deposit. */
    private static final class RecordingExperience implements TradeExperience {
        private final Map<UUID, Long> balances = new HashMap<>();

        void set(PlayerRef who, long amount) {
            balances.put(who.uuid(), amount);
        }

        long balance(PlayerRef who) {
            return balances.getOrDefault(who.uuid(), 0L);
        }

        @Override
        public long available(PlayerRef who) {
            return balance(who);
        }

        @Override
        public boolean withdraw(PlayerRef who, long points) {
            if (balance(who) < points) {
                return false;
            }
            balances.put(who.uuid(), balance(who) - points);
            return true;
        }

        @Override
        public void deposit(PlayerRef who, long points) {
            balances.put(who.uuid(), balance(who) + points);
        }
    }
}
