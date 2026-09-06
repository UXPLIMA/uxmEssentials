package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ReminderPreferences} backed by PDC. The preference is stored as a single byte under
 * {@code vote-remind-off}: byte {@code 0} means the player wants reminders (the default for a fresh
 * player), byte {@code 1} means they have opted out. Only the opted-out state is stored so new
 * players automatically opt in without any PDC entry.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>region-bound</b>. PDC reads and writes go through the live {@link Player}, so this
 * must be called on the player's region/command thread. The {@link NamespacedKey} is created once in
 * the constructor, never on a hot path.
 */
@NullMarked
public final class PdcReminderPreferences implements ReminderPreferences {

    private final NamespacedKey remindOffKey;

    public PdcReminderPreferences(Plugin plugin) {
        this.remindOffKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "vote-remind-off");
    }

    @Override
    public boolean wantsReminders(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            // Offline: cannot read PDC; assume they want reminders (safe default).
            return true;
        }
        return currentlyWants(player);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true; // safe default; no PDC to write
        }
        boolean nowWants = !currentlyWants(player);
        PdcFlag.set(player.getPersistentDataContainer(), remindOffKey, !nowWants);
        return nowWants;
    }

    private boolean currentlyWants(Player player) {
        return !PdcFlag.get(player.getPersistentDataContainer(), remindOffKey);
    }
}
