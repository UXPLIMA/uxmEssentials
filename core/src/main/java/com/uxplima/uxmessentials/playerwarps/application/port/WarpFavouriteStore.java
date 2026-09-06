package com.uxplima.uxmessentials.playerwarps.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;

/**
 * Outbound port for a player's favourite warps. The warps a player has starred so the browse UI can filter to
 * "my favourites". Keyed the other way round from the warp-owned stores: the row is per {@code (player, warp)}
 * and the natural query is "this player's favourites", so {@link #listFor} takes the player. One row per
 * {@code (player, warp)}, so {@link #add} is idempotent: starring an already-favourited warp is a no-op.
 *
 * <p>This store owns only the membership rows; the denormalised {@code player_warps.favourite_count} tally is
 * maintained by the favourite use case (P4-T6), not here.
 */
public interface WarpFavouriteStore {

    /** Star {@code warp} for {@code player}. Idempotent: a no-op when the warp is already a favourite. */
    void add(UUID player, PlayerWarpId warp);

    /** Unstar {@code warp} for {@code player}; a no-op when the warp was not a favourite. */
    void remove(UUID player, PlayerWarpId warp);

    /** True when {@code player} has starred {@code warp}. */
    boolean contains(UUID player, PlayerWarpId warp);

    /** Every warp {@code player} has starred, for the favourites-of browse filter. */
    List<PlayerWarpId> listFor(UUID player);
}
