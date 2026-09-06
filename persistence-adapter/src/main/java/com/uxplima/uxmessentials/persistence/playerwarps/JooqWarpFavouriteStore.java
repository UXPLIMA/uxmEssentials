package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites.PLAYER_WARP_FAVOURITES;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link WarpFavouriteStore} over the generated {@code PLAYER_WARP_FAVOURITES} table, one row per
 * {@code (player_uuid, warp_id)}, keyed player-first because the natural query is "this player's favourites".
 * {@link #add} inserts with {@code added_at} from the injected {@link Clock} and {@code ON CONFLICT DO NOTHING},
 * so re-starring an already-favourited warp is a silent no-op rather than a duplicate row or a moved timestamp.
 * The player uuid is canonical 36-char text, the warp id the surrogate {@code long}, and the instant epoch-millis
 *, the schema-wide convention. This store owns only the membership rows; the denormalised
 * {@code player_warps.favourite_count} tally is the favourite use case's concern, not this store's. Every
 * statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqWarpFavouriteStore extends JooqRepository implements WarpFavouriteStore {

    private final Clock clock;

    public JooqWarpFavouriteStore(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void add(UUID player, PlayerWarpId warp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        long addedAt = clock.millis();
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_FAVOURITES)
                    .set(PLAYER_WARP_FAVOURITES.PLAYER_UUID, player.toString())
                    .set(PLAYER_WARP_FAVOURITES.WARP_ID, warp.value())
                    .set(PLAYER_WARP_FAVOURITES.ADDED_AT, addedAt)
                    .onConflict(PLAYER_WARP_FAVOURITES.PLAYER_UUID, PLAYER_WARP_FAVOURITES.WARP_ID)
                    .doNothing()
                    .execute();
            return null;
        });
    }

    @Override
    public void remove(UUID player, PlayerWarpId warp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        write(dsl -> {
            dsl.deleteFrom(PLAYER_WARP_FAVOURITES)
                    .where(PLAYER_WARP_FAVOURITES.PLAYER_UUID.eq(player.toString()))
                    .and(PLAYER_WARP_FAVOURITES.WARP_ID.eq(warp.value()))
                    .execute();
            return null;
        });
    }

    @Override
    public boolean contains(UUID player, PlayerWarpId warp) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(warp, "warp");
        return read(dsl -> dsl.fetchExists(dsl.selectFrom(PLAYER_WARP_FAVOURITES)
                .where(PLAYER_WARP_FAVOURITES.PLAYER_UUID.eq(player.toString()))
                .and(PLAYER_WARP_FAVOURITES.WARP_ID.eq(warp.value()))));
    }

    @Override
    public List<PlayerWarpId> listFor(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.select(PLAYER_WARP_FAVOURITES.WARP_ID)
                .from(PLAYER_WARP_FAVOURITES)
                .where(PLAYER_WARP_FAVOURITES.PLAYER_UUID.eq(player.toString()))
                .orderBy(PLAYER_WARP_FAVOURITES.ADDED_AT.asc())
                .fetch(row -> PlayerWarpId.of(row.value1())));
    }
}
