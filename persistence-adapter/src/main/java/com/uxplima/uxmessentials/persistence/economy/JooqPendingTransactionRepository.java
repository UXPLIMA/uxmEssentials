package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyPendingPhysicalTransactions.ECONOMY_PENDING_PHYSICAL_TRANSACTIONS;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PendingTransactionRepository} over the generated
 * {@code ECONOMY_PENDING_PHYSICAL_TRANSACTIONS} table. Every statement is typed jOOQ DSL, no
 * string-concatenated SQL. The take-and-clear runs as one transaction so a credit replayed on join is read
 * and deleted atomically and cannot be applied twice.
 */
@NullMarked
public final class JooqPendingTransactionRepository extends JooqRepository implements PendingTransactionRepository {

    public JooqPendingTransactionRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void queueCredit(UUID playerUuid, String currencyId, BigDecimal amount) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");

        write(dsl -> {
            dsl.insertInto(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                    .set(
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID,
                            UUID.randomUUID().toString())
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID, playerUuid.toString())
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CURRENCY, currencyId)
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.AMOUNT, amount)
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CREATED_AT, System.currentTimeMillis())
                    .execute();
            return null;
        });
    }

    @Override
    public List<PendingTransaction> getAndClearPending(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        return write(dsl -> {
            List<PendingTransaction> pending = dsl.select(
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID,
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID,
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CURRENCY,
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.AMOUNT)
                    .from(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                    .where(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID.eq(playerUuid.toString()))
                    .fetch()
                    .map(row -> new PendingTransaction(
                            row.get(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID),
                            UUID.fromString(row.get(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID)),
                            row.get(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CURRENCY),
                            row.get(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.AMOUNT)));

            if (!pending.isEmpty()) {
                dsl.deleteFrom(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                        .where(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID.eq(playerUuid.toString()))
                        .execute();
            }
            return pending;
        });
    }
}
