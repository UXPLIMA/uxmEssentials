package com.uxplima.uxmessentials.persistence.economy;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the economy context's native-ledger persistence adapter, so the consuming bukkit-adapter wires
 * a {@link WalletRepository} (and the debounced writer + transaction telemetry) from the {@link Persistence}
 * handle it already holds without ever naming a jOOQ type. JOOQ is an {@code implementation} dependency of
 * this module, kept off the consumer's compile classpath, exactly as {@code HomeRepositories} does for homes.
 *
 * <p>The returned {@link WalletLedger} bundles the cached jOOQ repository (offline-read accelerator,
 * write-through at the database) with the settle writer and the telemetry batcher the economy module arms on
 * start and drains on stop.
 */
@NullMarked
public final class WalletRepositories {

    private WalletRepositories() {}

    /**
     * Build the native ledger over the shared persistence DSL for the configured {@code currencies}, with the
     * settle/telemetry loops driven by {@code scheduler} on the given debounce/flush windows.
     */
    public static WalletLedger ledger(
            Persistence persistence,
            CurrencyRegistry currencies,
            Scheduler scheduler,
            Logger log,
            Clock clock,
            Duration writeDebounce,
            Duration batchFlush) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(clock, "clock");
        WalletRepository repository =
                new CachedWalletRepository(new JooqWalletRepository(persistence.dsl(), currencies, clock));
        DebouncedWalletWriter writer = new DebouncedWalletWriter(repository, scheduler, log, writeDebounce);
        TransactionTelemetry telemetry = new TransactionTelemetry(persistence.dsl(), scheduler, log, batchFlush);
        return new WalletLedger(repository, writer, telemetry);
    }

    /**
     * Build the bare cached jOOQ repository with no settle/telemetry loops. For tests and tools that exercise
     * the ledger directly without arming background work.
     */
    public static WalletRepository repository(Persistence persistence, CurrencyRegistry currencies, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(clock, "clock");
        return new CachedWalletRepository(new JooqWalletRepository(persistence.dsl(), currencies, clock));
    }

    /**
     * The cached jOOQ repository as its concrete decorator type, so the wiring can hand the cross-server bus a
     * per-owner invalidation hook on the same cache the provider reads, a remote {@code /pay} or eco-admin
     * change drops exactly that owner's cached balance. The wiring then wraps this in its broadcasting
     * decorator and builds the ledger over the wrapped repository via {@link #ledgerOver}.
     */
    public static CachedWalletRepository cachedConcrete(
            Persistence persistence, CurrencyRegistry currencies, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(clock, "clock");
        return new CachedWalletRepository(new JooqWalletRepository(persistence.dsl(), currencies, clock));
    }

    /**
     * Build the native ledger over a caller-supplied {@code repository} (typically the cached repository
     * already wrapped in the cross-server broadcasting decorator), so the settle writer and telemetry loops
     * run over the same repository the native provider reads. Identical to {@link #ledger} except the
     * repository is provided rather than constructed here.
     */
    public static WalletLedger ledgerOver(
            WalletRepository repository,
            Persistence persistence,
            Scheduler scheduler,
            Logger log,
            Duration writeDebounce,
            Duration batchFlush) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        DebouncedWalletWriter writer = new DebouncedWalletWriter(repository, scheduler, log, writeDebounce);
        TransactionTelemetry telemetry = new TransactionTelemetry(persistence.dsl(), scheduler, log, batchFlush);
        return new WalletLedger(repository, writer, telemetry);
    }

    /** The jOOQ-backed {@code /paytoggle} accept-pay flag over the shared persistence DSL. */
    public static PayPreferences payPreferences(Persistence persistence, boolean toggleDefault) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPayPreferences(persistence.dsl(), toggleDefault);
    }

    /** The jOOQ-backed {@code /setworth} per-item price override store over the shared persistence DSL. */
    public static WorthOverrideStore worthOverrideStore(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqWorthOverrideStore(persistence.dsl());
    }

    /** The jOOQ-backed pending transaction repository. */
    public static com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository
            pendingTransactionRepository(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPendingTransactionRepository(persistence.dsl());
    }

    /** The jOOQ-backed economy data-maintenance port (telemetry trim + inactive-wallet purge). */
    public static com.uxplima.uxmessentials.economy.application.port.EconomyMaintenance maintenance(
            Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqEconomyMaintenance(persistence.dsl());
    }

    /** The jOOQ-backed banknote store. */
    public static com.uxplima.uxmessentials.economy.application.port.BanknoteStore banknoteStore(
            Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqBanknoteStore(persistence.dsl());
    }

    /** The jOOQ-backed bank repository. */
    public static com.uxplima.uxmessentials.economy.application.port.BankRepository bankRepository(
            Persistence persistence, CurrencyRegistry currencies, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(clock, "clock");
        return new JooqBankRepository(persistence.dsl(), currencies, clock);
    }

    /** The jOOQ-backed loan repository. */
    public static com.uxplima.uxmessentials.economy.application.port.LoanRepository loanRepository(
            Persistence persistence, CurrencyRegistry currencies, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(clock, "clock");
        return new JooqLoanRepository(persistence.dsl(), currencies, clock);
    }

    /** The jOOQ-backed economy backup manager. */
    public static EconomyBackupManager backupManager(
            Persistence persistence,
            WalletRepository walletRepository,
            CurrencyRegistry currencies,
            java.io.File dataFolder) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(walletRepository, "walletRepository");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(dataFolder, "dataFolder");
        return new EconomyBackupManager(persistence.dsl(), walletRepository, currencies, dataFolder);
    }
}
