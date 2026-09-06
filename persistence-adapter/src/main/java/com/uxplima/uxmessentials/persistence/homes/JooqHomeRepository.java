package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HomesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link HomeRepository} over the generated {@code HOMES} table. Reads rebuild an owner's
 * {@link HomeSet} from queryable rows in slot order; the quota count is a {@code COUNT(*)} so the limit
 * check never materialises the whole set. A {@code save} upserts on the {@code (owner, slot)} primary key
 * (a re-anchor or label/icon change overwrites the same row) and a delete is a keyed {@code DELETE}.
 * Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqHomeRepository extends JooqRepository implements HomeRepository {

    public JooqHomeRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public HomeSet load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<Home> homes = read(dsl -> dsl.selectFrom(HOMES)
                .where(HOMES.OWNER.eq(owner.uuid().toString()))
                .orderBy(HOMES.SLOT.asc())
                .fetch()
                .map(row -> HomeRows.toHome(row, owner)));
        return HomeSet.of(owner, homes);
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.fetchCount(HOMES, HOMES.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        return read(dsl -> dsl.selectFrom(HOMES)
                .where(HOMES.OWNER.eq(owner.uuid().toString()).and(HOMES.SLOT.eq(slot.index())))
                .fetchOptional()
                .map(row -> HomeRows.toHome(row, owner)));
    }

    @Override
    public void save(Home home) {
        Objects.requireNonNull(home, "home");
        write(dsl -> {
            upsert(dsl, home);
            return null;
        });
    }

    @Override
    public void deleteSlot(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        write(dsl -> dsl.deleteFrom(HOMES)
                .where(HOMES.OWNER.eq(owner.uuid().toString()).and(HOMES.SLOT.eq(slot.index())))
                .execute());
    }

    @Override
    public void deleteAll(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        write(dsl -> dsl.deleteFrom(HOMES)
                .where(HOMES.OWNER.eq(owner.uuid().toString()))
                .execute());
    }

    private static void upsert(DSLContext dsl, Home home) {
        HomesRecord record = dsl.newRecord(HOMES);
        HomeRows.apply(record, home);
        dsl.insertInto(HOMES)
                .set(record)
                .onConflict(HOMES.OWNER, HOMES.SLOT)
                .doUpdate()
                .set(HOMES.LABEL, record.getLabel())
                .set(HOMES.ICON, record.getIcon())
                .set(HOMES.WORLD, record.getWorld())
                .set(HOMES.WORLD_NAME, record.getWorldName())
                .set(HOMES.X, record.getX())
                .set(HOMES.Y, record.getY())
                .set(HOMES.Z, record.getZ())
                .set(HOMES.YAW, record.getYaw())
                .set(HOMES.PITCH, record.getPitch())
                .set(HOMES.PUBLIC, record.getPublic())
                .set(HOMES.UPDATED_AT, record.getUpdatedAt())
                .execute();
    }
}
