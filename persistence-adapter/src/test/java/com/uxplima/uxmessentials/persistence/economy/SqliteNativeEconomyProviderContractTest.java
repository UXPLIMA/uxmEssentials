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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.economy.application.NativeEconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code EconomyProviderContractTest} (docs/11-economy-integration.md §7, docs/05-testing.md §3.4)
 * exercised against the <strong>real native provider over embedded SQLite</strong>: the canonical worked
 * example proving the four pinned properties on the tested-default backend rather than an in-memory fake. The
 * core module already runs the same contract over an in-memory repository; this is the DB-backed
 * counterpart, where the double-spend guard is the actual SQLite guarded {@code UPDATE} and the single-writer
 * pool, not a JVM lock.
 *
 * <p>The four properties: atomic pay, insufficient-funds rejection (rejected, never clamped, non-mutating),
 * descending baltop ordering, and the load-bearing concurrent-debit double-spend safety. Testcontainers is
 * not required here. The embedded file db runs in-process; if a future contributor adds network-backend
 * runs they subclass the same shape behind Testcontainers.
 */
class SqliteNativeEconomyProviderContractTest {

    private Persistence persistence;
    private EconomyProvider economy;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new EconomyTestSupport.SqliteConfig(),
                dataFolder,
                EconomyTestSupport.baselineMigrations(),
                new EconomyTestSupport.NoopLogger());
        economy = new NativeEconomyProvider(
                WalletRepositories.repository(persistence, CURRENCIES, Clock.systemUTC()),
                CURRENCIES,
                Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void payIsAtomic() {
        PlayerRef a = randomPlayer();
        PlayerRef b = randomPlayer();
        economy.credit(a, coins(100));

        assertThat(economy.transfer(a, b, coins(40)).isOk()).isTrue();

        assertThat(economy.balance(a, COINS)).isEqualTo(coins(60));
        assertThat(economy.balance(b, COINS)).isEqualTo(coins(40));
    }

    @Test
    void insufficientFundsIsRejectedAndNonMutating() {
        PlayerRef a = randomPlayer();
        PlayerRef b = randomPlayer();
        economy.credit(a, coins(10));

        TransferResult result = economy.transfer(a, b, coins(40));

        assertThat(result).isInstanceOf(TransferResult.InsufficientFunds.class);
        assertThat(economy.balance(a, COINS)).isEqualTo(coins(10));
        assertThat(economy.balance(b, COINS)).isEqualTo(coins(0));
    }

    @Test
    void selfPayIsDenied() {
        PlayerRef a = randomPlayer();
        economy.credit(a, coins(100));

        assertThat(economy.transfer(a, a, coins(10))).isInstanceOf(TransferResult.DenyWith.class);
        assertThat(economy.balance(a, COINS)).isEqualTo(coins(100));
    }

    @Test
    void baltopIsOrderedDescendingByBalance() {
        economy.credit(randomPlayer(), coins(500));
        economy.credit(randomPlayer(), coins(200));
        economy.credit(randomPlayer(), coins(50));

        List<BaltopRow> rows = economy.top(COINS, 10);

        assertThat(rows).extracting(row -> row.balance().amount()).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @RepeatedTest(20)
    void concurrentDebitsNeverDoubleSpend() throws Exception {
        PlayerRef a = randomPlayer();
        economy.credit(a, coins(100));

        runConcurrently(20, () -> economy.debit(a, coins(10)));

        assertThat(economy.balance(a, COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        task.run();
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
    }
}
