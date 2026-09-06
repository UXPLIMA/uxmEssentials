package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.poses.application.port.PoseReturn;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The {@link PoseReturn} that puts a player back where a pose began. The return location is where the player stood
 * when they sat, which is at (or beside) the seat's own region, so the teleport runs on the player's entity thread
 *, the region that owns them under Folia, and no-ops when they are offline. A missing world is a no-op too.
 */
public final class BukkitPoseReturn implements PoseReturn {

    private final Plugin plugin;
    private final Scheduler scheduler;

    public BukkitPoseReturn(Plugin plugin, Scheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void returnTo(PlayerRef who, Position where) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(where, "where");
        scheduler.onEntity(who, () -> {
            Player player = plugin.getServer().getPlayer(who.uuid());
            World world = plugin.getServer().getWorld(where.world().uid());
            if (player != null && world != null) {
                Location destination = BukkitRefs.toLocation(world, where);
                player.teleport(destination);
            }
        });
    }
}
