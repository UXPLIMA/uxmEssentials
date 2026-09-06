package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.poses.application.port.PoseRegionFlags;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The WorldGuard side of the poses region gate, reached purely by reflection behind a plugin-present guard, the same
 * pattern the menu vocabulary's {@code worldguard-region} condition uses. It maps each {@link PoseType} to its custom
 * flag ({@code sit} / {@code playersit} / {@code pose} / {@code crawl}, registered at load by
 * {@link WorldGuardPoseFlagRegistrar}) and reports whether a covering region has set that flag DENY at a location.
 *
 * <p>Named the SDK only by string class-name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or method signature carries a {@code com.sk89q}
 * type: on a server without WorldGuard the present-guard short-circuits before any {@code Class.forName}, so none of
 * its classes load. The gate is fail-open. An absent plugin, an unknown world, an unregistered flag, or any
 * reflective failure (a version bump moving the query chain) all report "not denied" and are logged at most once,
 * because wrongly blocking a harmless sit is worse than missing a rare veto.
 */
@NullMarked
public final class WorldGuardPoseFlags implements PoseRegionFlags {

    /** The custom flag names, in the fixed order the registrar registers them. Package-shared with the registrar. */
    static final List<String> FLAG_NAMES = List.of("sit", "playersit", "pose", "crawl");

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public WorldGuardPoseFlags(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The WorldGuard custom flag that governs {@code type}: LAY / BELLYFLOP / SPIN all share the {@code pose} flag. */
    static String flagName(PoseType type) {
        return switch (type) {
            case SIT -> "sit";
            case PLAYER_SIT -> "playersit";
            case LAY, BELLYFLOP, SPIN -> "pose";
            case CRAWL -> "crawl";
        };
    }

    @Override
    public boolean deniesPose(Position where, PoseType type) {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(type, "type");
        if (!WorldGuardReflection.isEnabled(server)) {
            return false;
        }
        World world = server.getWorld(where.world().uid());
        if (world == null) {
            return false;
        }
        try {
            return WorldGuardReflection.queryDeny(new Location(world, where.x(), where.y(), where.z()), flagName(type));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return false;
        }
    }

    private void degrade(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=poses_worldguard_flag_query_failed reason={}", failure.toString());
        }
    }
}
