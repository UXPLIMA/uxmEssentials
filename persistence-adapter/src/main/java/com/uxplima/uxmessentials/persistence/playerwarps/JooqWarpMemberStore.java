package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpMembers.PLAYER_WARP_MEMBERS;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpMembersRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link WarpMemberStore} over the generated {@code PLAYER_WARP_MEMBERS} table, one row per
 * {@code (warp_id, player_uuid)}. {@link #put} upserts on the composite key: granting a player a new role
 * overwrites their existing role in place rather than inserting a second row, so a player holds at most one role
 * per warp. The role persists as {@link WarpRole#name()} and reads back through {@link WarpRole#parse}; a row
 * whose token no longer names a constant is skipped rather than crashing the load, but the skip is logged at
 * {@code warn} so an operator sees a vanished co-owner/manager instead of it disappearing silently (a future enum
 * rename, a raw DB edit, or an importer writing an unexpected token). Uuids are canonical 36-char text and instants
 * epoch-millis, the schema-wide convention. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqWarpMemberStore extends JooqRepository implements WarpMemberStore {

    private final Logger log;

    public JooqWarpMemberStore(DSLContext dsl, Logger log) {
        super(dsl);
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void put(PlayerWarpId warp, WarpMember member) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(member, "member");
        String role = member.role().name();
        long addedAt = member.addedAt().toEpochMilli();
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_MEMBERS)
                    .set(PLAYER_WARP_MEMBERS.WARP_ID, warp.value())
                    .set(PLAYER_WARP_MEMBERS.PLAYER_UUID, member.player().toString())
                    .set(PLAYER_WARP_MEMBERS.ROLE, role)
                    .set(PLAYER_WARP_MEMBERS.ADDED_AT, addedAt)
                    .onConflict(PLAYER_WARP_MEMBERS.WARP_ID, PLAYER_WARP_MEMBERS.PLAYER_UUID)
                    .doUpdate()
                    .set(PLAYER_WARP_MEMBERS.ROLE, role)
                    .execute();
            return null;
        });
    }

    @Override
    public void remove(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        write(dsl -> {
            dsl.deleteFrom(PLAYER_WARP_MEMBERS)
                    .where(PLAYER_WARP_MEMBERS.WARP_ID.eq(warp.value()))
                    .and(PLAYER_WARP_MEMBERS.PLAYER_UUID.eq(player.toString()))
                    .execute();
            return null;
        });
    }

    @Override
    public Optional<WarpRole> roleOf(PlayerWarpId warp, UUID player) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.select(PLAYER_WARP_MEMBERS.ROLE)
                .from(PLAYER_WARP_MEMBERS)
                .where(PLAYER_WARP_MEMBERS.WARP_ID.eq(warp.value()))
                .and(PLAYER_WARP_MEMBERS.PLAYER_UUID.eq(player.toString()))
                .fetchOptional(PLAYER_WARP_MEMBERS.ROLE)
                .flatMap(token -> parseRole(warp, player, token)));
    }

    @Override
    public List<WarpMember> list(PlayerWarpId warp) {
        Objects.requireNonNull(warp, "warp");
        return read(dsl -> dsl
                .selectFrom(PLAYER_WARP_MEMBERS)
                .where(PLAYER_WARP_MEMBERS.WARP_ID.eq(warp.value()))
                .orderBy(PLAYER_WARP_MEMBERS.ADDED_AT.asc())
                .fetch()
                .stream()
                .flatMap(row -> toMember(warp, row).stream())
                .toList());
    }

    private Optional<WarpMember> toMember(PlayerWarpId warp, PlayerWarpMembersRecord row) {
        UUID player = UUID.fromString(row.getPlayerUuid());
        return parseRole(warp, player, row.getRole())
                .map(role -> new WarpMember(player, role, Instant.ofEpochMilli(row.getAddedAt())));
    }

    /**
     * Parses a stored role token, warning (not throwing) when it names no constant so a fail-soft skip stays visible
     * to an operator. The happy path allocates nothing beyond {@link WarpRole#parse}'s own {@link Optional}.
     */
    private Optional<WarpRole> parseRole(PlayerWarpId warp, UUID player, String token) {
        Optional<WarpRole> role = WarpRole.parse(token);
        if (role.isEmpty()) {
            log.warn(
                    "event=playerwarp_member_role_unparseable warp={} player={} role={}: skipping the row",
                    warp.value(),
                    player,
                    token);
        }
        return role;
    }
}
