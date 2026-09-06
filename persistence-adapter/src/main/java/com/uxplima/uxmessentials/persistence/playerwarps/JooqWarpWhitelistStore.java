package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpWhitelist.PLAYER_WARP_WHITELIST;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link WarpWhitelistStore} over the generated {@code PLAYER_WARP_WHITELIST} table, one row per
 * {@code (warp_id, player_uuid)}. {@link #add} inserts with {@code added_at} read from the injected {@link Clock}
 * and an {@code ON CONFLICT DO NOTHING} on the composite key, so re-whitelisting a player is a silent no-op
 * rather than a duplicate row or a moved timestamp. The warp id is the surrogate {@code long}; the player uuid is
 * the canonical 36-char text and the instant is epoch-millis BIGINT, the schema-wide convention. Every statement
 * is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqWarpWhitelistStore extends JooqRepository implements WarpWhitelistStore {

    private final Clock clock;

    public JooqWarpWhitelistStore(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void add(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        long addedAt = clock.millis();
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_WHITELIST)
                    .set(PLAYER_WARP_WHITELIST.WARP_ID, warp.value())
                    .set(PLAYER_WARP_WHITELIST.PLAYER_UUID, player.toString())
                    .set(PLAYER_WARP_WHITELIST.ADDED_AT, addedAt)
                    .onConflict(PLAYER_WARP_WHITELIST.WARP_ID, PLAYER_WARP_WHITELIST.PLAYER_UUID)
                    .doNothing()
                    .execute();
            return null;
        });
    }

    @Override
    public void remove(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        write(dsl -> {
            dsl.deleteFrom(PLAYER_WARP_WHITELIST)
                    .where(PLAYER_WARP_WHITELIST.WARP_ID.eq(warp.value()))
                    .and(PLAYER_WARP_WHITELIST.PLAYER_UUID.eq(player.toString()))
                    .execute();
            return null;
        });
    }

    @Override
    public boolean contains(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.fetchExists(dsl.selectFrom(PLAYER_WARP_WHITELIST)
                .where(PLAYER_WARP_WHITELIST.WARP_ID.eq(warp.value()))
                .and(PLAYER_WARP_WHITELIST.PLAYER_UUID.eq(player.toString()))));
    }

    @Override
    public List<UUID> list(PlayerWarpId warp) {
        Objects.requireNonNull(warp, "warp");
        return read(dsl -> dsl.select(PLAYER_WARP_WHITELIST.PLAYER_UUID)
                .from(PLAYER_WARP_WHITELIST)
                .where(PLAYER_WARP_WHITELIST.WARP_ID.eq(warp.value()))
                .orderBy(PLAYER_WARP_WHITELIST.ADDED_AT.asc())
                .fetch(row -> UUID.fromString(row.value1())));
    }
}
