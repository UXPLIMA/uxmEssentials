package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites.PLAYER_WARP_FAVOURITES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link PlayerWarpBrowse}. The read side that answers the old browse's "verimsiz" (inefficient)
 * complaint. The previous browse GUI ran {@code SELECT *} over {@code player_warps}, rebuilt one {@code PlayerWarp}
 * aggregate per row, and paginated in Java; opening the browse got linearly slower as the server grew. This impl
 * instead issues one bounded query pair per page: a projection of only the card columns filtered, sorted, and
 * {@code LIMIT}/{@code OFFSET}-windowed, plus a {@code COUNT} over the same predicate for the total. It never loads
 * an aggregate and never reads the whole table, so a page over a hundred thousand warps costs the same as one over
 * eight: the guard {@code PlayerWarpBrowseIsPagedDriftTest} freezes that in.
 *
 * <p>Correctness notes. Every sort appends a stable {@code id ASC} tiebreaker so paging is deterministic across
 * reads. {@code viewerFavourited} comes from a {@code LEFT JOIN} on {@code player_warp_favourites} scoped to the
 * viewer, so it is one join rather than a per-card follow-up query. {@code sponsored} is computed against a supplied
 * {@link Clock} ({@code sponsored_until > now}). {@link WarpSort#RATING} orders by the stored Bayesian
 * {@code rating_score} column. The rating use case computes that value; here it is only read. {@link WarpSort#DISTANCE}
 * orders by squared planar distance within the viewer's world and falls back to {@link WarpSort#ALPHABETICAL} when
 * the query carries no viewer position. This port applies exactly the filters it is given and adds no hidden access
 * check: a card in a browse never grants access; the teleport gate is the real guard. Every statement is typed jOOQ
 * DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqPlayerWarpBrowse extends JooqRepository implements PlayerWarpBrowse {

    private static final PlayerWarpFavourites VF = PLAYER_WARP_FAVOURITES.as("vf");

    /**
     * The projection: only the columns a {@link WarpCard} renders, plus the joined favourite marker. Deliberately
     * not {@code player_warps.*}: a card is a flat listing row, never a rehydrated aggregate.
     */
    private static final List<SelectFieldOrAsterisk> CARD_FIELDS = List.of(
            PLAYER_WARPS.ID,
            PLAYER_WARPS.NAME,
            PLAYER_WARPS.DISPLAY_NAME,
            PLAYER_WARPS.OWNER,
            PLAYER_WARPS.OWNER_NAME,
            PLAYER_WARPS.WORLD_NAME,
            PLAYER_WARPS.SERVER_ID,
            PLAYER_WARPS.CATEGORY_ID,
            PLAYER_WARPS.ICON,
            PLAYER_WARPS.VISIT_COUNT,
            PLAYER_WARPS.UNIQUE_VISITORS,
            PLAYER_WARPS.RATING_AVERAGE,
            PLAYER_WARPS.RATING_COUNT,
            PLAYER_WARPS.FAVOURITE_COUNT,
            PLAYER_WARPS.PRICE_AMOUNT,
            PLAYER_WARPS.PRICE_CURRENCY,
            PLAYER_WARPS.ACCESS,
            PLAYER_WARPS.SPONSORED_UNTIL,
            VF.PLAYER_UUID);

    private final Clock clock;

    public JooqPlayerWarpBrowse(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Page<WarpCard> page(WarpQuery query) {
        Objects.requireNonNull(query, "query");
        long now = clock.millis();
        Condition where = buildWhere(query);
        return read(dsl -> {
            long total = countMatching(dsl, where);
            List<WarpCard> items = fetchPage(dsl, query, where, now);
            return new Page<>(items, total, query.page(), query.pageSize());
        });
    }

    @Override
    public List<WarpCard> activeSponsors(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        long now = clock.millis();
        // A small bounded read ordered by sponsor_slot for the pinned browse tiles. The sponsors live at
        // sponsored_until > now with a slot, indexed on idx_player_warps_sponsored, capped at limit. No viewer join is
        // needed (the pinned tiles carry no per-viewer favourite marker), so the favourite column reads as absent.
        return read(dsl -> dsl.select(CARD_FIELDS)
                .from(PLAYER_WARPS)
                .leftJoin(VF)
                .on(DSL.falseCondition())
                .where(PLAYER_WARPS.SPONSOR_SLOT.isNotNull())
                .and(PLAYER_WARPS.SPONSORED_UNTIL.isNotNull())
                .and(PLAYER_WARPS.SPONSORED_UNTIL.gt(now))
                .and(PLAYER_WARPS.STATUS.eq(WarpStatus.ACTIVE.name()))
                .and(PLAYER_WARPS.ACCESS.eq(WarpAccess.PUBLIC.name()))
                .orderBy(PLAYER_WARPS.SPONSOR_SLOT.asc(), PLAYER_WARPS.ID.asc())
                .limit(limit)
                .fetch(record -> toCard(record, now)));
    }

    private static long countMatching(DSLContext dsl, Condition where) {
        Integer count = dsl.selectCount().from(PLAYER_WARPS).where(where).fetchOne(0, Integer.class);
        return count == null ? 0L : count;
    }

    private static List<WarpCard> fetchPage(DSLContext dsl, WarpQuery query, Condition where, long now) {
        return dsl.select(CARD_FIELDS)
                .from(PLAYER_WARPS)
                .leftJoin(VF)
                .on(VF.WARP_ID
                        .eq(PLAYER_WARPS.ID)
                        .and(VF.PLAYER_UUID.eq(query.viewer().toString())))
                .where(where)
                .orderBy(orderBy(query))
                .limit(query.pageSize())
                .offset(query.page() * query.pageSize())
                .fetch(record -> toCard(record, now));
    }

    private static Condition buildWhere(WarpQuery query) {
        Condition where = DSL.noCondition();
        if (query.onlyActive()) {
            where = where.and(PLAYER_WARPS.STATUS.eq(WarpStatus.ACTIVE.name()));
        }
        where = and(where, query.access(), access -> PLAYER_WARPS.ACCESS.eq(access.name()));
        where = and(where, query.category(), category -> PLAYER_WARPS.CATEGORY_ID.eq(category));
        where = and(where, query.server(), server -> PLAYER_WARPS.SERVER_ID.eq(server));
        where = and(where, query.owner(), owner -> PLAYER_WARPS.OWNER.eq(owner.toString()));
        where = and(where, query.favouritesOf(), JooqPlayerWarpBrowse::favouritedBy);
        where = and(where, query.search(), JooqPlayerWarpBrowse::matchesText);
        if (query.sort() == WarpSort.DISTANCE) {
            // DISTANCE is only meaningful within one world, so it is also a filter, not just an ordering.
            where = and(
                    where,
                    query.viewerPosition(),
                    pos -> PLAYER_WARPS.WORLD.eq(pos.world().uid().toString()));
        }
        return where;
    }

    private static <T> Condition and(Condition base, Optional<T> value, Function<T, Condition> clause) {
        return value.map(present -> base.and(clause.apply(present))).orElse(base);
    }

    private static Condition favouritedBy(UUID player) {
        return PLAYER_WARPS.ID.in(DSL.select(PLAYER_WARP_FAVOURITES.WARP_ID)
                .from(PLAYER_WARP_FAVOURITES)
                .where(PLAYER_WARP_FAVOURITES.PLAYER_UUID.eq(player.toString())));
    }

    private static Condition matchesText(String search) {
        // containsIgnoreCase lowercases both sides and escapes LIKE wildcards, so no SQL is string-built and a
        // literal % or _ in the search text stays literal. A null display_name simply fails its half of the OR.
        return PLAYER_WARPS.NAME.containsIgnoreCase(search).or(PLAYER_WARPS.DISPLAY_NAME.containsIgnoreCase(search));
    }

    private static List<OrderField<?>> orderBy(WarpQuery query) {
        List<OrderField<?>> order = new ArrayList<>(2);
        order.add(primarySort(query));
        order.add(PLAYER_WARPS.ID.asc()); // stable tiebreaker: paging is deterministic even when the sort key ties
        return order;
    }

    private static OrderField<?> primarySort(WarpQuery query) {
        return switch (query.sort()) {
            case ALPHABETICAL -> PLAYER_WARPS.NAME.asc();
            case NEWEST -> PLAYER_WARPS.CREATED_AT.desc();
            case OLDEST -> PLAYER_WARPS.CREATED_AT.asc();
            case VISITS -> PLAYER_WARPS.VISIT_COUNT.desc();
            case UNIQUE_VISITORS -> PLAYER_WARPS.UNIQUE_VISITORS.desc();
            case RATING -> PLAYER_WARPS.RATING_SCORE.desc();
            case RATING_COUNT -> PLAYER_WARPS.RATING_COUNT.desc();
            case FAVOURITES -> PLAYER_WARPS.FAVOURITE_COUNT.desc();
            case RANDOM -> PLAYER_WARPS.RANDOM_SORT.asc();
            case DISTANCE ->
                query.viewerPosition().map(JooqPlayerWarpBrowse::distanceOrder).orElseGet(PLAYER_WARPS.NAME::asc);
        };
    }

    private static SortField<?> distanceOrder(Position pos) {
        Field<Double> dx = PLAYER_WARPS.X.sub(pos.x());
        Field<Double> dz = PLAYER_WARPS.Z.sub(pos.z());
        return dx.mul(dx).add(dz.mul(dz)).asc();
    }

    private static WarpCard toCard(Record row, long now) {
        Long sponsoredUntil = row.get(PLAYER_WARPS.SPONSORED_UNTIL);
        boolean sponsored = sponsoredUntil != null && sponsoredUntil > now;
        boolean favourited = row.get(VF.PLAYER_UUID) != null;
        String ownerName = row.get(PLAYER_WARPS.OWNER_NAME);
        return new WarpCard(
                PlayerWarpId.of(row.get(PLAYER_WARPS.ID)),
                row.get(PLAYER_WARPS.NAME),
                row.get(PLAYER_WARPS.DISPLAY_NAME),
                ownerName != null ? ownerName : row.get(PLAYER_WARPS.OWNER),
                row.get(PLAYER_WARPS.WORLD_NAME),
                row.get(PLAYER_WARPS.SERVER_ID),
                row.get(PLAYER_WARPS.CATEGORY_ID),
                row.get(PLAYER_WARPS.ICON),
                row.get(PLAYER_WARPS.VISIT_COUNT),
                row.get(PLAYER_WARPS.UNIQUE_VISITORS),
                row.get(PLAYER_WARPS.RATING_AVERAGE),
                row.get(PLAYER_WARPS.RATING_COUNT),
                row.get(PLAYER_WARPS.FAVOURITE_COUNT),
                row.get(PLAYER_WARPS.PRICE_AMOUNT),
                row.get(PLAYER_WARPS.PRICE_CURRENCY),
                WarpAccess.parse(row.get(PLAYER_WARPS.ACCESS)).orElseThrow(() -> unknownAccess(row)),
                sponsored,
                favourited);
    }

    private static IllegalStateException unknownAccess(Record row) {
        return new IllegalStateException(
                "unrecognised player warp access token in storage: " + row.get(PLAYER_WARPS.ACCESS));
    }
}
