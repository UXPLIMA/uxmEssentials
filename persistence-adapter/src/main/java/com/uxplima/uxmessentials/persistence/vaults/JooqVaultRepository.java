package com.uxplima.uxmessentials.persistence.vaults;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Vaults.VAULTS;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.VaultsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link VaultRepository} over the generated {@code VAULTS} table. A point read resolves one
 * owner's vault by the {@code (owner, idx)} primary key; the listing reads an owner's indices in ascending
 * order off the {@code idx_vaults_owner} index; the count is a {@code COUNT(*)} so the amount-quota check never
 * materialises the rows. A {@code save} upserts on the primary key. A re-save of the same vault overwrites its
 * size, contents and last-touched in place, so a close-save never inserts a duplicate row. A {@code delete}
 * removes the one {@code (owner, idx)} row, freeing the owner's quota slot; deleting an id with no row affects
 * zero rows and is a silent no-op. The inactive-vault purge deletes every row whose {@code last_touched} is
 * strictly before the cutoff in one statement and returns the affected count. Every statement is typed jOOQ
 * DSL; no SQL is ever string-concatenated.
 */
public final class JooqVaultRepository extends JooqRepository implements VaultRepository {

    public JooqVaultRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Vault> find(VaultId id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(VAULTS)
                .where(VAULTS.OWNER.eq(id.owner().toString()))
                .and(VAULTS.IDX.eq(id.index()))
                .fetchOptional()
                .map(VaultRows::toVault));
    }

    @Override
    public List<Integer> ownedIndices(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.select(VAULTS.IDX)
                .from(VAULTS)
                .where(VAULTS.OWNER.eq(owner.uuid().toString()))
                .orderBy(VAULTS.IDX.asc())
                .fetch(VAULTS.IDX));
    }

    @Override
    public List<VaultSummary> summaries(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.select(VAULTS.IDX, VAULTS.DISPLAY_NAME, VAULTS.ICON)
                .from(VAULTS)
                .where(VAULTS.OWNER.eq(owner.uuid().toString()))
                .orderBy(VAULTS.IDX.asc())
                .fetch(r -> new VaultSummary(r.value1(), r.value2(), r.value3())));
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.fetchCount(VAULTS, VAULTS.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public void save(Vault vault) {
        Objects.requireNonNull(vault, "vault");
        write(dsl -> {
            upsert(dsl, vault);
            return null;
        });
    }

    @Override
    public void delete(VaultId id) {
        Objects.requireNonNull(id, "id");
        write(dsl -> {
            dsl.deleteFrom(VAULTS)
                    .where(VAULTS.OWNER.eq(id.owner().toString()))
                    .and(VAULTS.IDX.eq(id.index()))
                    .execute();
            return null;
        });
    }

    @Override
    public int deleteUntouchedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return write(dsl -> dsl.deleteFrom(VAULTS)
                .where(VAULTS.LAST_TOUCHED.lt(cutoff.toEpochMilli()))
                .execute());
    }

    private static void upsert(DSLContext dsl, Vault vault) {
        VaultsRecord record = dsl.newRecord(VAULTS);
        VaultRows.apply(record, vault);
        dsl.insertInto(VAULTS)
                .set(record)
                .onConflict(VAULTS.OWNER, VAULTS.IDX)
                .doUpdate()
                .set(VAULTS.SIZE, record.getSize())
                .set(VAULTS.LAST_TOUCHED, record.getLastTouched())
                .set(VAULTS.CONTENTS, record.getContents())
                .set(VAULTS.DISPLAY_NAME, record.getDisplayName())
                .set(VAULTS.ICON, record.getIcon())
                .execute();
    }
}
