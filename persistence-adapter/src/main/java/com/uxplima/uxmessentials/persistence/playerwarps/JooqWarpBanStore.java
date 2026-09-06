package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpBans.PLAYER_WARP_BANS;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpBansRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link WarpBanStore} over the generated {@code PLAYER_WARP_BANS} table, one row per
 * {@code (warp_id, player_uuid)}. {@link #ban} upserts on the composite key: re-banning a player overwrites the
 * expiry, reason, and imposer in place rather than inserting a second row, so there is at most one ban per player
 * per warp. A {@code banned_until} of {@code null} is a permanent ban (an absent {@link Optional}); a present one
 * is the absolute expiry as epoch-millis BIGINT. The reason and imposer columns are nullable, a console or
 * reasonless ban stores {@code null}. Uuids are canonical 36-char text and instants epoch-millis, the schema-wide
 * convention. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqWarpBanStore extends JooqRepository implements WarpBanStore {

    public JooqWarpBanStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void ban(PlayerWarpId warp, BanRecord record) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(record, "record");
        @Nullable Long until = record.until().map(Instant::toEpochMilli).orElse(null);
        @Nullable String reason = record.reason().orElse(null);
        @Nullable String bannedBy = record.bannedBy().map(UUID::toString).orElse(null);
        long bannedAt = record.bannedAt().toEpochMilli();
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_BANS)
                    .set(PLAYER_WARP_BANS.WARP_ID, warp.value())
                    .set(PLAYER_WARP_BANS.PLAYER_UUID, record.player().toString())
                    .set(PLAYER_WARP_BANS.BANNED_UNTIL, until)
                    .set(PLAYER_WARP_BANS.REASON, reason)
                    .set(PLAYER_WARP_BANS.BANNED_BY, bannedBy)
                    .set(PLAYER_WARP_BANS.BANNED_AT, bannedAt)
                    .onConflict(PLAYER_WARP_BANS.WARP_ID, PLAYER_WARP_BANS.PLAYER_UUID)
                    .doUpdate()
                    .set(PLAYER_WARP_BANS.BANNED_UNTIL, until)
                    .set(PLAYER_WARP_BANS.REASON, reason)
                    .set(PLAYER_WARP_BANS.BANNED_BY, bannedBy)
                    .set(PLAYER_WARP_BANS.BANNED_AT, bannedAt)
                    .execute();
            return null;
        });
    }

    @Override
    public void unban(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        write(dsl -> {
            dsl.deleteFrom(PLAYER_WARP_BANS)
                    .where(PLAYER_WARP_BANS.WARP_ID.eq(warp.value()))
                    .and(PLAYER_WARP_BANS.PLAYER_UUID.eq(player.toString()))
                    .execute();
            return null;
        });
    }

    @Override
    public Optional<BanRecord> find(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(PLAYER_WARP_BANS)
                .where(PLAYER_WARP_BANS.WARP_ID.eq(warp.value()))
                .and(PLAYER_WARP_BANS.PLAYER_UUID.eq(player.toString()))
                .fetchOptional()
                .map(JooqWarpBanStore::toBanRecord));
    }

    @Override
    public List<BanRecord> list(PlayerWarpId warp) {
        Objects.requireNonNull(warp, "warp");
        return read(dsl -> dsl.selectFrom(PLAYER_WARP_BANS)
                .where(PLAYER_WARP_BANS.WARP_ID.eq(warp.value()))
                .orderBy(PLAYER_WARP_BANS.BANNED_AT.asc())
                .fetch(JooqWarpBanStore::toBanRecord));
    }

    private static BanRecord toBanRecord(PlayerWarpBansRecord row) {
        return new BanRecord(
                UUID.fromString(row.getPlayerUuid()),
                Optional.ofNullable(row.getBannedUntil()).map(Instant::ofEpochMilli),
                Optional.ofNullable(row.getReason()),
                Optional.ofNullable(row.getBannedBy()).map(UUID::fromString),
                Instant.ofEpochMilli(row.getBannedAt()));
    }
}
