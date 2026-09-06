package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A purely in-memory {@link SpawnDirectory}: per-world spawns, the singleton main spawn, named spawns, and
 * mirror redirects held in concurrent maps with no durable backing. The durable jOOQ store is the production
 * binding; this implementation is the embedded fallback the wiring would use if persistence were unavailable
 * and the seam the command-path test drives so the resolution chain can be exercised without a database.
 *
 * <p>This implementation carries no vanilla-world fallback. {@link #defaultSpawn} returns only the operator
 * set per-world spawn, the same as {@link #operatorSpawn}, so it satisfies the no-vanilla contract the
 * resolution chain relies on; the vanilla last-resort is the {@link VanillaFallbackSpawnDirectory} decorator's.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Every map is a {@link ConcurrentHashMap} and the main spawn is an
 * {@link AtomicReference}, all safe from any thread.
 */
@NullMarked
public final class InMemorySpawnDirectory implements SpawnDirectory {

    private final ConcurrentHashMap<UUID, Position> perWorld = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Position> named = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SpawnMirror> mirrors = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable Position> main = new AtomicReference<>();

    @Override
    public Optional<Position> defaultSpawn(WorldRef world) {
        return operatorSpawn(world);
    }

    @Override
    public Optional<Position> operatorSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return Optional.ofNullable(perWorld.get(world.uid()));
    }

    @Override
    public Optional<Position> mainSpawn() {
        return Optional.ofNullable(main.get());
    }

    @Override
    public Optional<Position> namedSpawn(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(named.get(key(name)));
    }

    @Override
    public Optional<SpawnMirror> mirrorFor(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return Optional.ofNullable(mirrors.get(world.uid()));
    }

    @Override
    public void setDefaultSpawn(WorldRef world, Position position) {
        Objects.requireNonNull(world, "world");
        perWorld.put(world.uid(), Objects.requireNonNull(position, "position"));
    }

    @Override
    public void setNamedSpawn(String name, Position position) {
        Objects.requireNonNull(name, "name");
        named.put(key(name), Objects.requireNonNull(position, "position"));
    }

    @Override
    public void setMainSpawn(Position position) {
        main.set(Objects.requireNonNull(position, "position"));
    }

    @Override
    public boolean removeDefaultSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return perWorld.remove(world.uid()) != null;
    }

    @Override
    public void setMirror(SpawnMirror mirror) {
        Objects.requireNonNull(mirror, "mirror");
        mirrors.put(mirror.sourceWorld(), mirror);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
