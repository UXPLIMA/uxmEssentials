package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that resolves player identities without exposing a Bukkit {@code Player}.
 *
 * <p>The adapter looks players up by name or UUID and maps the result to a {@link PlayerRef}.
 * Resolution by name returns the online player when one matches; offline-name resolution that hits a
 * profile cache or the mojang lookup is the adapter's concern and may be empty. Application code never
 * iterates {@code Bukkit.getOnlinePlayers()}: it asks this port.
 */
public interface PlayerLookup {

    /** The online player with this exact name, if one is connected. */
    Optional<PlayerRef> findOnlineByName(String name);

    /**
     * The player with this exact name, online first and otherwise a profile that has played before. Unlike
     * {@link #findOnlineByName}, this resolves an offline owner so a public player-warp stays reachable while
     * its owner is offline; a name the server has never seen resolves to empty. The default delegates to
     * {@link #findOnlineByName} so a test fake stays online-only unless it overrides this.
     */
    default Optional<PlayerRef> findByName(String name) {
        return findOnlineByName(name);
    }

    /** The player with this UUID, online or resolvable from the profile cache. */
    Optional<PlayerRef> findByUuid(UUID uuid);

    /** True when the player with this UUID is currently connected. */
    boolean isOnline(UUID uuid);
}
