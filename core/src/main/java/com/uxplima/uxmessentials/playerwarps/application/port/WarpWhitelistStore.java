package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;

/**
 * Outbound port for a warp's whitelist. The players allowed to teleport to a
 * {@link com.uxplima.uxmessentials.playerwarps.domain.WarpAccess#WHITELIST WHITELIST}-access warp. One row per
 * {@code (warp, player)}, so {@link #add} is idempotent: whitelisting an already-whitelisted player is a no-op,
 * not a duplicate row. The ordered access gate (P4-T3) consults {@link #contains} to decide a whitelist teleport.
 */
public interface WarpWhitelistStore {

    /** Whitelist {@code player} on {@code warp}. Idempotent: a no-op when the player is already whitelisted. */
    void add(PlayerWarpId warp, UUID player);

    /** Remove {@code player} from {@code warp}'s whitelist; a no-op when the player was not whitelisted. */
    void remove(PlayerWarpId warp, UUID player);

    /** True when {@code player} is on {@code warp}'s whitelist. */
    boolean contains(PlayerWarpId warp, UUID player);

    /** Every player whitelisted on {@code warp}. */
    List<UUID> list(PlayerWarpId warp);
}
