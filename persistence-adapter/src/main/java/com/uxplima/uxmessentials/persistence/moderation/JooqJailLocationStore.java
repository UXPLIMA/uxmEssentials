package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJailLocations.MODERATION_JAIL_LOCATIONS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationJailLocationsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link JailLocationStore} over the generated {@code MODERATION_JAIL_LOCATIONS} table. Jails
 * are server-wide and keyed by name alone, so a lookup is a single-row {@code SELECT} on the {@code name}
 * primary key, {@code names} reads every name sorted, and a {@code save} upserts on that key, a {@code
 * /setjail} onto an existing name overwrites the same row. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated.
 *
 * <p>The {@code created_at} metadata column is stamped at save time from the wall clock; the {@link StoredJail}
 * value object carries only the name and location, so the column is a write-only audit field the domain does
 * not read back.
 */
@NullMarked
public final class JooqJailLocationStore extends JooqRepository implements JailLocationStore {

    public JooqJailLocationStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void save(StoredJail jail) {
        Objects.requireNonNull(jail, "jail");
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsert(dsl, jail, savedAt);
            return null;
        });
    }

    @Override
    public boolean exists(String name) {
        Objects.requireNonNull(name, "name");
        String key = StoredJail.normalise(name);
        return read(dsl -> dsl.fetchExists(MODERATION_JAIL_LOCATIONS, MODERATION_JAIL_LOCATIONS.NAME.eq(key)));
    }

    @Override
    public Optional<StoredJail> find(String name) {
        Objects.requireNonNull(name, "name");
        String key = StoredJail.normalise(name);
        return read(dsl -> dsl.selectFrom(MODERATION_JAIL_LOCATIONS)
                .where(MODERATION_JAIL_LOCATIONS.NAME.eq(key))
                .fetchOptional()
                .map(JailLocationRows::toStoredJail));
    }

    @Override
    public boolean delete(String name) {
        Objects.requireNonNull(name, "name");
        String key = StoredJail.normalise(name);
        return write(dsl -> dsl.deleteFrom(MODERATION_JAIL_LOCATIONS)
                        .where(MODERATION_JAIL_LOCATIONS.NAME.eq(key))
                        .execute()
                > 0);
    }

    @Override
    public List<String> names() {
        return read(dsl -> dsl.select(MODERATION_JAIL_LOCATIONS.NAME)
                .from(MODERATION_JAIL_LOCATIONS)
                .orderBy(MODERATION_JAIL_LOCATIONS.NAME.asc())
                .fetch(MODERATION_JAIL_LOCATIONS.NAME));
    }

    private static void upsert(DSLContext dsl, StoredJail jail, long savedAt) {
        ModerationJailLocationsRecord record = dsl.newRecord(MODERATION_JAIL_LOCATIONS);
        JailLocationRows.apply(record, jail, savedAt);
        dsl.insertInto(MODERATION_JAIL_LOCATIONS)
                .set(record)
                .onConflict(MODERATION_JAIL_LOCATIONS.NAME)
                .doUpdate()
                .set(MODERATION_JAIL_LOCATIONS.WORLD, record.getWorld())
                .set(MODERATION_JAIL_LOCATIONS.WORLD_NAME, record.getWorldName())
                .set(MODERATION_JAIL_LOCATIONS.X, record.getX())
                .set(MODERATION_JAIL_LOCATIONS.Y, record.getY())
                .set(MODERATION_JAIL_LOCATIONS.Z, record.getZ())
                .set(MODERATION_JAIL_LOCATIONS.YAW, record.getYaw())
                .set(MODERATION_JAIL_LOCATIONS.PITCH, record.getPitch())
                .set(MODERATION_JAIL_LOCATIONS.CREATED_AT, record.getCreatedAt())
                .execute();
    }
}
