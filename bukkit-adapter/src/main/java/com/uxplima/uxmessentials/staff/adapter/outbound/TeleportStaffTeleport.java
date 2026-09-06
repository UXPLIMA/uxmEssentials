package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * The teleport-backed {@link com.uxplima.uxmessentials.staff.application.port.StaffTeleport}: the COMPASS gadget
 * and {@code /stafflist} route through the existing teleport {@link TeleportEngine} as an
 * {@link TeleportKind#ADMIN} hop (no cooldown, warmup, or request prompt), so staff mode never moves the player
 * itself. The target's live {@code Location} is read here, the one place a Bukkit lookup happens, and mapped to
 * a domain {@link Destination} through {@link BukkitRefs}.
 *
 * <p>Bound only when the teleport module is enabled; with teleport off the wiring binds
 * {@link com.uxplima.uxmessentials.staff.application.port.StaffTeleport#NONE}, so the gadget degrades to
 * "teleport could not be started" rather than failing.
 */
@NullMarked
public final class TeleportStaffTeleport implements com.uxplima.uxmessentials.staff.application.port.StaffTeleport {

    private final TeleportEngine engine;
    private final Server server;

    public TeleportStaffTeleport(TeleportEngine engine, Server server) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean teleportTo(PlayerRef staff, PlayerRef target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        Player targetPlayer = server.getPlayer(target.uuid());
        if (targetPlayer == null) {
            return false;
        }
        Destination destination = Destination.at(
                BukkitRefs.toPosition(Objects.requireNonNull(targetPlayer.getLocation(), "target location")));
        return engine.launch(staff, destination, TeleportKind.ADMIN).isOk();
    }
}
