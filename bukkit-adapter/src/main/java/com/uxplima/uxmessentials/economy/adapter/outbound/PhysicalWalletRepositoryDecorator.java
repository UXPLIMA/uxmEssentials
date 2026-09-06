package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Decorator for {@link WalletRepository} that intercepts physical-currency operations. A physical balance lives
 * in the player's live inventory, so every read or mutation here touches the Bukkit inventory/world API and must
 * run on that player's region (entity) thread. Folia forbids the off-thread inventory access the previous
 * version performed. The repository contract is synchronous, so each physical branch marshals its inventory work
 * onto the holder's entity thread through the injected {@link Scheduler} and waits for the result; the baltop scan
 * enumerates the roster on the global region thread and then reads each holder's inventory on that holder's own
 * entity thread, joining the per-player results off the tick thread. When the caller already owns the target thread
 * the work runs
 * inline rather than scheduling onto itself and blocking. That self-schedule-and-wait is a deadlock, and was
 * the cause of the {@code /baltop} marshalling timeouts. Off the owning thread the bounded wait is the standard
 * anti-corruption bridge; the entity branch passes a retired callback so a player who logs off mid-flight
 * releases the wait immediately instead of letting it run to the timeout.
 *
 * <p>Online players read/write their active inventory; an offline player's credit is queued in the pending
 * transactions table and an offline debit/transfer is refused, exactly as before. The {@link PlayerLookup} port
 * resolves online state without an off-thread {@code Bukkit.getPlayer} call from this thread.
 */
@NullMarked
public final class PhysicalWalletRepositoryDecorator implements WalletRepository {

    private static final Duration MARSHAL_TIMEOUT = Duration.ofSeconds(5);
    // A genuine marshalling timeout is now almost impossible (inline-on-owning-thread plus the retired
    // callback remove both deadlock causes), so log one only at most this often to avoid console spam if
    // something truly pathological keeps a tick thread wedged.
    private static final long WARN_THROTTLE_MILLIS = Duration.ofMinutes(1).toMillis();

    private final WalletRepository delegate;
    private final PendingTransactionRepository pendingRepo;
    private final BukkitInventoryCalculations calculations;
    private final CurrencyRegistry registry;
    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final Logger log;
    private final AtomicLong lastTimeoutWarnAt = new AtomicLong(Long.MIN_VALUE);

    public PhysicalWalletRepositoryDecorator(
            WalletRepository delegate,
            PendingTransactionRepository pendingRepo,
            BukkitInventoryCalculations calculations,
            CurrencyRegistry registry,
            Scheduler scheduler,
            PlayerLookup players,
            Logger log) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.pendingRepo = Objects.requireNonNull(pendingRepo, "pendingRepo");
        this.calculations = Objects.requireNonNull(calculations, "calculations");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        Optional<Wallet> dbWalletOpt = delegate.findByOwner(owner);
        boolean isOnline = players.isOnline(owner.uuid());
        if (dbWalletOpt.isEmpty() && !isOnline) {
            return Optional.empty();
        }

        Wallet dbWallet = dbWalletOpt.orElseGet(() -> Wallet.empty(owner));
        Map<Currency, Money> balances = new LinkedHashMap<>(dbWallet.balances());
        boolean hasPhysical = registry.all().stream().anyMatch(Currency::isPhysical);
        if (!hasPhysical) {
            return Optional.of(Wallet.of(owner, balances));
        }
        if (isOnline) {
            onEntityVoid(owner, player -> {
                for (Currency currency : registry.all()) {
                    if (currency.isPhysical()) {
                        balances.put(currency, Money.of(currency, calculations.getBalance(player, currency)));
                    }
                }
            });
        } else {
            for (Currency currency : registry.all()) {
                if (currency.isPhysical()) {
                    balances.put(currency, Money.zero(currency));
                }
            }
        }
        return Optional.of(Wallet.of(owner, balances));
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        delegate.ensureOwner(owner);
        return findByOwner(owner).orElseGet(() -> Wallet.empty(owner));
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        if (!balance.currency().isPhysical()) {
            delegate.upsertBalance(owner, balance);
            return;
        }
        if (!players.isOnline(owner.uuid())) {
            pendingRepo.queueCredit(owner.uuid(), balance.currency().id().value(), balance.amount());
            return;
        }
        onEntityVoid(owner, player -> {
            BigDecimal current = calculations.getBalance(player, balance.currency());
            BigDecimal diff = balance.amount().subtract(current);
            if (diff.signum() > 0) {
                calculations.credit(player, Money.of(balance.currency(), diff));
            } else if (diff.signum() < 0) {
                calculations.debit(player, Money.of(balance.currency(), diff.negate()));
            }
        });
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        if (!amount.currency().isPhysical()) {
            return delegate.debit(owner, amount);
        }
        if (!players.isOnline(owner.uuid())) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        return onEntity(owner, player -> calculations.debit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        if (!amount.currency().isPhysical()) {
            return delegate.credit(owner, amount);
        }
        if (!players.isOnline(owner.uuid())) {
            pendingRepo.queueCredit(owner.uuid(), amount.currency().id().value(), amount.amount());
            return Result.ok();
        }
        return onEntity(owner, player -> calculations.credit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        if (!amount.currency().isPhysical()) {
            return delegate.transfer(from, to, amount);
        }
        if (!players.isOnline(from.uuid())) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        Result<Unit, TransferError> debitResult =
                onEntity(from, player -> calculations.debit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
        if (debitResult.isErr()) {
            return debitResult;
        }
        if (players.isOnline(to.uuid())) {
            Result<Unit, TransferError> creditResult = onEntity(
                    to, player -> calculations.credit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
            if (creditResult.isErr()) {
                // Roll the debit back on the sender's thread; the move commits both legs or neither.
                onEntity(from, player -> calculations.credit(player, amount), Result.ok());
                return creditResult;
            }
        } else {
            pendingRepo.queueCredit(to.uuid(), amount.currency().id().value(), amount.amount());
        }
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
        // The all-virtual case is the common one and the only atomic one: a single guarded debit + clamped
        // credit in one DB transaction. When either leg is a physical (inventory) currency there is no shared
        // transaction to enrol the inventory in, so the source is debited first and the target credited only if
        // the debit took; a failed credit rolls the debit back, mirroring the physical transfer path.
        if (!debit.currency().isPhysical() && !credit.currency().isPhysical()) {
            return delegate.exchange(owner, debit, credit);
        }
        Result<Unit, TransferError> debited = debit(owner, debit);
        if (debited.isErr()) {
            return debited;
        }
        Result<Unit, TransferError> credited = credit(owner, credit);
        if (credited.isErr()) {
            credit(owner, debit);
            return credited;
        }
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        if (!currency.isPhysical()) {
            return delegate.top(currency, limit);
        }
        // The physical baltop reads every online inventory, but on Folia each inventory is owned by that player's
        // region thread, not the global thread, reading them all from a single global block is torn. So enumerate
        // the roster on the global thread (where Bukkit.getOnlinePlayers() is consistent), then read each player's
        // balance on their own entity thread into a per-player future, and join + aggregate off the tick thread.
        List<PlayerRef> online = onGlobal(this::onlinePlayerRefs, List.of());
        List<BaltopRow> rows = new ArrayList<>();
        for (Map.Entry<PlayerRef, CompletableFuture<Optional<BaltopRow>>> entry : readEach(currency, online)) {
            await(entry.getValue(), Optional.<BaltopRow>empty()).ifPresent(rows::add);
        }
        rows.sort((r1, r2) -> r2.balance().amount().compareTo(r1.balance().amount()));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    /** Snapshot the online players to refs, only legal on the global region thread. */
    private List<PlayerRef> onlinePlayerRefs() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refs.add(new PlayerRef(player.getUniqueId(), player.getName()));
        }
        return refs;
    }

    /**
     * Dispatch a per-player physical-balance read onto each player's entity thread, returning a future per ref.
     * A non-positive balance maps to empty so only holders rank; a player who logs off mid-flight completes empty
     * through the retired callback. The reads are dispatched first and awaited afterwards (in {@link #top}) so the
     * inventory scans run concurrently across regions rather than serialising one global block.
     */
    private List<Map.Entry<PlayerRef, CompletableFuture<Optional<BaltopRow>>>> readEach(
            Currency currency, List<PlayerRef> online) {
        List<Map.Entry<PlayerRef, CompletableFuture<Optional<BaltopRow>>>> futures = new ArrayList<>(online.size());
        for (PlayerRef ref : online) {
            futures.add(Map.entry(ref, onEntityAsync(ref, player -> baltopRow(currency, ref, player))));
        }
        return futures;
    }

    /** The player's physical balance as a ranked row, or empty when they hold none of {@code currency}. */
    private Optional<BaltopRow> baltopRow(Currency currency, PlayerRef ref, Player player) {
        BigDecimal bal = calculations.getBalance(player, currency);
        return bal.signum() > 0 ? Optional.of(new BaltopRow(ref, Money.of(currency, bal))) : Optional.empty();
    }

    /**
     * Schedule {@code work} on {@code owner}'s entity thread, completing the returned future with its result.
     * When this thread already owns the entity's region the work runs inline and the future is pre-completed,
     * for the same deadlock reason as {@link #onEntity}; otherwise a retired callback completes the future empty
     * if the entity is gone before the task runs.
     */
    private CompletableFuture<Optional<BaltopRow>> onEntityAsync(
            PlayerRef owner, Function<Player, Optional<BaltopRow>> work) {
        CompletableFuture<Optional<BaltopRow>> future = new CompletableFuture<>();
        if (scheduler.ownsEntity(owner)) {
            Player player = Bukkit.getPlayer(owner.uuid());
            future.complete(player == null || !player.isOnline() ? Optional.empty() : work.apply(player));
            return future;
        }
        scheduler.onEntity(
                owner,
                () -> {
                    Player player = Bukkit.getPlayer(owner.uuid());
                    if (player == null || !player.isOnline()) {
                        future.complete(Optional.empty());
                        return;
                    }
                    try {
                        future.complete(work.apply(player));
                    } catch (RuntimeException failure) {
                        future.completeExceptionally(failure);
                    }
                },
                () -> future.complete(Optional.empty()));
        return future;
    }

    /**
     * Run {@code work} on {@code owner}'s entity thread and wait for its result, falling back on a miss.
     * When this thread already owns the entity's region the work runs inline, scheduling and then
     * blocking on the entity's own thread would deadlock, since the scheduled task cannot start until
     * this caller returns. Otherwise the work is scheduled with a retired callback so an entity that
     * logs off before the task fires completes the future with the fallback instead of hanging until
     * the timeout.
     */
    private <T> T onEntity(PlayerRef owner, Function<Player, T> work, T fallback) {
        if (scheduler.ownsEntity(owner)) {
            Player player = Bukkit.getPlayer(owner.uuid());
            return player == null || !player.isOnline() ? fallback : work.apply(player);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.onEntity(
                owner,
                () -> {
                    Player player = Bukkit.getPlayer(owner.uuid());
                    if (player == null || !player.isOnline()) {
                        future.complete(fallback);
                        return;
                    }
                    try {
                        future.complete(work.apply(player));
                    } catch (RuntimeException failure) {
                        future.completeExceptionally(failure);
                    }
                },
                () -> future.complete(fallback));
        return await(future, fallback);
    }

    /** Run a side-effecting {@code work} on {@code owner}'s entity thread and wait for it to finish. */
    private void onEntityVoid(PlayerRef owner, Consumer<Player> work) {
        if (scheduler.ownsEntity(owner)) {
            Player player = Bukkit.getPlayer(owner.uuid());
            if (player != null && player.isOnline()) {
                work.accept(player);
            }
            return;
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.onEntity(
                owner,
                () -> {
                    Player player = Bukkit.getPlayer(owner.uuid());
                    if (player == null || !player.isOnline()) {
                        future.complete(Boolean.FALSE);
                        return;
                    }
                    try {
                        work.accept(player);
                        future.complete(Boolean.TRUE);
                    } catch (RuntimeException failure) {
                        future.completeExceptionally(failure);
                    }
                },
                () -> future.complete(Boolean.FALSE));
        await(future, Boolean.FALSE);
    }

    /**
     * Run {@code work} on the global region thread and wait for its result. When this thread already
     * owns the global region the work runs inline, for the same deadlock reason as {@link #onEntity}.
     */
    private <T> T onGlobal(Supplier<T> work, T fallback) {
        if (scheduler.onGlobalThread()) {
            return work.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                future.complete(work.get());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        return await(future, fallback);
    }

    private <T> T await(CompletableFuture<T> future, T fallback) {
        try {
            return future.get(MARSHAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (TimeoutException timeout) {
            warnTimeout();
            return fallback;
        } catch (java.util.concurrent.ExecutionException failure) {
            log.error("physical wallet inventory work failed", failure);
            return fallback;
        }
    }

    /** Log a marshalling timeout at most once per throttle window so a stuck tick thread cannot spam the log. */
    private void warnTimeout() {
        long now = System.currentTimeMillis();
        long previous = lastTimeoutWarnAt.get();
        if (now - previous >= WARN_THROTTLE_MILLIS && lastTimeoutWarnAt.compareAndSet(previous, now)) {
            log.warn("physical wallet inventory marshalling timed out after {}ms", MARSHAL_TIMEOUT.toMillis());
        }
    }
}
