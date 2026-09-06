package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerSitPreferences} implementation. The {@code /poses toggle} opt-out is a per-player preference that
 * survives relog, so it is stamped in PDC under a single pre-created key. The sanctioned use for transient
 * per-holder state, like the teleport and message toggles. The stored byte holds the <em>allow</em> state directly:
 * an absent key defaults to allow (the GSit convention. A player who never touched it can be sat on), so a first
 * toggle writes a refusing {@code (byte) 0}.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Reads and writes go through the owning live {@code Player} on its region thread;
 * an offline player is never stamped and reads the allow default. The {@link NamespacedKey} is created once as a
 * constant, never on a hot path.
 */
@NullMarked
public final class PdcPlayerSitPreferences implements PlayerSitPreferences {

    private static final NamespacedKey ALLOW_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("uxmessentials:poses_playersit_allow"), "poses_playersit_allow key");

    @Override
    public boolean allowsSitting(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true; // a player who is offline (or never toggled) allows being sat on by default
        }
        return PdcFlag.getOrDefault(player.getPersistentDataContainer(), ALLOW_KEY, true);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return true;
        }
        boolean nowAllows = !allowsSitting(who);
        PdcFlag.set(player.getPersistentDataContainer(), ALLOW_KEY, nowAllows);
        return nowAllows;
    }
}
