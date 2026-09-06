package com.uxplima.uxmessentials.persistence.teleport;

import static com.uxplima.uxmessentials.persistence.jooq.tables.RtpPool.RTP_POOL;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.RtpPoolRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link RtpPoolStore} over the V69 {@code rtp_pool} table. A {@code save} inserts the batch in a
 * single transaction with application-assigned {@code max(id)+1} keys (the moderation warn/sanction-history idiom,
 * so the schema needs no dialect-specific auto-increment), then evicts the oldest rows beyond the per-world cap so
 * the pool stays bounded. A {@code load} returns the freshest columns for a world (newest validation first) for the
 * startup pre-warm to re-probe; {@code deleteStale} sweeps aged-out columns across every world; {@code count} is a
 * single keyed aggregate. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>Every method runs a synchronous query and so must be called off the tick thread (the callers dispatch through
 * the {@code Scheduler} port). The eviction keeps two cheap statements. A count, then a bounded id-select and
 * delete. Rather than a correlated sub-query, so it stays portable across the SQLite default and the network
 * backends.
 */
@NullMarked
public final class JooqRtpPoolRepository extends JooqRepository implements RtpPoolStore {

    private final int maxPerWorld;
    private final Clock clock;

    public JooqRtpPoolRepository(DSLContext dsl, int maxPerWorld, Clock clock) {
        super(dsl);
        if (maxPerWorld < 1) {
            throw new IllegalArgumentException("maxPerWorld must be >= 1: " + maxPerWorld);
        }
        this.maxPerWorld = maxPerWorld;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void save(WorldRef world, Collection<RtpColumn> columns) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(columns, "columns");
        if (columns.isEmpty()) {
            return;
        }
        write(dsl -> {
            insertAll(dsl, columns);
            enforceCap(dsl, world);
            return null;
        });
    }

    @Override
    public List<RtpColumn> load(WorldRef world, int limit) {
        Objects.requireNonNull(world, "world");
        if (limit <= 0) {
            return List.of();
        }
        return read(dsl -> dsl.selectFrom(RTP_POOL)
                .where(RTP_POOL.WORLD_UUID.eq(world.uid().toString()))
                .orderBy(RTP_POOL.VALIDATED_AT.desc(), RTP_POOL.ID.desc())
                .limit(limit)
                .fetch()
                .map(row -> RtpPoolRows.toColumn(world, row)));
    }

    @Override
    public List<RtpColumn> loadByBiome(WorldRef world, BiomeName biome, int limit) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(biome, "biome");
        if (limit <= 0) {
            return List.of();
        }
        return read(dsl -> dsl.selectFrom(RTP_POOL)
                .where(RTP_POOL.WORLD_UUID.eq(world.uid().toString()))
                .and(RTP_POOL.BIOME.eq(biome.value()))
                .orderBy(RTP_POOL.VALIDATED_AT.desc(), RTP_POOL.ID.desc())
                .limit(limit)
                .fetch()
                .map(row -> RtpPoolRows.toColumn(world, row)));
    }

    @Override
    public int deleteStale(Duration olderThan) {
        Objects.requireNonNull(olderThan, "olderThan");
        long cutoff = clock.millis() - olderThan.toMillis();
        return write(dsl ->
                dsl.deleteFrom(RTP_POOL).where(RTP_POOL.VALIDATED_AT.lt(cutoff)).execute());
    }

    @Override
    public int count(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return read(dsl ->
                dsl.fetchCount(RTP_POOL, RTP_POOL.WORLD_UUID.eq(world.uid().toString())));
    }

    private static void insertAll(DSLContext dsl, Collection<RtpColumn> columns) {
        long id = nextId(dsl);
        for (RtpColumn column : columns) {
            RtpPoolRecord record = dsl.newRecord(RTP_POOL);
            RtpPoolRows.apply(record, id++, column);
            dsl.insertInto(RTP_POOL).set(record).execute();
        }
    }

    /** Trim {@code world}'s pool down to the cap by deleting its oldest-validated rows once it overflows. */
    private void enforceCap(DSLContext dsl, WorldRef world) {
        String key = world.uid().toString();
        int over = dsl.fetchCount(RTP_POOL, RTP_POOL.WORLD_UUID.eq(key)) - maxPerWorld;
        if (over <= 0) {
            return;
        }
        List<Long> victims = dsl.select(RTP_POOL.ID)
                .from(RTP_POOL)
                .where(RTP_POOL.WORLD_UUID.eq(key))
                .orderBy(RTP_POOL.VALIDATED_AT.asc(), RTP_POOL.ID.asc())
                .limit(over)
                .fetch(RTP_POOL.ID);
        dsl.deleteFrom(RTP_POOL).where(RTP_POOL.ID.in(victims)).execute();
    }

    private static long nextId(DSLContext dsl) {
        Long maxId = dsl.select(DSL.max(RTP_POOL.ID)).from(RTP_POOL).fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }
}
