package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.NativeEconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.economy.WalletRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code PlayerWarpEconomy} contract exercised against the <strong>real native provider over embedded
 * SQLite</strong> and the real {@code player_warps} bank column. The dominating property is
 * {@link #concurrentChargesNeverDoubleSpend()}: a payer funded for exactly {@code K} charges fires {@code K +
 * extra} concurrent charges, and exactly {@code K} may take. The guard is the actual SQLite guarded debit, not
 * a JVM lock. The rest pin the compensation, withdraw, auto-payout, refund and affordability paths.
 */
class JooqPlayerWarpEconomyContractTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .max(new BigDecimal("1000000000000"))
            .build();
    private static final CurrencyRegistry CURRENCIES = CurrencyRegistry.single(COINS);
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position POSITION = new Position(WORLD, 12.5, 64.0, -30.5, 90.0f, -12.0f);

    private Persistence persistence;
    private EconomyProvider economy;
    private JooqPlayerWarpRepository warps;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        economy = new NativeEconomyProvider(
                WalletRepositories.repository(persistence, CURRENCIES, clock), CURRENCIES, clock);
        warps = new JooqPlayerWarpRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @RepeatedTest(50)
    void concurrentChargesNeverDoubleSpend() throws Exception {
        int k = 10;
        int extra = 10;
        BigDecimal price = new BigDecimal("10");
        PlayerRef payer = randomPlayer();
        PlayerWarpId warp = seedWarp(randomPlayer(), "citadel");
        // Funded for exactly K charges: not one debit more can take, no matter how they race.
        economy.credit(payer, Money.of(COINS, price.multiply(BigDecimal.valueOf(k))));
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);

        List<Result<Unit, ChargeError>> results =
                runConcurrently(k + extra, () -> bridge.chargeAndAccrue(payer, warp, price, "default"));

        assertThat(results.stream().filter(Result::isOk).count()).isEqualTo(k);
        assertThat(results.stream().filter(Result::isErr).map(Result::errorOrThrow))
                .allMatch(error -> error == ChargeError.INSUFFICIENT_FUNDS);
        assertThat(economy.balance(payer, COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);
        // net per charge = 10 − 10% = 9; K successes bank exactly K × 9 and never a lost or doubled charge.
        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("90"));
    }

    @Test
    void chargingWithoutFundsReportsInsufficientAndBanksNothing() {
        PlayerRef payer = randomPlayer();
        PlayerWarpId warp = seedWarp(randomPlayer(), "keep");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);

        Result<Unit, ChargeError> result = bridge.chargeAndAccrue(payer, warp, new BigDecimal("10"), "default");

        assertThat(result.errorOrThrow()).isEqualTo(ChargeError.INSUFFICIENT_FUNDS);
        assertThat(bank(warp)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void anAccrualFailureRefundsThePayerAndReportsAccrualFailed() {
        PlayerRef payer = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("200")));
        PlayerWarpId warp = seedWarp(randomPlayer(), "spire");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);
        // Drop the bank table so the debit still takes but the accrue UPDATE throws, the real compensation path.
        dropPlayerWarps();

        Result<Unit, ChargeError> result = bridge.chargeAndAccrue(payer, warp, new BigDecimal("50"), "default");

        assertThat(result.errorOrThrow()).isEqualTo(ChargeError.ACCRUAL_FAILED);
        assertThat(economy.balance(payer, COINS).amount()).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    void withdrawPaysTheBankToTheOwnerOnceAndNeverTwice() {
        PlayerRef payer = randomPlayer();
        PlayerRef owner = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("100")));
        PlayerWarpId warp = seedWarp(owner, "bastion");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);
        assertThat(bridge.chargeAndAccrue(payer, warp, new BigDecimal("100"), "default")
                        .isOk())
                .isTrue();
        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("90"));
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(bridge.withdraw(warp, owner).isOk()).isTrue();
        assertThat(bank(warp)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(new BigDecimal("90"));

        // A second withdraw finds an empty bank and pays nothing more, no double credit.
        assertThat(bridge.withdraw(warp, owner).isOk()).isTrue();
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(new BigDecimal("90"));
    }

    @Test
    void autoPayoutOnAnOfflineCapableCurrencyPaysTheOwnerImmediately() {
        PlayerRef payer = randomPlayer();
        PlayerRef owner = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("100")));
        PlayerWarpId warp = seedWarp(owner, "haven");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), true, true);

        assertThat(bridge.chargeAndAccrue(payer, warp, new BigDecimal("100"), "default")
                        .isOk())
                .isTrue();

        assertThat(bank(warp)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(new BigDecimal("90"));
    }

    @Test
    void autoPayoutIsSkippedWhenTheCurrencyCannotBeWrittenOffline() {
        PlayerRef payer = randomPlayer();
        PlayerRef owner = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("100")));
        PlayerWarpId warp = seedWarp(owner, "outpost");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), true, false);

        assertThat(bridge.chargeAndAccrue(payer, warp, new BigDecimal("100"), "default")
                        .isOk())
                .isTrue();

        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("90"));
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void refundCreditsAndCanAffordReadsTheBalance() {
        PlayerRef who = randomPlayer();
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);
        assertThat(bridge.canAfford(who, new BigDecimal("10"), "default")).isFalse();

        assertThat(bridge.refund(who, new BigDecimal("25"), "default").isOk()).isTrue();

        assertThat(economy.balance(who, COINS).amount()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(bridge.canAfford(who, new BigDecimal("25"), "default")).isTrue();
        assertThat(bridge.canAfford(who, new BigDecimal("26"), "default")).isFalse();
    }

    @Test
    void aWarpDeletedInTheChargeWindowRefundsThePayerAndReportsAccrualFailed() {
        PlayerRef payer = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("200")));
        PlayerWarpId warp = seedWarp(randomPlayer(), "voidhold");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);
        BigDecimal balanceBefore = economy.balance(payer, COINS).amount();
        // The warp row vanishes in the window between the debit and the accrue, a concurrent /pwarp delete or
        // admin purge. The debit still takes, but the guarded accrue UPDATE now matches no row: this is the exact
        // silent-money-loss defect (0 rows, no throw, no compensation) the row-count check closes.
        deleteWarpRow(warp);

        Result<Unit, ChargeError> result = bridge.chargeAndAccrue(payer, warp, new BigDecimal("50"), "default");

        assertThat(result.errorOrThrow()).isEqualTo(ChargeError.ACCRUAL_FAILED);
        // The compensating credit ran: the payer is exactly whole, debited nothing net.
        assertThat(economy.balance(payer, COINS).amount()).isEqualByComparingTo(balanceBefore);
        // Nothing was banked: the row is gone and the accrue neither re-created it nor left a phantom balance.
        assertThat(bank(warp)).isNull();
    }

    @Test
    void aCutPercentOutOfRangeIsClampedSoTheBankIsNeverNegativeOrOverpaid() {
        // 150% would drive net = price − cut negative; the record clamps the stored percent to 100.
        assertThat(new JooqPlayerWarpEconomy.PayoutConfig(new BigDecimal("150"), false).cutPercent())
                .isEqualByComparingTo(new BigDecimal("100"));
        // A negative percent would overpay the bank (net > price); it clamps to 0.
        assertThat(new JooqPlayerWarpEconomy.PayoutConfig(new BigDecimal("-20"), false).cutPercent())
                .isEqualByComparingTo(BigDecimal.ZERO);

        // End to end at the clamped ceiling: 150% behaves as 100%, so net = 40 − 40 = 0, never a negative delta.
        PlayerRef payer = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("100")));
        PlayerWarpId warp = seedWarp(randomPlayer(), "toll");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("150"), false, true);

        assertThat(bridge.chargeAndAccrue(payer, warp, new BigDecimal("40"), "default")
                        .isOk())
                .isTrue();

        assertThat(bank(warp)).isEqualByComparingTo(BigDecimal.ZERO);
        // The payer is still debited the full price, the whole fee is the cut, the bank simply banks nothing.
        assertThat(economy.balance(payer, COINS).amount()).isEqualByComparingTo(new BigDecimal("60"));
    }

    @Test
    void anAutoPayoutFaultLeavesTheChargeOkAndTheMoneyBanked() {
        PlayerRef payer = randomPlayer();
        economy.credit(payer, Money.of(COINS, new BigDecimal("100")));
        PlayerWarpId warp = seedWarp(randomPlayer(), "sanctum");
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), true, true);
        // Corrupt the owner column so the auto-payout snapshot read throws (UUID.fromString rejects it) *after* the
        // debit and accrue have committed. A convenience-payout fault must never unwind a correctly-banked charge:
        // the charge still reports ok and the owner's share stays banked for a manual /pwarp withdraw.
        corruptOwnerUuid(warp);

        Result<Unit, ChargeError> result = bridge.chargeAndAccrue(payer, warp, new BigDecimal("100"), "default");

        assertThat(result.isOk()).isTrue();
        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("90"));
        assertThat(economy.balance(payer, COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rentIsTakenFromTheWarpBankAloneWhenItCovers() {
        PlayerRef owner = randomPlayer();
        PlayerWarpId warp = seedWarp(owner, "citadel");
        setBank(warp, new BigDecimal("200"));
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);

        assertThat(bridge.collectRent(warp, owner, new BigDecimal("100"), "default")
                        .isOk())
                .isTrue();

        // The warp pays its own rent: 100 comes straight out of the bank, the owner's wallet is never touched.
        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rentTakesTheShortfallFromTheOwnerWalletWhenTheBankIsThin() {
        PlayerRef owner = randomPlayer();
        economy.credit(owner, Money.of(COINS, new BigDecimal("100")));
        PlayerWarpId warp = seedWarp(owner, "haven");
        setBank(warp, new BigDecimal("40"));
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);

        assertThat(bridge.collectRent(warp, owner, new BigDecimal("100"), "default")
                        .isOk())
                .isTrue();

        // Bank covers 40, the wallet covers the 60 shortfall.
        assertThat(bank(warp)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    void rentNeitherBankNorWalletCanCoverRollsBackTheBankDeduction() {
        PlayerRef owner = randomPlayer();
        economy.credit(owner, Money.of(COINS, new BigDecimal("10")));
        PlayerWarpId warp = seedWarp(owner, "keep");
        setBank(warp, new BigDecimal("30"));
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);

        Result<Unit, ChargeError> result = bridge.collectRent(warp, owner, new BigDecimal("100"), "default");

        assertThat(result.errorOrThrow()).isEqualTo(ChargeError.INSUFFICIENT_FUNDS);
        // The bank deduction was unwound: the bank holds exactly what it did, the owner's wallet is untouched.
        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(economy.balance(owner, COINS).amount()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void rentOfZeroIsANoOp() {
        PlayerRef owner = randomPlayer();
        PlayerWarpId warp = seedWarp(owner, "toll");
        setBank(warp, new BigDecimal("50"));
        JooqPlayerWarpEconomy bridge = bridge(new BigDecimal("10"), false, true);

        assertThat(bridge.collectRent(warp, owner, BigDecimal.ZERO, "default").isOk())
                .isTrue();

        assertThat(bank(warp)).isEqualByComparingTo(new BigDecimal("50"));
    }

    /** Set the warp's earnings bank directly (a committed write; the pool runs autoCommit off). */
    private void setBank(PlayerWarpId warp, BigDecimal amount) {
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.EARNED_AMOUNT, amount)
                        .where(PLAYER_WARPS.ID.eq(warp.value()))
                        .execute());
    }

    private JooqPlayerWarpEconomy bridge(BigDecimal cutPercent, boolean autoPayout, boolean worksOffline) {
        CurrencyBackendRegistry backends =
                CurrencyBackendRegistry.of(List.<CurrencyBackend>of(new StubBackend("native", worksOffline)));
        return new JooqPlayerWarpEconomy(
                persistence,
                economy,
                CURRENCIES,
                backends,
                new JooqPlayerWarpEconomy.PayoutConfig(cutPercent, autoPayout),
                new NoopLogger());
    }

    private PlayerWarpId seedWarp(PlayerRef owner, String name) {
        return warps.save(PlayerWarp.create(owner, owner.name(), PlayerWarpName.of(name), POSITION, Instant.EPOCH));
    }

    private BigDecimal bank(PlayerWarpId warp) {
        return persistence
                .dsl()
                .select(PLAYER_WARPS.EARNED_AMOUNT)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(warp.value()))
                .fetchOne(PLAYER_WARPS.EARNED_AMOUNT);
    }

    private void dropPlayerWarps() {
        persistence
                .dsl()
                .transaction(configuration ->
                        DSL.using(configuration).dropTable(PLAYER_WARPS).execute());
    }

    /** Delete one warp row, leaving the table intact, a concurrent /pwarp delete in the charge window. */
    private void deleteWarpRow(PlayerWarpId warp) {
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .deleteFrom(PLAYER_WARPS)
                        .where(PLAYER_WARPS.ID.eq(warp.value()))
                        .execute());
    }

    /** Poison the owner column so any snapshot read of this warp throws when it parses the uuid. */
    private void corruptOwnerUuid(PlayerWarpId warp) {
        persistence
                .dsl()
                .transaction(configuration -> DSL.using(configuration)
                        .update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.OWNER, "not-a-uuid")
                        .where(PLAYER_WARPS.ID.eq(warp.value()))
                        .execute());
    }

    private static List<Result<Unit, ChargeError>> runConcurrently(
            int threads, Supplier<Result<Unit, ChargeError>> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Queue<Result<Unit, ChargeError>> results = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        results.add(task.get());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        return List.copyOf(results);
    }

    private static PlayerRef randomPlayer() {
        return new PlayerRef(
                UUID.randomUUID(), "p" + UUID.randomUUID().toString().substring(0, 6));
    }

    /** Only {@code id()}/{@code worksOffline()} are read by the bridge; the money methods must never be called. */
    private record StubBackend(String id, boolean worksOffline) implements CurrencyBackend {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean atomicDebit() {
            return true;
        }

        @Override
        public Precision precision() {
            throw unsupported();
        }

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            throw unsupported();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw unsupported();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw unsupported();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("stub backend: only id()/worksOffline() are consulted");
        }
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
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
