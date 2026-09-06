package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Ignores.IGNORES;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreEntry;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link IgnoreStore} over the generated {@code IGNORES} table. The ignore list is
 * persistent (it survives restart, and ignore-aware {@code /msg} resolution reads it), keyed by
 * {@code (owner, ignored)} so a repeat {@code /ignore} upserts the same row rather than inserting a
 * duplicate. The stored scope is the {@link IgnoreScope} constant name; an unknown value read back is
 * treated as {@link IgnoreScope#ALL} so an ignore is never silently dropped. Every statement is typed jOOQ
 * DSL; no SQL is ever string-concatenated.
 *
 * <p>The ignored player's name is not persisted (only the uuid is). The column shape stays identical on
 * every backend, so an {@link IgnoreEntry} rebuilt from a row carries the ignored uuid with the uuid text
 * as a placeholder name; the command adapter resolves the live name for display.
 */
public final class JooqIgnoreStore extends JooqRepository implements IgnoreStore {

    public JooqIgnoreStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public IgnoreList load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<IgnoreEntry> entries = read(dsl -> dsl.selectFrom(IGNORES)
                .where(IGNORES.OWNER.eq(owner.uuid().toString()))
                .fetch()
                .map(row -> toEntry(row.getIgnored(), row.getScope())));
        return IgnoreList.of(owner, entries);
    }

    @Override
    public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ignored, "ignored");
        Objects.requireNonNull(scope, "scope");
        write(dsl -> upsert(dsl, owner, ignored, scope));
    }

    @Override
    public void unignore(PlayerRef owner, PlayerRef ignored) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ignored, "ignored");
        write(dsl -> dsl.deleteFrom(IGNORES)
                .where(IGNORES.OWNER.eq(owner.uuid().toString()))
                .and(IGNORES.IGNORED.eq(ignored.uuid().toString()))
                .execute());
    }

    private static int upsert(DSLContext dsl, PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
        return dsl.insertInto(IGNORES)
                .set(IGNORES.OWNER, owner.uuid().toString())
                .set(IGNORES.IGNORED, ignored.uuid().toString())
                .set(IGNORES.SCOPE, scope.name())
                .onConflict(IGNORES.OWNER, IGNORES.IGNORED)
                .doUpdate()
                .set(IGNORES.SCOPE, scope.name())
                .execute();
    }

    private static IgnoreEntry toEntry(String ignoredUuid, String scope) {
        UUID uuid = UUID.fromString(ignoredUuid);
        return new IgnoreEntry(new PlayerRef(uuid, ignoredUuid), IgnoreScope.fromStored(scope));
    }
}
