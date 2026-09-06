package com.uxplima.uxmessentials.economy.fakes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * A hand-rolled in-memory {@link WalletRepository} for the economy tests. It stands in for the jOOQ +
 * Caffeine ledger so the domain/application can be exercised without a database, and, crucially, it models
 * the guarded debit/transfer the same way the real DB does: the {@code BALANCE >= ?} check and the write are
 * one atomic step. A single global lock serialises the guarded writes exactly as the database serialises the
 * guarded {@code UPDATE}, so the concurrent double-spend contract test proves real serialisation rather than
 * a lucky interleaving.
 */
public final class InMemoryWalletRepository implements WalletRepository {

    private final Map<PlayerRef, String> owners = new ConcurrentHashMap<>();
    private final Map<Key, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ReentrantLock guard = new ReentrantLock();

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        if (!owners.containsKey(owner) && noBalances(owner)) {
            return Optional.empty();
        }
        return Optional.of(materialise(owner));
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        owners.putIfAbsent(owner, owner.name());
        return materialise(owner);
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        owners.putIfAbsent(owner, owner.name());
        guard.lock();
        try {
            balances.put(new Key(owner, balance.currency()), balance.amount());
        } finally {
            guard.unlock();
        }
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        guard.lock();
        try {
            Key fromKey = new Key(from, amount.currency());
            BigDecimal have = balances.getOrDefault(fromKey, BigDecimal.ZERO);
            if (have.compareTo(amount.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            Key toKey = new Key(to, amount.currency());
            BigDecimal target = balances.getOrDefault(toKey, BigDecimal.ZERO);
            if (target.add(amount.amount()).compareTo(amount.currency().max()) > 0) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            balances.put(fromKey, have.subtract(amount.amount()));
            owners.putIfAbsent(to, to.name());
            balances.put(toKey, target.add(amount.amount()));
            return Result.ok();
        } finally {
            guard.unlock();
        }
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        guard.lock();
        try {
            Key key = new Key(owner, amount.currency());
            BigDecimal have = balances.getOrDefault(key, BigDecimal.ZERO);
            if (have.compareTo(amount.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            balances.put(key, have.subtract(amount.amount()));
            return Result.ok();
        } finally {
            guard.unlock();
        }
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        guard.lock();
        try {
            Key key = new Key(owner, amount.currency());
            BigDecimal have = balances.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal next = have.add(amount.amount());
            if (next.compareTo(amount.currency().max()) > 0) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            owners.putIfAbsent(owner, owner.name());
            balances.put(key, next);
            return Result.ok();
        } finally {
            guard.unlock();
        }
    }

    @Override
    public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
        guard.lock();
        try {
            Key fromKey = new Key(owner, debit.currency());
            BigDecimal have = balances.getOrDefault(fromKey, BigDecimal.ZERO);
            if (have.compareTo(debit.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            Key toKey = new Key(owner, credit.currency());
            BigDecimal target = balances.getOrDefault(toKey, BigDecimal.ZERO);
            if (target.add(credit.amount()).compareTo(credit.currency().max()) > 0) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            balances.put(fromKey, have.subtract(debit.amount()));
            owners.putIfAbsent(owner, owner.name());
            balances.put(toKey, target.add(credit.amount()));
            return Result.ok();
        } finally {
            guard.unlock();
        }
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        List<BaltopRow> rows = new ArrayList<>();
        for (Map.Entry<Key, BigDecimal> entry : balances.entrySet()) {
            if (entry.getKey().currency().equals(currency)) {
                rows.add(new BaltopRow(refFor(entry.getKey().owner()), Money.of(currency, entry.getValue())));
            }
        }
        rows.sort(
                Comparator.comparing((BaltopRow row) -> row.balance().amount()).reversed());
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }

    private PlayerRef refFor(PlayerRef owner) {
        return owner;
    }

    private boolean noBalances(PlayerRef owner) {
        return balances.keySet().stream().noneMatch(key -> key.owner().equals(owner));
    }

    private Wallet materialise(PlayerRef owner) {
        Map<Currency, Money> byCurrency = new java.util.LinkedHashMap<>();
        for (Map.Entry<Key, BigDecimal> entry : balances.entrySet()) {
            if (entry.getKey().owner().equals(owner)) {
                Currency currency = entry.getKey().currency();
                byCurrency.put(currency, Money.of(currency, entry.getValue()));
            }
        }
        return Wallet.of(owner, byCurrency);
    }

    private record Key(PlayerRef owner, Currency currency) {}
}
