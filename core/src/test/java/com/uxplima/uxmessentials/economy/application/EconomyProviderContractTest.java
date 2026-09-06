package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * The behavioural contract every {@link EconomyProvider} implementation must satisfy, written once and
 * subclassed by each implementation with a factory ({@code docs/11-economy-integration.md} §7,
 * {@code docs/05-testing.md} §3.4). The four pinned properties are: atomic pay, insufficient-funds
 * rejection (rejected, never clamped, and non-mutating), descending baltop ordering, and: the load-bearing
 * case, concurrent-debit double-spend safety. A provider that passes single-threaded but loses the
 * concurrent race is unfit to register via the {@code ServicesManager}; this suite is where that surfaces.
 */
abstract class EconomyProviderContractTest {

    /** Each implementation returns a fresh provider over empty state. */
    protected abstract EconomyProvider newProvider();

    private static PlayerRef randomPlayer() {
        return new PlayerRef(
                UUID.randomUUID(), "p" + UUID.randomUUID().toString().substring(0, 6));
    }

    private static Money coins(long amount) {
        return Money.of(Currencies.COINS, amount);
    }

    @Test
    void payIsAtomic() {
        EconomyProvider eco = newProvider();
        PlayerRef a = randomPlayer();
        PlayerRef b = randomPlayer();
        eco.credit(a, coins(100));

        assertThat(eco.transfer(a, b, coins(40)).isOk()).isTrue();

        assertThat(eco.balance(a, Currencies.COINS)).isEqualTo(coins(60));
        assertThat(eco.balance(b, Currencies.COINS)).isEqualTo(coins(40));
    }

    @Test
    void insufficientFundsIsRejectedAndNonMutating() {
        EconomyProvider eco = newProvider();
        PlayerRef a = randomPlayer();
        PlayerRef b = randomPlayer();
        eco.credit(a, coins(10));

        TransferResult result = eco.transfer(a, b, coins(40));

        assertThat(result).isInstanceOf(TransferResult.InsufficientFunds.class);
        assertThat(eco.balance(a, Currencies.COINS)).isEqualTo(coins(10));
        assertThat(eco.balance(b, Currencies.COINS)).isEqualTo(coins(0));
    }

    @Test
    void selfPayIsDenied() {
        EconomyProvider eco = newProvider();
        PlayerRef a = randomPlayer();
        eco.credit(a, coins(100));

        assertThat(eco.transfer(a, a, coins(10))).isInstanceOf(TransferResult.DenyWith.class);
        assertThat(eco.balance(a, Currencies.COINS)).isEqualTo(coins(100));
    }

    @Test
    void baltopIsOrderedDescendingByBalance() {
        EconomyProvider eco = newProvider();
        eco.credit(randomPlayer(), coins(500));
        eco.credit(randomPlayer(), coins(200));
        eco.credit(randomPlayer(), coins(50));

        List<BaltopRow> rows = eco.top(Currencies.COINS, 10);

        assertThat(rows).extracting(row -> row.balance().amount()).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void baltopRespectsLimit() {
        EconomyProvider eco = newProvider();
        for (int i = 0; i < 5; i++) {
            eco.credit(randomPlayer(), coins(10L * (i + 1)));
        }

        assertThat(eco.top(Currencies.COINS, 3)).hasSize(3);
    }

    @RepeatedTest(50)
    void concurrentDebitsNeverDoubleSpend() throws Exception {
        EconomyProvider eco = newProvider();
        PlayerRef a = randomPlayer();
        eco.credit(a, coins(100));

        runConcurrently(20, () -> eco.debit(a, coins(10)));

        assertThat(eco.balance(a, Currencies.COINS).amount()).isEqualByComparingTo(BigDecimal.ZERO);
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
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
