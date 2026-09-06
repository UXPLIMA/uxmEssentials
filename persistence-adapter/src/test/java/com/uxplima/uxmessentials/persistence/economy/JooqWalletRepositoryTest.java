package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.COINS;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.CURRENCIES;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWalletRepository} against the default embedded SQLite backend with the
 * Flyway V2 economy baseline applied. It proves the credit/debit/transfer round-trip, the guarded debit (an
 * over-draw changes zero rows and mutates nothing, the double-spend guard), the offline-owner upsert
 * ({@code ensureOwner} before an offline-target write), the {@code (owner, currency)} balance key, baltop
 * ordering off the descending index, and, the load-bearing case, concurrent debits against the single
 * SQLite writer never double-spending past zero.
 */
class JooqWalletRepositoryTest {

    private Persistence persistence;
    private JooqWalletRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new EconomyTestSupport.SqliteConfig(),
                dataFolder,
                EconomyTestSupport.baselineMigrations(),
                new EconomyTestSupport.NoopLogger());
        repository = new JooqWalletRepository(
                persistence.dsl(), CURRENCIES, Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void creditMaterialisesAndReadsBack() {
        PlayerRef owner = randomPlayer();

        assertThat(repository.credit(owner, coins(100)).isOk()).isTrue();

        assertThat(repository.findByOwner(owner).orElseThrow().balanceOf(COINS)).isEqualTo(coins(100));
    }

    @Test
    void unknownOwnerHasNoWallet() {
        assertThat(repository.findByOwner(randomPlayer())).isEmpty();
    }

    @Test
    void debitGuardRejectsAnOverdrawAndMutatesNothing() {
        PlayerRef owner = randomPlayer();
        repository.credit(owner, coins(30));

        Result<Unit, TransferError> result = repository.debit(owner, coins(50));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(repository.findByOwner(owner).orElseThrow().balanceOf(COINS)).isEqualTo(coins(30));
    }

    @Test
    void transferMovesBothLegsAtomically() {
        PlayerRef from = randomPlayer();
        PlayerRef to = randomPlayer();
        repository.credit(from, coins(100));

        assertThat(repository.transfer(from, to, coins(40)).isOk()).isTrue();

        assertThat(repository.findByOwner(from).orElseThrow().balanceOf(COINS)).isEqualTo(coins(60));
        assertThat(repository.findByOwner(to).orElseThrow().balanceOf(COINS)).isEqualTo(coins(40));
    }

    @Test
    void exchangeMovesBothCurrencyLegsAtomically() {
        JooqWalletRepository multi = new JooqWalletRepository(
                persistence.dsl(), EconomyTestSupport.MULTI, Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
        PlayerRef owner = randomPlayer();
        multi.credit(owner, coins(100));

        Result<Unit, TransferError> result = multi.exchange(owner, coins(40), EconomyTestSupport.gems(3));

        assertThat(result.isOk()).isTrue();
        assertThat(multi.findByOwner(owner).orElseThrow().balanceOf(COINS)).isEqualTo(coins(60));
        assertThat(multi.findByOwner(owner).orElseThrow().balanceOf(EconomyTestSupport.GEMS))
                .isEqualTo(EconomyTestSupport.gems(3));
    }

    @Test
    void exchangeShortSourceCreditsNothing() {
        JooqWalletRepository multi = new JooqWalletRepository(
                persistence.dsl(), EconomyTestSupport.MULTI, Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
        PlayerRef owner = randomPlayer();
        multi.credit(owner, coins(20));

        Result<Unit, TransferError> result = multi.exchange(owner, coins(50), EconomyTestSupport.gems(3));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        // The guarded debit changed no rows, so the target gems leg never ran and the source is untouched.
        assertThat(multi.findByOwner(owner).orElseThrow().balanceOf(COINS)).isEqualTo(coins(20));
        assertThat(multi.findByOwner(owner).orElseThrow().balanceOf(EconomyTestSupport.GEMS))
                .isEqualTo(EconomyTestSupport.gems(0));
    }

    @Test
    void exchangePastTargetMaxLeavesSourceUntouched() {
        JooqWalletRepository multi = new JooqWalletRepository(
                persistence.dsl(), EconomyTestSupport.MULTI, Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
        PlayerRef owner = randomPlayer();
        multi.credit(owner, coins(500));

        // Crediting 150 gems would exceed the gems max of 100: the whole transaction rolls back, source intact.
        Result<Unit, TransferError> result = multi.exchange(owner, coins(150), EconomyTestSupport.gems(150));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);
        assertThat(multi.findByOwner(owner).orElseThrow().balanceOf(COINS)).isEqualTo(coins(500));
        assertThat(multi.findByOwner(owner).orElseThrow().balanceOf(EconomyTestSupport.GEMS))
                .isEqualTo(EconomyTestSupport.gems(0));
    }

    @Test
    void transferPastTargetMaxLeavesBothSidesUntouched() {
        PlayerRef from = randomPlayer();
        PlayerRef to = randomPlayer();
        repository.credit(from, coins(50));
        Money atMax = Money.of(COINS, new BigDecimal("999999999999"));
        repository.upsertBalance(to, atMax);

        // Crediting the target would push it past the coins max: the whole transfer rolls back, payer intact.
        Result<Unit, TransferError> result = repository.transfer(from, to, coins(10));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);
        assertThat(repository.findByOwner(from).orElseThrow().balanceOf(COINS)).isEqualTo(coins(50));
        assertThat(repository.findByOwner(to).orElseThrow().balanceOf(COINS)).isEqualTo(atMax);
    }

    @Test
    void transferToANeverJoinedTargetUpsertsTheOwnerRow() {
        PlayerRef from = randomPlayer();
        PlayerRef neverJoined = randomPlayer();
        repository.credit(from, coins(50));

        assertThat(repository.transfer(from, neverJoined, coins(25)).isOk()).isTrue();

        assertThat(repository.findByOwner(neverJoined).orElseThrow().balanceOf(COINS))
                .isEqualTo(coins(25));
    }

    @Test
    void ensureOwnerMaterialisesAnOfflineTargetBeforeAWrite() {
        PlayerRef neverJoined = randomPlayer();

        repository.ensureOwner(neverJoined);
        repository.upsertBalance(neverJoined, coins(15));

        assertThat(repository.findByOwner(neverJoined).orElseThrow().balanceOf(COINS))
                .isEqualTo(coins(15));
    }

    @Test
    void creditHonoursTheMaxBalanceClamp() {
        PlayerRef owner = randomPlayer();
        Money near = Money.of(COINS, new BigDecimal("999999999999"));
        repository.upsertBalance(owner, near);

        Result<Unit, TransferError> result = repository.credit(owner, coins(10));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);
        assertThat(repository.findByOwner(owner).orElseThrow().balanceOf(COINS)).isEqualTo(near);
    }

    @Test
    void baltopIsOrderedDescendingAndBounded() {
        repository.credit(randomPlayer(), coins(500));
        repository.credit(randomPlayer(), coins(200));
        repository.credit(randomPlayer(), coins(50));
        repository.credit(randomPlayer(), coins(900));

        List<BaltopRow> rows = repository.top(COINS, 3);

        assertThat(rows).hasSize(3);
        assertThat(rows)
                .extracting(row -> row.balance().amount())
                .containsExactly(new BigDecimal("900.00"), new BigDecimal("500.00"), new BigDecimal("200.00"));
    }

    @RepeatedTest(20)
    void concurrentDebitsNeverDoubleSpendAgainstTheSingleSqliteWriter() throws Exception {
        PlayerRef owner = randomPlayer();
        repository.credit(owner, coins(100));

        AtomicInteger succeeded = runConcurrently(20, () -> repository.debit(owner, coins(10)));

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(repository.findByOwner(owner).orElseThrow().balanceOf(COINS).amount())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private AtomicInteger runConcurrently(int threads, java.util.function.Supplier<Result<Unit, TransferError>> task)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        if (task.get().isOk()) {
                            succeeded.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
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
        return succeeded;
    }
}
