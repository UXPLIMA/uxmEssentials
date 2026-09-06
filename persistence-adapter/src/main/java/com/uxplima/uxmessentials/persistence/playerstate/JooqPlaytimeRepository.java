package com.uxplima.uxmessentials.persistence.playerstate;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerstatePlaytime.PLAYERSTATE_PLAYTIME;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record8;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PlaytimeRepository} over the generated {@code PLAYERSTATE_PLAYTIME} table. One row per
 * {@code (uuid, day)} holds that day's active and AFK seconds; an {@link #addSeconds} is an upsert on that
 * composite key (the day's first sample inserts, every later one adds), and {@link #summaryOf} is a single
 * conditional-{@code SUM} aggregate that computes all four windows (today / last 7 days / last 30 days / all time)
 * in one round trip. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>The {@code day} column is the ISO-8601 date stored as {@code yyyy-MM-dd} text (V64 explains the portability
 * choice over a SQL {@code DATE}); a {@code yyyy-MM-dd} string orders the same lexicographically as
 * chronologically, so the window predicates are plain string range comparisons that behave identically on SQLite,
 * MySQL and PostgreSQL.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>DB-backed</b>. Reads and writes run off the tick thread; the upsert serialises concurrent samples
 * for one player-day at the database, so no JVM lock is held.
 */
@NullMarked
public final class JooqPlaytimeRepository extends JooqRepository implements PlaytimeRepository {

    /** Inclusive window spans, in days back from today (today is span 1). */
    private static final long WEEK_DAYS = 7L;

    private static final long MONTH_DAYS = 30L;

    public JooqPlaytimeRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(day, "day");
        if (activeDelta < 0 || afkDelta < 0) {
            throw new IllegalArgumentException(
                    "deltas must be non-negative: active=" + activeDelta + " afk=" + afkDelta);
        }
        if (activeDelta == 0 && afkDelta == 0) {
            return;
        }
        String dayText = format(day);
        write(dsl -> {
            dsl.insertInto(PLAYERSTATE_PLAYTIME)
                    .set(PLAYERSTATE_PLAYTIME.UUID, uuid.toString())
                    .set(PLAYERSTATE_PLAYTIME.DAY, dayText)
                    .set(PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS, activeDelta)
                    .set(PLAYERSTATE_PLAYTIME.AFK_SECONDS, afkDelta)
                    .onConflict(PLAYERSTATE_PLAYTIME.UUID, PLAYERSTATE_PLAYTIME.DAY)
                    .doUpdate()
                    // Accumulate onto the stored value rather than overwriting it: each sample is a delta.
                    .set(PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS, PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS.plus(activeDelta))
                    .set(PLAYERSTATE_PLAYTIME.AFK_SECONDS, PLAYERSTATE_PLAYTIME.AFK_SECONDS.plus(afkDelta))
                    .execute();
            return null;
        });
    }

    @Override
    public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(today, "today");
        String todayText = format(today);
        String weekStart = format(today.minusDays(WEEK_DAYS - 1));
        String monthStart = format(today.minusDays(MONTH_DAYS - 1));
        return read(dsl -> {
            Record8<Long, Long, Long, Long, Long, Long, Long, Long> row = dsl.select(
                            windowed(PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS, PLAYERSTATE_PLAYTIME.DAY.ge(todayText)),
                            windowed(PLAYERSTATE_PLAYTIME.AFK_SECONDS, PLAYERSTATE_PLAYTIME.DAY.ge(todayText)),
                            windowed(PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS, PLAYERSTATE_PLAYTIME.DAY.ge(weekStart)),
                            windowed(PLAYERSTATE_PLAYTIME.AFK_SECONDS, PLAYERSTATE_PLAYTIME.DAY.ge(weekStart)),
                            windowed(PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS, PLAYERSTATE_PLAYTIME.DAY.ge(monthStart)),
                            windowed(PLAYERSTATE_PLAYTIME.AFK_SECONDS, PLAYERSTATE_PLAYTIME.DAY.ge(monthStart)),
                            total(PLAYERSTATE_PLAYTIME.ACTIVE_SECONDS),
                            total(PLAYERSTATE_PLAYTIME.AFK_SECONDS))
                    .from(PLAYERSTATE_PLAYTIME)
                    .where(PLAYERSTATE_PLAYTIME.UUID.eq(uuid.toString()))
                    .fetchOne();
            if (row == null) {
                return PlaytimeSummary.empty();
            }
            return PlaytimeSummary.ofSeconds(
                    seconds(row.value1()),
                    seconds(row.value2()),
                    seconds(row.value3()),
                    seconds(row.value4()),
                    seconds(row.value5()),
                    seconds(row.value6()),
                    seconds(row.value7()),
                    seconds(row.value8()));
        });
    }

    @Override
    public void reset(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        write(dsl -> dsl.deleteFrom(PLAYERSTATE_PLAYTIME)
                .where(PLAYERSTATE_PLAYTIME.UUID.eq(uuid.toString()))
                .execute());
    }

    @Override
    public void resetAll() {
        write(dsl -> dsl.deleteFrom(PLAYERSTATE_PLAYTIME).execute());
    }

    /** SUM of {@code column} over the rows where {@code inWindow} holds, zero outside it: one window's total. */
    private static Field<Long> windowed(Field<Long> column, org.jooq.Condition inWindow) {
        return org.jooq
                .impl
                .DSL
                .sum(org.jooq.impl.DSL.when(inWindow, column).otherwise(0L))
                .cast(Long.class);
    }

    /** SUM of {@code column} over every row, the all-time total. */
    private static Field<Long> total(Field<Long> column) {
        return org.jooq.impl.DSL.sum(column).cast(Long.class);
    }

    private static long seconds(@org.jspecify.annotations.Nullable Long value) {
        // SUM over no rows is null on every backend; treat the absent player-window as zero.
        return value == null ? 0L : value;
    }

    private static String format(LocalDate day) {
        return day.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
