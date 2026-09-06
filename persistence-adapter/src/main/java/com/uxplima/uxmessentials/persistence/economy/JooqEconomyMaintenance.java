package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyBankMembers.ECONOMY_BANK_MEMBERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyCreditScores.ECONOMY_CREDIT_SCORES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyLoans.ECONOMY_LOANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyPayPreferences.ECONOMY_PAY_PREFERENCES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomySharedBanks.ECONOMY_SHARED_BANKS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.economy.application.port.EconomyMaintenance;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link EconomyMaintenance} over the generated economy tables. Telemetry trimming is one
 * {@code DELETE … WHERE ts < ?}; the wallet purge runs in a transaction that removes an owner's pay preferences
 * and wallet rows (the {@code wallet_balances} FK child) before the {@code economy_owners} identity row, so the
 * foreign keys are honoured in delete order. {@link #protectedOwners()} reads the live FK graph, loans, credit
 * scores, bank memberships, and bank creators, so the task can never hand those owners to {@link #purgeOwners}.
 * Deletes are chunked under the parameter limit the embedded SQLite backend enforces.
 */
@NullMarked
public final class JooqEconomyMaintenance implements EconomyMaintenance {

    private static final int CHUNK = 500;

    private final DSLContext dsl;

    public JooqEconomyMaintenance(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public int countTransactionsBefore(long cutoffMillis) {
        return dsl.transactionResult(cfg -> cfg.dsl().fetchCount(TRANSACTIONS, TRANSACTIONS.TS.lt(cutoffMillis)));
    }

    @Override
    public int deleteTransactionsBefore(long cutoffMillis) {
        return dsl.transactionResult(cfg -> cfg.dsl()
                .deleteFrom(TRANSACTIONS)
                .where(TRANSACTIONS.TS.lt(cutoffMillis))
                .execute());
    }

    @Override
    public List<PlayerRef> allOwners() {
        return dsl.transactionResult(cfg ->
                cfg.dsl().select(ECONOMY_OWNERS.OWNER, ECONOMY_OWNERS.NAME).from(ECONOMY_OWNERS).fetch().stream()
                        .map(row -> {
                            String owner = row.get(ECONOMY_OWNERS.OWNER);
                            String name = row.get(ECONOMY_OWNERS.NAME);
                            return new PlayerRef(UUID.fromString(owner), name == null ? owner : name);
                        })
                        .collect(Collectors.toList()));
    }

    @Override
    public Set<UUID> protectedOwners() {
        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();
            Set<UUID> protectedOwners = new HashSet<>();
            addUuids(
                    protectedOwners,
                    tx.selectDistinct(ECONOMY_LOANS.PLAYER_UUID)
                            .from(ECONOMY_LOANS)
                            .fetch(ECONOMY_LOANS.PLAYER_UUID));
            addUuids(
                    protectedOwners,
                    tx.selectDistinct(ECONOMY_CREDIT_SCORES.PLAYER_UUID)
                            .from(ECONOMY_CREDIT_SCORES)
                            .fetch(ECONOMY_CREDIT_SCORES.PLAYER_UUID));
            addUuids(
                    protectedOwners,
                    tx.selectDistinct(ECONOMY_BANK_MEMBERS.PLAYER_UUID)
                            .from(ECONOMY_BANK_MEMBERS)
                            .fetch(ECONOMY_BANK_MEMBERS.PLAYER_UUID));
            addUuids(
                    protectedOwners,
                    tx.selectDistinct(ECONOMY_SHARED_BANKS.CREATOR_UUID)
                            .from(ECONOMY_SHARED_BANKS)
                            .fetch(ECONOMY_SHARED_BANKS.CREATOR_UUID));
            return protectedOwners;
        });
    }

    @Override
    public int purgeOwners(Collection<UUID> owners) {
        Objects.requireNonNull(owners, "owners");
        if (owners.isEmpty()) {
            return 0;
        }
        List<String> keys = owners.stream().map(UUID::toString).collect(Collectors.toList());
        int removed = 0;
        for (List<String> chunk : partition(keys)) {
            removed += dsl.transactionResult(cfg -> {
                DSLContext tx = cfg.dsl();
                tx.deleteFrom(ECONOMY_PAY_PREFERENCES)
                        .where(ECONOMY_PAY_PREFERENCES.OWNER.in(chunk))
                        .execute();
                tx.deleteFrom(WALLET_BALANCES)
                        .where(WALLET_BALANCES.OWNER.in(chunk))
                        .execute();
                return tx.deleteFrom(ECONOMY_OWNERS)
                        .where(ECONOMY_OWNERS.OWNER.in(chunk))
                        .execute();
            });
        }
        return removed;
    }

    private static void addUuids(Set<UUID> target, List<String> raw) {
        for (String value : raw) {
            if (value != null) {
                target.add(UUID.fromString(value));
            }
        }
    }

    private static List<List<String>> partition(List<String> keys) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < keys.size(); i += CHUNK) {
            chunks.add(keys.subList(i, Math.min(i + CHUNK, keys.size())));
        }
        return chunks;
    }
}
