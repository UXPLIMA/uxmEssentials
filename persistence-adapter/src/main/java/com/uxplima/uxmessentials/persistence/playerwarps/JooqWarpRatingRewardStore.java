package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatingRewards.PLAYER_WARP_RATING_REWARDS;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingRewardStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link WarpRatingRewardStore} over the generated {@code PLAYER_WARP_RATING_REWARDS} table, one row per
 * {@code (subject_uuid, warp_id, reward_id)}, the primary key that dedups a rating reward so it cannot be farmed.
 * {@link #record} inserts with {@code ON CONFLICT DO NOTHING}, so recording the same grant twice is a silent no-op
 * rather than a duplicate row or a moved timestamp, and {@link #hasAwarded} is a bare {@code fetchExists} on the key.
 * The subject uuid is canonical 36-char text, the warp id the surrogate {@code long}, and the instant epoch-millis
 * the schema-wide convention. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqWarpRatingRewardStore extends JooqRepository implements WarpRatingRewardStore {

    public JooqWarpRatingRewardStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public boolean hasAwarded(UUID subject, PlayerWarpId warp, String rewardId) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(rewardId, "rewardId");
        return read(dsl -> dsl.fetchExists(dsl.selectFrom(PLAYER_WARP_RATING_REWARDS)
                .where(PLAYER_WARP_RATING_REWARDS.SUBJECT_UUID.eq(subject.toString()))
                .and(PLAYER_WARP_RATING_REWARDS.WARP_ID.eq(warp.value()))
                .and(PLAYER_WARP_RATING_REWARDS.REWARD_ID.eq(rewardId))));
    }

    @Override
    public void record(UUID subject, PlayerWarpId warp, String rewardId, String kind, Instant at) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(rewardId, "rewardId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        long awardedAt = at.toEpochMilli();
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_RATING_REWARDS)
                    .set(PLAYER_WARP_RATING_REWARDS.SUBJECT_UUID, subject.toString())
                    .set(PLAYER_WARP_RATING_REWARDS.WARP_ID, warp.value())
                    .set(PLAYER_WARP_RATING_REWARDS.REWARD_ID, rewardId)
                    .set(PLAYER_WARP_RATING_REWARDS.KIND, kind)
                    .set(PLAYER_WARP_RATING_REWARDS.AWARDED_AT, awardedAt)
                    .onConflict(
                            PLAYER_WARP_RATING_REWARDS.SUBJECT_UUID,
                            PLAYER_WARP_RATING_REWARDS.WARP_ID,
                            PLAYER_WARP_RATING_REWARDS.REWARD_ID)
                    .doNothing()
                    .execute();
            return null;
        });
    }
}
