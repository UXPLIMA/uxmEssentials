package com.uxplima.uxmessentials.playerwarps.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;

/**
 * Outbound port for a warp's per-player bans. One row per {@code (warp, player)}, so {@link #ban} is an upsert:
 * re-banning a player who is already banned overwrites the existing row (updating the reason, expiry, and who
 * imposed it) rather than inserting a second. The ordered access gate (P4-T3) calls {@link #isBannedAt} to reject
 * a teleport, so the active-at check lives here rather than in the caller.
 */
public interface WarpBanStore {

    /** Ban the player named in {@code record} from {@code warp}, upserting on {@code (warp, record.player)}. */
    void ban(PlayerWarpId warp, BanRecord record);

    /** Lift {@code player}'s ban from {@code warp}; a no-op when the player is not banned. */
    void unban(PlayerWarpId warp, UUID player);

    /** The ban held against {@code player} on {@code warp}, whether or not it is still in force. */
    Optional<BanRecord> find(PlayerWarpId warp, UUID player);

    /** Every ban on {@code warp}, including any that have already expired. */
    List<BanRecord> list(PlayerWarpId warp);

    /**
     * True when {@code player} is under a ban on {@code warp} that is still in force at {@code now}: an expired
     * timed ban reads as not banned. Composes {@link #find} with {@link BanRecord#isActiveAt} so callers never
     * re-implement the expiry rule.
     */
    default boolean isBannedAt(PlayerWarpId warp, UUID player, Instant now) {
        return find(warp, player).map(record -> record.isActiveAt(now)).orElse(false);
    }
}
