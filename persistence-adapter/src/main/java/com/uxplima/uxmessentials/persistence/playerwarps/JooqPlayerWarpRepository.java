package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpBans.PLAYER_WARP_BANS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites.PLAYER_WARP_FAVOURITES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpMembers.PLAYER_WARP_MEMBERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPayments.PLAYER_WARP_PAYMENTS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPendingTeleports.PLAYER_WARP_PENDING_TELEPORTS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatingRewards.PLAYER_WARP_RATING_REWARDS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatings.PLAYER_WARP_RATINGS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpVisits.PLAYER_WARP_VISITS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpWhitelist.PLAYER_WARP_WHITELIST;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PlayerWarpRepository} over the V70 {@code player_warps} table. A warp is now keyed by a
 * durable surrogate {@link PlayerWarpId} and its name is globally unique, so {@link #findByName} and
 * {@link #existsByName} are single-column lookups, {@link #findById} keys on the surrogate, and the owner-scoped
 * lists read the owner's rows in stored creation order. A {@link #save} upserts on the surrogate, allocating a
 * fresh {@code max(id)+1} key on insert (the V5/V11/V69 idiom, so the schema needs no auto-increment) and updating
 * in place on an existing id, without ever touching the {@code password_*} columns. A {@link #deleteById} removes
 * the warp's side-table rows and the parent row in one transaction, since the schema carries no {@code ON DELETE
 * CASCADE}. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqPlayerWarpRepository extends JooqRepository implements PlayerWarpRepository {

    private final Function<UUID, String> names;
    private final LongSupplier randomSort;

    /** Backward-compatible: no profile resolver, so a null {@code owner_name} falls back to the uuid string. */
    public JooqPlayerWarpRepository(DSLContext dsl) {
        this(dsl, UUID::toString);
    }

    public JooqPlayerWarpRepository(DSLContext dsl, Function<UUID, String> names) {
        this(dsl, names, () -> ThreadLocalRandom.current().nextLong());
    }

    /**
     * @param randomSort the source of the {@code random_sort} ordering key stamped on each insert (and rewritten
     *     by {@link #reshuffle()}). A {@code RandomGenerator} in production; a test injects a deterministic
     *     supplier so a seeded RANDOM browse is reproducible. Never {@code Math.random()} on this hot write path.
     */
    public JooqPlayerWarpRepository(DSLContext dsl, Function<UUID, String> names, LongSupplier randomSort) {
        super(dsl);
        this.names = Objects.requireNonNull(names, "names");
        this.randomSort = Objects.requireNonNull(randomSort, "randomSort");
    }

    @Override
    public Optional<PlayerWarp> findByName(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.NAME.eq(name.value()))
                .fetchOptional()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public Optional<PlayerWarp> findById(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOptional()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> ownedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .orderBy(PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.ID.asc())
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.OWNER.eq(owner.uuid().toString()))
                .and(PLAYER_WARPS.STATUS.eq(WarpStatus.ACTIVE.name()))
                .and(PLAYER_WARPS.ACCESS.eq(WarpAccess.PUBLIC.name()))
                .orderBy(PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.ID.asc())
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> all() {
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .orderBy(PLAYER_WARPS.OWNER.asc(), PLAYER_WARPS.CREATED_AT.asc(), PLAYER_WARPS.ID.asc())
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl ->
                dsl.fetchCount(PLAYER_WARPS, PLAYER_WARPS.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public boolean existsByName(PlayerWarpName name) {
        Objects.requireNonNull(name, "name");
        return read(dsl -> dsl.fetchExists(PLAYER_WARPS, PLAYER_WARPS.NAME.eq(name.value())));
    }

    @Override
    public PlayerWarpId save(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        return write(dsl -> warp.id().map(id -> update(dsl, warp, id)).orElseGet(() -> insert(dsl, warp)));
    }

    @Override
    public void deleteById(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        long key = id.value();
        write(dsl -> {
            deleteSideRows(dsl, key);
            return dsl.deleteFrom(PLAYER_WARPS).where(PLAYER_WARPS.ID.eq(key)).execute();
        });
    }

    @Override
    public void recordVisit(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        // One guarded statement, not a read-modify-write, so two concurrent visits both land instead of racing.
        write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.VISIT_COUNT, PLAYER_WARPS.VISIT_COUNT.add(1L))
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute());
    }

    @Override
    public void updateRating(PlayerWarpId id, RatingSummary summary) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        // One guarded UPDATE overwriting the whole rollup the browse sorts on; the rate use case recomputes the
        // Bayesian score from the rating rows and hands the finished summary here, so the sort column never drifts.
        write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.RATING_SUM, summary.sum())
                .set(PLAYER_WARPS.RATING_COUNT, summary.count())
                .set(PLAYER_WARPS.RATING_AVERAGE, summary.average())
                .set(PLAYER_WARPS.RATING_SCORE, summary.score())
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute());
    }

    @Override
    public void refreshFavouriteCount(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        // Recompute from the source rows, not a +/-1 bump, so a double-click race can never drift the stored count.
        // The correlated COUNT reads a different table (player_warp_favourites), so it stays portable, unlike the
        // self-referential UPDATE ... SET x = (SELECT ... FROM the-same-table) that MySQL forbids.
        write(dsl -> dsl.update(PLAYER_WARPS)
                .set(
                        PLAYER_WARPS.FAVOURITE_COUNT,
                        DSL.selectCount()
                                .from(PLAYER_WARP_FAVOURITES)
                                .where(PLAYER_WARP_FAVOURITES.WARP_ID.eq(id.value())))
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute());
    }

    @Override
    public List<PlayerWarp> dueForRent(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        // Bounded index range scan on idx_player_warps_rent, oldest-due first, never a full-table scan. A warp with
        // no rent term (NULL rent_paid_until) never matches, so it is exempt until the sub-group enrols it.
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.STATUS.eq(WarpStatus.ACTIVE.name()))
                .and(PLAYER_WARPS.RENT_PAID_UNTIL.isNotNull())
                .and(PLAYER_WARPS.RENT_PAID_UNTIL.le(now.toEpochMilli()))
                .orderBy(PLAYER_WARPS.RENT_PAID_UNTIL.asc(), PLAYER_WARPS.ID.asc())
                .limit(limit)
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<PlayerWarp> suspendedForRent(int limit) {
        // Every suspended warp is a retry/archive candidate; those closest to their archive deadline come first, so
        // an oversubscribed sweep archives the most-overdue before it hits the batch cap.
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.STATUS.eq(WarpStatus.SUSPENDED.name()))
                .orderBy(PLAYER_WARPS.RENT_ARCHIVE_AFTER.asc(), PLAYER_WARPS.ID.asc())
                .limit(limit)
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public List<RentReminderCandidate> remindableForRent(Instant now, Instant horizon, int maxStage, int limit) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(horizon, "horizon");
        // Active warps whose term falls inside the reminder horizon and that have not yet been reminded for every
        // window, a light projection, never the full aggregate, so the reminder pass reads only what it mails on.
        return read(dsl -> dsl.select(
                        PLAYER_WARPS.ID,
                        PLAYER_WARPS.OWNER,
                        PLAYER_WARPS.OWNER_NAME,
                        PLAYER_WARPS.NAME,
                        PLAYER_WARPS.RENT_PAID_UNTIL,
                        PLAYER_WARPS.RENT_REMINDED_STAGE)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.STATUS.eq(WarpStatus.ACTIVE.name()))
                .and(PLAYER_WARPS.RENT_PAID_UNTIL.isNotNull())
                .and(PLAYER_WARPS.RENT_PAID_UNTIL.gt(now.toEpochMilli()))
                .and(PLAYER_WARPS.RENT_PAID_UNTIL.le(horizon.toEpochMilli()))
                .and(PLAYER_WARPS.RENT_REMINDED_STAGE.lt(maxStage))
                .orderBy(PLAYER_WARPS.RENT_PAID_UNTIL.asc(), PLAYER_WARPS.ID.asc())
                .limit(limit)
                .fetch()
                .map(this::toReminderCandidate));
    }

    @Override
    public void markRentReminded(PlayerWarpId id, int stage) {
        Objects.requireNonNull(id, "id");
        write(dsl -> dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.RENT_REMINDED_STAGE, stage)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute());
    }

    @Override
    public Optional<Instant> sponsorCooldownUntil(PlayerWarpId id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.select(PLAYER_WARPS.SPONSOR_COOLDOWN_UNTIL)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOptional(PLAYER_WARPS.SPONSOR_COOLDOWN_UNTIL)
                .filter(Objects::nonNull)
                .map(Instant::ofEpochMilli));
    }

    @Override
    public int activeSponsorCount(PlayerRef owner, Instant now) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(now, "now");
        return read(dsl -> dsl.fetchCount(
                PLAYER_WARPS,
                PLAYER_WARPS
                        .OWNER
                        .eq(owner.uuid().toString())
                        .and(PLAYER_WARPS.SPONSORED_UNTIL.isNotNull())
                        .and(PLAYER_WARPS.SPONSORED_UNTIL.gt(now.toEpochMilli()))));
    }

    @Override
    public Set<Integer> activeSponsorSlots(Instant now) {
        Objects.requireNonNull(now, "now");
        return read(dsl -> dsl.select(PLAYER_WARPS.SPONSOR_SLOT)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.SPONSOR_SLOT.isNotNull())
                .and(PLAYER_WARPS.SPONSORED_UNTIL.isNotNull())
                .and(PLAYER_WARPS.SPONSORED_UNTIL.gt(now.toEpochMilli()))
                .fetchSet(PLAYER_WARPS.SPONSOR_SLOT));
    }

    @Override
    public List<PlayerWarp> expiredSponsorships(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        // Bounded index range scan on idx_player_warps_sponsored, oldest-expiry first, never a full-table scan. A warp
        // whose slot is already freed (NULL sponsor_slot) never matches, so a second sweep pass skips it.
        return read(dsl -> dsl.selectFrom(PLAYER_WARPS)
                .where(PLAYER_WARPS.SPONSOR_SLOT.isNotNull())
                .and(PLAYER_WARPS.SPONSORED_UNTIL.isNotNull())
                .and(PLAYER_WARPS.SPONSORED_UNTIL.le(now.toEpochMilli()))
                .orderBy(PLAYER_WARPS.SPONSORED_UNTIL.asc(), PLAYER_WARPS.ID.asc())
                .limit(limit)
                .fetch()
                .map(row -> PlayerWarpRows.toPlayerWarp(row, names)));
    }

    @Override
    public void expireSponsorship(PlayerWarpId id, Instant cooldownUntil) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cooldownUntil, "cooldownUntil");
        // Free the slot and stamp the cooldown in one guarded UPDATE; sponsored_until is left as-is (now in the past),
        // so the freed slot no longer counts as active either way.
        write(dsl -> dsl.update(PLAYER_WARPS)
                .setNull(PLAYER_WARPS.SPONSOR_SLOT)
                .set(PLAYER_WARPS.SPONSOR_COOLDOWN_UNTIL, cooldownUntil.toEpochMilli())
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute());
    }

    private RentReminderCandidate toReminderCandidate(
            org.jooq.Record6<Long, String, String, String, Long, Integer> row) {
        UUID ownerUuid = UUID.fromString(row.value2());
        String ownerName = row.value3() != null ? row.value3() : names.apply(ownerUuid);
        return new RentReminderCandidate(
                PlayerWarpId.of(row.value1()),
                new PlayerRef(ownerUuid, ownerName),
                PlayerWarpName.of(row.value4()),
                Instant.ofEpochMilli(row.value5()),
                row.value6());
    }

    /**
     * Assign every warp a fresh {@code random_sort} key so the RANDOM browse order is not frozen forever. One
     * transaction: read the ids, then batch an update per row binding a new random long. It is done application-side
     * because no single SQL {@code RANDOM()} name is portable across SQLite, MySQL, and PostgreSQL. Meant to be
     * called on a config cadence off the tick thread; wiring that scheduled task is a P6 follow-up. Returns how many
     * rows were reshuffled.
     */
    public int reshuffle() {
        return write(dsl -> {
            List<Long> ids = dsl.select(PLAYER_WARPS.ID).from(PLAYER_WARPS).fetch(PLAYER_WARPS.ID);
            List<Query> updates = new ArrayList<>(ids.size());
            for (Long id : ids) {
                updates.add(dsl.update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.RANDOM_SORT, randomSort.getAsLong())
                        .where(PLAYER_WARPS.ID.eq(id)));
            }
            return updates.isEmpty() ? 0 : dsl.batch(updates).execute().length;
        });
    }

    private PlayerWarpId insert(DSLContext dsl, PlayerWarp warp) {
        long id = nextId(dsl);
        PlayerWarpsRecord record = dsl.newRecord(PLAYER_WARPS);
        PlayerWarpRows.apply(record, warp);
        record.setId(id);
        // random_sort is a persistence-only ordering key the RANDOM browse pages by; stamp it once here. The
        // update branch never touches it, so a visibility flip keeps a warp's place in the shuffle stable until a
        // reshuffle rewrites it.
        record.setRandomSort(randomSort.getAsLong());
        // A brand-new warp has no password, so the three password_* columns stay unset (NULL) on the insert.
        dsl.insertInto(PLAYER_WARPS).set(record).execute();
        return PlayerWarpId.of(id);
    }

    private PlayerWarpId update(DSLContext dsl, PlayerWarp warp, PlayerWarpId id) {
        PlayerWarpsRecord record = dsl.newRecord(PLAYER_WARPS);
        PlayerWarpRows.apply(record, warp);
        // apply() sets neither the id nor the password_* columns, so the UPDATE touches every other column but
        // leaves a set password intact: a visibility flip must never wipe it.
        dsl.update(PLAYER_WARPS)
                .set(record)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .execute();
        return id;
    }

    private static void deleteSideRows(DSLContext dsl, long warpId) {
        dsl.deleteFrom(PLAYER_WARP_RATINGS)
                .where(PLAYER_WARP_RATINGS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_VISITS)
                .where(PLAYER_WARP_VISITS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_BANS)
                .where(PLAYER_WARP_BANS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_WHITELIST)
                .where(PLAYER_WARP_WHITELIST.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_MEMBERS)
                .where(PLAYER_WARP_MEMBERS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_FAVOURITES)
                .where(PLAYER_WARP_FAVOURITES.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_PAYMENTS)
                .where(PLAYER_WARP_PAYMENTS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_RATING_REWARDS)
                .where(PLAYER_WARP_RATING_REWARDS.WARP_ID.eq(warpId))
                .execute();
        dsl.deleteFrom(PLAYER_WARP_PENDING_TELEPORTS)
                .where(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID.eq(warpId))
                .execute();
    }

    private static long nextId(DSLContext dsl) {
        Long maxId = dsl.select(DSL.max(PLAYER_WARPS.ID)).from(PLAYER_WARPS).fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }
}
