package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Anti-corruption layer over Bukkit's world APIs. World-handle operations ({@link #create},
 * {@link #load}, {@link #unload}) must be invoked on the global region thread (the caller's
 * responsibility, via the {@code Scheduler} port). Filesystem operations ({@link #deleteFiles},
 * {@link #scanFolder}) read/delete under the server's world container.
 */
@NullMarked
public final class BukkitWorldEngine implements WorldEngine {

    private final Server server;
    private final Logger log;
    private final WorldGeneratorResolver generators;

    public BukkitWorldEngine(Server server, Logger log, WorldGeneratorResolver generators) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
        this.generators = Objects.requireNonNull(generators, "generators");
    }

    @Override
    public Result<Unit, WorldError> create(ManagedWorld world) {
        return loadOrCreate(world.name(), Optional.of(world.spec()));
    }

    @Override
    public Result<Unit, WorldError> load(ManagedWorld world) {
        return loadOrCreate(world.name(), Optional.of(world.spec()));
    }

    private Result<Unit, WorldError> loadOrCreate(WorldName name, Optional<WorldSpec> spec) {
        try {
            WorldCreator creator = new WorldCreator(name.value());
            spec.ifPresent(s -> applySpec(creator, s));
            World created = creator.createWorld();
            if (created == null) {
                return Result.err(WorldError.IO_ERROR);
            }
            return Result.ok();
        } catch (RuntimeException e) {
            log.error("Failed to create/load world " + name.value(), e);
            return Result.err(WorldError.IO_ERROR);
        }
    }

    private void applySpec(WorldCreator creator, WorldSpec spec) {
        creator.environment(toBukkitEnvironment(spec.environment()));
        creator.type(toBukkitType(spec.worldType()));
        spec.seed().ifPresent(creator::seed);
        spec.generator().ifPresent(g -> applyGenerator(creator, g));
        creator.generateStructures(spec.generateStructures());
    }

    /**
     * Routes a generator ref onto the {@link WorldCreator}: our own {@code uxmEssentials:void|flat} refs
     * take the object overload (the resolver's {@code ChunkGenerator}), so they behave identically across
     * every path that supplies the spec: an internal {@link #create}, a {@link #load} of a registered
     * world (the spec is re-applied), and a world configured in {@code bukkit.yml} and served through the
     * plugin's {@code getDefaultWorldGenerator} hook. Any other token, an unknown built-in id or an external
     * {@code plugin[:args]} ref: takes Bukkit's String overload unchanged.
     */
    void applyGenerator(WorldCreator creator, GeneratorRef g) {
        BuiltInGenerators.idOf(g.value())
                .ifPresentOrElse(
                        id -> generators
                                .resolve(id)
                                .ifPresentOrElse(creator::generator, () -> creator.generator(g.value())),
                        () -> creator.generator(g.value()));
    }

    @Override
    public Result<Unit, WorldError> unload(WorldName name, boolean save) {
        World world = server.getWorld(name.value());
        if (world == null) {
            return Result.err(WorldError.NOT_LOADED);
        }
        return server.unloadWorld(world, save) ? Result.ok() : Result.err(WorldError.IO_ERROR);
    }

    @Override
    public Result<Unit, WorldError> deleteFiles(WorldName name) {
        if (server.getWorld(name.value()) != null) {
            return Result.err(WorldError.ALREADY_LOADED); // must be unloaded first
        }
        Path folder = worldFolder(name);
        if (!Files.isDirectory(folder)) {
            return Result.err(WorldError.FOLDER_MISSING);
        }
        try (var paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
            return Files.exists(folder) ? Result.err(WorldError.IO_ERROR) : Result.ok();
        } catch (IOException e) {
            log.error("Failed to delete world folder " + name.value(), e);
            return Result.err(WorldError.IO_ERROR);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete " + path, e);
        }
    }

    @Override
    public Optional<DetectedWorld> scanFolder(WorldName name) {
        World live = server.getWorld(name.value());
        if (live != null) {
            return Optional.of(
                    new DetectedWorld(fromBukkitEnvironment(live.getEnvironment()), Optional.of(live.getSeed())));
        }
        Path levelDat = worldFolder(name).resolve("level.dat");
        if (!Files.isRegularFile(levelDat)) {
            return Optional.empty();
        }
        // Precise NBT environment/seed detection for unloaded folders needs NMS; default NORMAL/no-seed.
        return Optional.of(new DetectedWorld(WorldEnvironment.NORMAL, Optional.empty()));
    }

    @Override
    public boolean exists(WorldName name) {
        return server.getWorld(name.value()) != null || Files.isDirectory(worldFolder(name));
    }

    @Override
    public boolean isLoaded(WorldName name) {
        return server.getWorld(name.value()) != null;
    }

    @Override
    public Set<WorldName> loadedWorldNames() {
        Set<WorldName> names = new HashSet<>();
        for (World world : server.getWorlds()) {
            names.add(WorldName.of(world.getName()));
        }
        return names;
    }

    /**
     * Every world folder under the server container (loaded or not), identified by a {@code level.dat}.
     * Used by the enable-time reconcile in sub-project A; not part of the {@link WorldEngine} port.
     */
    public Set<WorldName> onDiskWorldNames() {
        Set<WorldName> names = new HashSet<>();
        Path container = server.getWorldContainer().toPath();
        try (var entries = Files.list(container)) {
            entries.filter(BukkitWorldEngine::isWorldFolder).forEach(p -> addIfValid(names, p));
            return names;
        } catch (IOException e) {
            log.error("Failed to scan world container " + container, e);
            return names;
        }
    }

    private static boolean isWorldFolder(Path candidate) {
        return Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("level.dat"));
    }

    private void addIfValid(Set<WorldName> names, Path folder) {
        Path fileName = folder.getFileName();
        if (fileName == null) {
            return;
        }
        try {
            names.add(WorldName.of(fileName.toString()));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping world folder with an unusable name: {}", fileName);
        }
    }

    @Override
    public Optional<WorldName> defaultWorldName() {
        List<World> worlds = server.getWorlds();
        return worlds.isEmpty()
                ? Optional.empty()
                : Optional.of(WorldName.of(worlds.get(0).getName()));
    }

    @Override
    public Optional<UUID> uidOf(WorldName name) {
        World world = server.getWorld(name.value());
        return Optional.ofNullable(world).map(World::getUID);
    }

    @Override
    public int playerCount(WorldName name) {
        World world = server.getWorld(name.value());
        return world == null ? 0 : world.getPlayers().size();
    }

    @Override
    public Optional<Position> spawnPoint(WorldName name) {
        World world = server.getWorld(name.value());
        return Optional.ofNullable(world).map(w -> BukkitRefs.toPosition(w.getSpawnLocation()));
    }

    private Path worldFolder(WorldName name) {
        return server.getWorldContainer().toPath().resolve(name.value());
    }

    private static World.Environment toBukkitEnvironment(WorldEnvironment environment) {
        return switch (environment) {
            case NORMAL -> World.Environment.NORMAL;
            case NETHER -> World.Environment.NETHER;
            case THE_END -> World.Environment.THE_END;
        };
    }

    private static WorldEnvironment fromBukkitEnvironment(World.@Nullable Environment environment) {
        if (environment == null) {
            return WorldEnvironment.NORMAL;
        }
        return switch (environment) {
            case NETHER -> WorldEnvironment.NETHER;
            case THE_END -> WorldEnvironment.THE_END;
            default -> WorldEnvironment.NORMAL;
        };
    }

    private static WorldType toBukkitType(WorldGenType type) {
        return switch (type) {
            case NORMAL -> WorldType.NORMAL;
            case FLAT -> WorldType.FLAT;
            case AMPLIFIED -> WorldType.AMPLIFIED;
            case LARGE_BIOMES -> WorldType.LARGE_BIOMES;
        };
    }
}
