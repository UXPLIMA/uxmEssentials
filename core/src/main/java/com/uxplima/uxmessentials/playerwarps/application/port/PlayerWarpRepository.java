package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for durable player-warp storage. Every warp fact (owner, name, world, coordinates, access,
 * status, economy, and the rating/visit rollups) is a first-class column. There is no opaque JSON blob (the
 * architecture persistence invariant), so a {@link PlayerWarp} loaded from a row is rebuilt from queryable
 * fields, and the list queries read them in stored creation order.
 *
 * <p>Warp names are now server-wide unique, so lookups key on the {@link PlayerWarpName} or the surrogate
 * {@link PlayerWarpId}, never on {@code (owner, name)}. A {@link #save} upserts on the surrogate id, assigning a
 * fresh one on insert and returning it, and a delete is by id. A cache decorator may sit in front of this port;
 * the contract here is the durable source of truth.
 */
public interface PlayerWarpRepository {

    /** The warp under {@code name}, if one exists anywhere on the server (names are globally unique). */
    Optional<PlayerWarp> findByName(PlayerWarpName name);

    /** The warp with the surrogate key {@code id}, if it still exists. */
    Optional<PlayerWarp> findById(PlayerWarpId id);

    /** Every warp {@code owner} owns, in stored creation order, for the {@code /pwarps} list. */
    List<PlayerWarp> ownedBy(PlayerRef owner);

    /**
     * The {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#ACTIVE active},
     * {@link com.uxplima.uxmessentials.playerwarps.domain.WarpAccess#PUBLIC public} warps {@code owner} owns, in
     * stored creation order, for a cross-owner {@code /pwarps}. A private, suspended, or archived warp never
     * leaks to another player through this query.
     */
    List<PlayerWarp> publicOwnedBy(PlayerRef owner);

    /**
     * Every warp across every owner, in stored creation order, for the management GUI's admin view (an operator
     * holding {@code uxmessentials.pwarp.gui} manages all players' warps, not just their own). A bounded scan run
     * off the tick thread; an owner-scoped caller uses {@link #ownedBy} instead. The default returns an empty
     * list so a store that does not implement it simply shows nothing rather than failing, the jOOQ adapter
     * overrides it with the real query.
     */
    default List<PlayerWarp> all() {
        return List.of();
    }

    /** How many warps {@code owner} owns (the {@code /setpwarp} limit check). */
    int count(PlayerRef owner);

    /** True when a warp already exists under {@code name} anywhere on the server (the global name-collision check). */
    boolean existsByName(PlayerWarpName name);

    /**
     * Insert {@code warp} or overwrite the row under its surrogate {@link PlayerWarpId}, returning the id. A warp
     * with no id is a new row: the store assigns a fresh key and returns it, so the caller can re-read the saved
     * aggregate through {@link PlayerWarp#withId}. A warp that already carries an id is updated in place and that
     * same id is returned.
     */
    PlayerWarpId save(PlayerWarp warp);

    /** Remove the warp with surrogate key {@code id}; a no-op when no such row exists. */
    void deleteById(PlayerWarpId id);

    /**
     * Atomically bump the visit counter on the warp with surrogate key {@code id} by one. This is a
     * high-frequency, eventually-consistent write, it runs on every teleport to the warp, so it does the
     * increment in the database rather than reading, mutating, and saving the whole row (which would lose
     * concurrent visits to a last-writer-wins race). It deliberately stays off the cross-server sync path:
     * a visitor count drifting by a few on peers until the next real change is fine, and is not worth a
     * cluster-wide cache invalidation per visit.
     */
    void recordVisit(PlayerWarpId id);

    /**
     * Overwrite the denormalised rating rollup on the warp with surrogate key {@code id} with {@code summary}, the
     * sum, count, average, and the Bayesian score the "top rated" browse sorts on. A guarded single-row UPDATE the
     * rate use case runs after recomputing the rollup from the {@code WarpRatingStore}, so the sort column stays in
     * step with the vote rows without the browse ever touching the per-vote table.
     */
    void updateRating(PlayerWarpId id, RatingSummary summary);

    /**
     * Recompute {@code favourite_count} on the warp with surrogate key {@code id} from the live favourite rows
     * {@code SET favourite_count = (SELECT COUNT(*) FROM player_warp_favourites WHERE warp_id = id)}, rather than a
     * {@code +1}/{@code -1} bump, so a double-click that races the favourite membership check can never drift the
     * stored count away from the true row count. The favourite use case calls it after a star or un-star commits.
     */
    void refreshFavouriteCount(PlayerWarpId id);

    /**
     * A bounded page of {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#ACTIVE active} warps whose
     * paid rent term has lapsed ({@code rent_paid_until <= now}), oldest-due first, for the rent sweep's charge
     * pass. Indexed on {@code rent_paid_until} ({@code idx_player_warps_rent}) and capped at {@code limit} so the
     * sweep is always a bounded index range scan, never a full-table scan. A warp with no rent term (NULL
     * {@code rent_paid_until}) never matches, so it is exempt until the sub-group enrols it. The default returns an
     * empty list so an undecorated store simply charges nothing; the jOOQ adapter overrides it.
     */
    default List<PlayerWarp> dueForRent(Instant now, int limit) {
        return List.of();
    }

    /**
     * A bounded page of {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#SUSPENDED suspended} warps
     * for the rent sweep's retry/archive pass, those closest to their archive deadline first. Capped at
     * {@code limit}. Each is re-charged (restoring it on success) or, if its grace window has lapsed, archived
     * the policy decides per warp from the aggregate's rent state. The default returns an empty list; the jOOQ
     * adapter overrides it.
     */
    default List<PlayerWarp> suspendedForRent(int limit) {
        return List.of();
    }

    /**
     * A bounded page of {@link com.uxplima.uxmessentials.playerwarps.domain.WarpStatus#ACTIVE active} warps whose
     * paid term falls within {@code (now, horizon]} and whose {@code rent_reminded_stage} is still below
     * {@code maxStage}, for the reminder pass: the candidates that might still owe a heads-up mail. Capped at
     * {@code limit} and read as a light projection ({@link RentReminderCandidate}), never the full aggregate. The
     * default returns an empty list; the jOOQ adapter overrides it.
     */
    default List<RentReminderCandidate> remindableForRent(Instant now, Instant horizon, int maxStage, int limit) {
        return List.of();
    }

    /**
     * Set the {@code rent_reminded_stage} dedup counter on the warp with surrogate key {@code id} to {@code stage}
     * in one guarded UPDATE. The reminder pass bumps it after mailing a window, and the settle pass resets it to
     * {@code 0} once rent is paid so the next term's reminders start fresh. This column is persistence-only (it is
     * never a fact on the aggregate), so it has its own writer rather than riding a whole-row save. The default is
     * a no-op; the jOOQ adapter overrides it.
     */
    default void markRentReminded(PlayerWarpId id, int stage) {
        // No-op for an in-memory store that does not track the reminder dedup counter.
    }

    /**
     * The warps {@code owner} owns if they are already in memory, without touching the database. A cache
     * decorator returns its cached set on a hit and an empty {@link Optional} on a miss; an undecorated store
     * has nothing in memory and so returns empty. This exists for tick-thread callers that must never block
     * on I/O, chiefly the {@code /pwarp}/{@code /pwarp del} name-argument suggesters, which complete only the
     * warps a join-warmed cache already holds and suggest nothing on a cold miss rather than reaching the
     * disk while the player types. Never loads; never blocks.
     */
    default Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
        return Optional.empty();
    }

    /**
     * The post-expiry sponsor cooldown instant for the warp {@code id}, the {@code sponsor_cooldown_until} V73
     * column, or empty when the warp carries none. {@code BuySponsorship} reads it to refuse a re-sponsor while the
     * warp is still cooling down. Persistence-only (never a fact on the aggregate), so it has its own reader. The
     * default returns empty; the jOOQ adapter overrides it.
     */
    default Optional<Instant> sponsorCooldownUntil(PlayerWarpId id) {
        return Optional.empty();
    }

    /**
     * How many of {@code owner}'s warps hold a live sponsorship as of {@code now} ({@code sponsored_until > now})
     * the concurrent-sponsorship count {@code BuySponsorship} caps per player. A bounded count query, never a scan of
     * the owner's aggregates. The default returns {@code 0}; the jOOQ adapter overrides it.
     */
    default int activeSponsorCount(PlayerRef owner, Instant now) {
        return 0;
    }

    /**
     * The sponsor slots currently occupied by a live sponsorship as of {@code now} ({@code sponsored_until > now} with
     * a non-NULL {@code sponsor_slot}), so {@code BuySponsorship} can pick the lowest free slot. A small bounded read.
     * The default returns an empty set; the jOOQ adapter overrides it.
     */
    default Set<Integer> activeSponsorSlots(Instant now) {
        return Set.of();
    }

    /**
     * A bounded page of warps whose sponsorship term has lapsed ({@code sponsored_until <= now}) but that still hold a
     * {@code sponsor_slot}, oldest-expiry first, for the sponsor expiry sweep. Indexed on {@code sponsored_until}
     * ({@code idx_player_warps_sponsored}) and capped at {@code limit}, so the sweep is always a bounded index range
     * scan, never a full-table scan. The default returns an empty list; the jOOQ adapter overrides it.
     */
    default List<PlayerWarp> expiredSponsorships(Instant now, int limit) {
        return List.of();
    }

    /**
     * Free the warp {@code id}'s sponsor slot (set {@code sponsor_slot} to NULL) and stamp its post-expiry cooldown
     * ({@code sponsor_cooldown_until = cooldownUntil}) in one guarded UPDATE: the expiry sweep's write. The default
     * is a no-op; the jOOQ adapter overrides it.
     */
    default void expireSponsorship(PlayerWarpId id, Instant cooldownUntil) {
        // No-op for an in-memory store that does not track sponsor expiry.
    }
}
