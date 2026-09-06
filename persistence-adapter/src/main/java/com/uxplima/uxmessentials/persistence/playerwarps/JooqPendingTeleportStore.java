package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPendingTeleports.PLAYER_WARP_PENDING_TELEPORTS;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpPendingTeleportsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link PendingTeleportStore} over the generated {@code PLAYER_WARP_PENDING_TELEPORTS} table, one
 * row per player, keyed by the {@code player_uuid} primary key so a player can only ever have one teleport in
 * flight. {@link #record} upserts on that key, so a fresh cross-server request overwrites a stale one in place
 * rather than leaving orphaned rows. The uuid is canonical 36-char text, the warp the surrogate {@code long}, the
 * request time epoch-millis, and the charged amount a {@code DECIMAL(20,4)} paired with its currency id, both
 * NULL when nothing was charged. Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqPendingTeleportStore extends JooqRepository implements PendingTeleportStore {

    public JooqPendingTeleportStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void record(PendingTeleport pending) {
        Objects.requireNonNull(pending, "pending");
        String player = pending.player().toString();
        long warp = pending.warp().value();
        long requestedAt = pending.requestedAt().toEpochMilli();
        @Nullable BigDecimal amount = pending.paid().map(WarpCost::amount).orElse(null);
        @Nullable String currency = pending.paid().map(WarpCost::currencyId).orElse(null);
        write(dsl -> {
            dsl.insertInto(PLAYER_WARP_PENDING_TELEPORTS)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID, player)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, warp)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, pending.targetServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, pending.originServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, requestedAt)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, amount)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, currency)
                    .onConflict(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID)
                    .doUpdate()
                    .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, warp)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, pending.targetServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, pending.originServer())
                    .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, requestedAt)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, amount)
                    .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, currency)
                    .execute();
            return null;
        });
    }

    @Override
    public Optional<PendingTeleport> find(UUID player) {
        Objects.requireNonNull(player, "player");
        return read(dsl -> dsl.selectFrom(PLAYER_WARP_PENDING_TELEPORTS)
                .where(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID.eq(player.toString()))
                .fetchOptional()
                .map(JooqPendingTeleportStore::toPending));
    }

    @Override
    public void clear(UUID player) {
        Objects.requireNonNull(player, "player");
        write(dsl -> {
            dsl.deleteFrom(PLAYER_WARP_PENDING_TELEPORTS)
                    .where(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID.eq(player.toString()))
                    .execute();
            return null;
        });
    }

    /** Rebuild the aggregate from a row; the charge is present only when both the amount and currency survived. */
    private static PendingTeleport toPending(PlayerWarpPendingTeleportsRecord row) {
        return new PendingTeleport(
                UUID.fromString(row.getPlayerUuid()),
                PlayerWarpId.of(row.getWarpId()),
                row.getTargetServer(),
                row.getOriginServer(),
                Instant.ofEpochMilli(row.getRequestedAt()),
                paidOf(row.getPaidAmount(), row.getPaidCurrency()));
    }

    /** A charge is only present when both columns are non-NULL; a half-written pair reads as no charge. */
    private static Optional<WarpCost> paidOf(@Nullable BigDecimal amount, @Nullable String currency) {
        if (amount == null || currency == null) {
            return Optional.empty();
        }
        return Optional.of(WarpCost.of(amount, currency));
    }
}
