package com.uxplima.uxmessentials.shared.adapter.outbound.worldguard;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.WorldGuardFlags;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The WorldGuard side of the {@link WorldGuardFlags} port, reached purely by reflection behind a plugin-present guard
 * the same pattern the poses region gate and the claim providers use. It reports whether a covering region has set a
 * named custom flag (e.g. {@code set-pwarp}, registered at load by {@link WorldGuardSetPwarpFlagRegistrar}) to DENY at
 * a location.
 *
 * <p>Named the SDK only by string class-name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or method signature carries a {@code com.sk89q}
 * type: on a server without WorldGuard the present-guard short-circuits before any {@code Class.forName}, so none of
 * its classes load. The gate is fail-open. An absent plugin, an unknown world, an unregistered flag, or any
 * reflective, linkage, or runtime failure (a version bump moving the query chain) all report "not denied" and are
 * logged at most once, because wrongly refusing a legitimate warp is worse than missing a rare veto.
 */
@NullMarked
public final class BukkitWorldGuardFlags implements WorldGuardFlags {

    private final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public BukkitWorldGuardFlags(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean supported() {
        return WorldGuardReflection.isEnabled(server);
    }

    @Override
    public boolean deniesFlag(String flagName, Position where) {
        Objects.requireNonNull(flagName, "flagName");
        Objects.requireNonNull(where, "where");
        if (!supported()) {
            return false;
        }
        World world = server.getWorld(where.world().uid());
        if (world == null) {
            return false;
        }
        try {
            return WorldGuardReflection.queryDeny(new Location(world, where.x(), where.y(), where.z()), flagName);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return false;
        }
    }

    /** The resolved {@code StateFlag.State} is a DENY only when its enum constant is named {@code DENY}. */
    static boolean isDeny(@Nullable Object state) {
        return WorldGuardReflection.isDeny(state);
    }

    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=worldguard_flag_query_failed reason={}", failure.toString());
        }
    }
}
