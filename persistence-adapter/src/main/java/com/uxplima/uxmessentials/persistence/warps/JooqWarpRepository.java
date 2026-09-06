package com.uxplima.uxmessentials.persistence.warps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.WarpRatings.WARP_RATINGS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Warps.WARPS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link WarpRepository} over the generated {@code WARPS} table. Warps are server-wide and
 * keyed by name alone, so a lookup is a single-row {@code SELECT} on the {@code name} primary key, the list
 * reads every row in stored creation order, and a {@code save} upserts on that key: a re-anchor overwrites
 * the same row. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqWarpRepository extends JooqRepository implements WarpRepository {

    private final java.util.function.Function<java.util.UUID, String> names;

    /** Backward-compatible: no profile resolver, so the owner name stays the uuid string (non-display callers). */
    public JooqWarpRepository(DSLContext dsl) {
        this(dsl, java.util.UUID::toString);
    }

    public JooqWarpRepository(DSLContext dsl, java.util.function.Function<java.util.UUID, String> names) {
        super(dsl);
        this.names = Objects.requireNonNull(names, "names");
    }

    @Override
    public Optional<Warp> find(WarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(WARPS)
                .where(WARPS.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> WarpRows.toWarp(row, names)));
    }

    @Override
    public List<Warp> all() {
        return read(dsl -> dsl.selectFrom(WARPS)
                .orderBy(WARPS.CREATED_AT.asc(), WARPS.NAME.asc())
                .fetch()
                .map(row -> WarpRows.toWarp(row, names)));
    }

    @Override
    public boolean exists(WarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(WARPS, WARPS.NAME.eq(name.value())));
    }

    @Override
    public void save(Warp warp) {
        Objects.requireNonNull(warp, "warp");
        write(dsl -> {
            upsert(dsl, warp);
            return null;
        });
    }

    @Override
    public void delete(WarpName name) {
        Objects.requireNonNull(name, "name");
        write(dsl -> dsl.deleteFrom(WARPS).where(WARPS.NAME.eq(name.value())).execute());
    }

    @Override
    public void rate(WarpName name, java.util.UUID player, double rating) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(player, "player");
        write(dsl -> {
            dsl.insertInto(WARP_RATINGS)
                    .set(WARP_RATINGS.WARP_NAME, name.value())
                    .set(WARP_RATINGS.PLAYER_UUID, player.toString())
                    .set(WARP_RATINGS.RATING, rating)
                    .onConflict(WARP_RATINGS.WARP_NAME, WARP_RATINGS.PLAYER_UUID)
                    .doUpdate()
                    .set(WARP_RATINGS.RATING, rating)
                    .execute();
            return null;
        });
    }

    @Override
    public double averageRating(WarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> {
            java.math.BigDecimal val = dsl.select(org.jooq.impl.DSL.avg(WARP_RATINGS.RATING))
                    .from(WARP_RATINGS)
                    .where(WARP_RATINGS.WARP_NAME.eq(name.value()))
                    .fetchOneInto(java.math.BigDecimal.class);
            return val == null ? 0.0 : val.doubleValue();
        });
    }

    private static void upsert(DSLContext dsl, Warp warp) {
        WarpsRecord record = dsl.newRecord(WARPS);
        WarpRows.apply(record, warp);
        dsl.insertInto(WARPS)
                .set(record)
                .onConflict(WARPS.NAME)
                .doUpdate()
                .set(WARPS.WORLD, record.getWorld())
                .set(WARPS.WORLD_NAME, record.getWorldName())
                .set(WARPS.X, record.getX())
                .set(WARPS.Y, record.getY())
                .set(WARPS.Z, record.getZ())
                .set(WARPS.YAW, record.getYaw())
                .set(WARPS.PITCH, record.getPitch())
                .set(WARPS.OWNER, record.getOwner())
                .set(WARPS.COST, record.getCost())
                .set(WARPS.COST_CURRENCY, record.getCostCurrency())
                .set(WARPS.REQUIRED_PERMISSION, record.getRequiredPermission())
                .set(WARPS.VISITORS, record.getVisitors())
                .set(WARPS.PASSWORD, record.getPassword())
                .set(WARPS.IS_LOCKED, record.getIsLocked())
                .set(WARPS.WELCOME_MESSAGE, record.getWelcomeMessage())
                .set(WARPS.WELCOME_MESSAGE_TYPE, record.getWelcomeMessageType())
                .set(WARPS.DEPARTURE_SOUND, record.getDepartureSound())
                .set(WARPS.ARRIVAL_SOUND, record.getArrivalSound())
                .set(WARPS.DEPARTURE_PARTICLE, record.getDepartureParticle())
                .set(WARPS.ARRIVAL_PARTICLE, record.getArrivalParticle())
                .set(WARPS.WARMUP_SECONDS, record.getWarmupSeconds())
                .set(WARPS.COOLDOWN_SECONDS, record.getCooldownSeconds())
                .set(WARPS.ICON_MATERIAL, record.getIconMaterial())
                .set(WARPS.CATEGORY_ID, record.getCategoryId())
                .execute();
    }
}
