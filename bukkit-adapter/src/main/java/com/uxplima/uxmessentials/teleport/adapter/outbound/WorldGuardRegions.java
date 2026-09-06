package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardReflection;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.port.ProtectedLand;
import org.jspecify.annotations.NullMarked;

/**
 * The WorldGuard side of the random-teleport protection check, reached purely by reflection behind a plugin-present
 * guard: the same pattern the poses region gate and the menu vocabulary's {@code worldguard-region} condition use.
 * A position is protected when <em>any</em> WorldGuard region covers it: the pre-warmed pool is shared and has no
 * owning player, so the question is spatial ("is this spot inside a region") rather than "may player X enter it".
 *
 * <p>The reflective walk itself belongs to {@link WorldGuardReflection}, the one entry point every WorldGuard
 * adapter shares, so a WorldGuard release that moves a step in the query chain is fixed in one place rather than
 * four. No field or method signature here carries a {@code com.sk89q} type: on a server without WorldGuard the
 * present-guard short-circuits before any {@code Class.forName}, so none of its classes load. The check is fail-open: an absent plugin, an unknown
 * world, or any reflective failure (a version bump moving the query chain) all report "not protected" and are logged
 * at most once, because wrongly refusing wilderness would starve the pool, and the global {@code __global__} region
 * which has no physical bounds: is not returned by the spatial query, so pure wilderness reports empty.
 *
 * <p>Threading: called from the safe-search snapshot callback, which already runs on the candidate chunk's owning
 * region thread, so the region query (an in-memory spatial lookup, not a chunk load) needs no extra scheduler hop.
 */
@NullMarked
public final class WorldGuardRegions implements ProtectedLand {

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public WorldGuardRegions(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean isProtected(Position where) {
        Objects.requireNonNull(where, "where");
        if (!WorldGuardReflection.isEnabled(server)) {
            return false;
        }
        World world = server.getWorld(where.world().uid());
        if (world == null) {
            return false;
        }
        try {
            return coversAnyRegion(new Location(world, where.x(), where.y(), where.z()));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return false;
        }
    }

    /** True when the region container reports at least one region physically covering {@code location}. */
    private boolean coversAnyRegion(Location location) throws ReflectiveOperationException {
        Object applicable = WorldGuardReflection.applicableRegions(
                WorldGuardReflection.createQuery(), WorldGuardReflection.adapt(location));
        if (applicable == null) {
            return false;
        }
        Object regions = applicable.getClass().getMethod("getRegions").invoke(applicable);
        return regions instanceof Collection<?> set && !set.isEmpty();
    }

    private void degrade(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=rtp_worldguard_region_query_failed reason={}", failure.toString());
        }
    }
}
