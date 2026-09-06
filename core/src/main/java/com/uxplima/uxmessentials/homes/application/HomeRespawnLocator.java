package com.uxplima.uxmessentials.homes.application;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The non-blocking seam the teleport context's respawn listener calls to resolve a {@code HOME} step in the
 * per-world respawn chain: where the player's home is, read from the cache only. It exists so the respawn
 * listener, which runs inside the {@link org.bukkit.event.player.PlayerRespawnEvent} on the player's region
 * thread: never reaches the database while resolving a death landing.
 *
 * <p>The implementation reads the owner's already-warmed home set (the join cache-warmer fills it on login)
 * and returns the lowest-slot home's position. A cold cache or an owner with no homes yields empty, and the
 * chain falls through to its next step. <b>Must never load or block</b>: a respawn that stalls on disk I/O
 * would hang the region thread.
 */
public interface HomeRespawnLocator {

    /**
     * The position of {@code who}'s lowest-slot home if it is already in the cache, or empty on a cold cache
     * or when the player owns no homes. Never loads from the database; never blocks.
     */
    Optional<Position> respawnHome(PlayerRef who);
}
