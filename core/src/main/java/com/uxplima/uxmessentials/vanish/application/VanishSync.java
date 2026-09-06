package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The vanish context's cross-server change currency: one player's vanish state as it crosses the bus, decoupled from
 * the wire frame the same way {@code TradeSignal} is decoupled from {@code TradeSignalFrame}. A {@link VanishBus}
 * carries these. The application publishes one when a player vanishes, reappears, or has their level re-resolved, and
 * receives one for every remote change a peer made. Without the vanish application ever naming the shared network
 * frame types or the transport.
 *
 * <p>The player name rides along so a network-wide {@code /vanish list} on a backend where the hidden player is not
 * online can still render them. When {@link #vanished()} is false the {@link #level()} is not meaningful (a reveal
 * carries {@link VanishLevel#DEFAULT} as a placeholder), so a consumer must branch on the flag, not the level.
 *
 * @param player the player whose vanish state changed
 * @param playerName the player's name at the moment of the change
 * @param vanished {@code true} when the player is now vanished, {@code false} when they reappeared
 * @param level the resolved use level; a placeholder ({@link VanishLevel#DEFAULT}) on a reveal
 */
public record VanishSync(UUID player, String playerName, boolean vanished, VanishLevel level) {

    public VanishSync {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(level, "level");
    }

    /** A change announcing {@code who} is now vanished at {@code level}. */
    public static VanishSync vanished(PlayerRef who, VanishLevel level) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(level, "level");
        return new VanishSync(who.uuid(), who.name(), true, level);
    }

    /** A change announcing {@code who} has reappeared. */
    public static VanishSync revealed(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return new VanishSync(who.uuid(), who.name(), false, VanishLevel.DEFAULT);
    }
}
