package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link WalletRepository} over the generated {@code ECONOMY_OWNERS} and
 * {@code WALLET_BALANCES} tables: the native ledger's durable side. It honours invariant (d): balances are
 * DB-backed and survive a world rollback (never PDC, never an in-memory authority). Every statement is typed
 * jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>The load-bearing rule is the guarded debit ({@code docs/02-concurrency.md} §6.7): a debit is a single
 * {@code UPDATE … WHERE owner = ? AND currency = ? AND amount >= ?}, so the database serialises two
 * concurrent debits and an over-draw updates zero rows ({@link TransferError#INSUFFICIENT_FUNDS}). A transfer
 * is that guarded debit plus the credit in one transaction: both legs commit together or neither does. No
 * application-level lock exists or is wanted; a JVM lock would be wrong the moment two servers share the DB.
 */
@NullMarked
public final class JooqWalletRepository extends JooqRepository implements WalletRepository {

    private final CurrencyRegistry currencies;
    private final Clock clock;

    public JooqWalletRepository(DSLContext dsl, CurrencyRegistry currencies, Clock clock) {
        super(dsl);
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        String key = WalletRows.ownerKey(owner);
        return read(dsl -> {
            boolean known = dsl.fetchExists(ECONOMY_OWNERS, ECONOMY_OWNERS.OWNER.eq(key))
                    || dsl.fetchExists(WALLET_BALANCES, WALLET_BALANCES.OWNER.eq(key));
            if (!known) {
                return Optional.empty();
            }
            return Optional.of(WalletRows.toWallet(
                    owner,
                    dsl.selectFrom(WALLET_BALANCES)
                            .where(WALLET_BALANCES.OWNER.eq(key))
                            .fetch(),
                    currencies));
        });
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return write(dsl -> {
            upsertOwner(dsl, owner);
            return WalletRows.toWallet(
                    owner,
                    dsl.selectFrom(WALLET_BALANCES)
                            .where(WALLET_BALANCES.OWNER.eq(WalletRows.ownerKey(owner)))
                            .fetch(),
                    currencies);
        });
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(balance, "balance");
        write(dsl -> {
            upsertOwner(dsl, owner);
            dsl.insertInto(WALLET_BALANCES)
                    .set(WALLET_BALANCES.OWNER, WalletRows.ownerKey(owner))
                    .set(WALLET_BALANCES.CURRENCY, WalletRows.currencyKey(balance))
                    .set(WALLET_BALANCES.AMOUNT, WalletRows.amount(balance))
                    .onConflict(WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY)
                    .doUpdate()
                    .set(WALLET_BALANCES.AMOUNT, WalletRows.amount(balance))
                    .execute();
            return null;
        });
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        // The guarded debit of `from` and the clamp-checked credit of `to` commit together or neither does. The
        // clamp can only be evaluated after the debit has taken (its row must be readable in the same
        // transaction), so a max breach throws a rollback signal to undo the committed debit: a returned err
        // would otherwise commit (the write helper only rolls back on a throw), leaving `from` debited while
        // `to` was never credited. This is the same shape as exchange, across two owners of one currency.
        try {
            return write(dsl -> {
                if (guardedDebit(dsl, from, amount) == 0) {
                    return Result.err(TransferError.INSUFFICIENT_FUNDS);
                }
                BigDecimal max = amount.currency().max();
                BigDecimal current = balanceWithin(dsl, to, amount.currency());
                if (current.add(amount.amount()).compareTo(max) > 0) {
                    throw new RollbackSignal(TransferError.BALANCE_MAX_EXCEEDED);
                }
                upsertOwner(dsl, to);
                creditRow(dsl, to, amount);
                return Result.ok();
            });
        } catch (RollbackSignal rolledBack) {
            return Result.err(rolledBack.error);
        }
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return write(dsl ->
                guardedDebit(dsl, owner, amount) == 0 ? Result.err(TransferError.INSUFFICIENT_FUNDS) : Result.ok());
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        return write(dsl -> {
            BigDecimal max = amount.currency().max();
            BigDecimal current = balanceWithin(dsl, owner, amount.currency());
            if (current.add(amount.amount()).compareTo(max) > 0) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            upsertOwner(dsl, owner);
            creditRow(dsl, owner, amount);
            return Result.ok();
        });
    }

    @Override
    public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(debit, "debit");
        Objects.requireNonNull(credit, "credit");
        // One transaction, mirroring transfer but across two currencies of the same owner: the guarded debit of
        // the source and the clamp-checked credit of the target commit together or neither does. The guarded
        // debit is atomic (a concurrent over-draw changes no rows). The clamp can only be evaluated after the
        // debit has taken, so a clamp breach throws a rollback signal to undo the committed debit: a returned
        // err would otherwise commit (the write helper only rolls back on a throw), leaving the source debited.
        try {
            return write(dsl -> {
                if (guardedDebit(dsl, owner, debit) == 0) {
                    return Result.err(TransferError.INSUFFICIENT_FUNDS);
                }
                BigDecimal max = credit.currency().max();
                BigDecimal current = balanceWithin(dsl, owner, credit.currency());
                if (current.add(credit.amount()).compareTo(max) > 0) {
                    throw new RollbackSignal(TransferError.BALANCE_MAX_EXCEEDED);
                }
                creditRow(dsl, owner, credit);
                return Result.ok();
            });
        } catch (RollbackSignal rolledBack) {
            return Result.err(rolledBack.error);
        }
    }

    /**
     * Thrown from inside a {@link #write} unit of work to force a rollback of a mutation already applied in the
     * same transaction (the write helper commits a normally-returned value and only rolls back on a throw). It
     * is caught by the same method that started the transaction and turned back into a typed error result.
     */
    private static final class RollbackSignal extends RuntimeException {
        private final transient TransferError error;

        RollbackSignal(TransferError error) {
            super(null, null, false, false);
            this.error = error;
        }
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        if (limit <= 0) {
            throw new IllegalArgumentException("baltop limit must be positive: " + limit);
        }
        return read(dsl -> dsl.select(WALLET_BALANCES.OWNER, WALLET_BALANCES.AMOUNT, ECONOMY_OWNERS.NAME)
                .from(WALLET_BALANCES)
                .leftJoin(ECONOMY_OWNERS)
                .on(WALLET_BALANCES.OWNER.eq(ECONOMY_OWNERS.OWNER))
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .orderBy(WALLET_BALANCES.AMOUNT.desc())
                .limit(limit)
                .fetch()
                .map(row -> new BaltopRow(
                        ownerRef(row.get(WALLET_BALANCES.OWNER), row.get(ECONOMY_OWNERS.NAME)),
                        Money.of(currency, row.get(WALLET_BALANCES.AMOUNT)))));
    }

    /** The single guarded {@code UPDATE}: subtracts only when funds suffice, returning the rows changed (0 or 1). */
    private int guardedDebit(DSLContext dsl, PlayerRef owner, Money amount) {
        return dsl.update(WALLET_BALANCES)
                .set(WALLET_BALANCES.AMOUNT, WALLET_BALANCES.AMOUNT.minus(amount.amount()))
                .where(WALLET_BALANCES.OWNER.eq(WalletRows.ownerKey(owner)))
                .and(WALLET_BALANCES.CURRENCY.eq(WalletRows.currencyKey(amount)))
                .and(WALLET_BALANCES.AMOUNT.ge(amount.amount()))
                .execute();
    }

    private void creditRow(DSLContext dsl, PlayerRef owner, Money amount) {
        dsl.insertInto(WALLET_BALANCES)
                .set(WALLET_BALANCES.OWNER, WalletRows.ownerKey(owner))
                .set(WALLET_BALANCES.CURRENCY, WalletRows.currencyKey(amount))
                .set(WALLET_BALANCES.AMOUNT, WalletRows.amount(amount))
                .onConflict(WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY)
                .doUpdate()
                .set(WALLET_BALANCES.AMOUNT, WALLET_BALANCES.AMOUNT.plus(amount.amount()))
                .execute();
    }

    private BigDecimal balanceWithin(DSLContext dsl, PlayerRef owner, Currency currency) {
        return dsl.select(WALLET_BALANCES.AMOUNT)
                .from(WALLET_BALANCES)
                .where(WALLET_BALANCES.OWNER.eq(WalletRows.ownerKey(owner)))
                .and(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .fetchOptional(WALLET_BALANCES.AMOUNT)
                .orElse(BigDecimal.ZERO);
    }

    private void upsertOwner(DSLContext dsl, PlayerRef owner) {
        dsl.insertInto(ECONOMY_OWNERS)
                .set(ECONOMY_OWNERS.OWNER, WalletRows.ownerKey(owner))
                .set(ECONOMY_OWNERS.NAME, ownerName(owner))
                .set(ECONOMY_OWNERS.CREATED_AT, clock.instant().toEpochMilli())
                .onConflict(ECONOMY_OWNERS.OWNER)
                .doNothing()
                .execute();
    }

    private static PlayerRef ownerRef(String uuid, @org.jspecify.annotations.Nullable String name) {
        String resolved = name == null || name.isBlank() ? uuid : name;
        return new PlayerRef(java.util.UUID.fromString(uuid), resolved);
    }

    private static String ownerName(PlayerRef owner) {
        String name = owner.name();
        return name.length() <= 16 ? name : name.substring(0, 16);
    }
}
