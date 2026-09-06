package com.uxplima.uxmessentials.persistence.communication;

import static com.uxplima.uxmessentials.persistence.jooq.tables.CommunicationAnnouncerSettings.COMMUNICATION_ANNOUNCER_SETTINGS;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSettings;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link AnnouncerSettingsStore} over the single-row {@code COMMUNICATION_ANNOUNCER_SETTINGS} table.
 * The row is seeded by the V66 migration keyed by id {@code 0}, so {@link #load} is a single-row read on the primary
 * key and {@link #save} is an in-place update of that same row: there is exactly one global settings record. The
 * two override columns carry a {@code -1} sentinel for "unset, defer to the file default"; the mapping folds a
 * non-negative value into an {@link AnnouncerSettings} override and a sentinel back to empty. Every statement is
 * typed jOOQ DSL; no SQL is string-concatenated.
 *
 * <p>Read on the announcer's async merge (once per tick) and written on the editor's async settings save (rare), so
 * like the announcement store it carries no cache.
 */
@NullMarked
public final class JooqAnnouncerSettingsStore extends JooqRepository implements AnnouncerSettingsStore {

    private static final short SETTINGS_ROW = (short) 0;
    private static final long UNSET = -1L;

    public JooqAnnouncerSettingsStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public AnnouncerSettings load() {
        return read(dsl -> dsl.selectFrom(COMMUNICATION_ANNOUNCER_SETTINGS)
                .where(COMMUNICATION_ANNOUNCER_SETTINGS.ID.eq(SETTINGS_ROW))
                .fetchOptional()
                .map(row -> new AnnouncerSettings(interval(row.getIntervalSeconds()), gate(row.getMinOnlinePlayers())))
                .orElseGet(AnnouncerSettings::unset));
    }

    @Override
    public void save(AnnouncerSettings settings) {
        Objects.requireNonNull(settings, "settings");
        long intervalSeconds = settings.interval().map(Duration::toSeconds).orElse(UNSET);
        long gate = settings.minOnlinePlayers().isPresent()
                ? settings.minOnlinePlayers().getAsInt()
                : UNSET;
        long savedAt = System.currentTimeMillis();
        write(dsl -> dsl.update(COMMUNICATION_ANNOUNCER_SETTINGS)
                .set(COMMUNICATION_ANNOUNCER_SETTINGS.INTERVAL_SECONDS, intervalSeconds)
                .set(COMMUNICATION_ANNOUNCER_SETTINGS.MIN_ONLINE_PLAYERS, gate)
                .set(COMMUNICATION_ANNOUNCER_SETTINGS.UPDATED_AT, savedAt)
                .where(COMMUNICATION_ANNOUNCER_SETTINGS.ID.eq(SETTINGS_ROW))
                .execute());
    }

    private static Optional<Duration> interval(long seconds) {
        return seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
    }

    private static OptionalInt gate(long players) {
        return players >= 0 ? OptionalInt.of((int) players) : OptionalInt.empty();
    }
}
