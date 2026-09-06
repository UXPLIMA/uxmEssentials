package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishPickupPreferences;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VanishPickupPreferences} implementation. The {@code /vanish pickup} choice is a per-player preference that
 * survives relog, so it is stamped in PDC under a single pre-created key. The sanctioned use for transient per-holder
 * state, like the poses opt-out and the teleport toggles. An unstamped player reads the module's {@code pickup-items}
 * config default, so the first toggle writes an explicit override that then wins over a later config change.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Reads and writes go through the owning live {@code Player} on its region thread
 * the thread a pickup event fires on and the thread {@code /vanish pickup} runs on, and an offline player is never
 * stamped and reads the config default. The {@link NamespacedKey} is created once as a constant, never on a hot path.
 */
@NullMarked
public final class PdcVanishPickup implements VanishPickupPreferences {

    private static final NamespacedKey PICKUP_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("uxmessentials:vanish_pickup"), "vanish_pickup key");

    private final Server server;
    private final boolean defaultPicksUp;

    public PdcVanishPickup(Server server, boolean defaultPicksUp) {
        this.server = Objects.requireNonNull(server, "server");
        this.defaultPicksUp = defaultPicksUp;
    }

    @Override
    public boolean picksUp(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return defaultPicksUp;
        }
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), PICKUP_KEY, defaultPicksUp);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return defaultPicksUp;
        }
        boolean nowPicksUp = !picksUp(who);
        PdcFlag.set(player.getPersistentDataContainer(), PICKUP_KEY, nowPicksUp);
        return nowPicksUp;
    }
}
