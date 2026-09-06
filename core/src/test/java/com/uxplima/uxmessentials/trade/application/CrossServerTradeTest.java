package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.application.port.TradeItemDelivery;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The cross-server two-phase commit engine, driven with two coordinators (one per backend) over a single shared escrow
 * store and a single shared DB-backed economy, the real topology of a network trade. The two fake buses route each
 * side's signals into the other coordinator, so the handshake runs exactly as it would across the proxy. The invariant
 * under test is the money-and-items-conservation one: on commit both stakes reach the right players once; on any
 * back-out both stakes return to their owners once; a debit that cannot be covered escrows nothing; and a duplicate
 * signal or a rejoin never moves goods twice.
 */
class CrossServerTradeTest {

    private static final String SERVER_A = "survival-1";
    private static final String SERVER_B = "lobby-2";
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final String SWORD = "diamond_sword";
    private static final String APPLE = "golden_apple";

    private FakeEscrowStore store;
    private FakeEconomy economy;
    private FakeDelivery deliveryA;
    private FakeDelivery deliveryB;
    private RoutingBus busA;
    private RoutingBus busB;
    private CrossServerTrade serverA;
    private CrossServerTrade serverB;

    @BeforeEach
    void setUp() {
        store = new FakeEscrowStore();
        economy = new FakeEconomy();
        economy.set(ALICE, "coins", new BigDecimal("500"));
        economy.set(BOB, "coins", new BigDecimal("500"));
        deliveryA = new FakeDelivery(ALICE);
        deliveryB = new FakeDelivery(BOB);
        busA = new RoutingBus(SERVER_A);
        busB = new RoutingBus(SERVER_B);
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);
        serverA = new CrossServerTrade(store, economy, busA, deliveryA, clock, new SilentLogger());
        serverB = new CrossServerTrade(store, economy, busB, deliveryB, clock, new SilentLogger());
        busA.deliverTo(serverB);
        busB.deliverTo(serverA);
    }

    @Test
    void commitDeliversBothEscrowsToTheRightPlayers() {
        TradeId id = TradeId.newId();
        boolean aliceEscrowed = serverA.escrow(id, ALICE, SERVER_B, BOB, SWORD, 1, coins("100"));
        boolean bobEscrowed = serverB.escrow(id, BOB, SERVER_A, ALICE, APPLE, 1, coins("40"));

        assertThat(aliceEscrowed).isTrue();
        assertThat(bobEscrowed).isTrue();
        // Alice paid 100 and received Bob's 40; Bob paid 40 and received Alice's 100.
        assertThat(economy.balance(ALICE, "coins")).isEqualByComparingTo("440");
        assertThat(economy.balance(BOB, "coins")).isEqualByComparingTo("560");
        // Each player received exactly the counterpart's item stack, once.
        assertThat(deliveryA.received).containsExactly("golden_apple");
        assertThat(deliveryB.received).containsExactly("diamond_sword");
        // Both rows are cleaned up once both sides are claimed.
        assertThat(store.find(id, ALICE.uuid())).isEmpty();
        assertThat(store.find(id, BOB.uuid())).isEmpty();
    }

    @Test
    void cancelBeforeCounterpartEscrowsRefundsTheStakeWithNoLoss() {
        TradeId id = TradeId.newId();
        serverA.escrow(id, ALICE, SERVER_B, BOB, SWORD, 1, coins("100"));
        assertThat(economy.balance(ALICE, "coins")).isEqualByComparingTo("400");

        serverA.cancel(id, ALICE.uuid());

        assertThat(economy.balance(ALICE, "coins")).isEqualByComparingTo("500");
        assertThat(deliveryA.received).containsExactly("diamond_sword");
        assertThat(store.find(id, ALICE.uuid())).isEmpty();
        assertThat(deliveryB.received).isEmpty();
    }

    @Test
    void declineByOneSideRefundsBothSidesOnce() {
        // Bob escrows first, then Alice declines (never escrows): Bob's ABORT-driven refund must return his stake.
        TradeId id = TradeId.newId();
        serverB.escrow(id, BOB, SERVER_A, ALICE, APPLE, 1, coins("40"));
        assertThat(economy.balance(BOB, "coins")).isEqualByComparingTo("460");

        serverB.cancel(id, BOB.uuid());

        assertThat(economy.balance(BOB, "coins")).isEqualByComparingTo("500");
        assertThat(deliveryB.received).containsExactly("golden_apple");
        assertThat(store.find(id, BOB.uuid())).isEmpty();
    }

    @Test
    void aRecipientOfflineAtCommitIsSettledOnRejoinWithNoLossOrDuplication() {
        TradeId id = TradeId.newId();
        serverA.escrow(id, ALICE, SERVER_B, BOB, SWORD, 1, coins("100"));
        deliveryB.online = false; // Bob places his items then drops before the commit lands on his backend.
        serverB.escrow(id, BOB, SERVER_A, ALICE, APPLE, 1, coins("40"));

        // Alice (online) already received Bob's stake; Bob's delivery of Alice's stack was deferred.
        assertThat(deliveryA.received).containsExactly("golden_apple");
        assertThat(deliveryB.received).isEmpty();
        assertThat(economy.balance(ALICE, "coins")).isEqualByComparingTo("440");

        deliveryB.online = true;
        serverB.reconcile(BOB.uuid());
        serverB.reconcile(BOB.uuid()); // idempotent: a second rejoin delivers nothing more.

        assertThat(deliveryB.received).containsExactly("diamond_sword");
        assertThat(economy.balance(BOB, "coins")).isEqualByComparingTo("560");
        assertThat(store.find(id, ALICE.uuid())).isEmpty();
        assertThat(store.find(id, BOB.uuid())).isEmpty();
    }

    @Test
    void anUnaffordableStakeEscrowsNothingAndIsDoubleSpendSafe() {
        TradeId id = TradeId.newId();
        boolean escrowed = serverA.escrow(id, ALICE, SERVER_B, BOB, SWORD, 1, coins("9999"));

        assertThat(escrowed).isFalse();
        assertThat(economy.balance(ALICE, "coins")).isEqualByComparingTo("500");
        assertThat(store.find(id, ALICE.uuid())).isEmpty();
    }

    @Test
    void duplicateCommitSignalsDeliverGoodsOnlyOnce() {
        TradeId id = TradeId.newId();
        serverA.escrow(id, ALICE, SERVER_B, BOB, SWORD, 1, coins("100"));
        serverB.escrow(id, BOB, SERVER_A, ALICE, APPLE, 1, coins("40"));
        // Replay a stale COMMIT to each side: the guarded claim makes it a no-op.
        serverA.onSignal(new TradeSignal(id, TradeSignalType.COMMIT, BOB, ALICE, SERVER_B));
        serverB.onSignal(new TradeSignal(id, TradeSignalType.COMMIT, ALICE, BOB, SERVER_A));

        assertThat(deliveryA.received).containsExactly("golden_apple");
        assertThat(deliveryB.received).containsExactly("diamond_sword");
        assertThat(economy.balance(ALICE, "coins")).isEqualByComparingTo("440");
        assertThat(economy.balance(BOB, "coins")).isEqualByComparingTo("560");
    }

    private static Map<String, BigDecimal> coins(String amount) {
        return Map.of("coins", new BigDecimal(amount));
    }

    /** An in-memory escrow store with the same guarded transitions the jOOQ store enforces. */
    private static final class FakeEscrowStore implements TradeEscrowStore {
        private final Map<String, TradeEscrow> rows = new HashMap<>();

        private static String key(TradeId id, UUID owner) {
            return id + "/" + owner;
        }

        @Override
        public void escrow(TradeEscrow escrow) {
            rows.put(key(escrow.tradeId(), escrow.owner().uuid()), escrow);
        }

        @Override
        public Optional<TradeEscrow> find(TradeId tradeId, UUID owner) {
            return Optional.ofNullable(rows.get(key(tradeId, owner)));
        }

        @Override
        public List<TradeEscrow> findByOwner(UUID owner) {
            List<TradeEscrow> owned = new ArrayList<>();
            for (TradeEscrow row : rows.values()) {
                if (row.owner().uuid().equals(owner)) {
                    owned.add(row);
                }
            }
            return owned;
        }

        @Override
        public boolean commitBoth(TradeId tradeId, UUID a, UUID b) {
            TradeEscrow ra = rows.get(key(tradeId, a));
            TradeEscrow rb = rows.get(key(tradeId, b));
            if (ra == null
                    || rb == null
                    || ra.phase() != TradeEscrowPhase.HELD
                    || rb.phase() != TradeEscrowPhase.HELD) {
                return false;
            }
            rows.put(key(tradeId, a), ra.withPhase(TradeEscrowPhase.COMMITTED));
            rows.put(key(tradeId, b), rb.withPhase(TradeEscrowPhase.COMMITTED));
            return true;
        }

        @Override
        public boolean claim(TradeId tradeId, UUID owner) {
            TradeEscrow row = rows.get(key(tradeId, owner));
            if (row == null || row.phase() != TradeEscrowPhase.COMMITTED) {
                return false;
            }
            rows.put(key(tradeId, owner), row.withPhase(TradeEscrowPhase.CLAIMED));
            return true;
        }

        @Override
        public boolean beginRefund(TradeId tradeId, UUID owner) {
            TradeEscrow row = rows.get(key(tradeId, owner));
            if (row == null || row.phase() != TradeEscrowPhase.HELD) {
                return false;
            }
            rows.remove(key(tradeId, owner));
            return true;
        }

        @Override
        public void clear(TradeId tradeId, UUID owner) {
            rows.remove(key(tradeId, owner));
        }
    }

    /** A shared DB-backed economy: a guarded withdraw and an unconditional deposit, per player and currency. */
    private static final class FakeEconomy implements TradeEconomy {
        private final Map<String, BigDecimal> balances = new HashMap<>();

        void set(PlayerRef who, String currency, BigDecimal amount) {
            balances.put(who.uuid() + "/" + currency, amount);
        }

        BigDecimal balance(PlayerRef who, String currency) {
            return balances.getOrDefault(who.uuid() + "/" + currency, BigDecimal.ZERO);
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return balance(who, currencyId).compareTo(amount) >= 0;
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
            throw new UnsupportedOperationException("cross-server trades use withdraw/deposit");
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (!canAfford(who, amount, currencyId)) {
                return false;
            }
            set(who, currencyId, balance(who, currencyId).subtract(amount));
            return true;
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            set(who, currencyId, balance(who, currencyId).add(amount));
        }
    }

    /** A bus that routes a published signal into the other backend's coordinator, dropping self-origin frames. */
    private static final class RoutingBus implements TradeBus {
        private final String server;
        private @Nullable CrossServerTrade peer;

        RoutingBus(String server) {
            this.server = server;
        }

        void deliverTo(CrossServerTrade peer) {
            this.peer = peer;
        }

        @Override
        public void send(TradeSignal signal) {
            java.util.Objects.requireNonNull(peer, "peer").onSignal(signal);
        }

        @Override
        public void subscribe(Consumer<TradeSignal> handler) {
            // Routing is wired directly to the peer coordinator for the test.
        }

        @Override
        public String localServer() {
            return server;
        }

        @Override
        public boolean healthy() {
            return true;
        }
    }

    /** Records the item stacks delivered to one backend's local player and whether that player is online. */
    private static final class FakeDelivery implements TradeItemDelivery {
        private final PlayerRef local;
        private final List<String> received = new ArrayList<>();
        private boolean online = true;

        FakeDelivery(PlayerRef local) {
            this.local = local;
        }

        @Override
        public boolean isOnline(PlayerRef player) {
            return player.equals(local) && online;
        }

        @Override
        public void deliver(PlayerRef player, String itemData) {
            if (!itemData.isBlank()) {
                received.add(itemData);
            }
        }
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
