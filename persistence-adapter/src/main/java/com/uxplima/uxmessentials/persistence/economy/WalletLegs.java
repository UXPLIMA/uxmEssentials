package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The wallet legs the loan and bank repositories run inside their own transactions so a money move stays atomic
 * with its aggregate row. These are the exact guarded credit/debit idioms {@code JooqWalletRepository} uses
 * a debit is a single {@code UPDATE … WHERE amount >= ?} that changes 0 or 1 rows, a credit is a clamp-checked
 * upsert, lifted here so a loan disburse or a bank deposit performs the same database-serialised move within
 * the transaction that also writes the loan/bank row. Every statement is typed jOOQ DSL; no SQL is concatenated.
 */
@NullMarked
final class WalletLegs {

    private WalletLegs() {}

    /** The guarded debit: subtracts only when funds suffice, returning the rows changed (0 or 1). */
    static int guardedDebit(DSLContext dsl, PlayerRef owner, Money amount) {
        return dsl.update(WALLET_BALANCES)
                .set(WALLET_BALANCES.AMOUNT, WALLET_BALANCES.AMOUNT.minus(amount.amount()))
                .where(WALLET_BALANCES.OWNER.eq(WalletRows.ownerKey(owner)))
                .and(WALLET_BALANCES.CURRENCY.eq(WalletRows.currencyKey(amount)))
                .and(WALLET_BALANCES.AMOUNT.ge(amount.amount()))
                .execute();
    }

    /** True when crediting {@code amount} would keep {@code owner} within the currency's max balance. */
    static boolean creditFitsClamp(DSLContext dsl, PlayerRef owner, Money amount) {
        BigDecimal current = dsl.select(WALLET_BALANCES.AMOUNT)
                .from(WALLET_BALANCES)
                .where(WALLET_BALANCES.OWNER.eq(WalletRows.ownerKey(owner)))
                .and(WALLET_BALANCES.CURRENCY.eq(WalletRows.currencyKey(amount)))
                .fetchOptional(WALLET_BALANCES.AMOUNT)
                .orElse(BigDecimal.ZERO);
        return current.add(amount.amount()).compareTo(amount.currency().max()) <= 0;
    }

    /** Add {@code amount} to {@code owner}'s balance (insert-or-add), assuming the clamp has been checked. */
    static void creditRow(DSLContext dsl, PlayerRef owner, Money amount, long createdAt) {
        upsertOwner(dsl, owner, createdAt);
        dsl.insertInto(WALLET_BALANCES)
                .set(WALLET_BALANCES.OWNER, WalletRows.ownerKey(owner))
                .set(WALLET_BALANCES.CURRENCY, WalletRows.currencyKey(amount))
                .set(WALLET_BALANCES.AMOUNT, WalletRows.amount(amount))
                .onConflict(WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY)
                .doUpdate()
                .set(WALLET_BALANCES.AMOUNT, WALLET_BALANCES.AMOUNT.plus(amount.amount()))
                .execute();
    }

    /** Materialise the owner row if absent, so a credit to a never-joined player cannot break a foreign key. */
    static void upsertOwner(DSLContext dsl, PlayerRef owner, long createdAt) {
        dsl.insertInto(ECONOMY_OWNERS)
                .set(ECONOMY_OWNERS.OWNER, WalletRows.ownerKey(owner))
                .set(ECONOMY_OWNERS.NAME, ownerName(owner))
                .set(ECONOMY_OWNERS.CREATED_AT, createdAt)
                .onConflict(ECONOMY_OWNERS.OWNER)
                .doNothing()
                .execute();
    }

    private static String ownerName(PlayerRef owner) {
        String name = owner.name();
        return name.length() <= 16 ? name : name.substring(0, 16);
    }
}
