package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * {@link BroadcastVisibility} backed by PDC. The preference is stored as a single byte under
 * {@code vote-broadcast-off}: byte {@code 0} means the player receives vote broadcasts (the default
 * for a fresh player), byte {@code 1} means they have hidden them. Only the hidden state is stored so
 * a new player automatically receives broadcasts without any PDC entry.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. PDC reads and writes go through the live {@link Player}, so this must
 * be called on the player's region/command thread. The broadcaster runs the {@link #receivesBroadcasts}
 * check on each recipient's entity thread and the {@code /vote broadcasts} command runs the toggle on the
 * sender's region thread. The {@link NamespacedKey} is created once in the constructor, never on a hot
 * path. Mirrors {@code PdcReminderPreferences}.
 */
@NullMarked
public final class PdcBroadcastVisibility implements BroadcastVisibility {

    private final NamespacedKey broadcastOffKey;

    public PdcBroadcastVisibility(Plugin plugin) {
        this.broadcastOffKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "vote-broadcast-off");
    }

    @Override
    public boolean receivesBroadcasts(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            // Offline: cannot read PDC; default to receiving (the broadcaster only targets online players).
            return true;
        }
        return currentlyReceives(player);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true; // safe default; no PDC to write
        }
        boolean nowReceives = !currentlyReceives(player);
        PdcFlag.set(player.getPersistentDataContainer(), broadcastOffKey, !nowReceives);
        return nowReceives;
    }

    private boolean currentlyReceives(Player player) {
        return !PdcFlag.get(player.getPersistentDataContainer(), broadcastOffKey);
    }
}
