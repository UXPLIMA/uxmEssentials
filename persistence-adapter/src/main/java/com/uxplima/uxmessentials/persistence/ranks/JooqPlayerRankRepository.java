package com.uxplima.uxmessentials.persistence.ranks;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerRanks.PLAYER_RANKS;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import org.jooq.DSLContext;
import org.jooq.Record;

/**
 * The jOOQ-backed {@link PlayerRankRepository} over the generated {@code PLAYER_RANKS} table. A point read
 * resolves one player's pointer by the {@code uuid} primary key; a {@code save} upserts on that key, a re-save
 * overwrites the rank id, prestige and last-write instant in place, so an advance never inserts a duplicate row.
 * The {@code updated_at} stamp is taken from the injected {@link Clock} at write time, so it is deterministic
 * under test. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqPlayerRankRepository extends JooqRepository implements PlayerRankRepository {

    private final Clock clock;

    public JooqPlayerRankRepository(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<PlayerRank> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return read(dsl -> dsl.selectFrom(PLAYER_RANKS)
                .where(PLAYER_RANKS.UUID.eq(playerId.toString()))
                .fetchOptional()
                .map(JooqPlayerRankRepository::toPlayerRank));
    }

    @Override
    public void save(UUID playerId, RankId rankId, Prestige prestige) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rankId, "rankId");
        Objects.requireNonNull(prestige, "prestige");
        write(dsl -> {
            upsert(dsl, playerId, rankId, prestige);
            return null;
        });
    }

    private void upsert(DSLContext dsl, UUID playerId, RankId rankId, Prestige prestige) {
        long now = clock.instant().toEpochMilli();
        dsl.insertInto(PLAYER_RANKS)
                .set(PLAYER_RANKS.UUID, playerId.toString())
                .set(PLAYER_RANKS.RANK_ID, rankId.value())
                .set(PLAYER_RANKS.PRESTIGE, prestige.level())
                .set(PLAYER_RANKS.UPDATED_AT, now)
                .onConflict(PLAYER_RANKS.UUID)
                .doUpdate()
                .set(PLAYER_RANKS.RANK_ID, rankId.value())
                .set(PLAYER_RANKS.PRESTIGE, prestige.level())
                .set(PLAYER_RANKS.UPDATED_AT, now)
                .execute();
    }

    /** Rebuild a {@link PlayerRank} pointer from a queried row: the raw rank id and prestige, both first-class. */
    private static PlayerRank toPlayerRank(Record row) {
        return new PlayerRank(RankId.of(row.get(PLAYER_RANKS.RANK_ID)), new Prestige(row.get(PLAYER_RANKS.PRESTIGE)));
    }
}
