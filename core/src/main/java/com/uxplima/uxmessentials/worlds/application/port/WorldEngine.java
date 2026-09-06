package com.uxplima.uxmessentials.worlds.application.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Anti-corruption layer over Bukkit's world APIs ({@code WorldCreator}, {@code Server#getWorld},
 * {@code unloadWorld}, the world folder on disk). The only place in the worlds context that touches
 * {@code org.bukkit.World}. World handle operations run on the global thread; file operations run
 * off-tick: both are the adapter's responsibility, invoked through the {@code Scheduler} port.
 */
public interface WorldEngine {

    /** Create and load the world described by the aggregate. */
    Result<Unit, WorldError> create(ManagedWorld world);

    /**
     * Load an existing (registered or on-disk) world, re-applying its {@link ManagedWorld#spec()}
     * environment, type, seed, and generator, to the world handle. Re-applying the spec is what
     * keeps a built-in {@code uxmEssentials:void|flat} world's object generator in force across
     * restarts: Bukkit cannot persist an object generator, so without re-supplying it newly generated
     * chunks would fall back to vanilla terrain.
     */
    Result<Unit, WorldError> load(ManagedWorld world);

    /** Unload a loaded world, optionally saving it first. */
    Result<Unit, WorldError> unload(WorldName name, boolean save);

    /** Permanently delete the world's folder from disk (must be unloaded and non-default). */
    Result<Unit, WorldError> deleteFiles(WorldName name);

    /** Read {@code level.dat} for an on-disk, possibly-unloaded world. */
    Optional<DetectedWorld> scanFolder(WorldName name);

    boolean exists(WorldName name);

    boolean isLoaded(WorldName name);

    Set<WorldName> loadedWorldNames();

    /**
     * The server's default (primary) world, or empty when the server has no worlds loaded, which a
     * caller comparing against a name must treat as "not the default" rather than dereferencing.
     */
    Optional<WorldName> defaultWorldName();

    Optional<UUID> uidOf(WorldName name);

    int playerCount(WorldName name);

    /** The world's current spawn point, or empty when the world is not loaded/known. */
    Optional<Position> spawnPoint(WorldName name);

    /** What a folder scan can determine about a world without loading it. */
    record DetectedWorld(WorldEnvironment environment, Optional<Long> seed) {}
}
