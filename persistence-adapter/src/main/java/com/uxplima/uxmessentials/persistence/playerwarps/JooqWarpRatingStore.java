package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatings.PLAYER_WARP_RATINGS;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RatingTally;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link WarpRatingStore} over the generated {@code PLAYER_WARP_RATINGS} table, one row per
 * {@code (warp_id, player_uuid)}. {@link #put} upserts on the composite key, so re-rating overwrites a player's star
 * in place rather than stacking a second vote; the star and its timestamp are the only columns a re-rate moves.
 * {@link #tally} folds the warp's rows into a {@link RatingTally}, guarding the null {@code SUM} a rating-less warp
 * yields so it reads {@code (0, 0)} instead of null, and {@link #globalMean} averages every star on the server for
 * the Bayesian prior, reading {@code 0.0} when the table is empty. Uuids are canonical 36-char text and instants
 * epoch-millis, the schema-wide convention. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqWarpRatingStore extends JooqRepository implements WarpRatingStore {

    public JooqWarpRatingStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void put(PlayerWarpId warp, UUID player, int stars, Instant at) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(at, "at");
        long ratedAt = at.toEpochMilli();
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_RATINGS)
                    .set(PLAYER_WARP_RATINGS.WARP_ID, warp.value())
                    .set(PLAYER_WARP_RATINGS.PLAYER_UUID, player.toString())
                    .set(PLAYER_WARP_RATINGS.STARS, stars)
                    .set(PLAYER_WARP_RATINGS.RATED_AT, ratedAt)
                    .onConflict(PLAYER_WARP_RATINGS.WARP_ID, PLAYER_WARP_RATINGS.PLAYER_UUID)
                    .doUpdate()
                    .set(PLAYER_WARP_RATINGS.STARS, stars)
                    .set(PLAYER_WARP_RATINGS.RATED_AT, ratedAt)
                    .execute();
            return null;
        });
    }

    @Override
    public RatingTally tally(PlayerWarpId warp) {
        Objects.requireNonNull(warp, "warp");
        return read(dsl -> {
            Record2<Long, Integer> row = dsl.select(
                            DSL.sum(PLAYER_WARP_RATINGS.STARS).cast(Long.class), DSL.count())
                    .from(PLAYER_WARP_RATINGS)
                    .where(PLAYER_WARP_RATINGS.WARP_ID.eq(warp.value()))
                    .fetchOne();
            if (row == null) {
                return RatingTally.empty();
            }
            // SUM over no rows is null on every backend; a warp nobody has rated tallies to (0, 0).
            Long sum = row.value1();
            Integer count = row.value2();
            return new RatingTally(sum == null ? 0L : sum, count == null ? 0 : count);
        });
    }

    @Override
    public double globalMean() {
        return read(dsl -> {
            BigDecimal mean = dsl.select(DSL.avg(PLAYER_WARP_RATINGS.STARS))
                    .from(PLAYER_WARP_RATINGS)
                    .fetchOne(0, BigDecimal.class);
            // AVG over an empty table is null; no ratings yet means no prior, so the mean reads 0.0.
            return mean == null ? 0.0 : mean.doubleValue();
        });
    }
}
