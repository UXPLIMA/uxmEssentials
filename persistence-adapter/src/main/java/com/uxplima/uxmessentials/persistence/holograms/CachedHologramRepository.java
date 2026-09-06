package com.uxplima.uxmessentials.persistence.holograms;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;

/**
 * An in-memory-authoritative decorator over a delegate {@link HologramRepository}. Holograms are server-wide
 * and the full set is small; it is loaded once, the renderer's spawn-on-enable reads {@link #all()}, and
 * thereafter the loaded set is the authoritative answer for {@code find}/{@code exists}/{@code all}: a name not
 * in the set is absent rather than a trigger to re-query the database. This matters most for the refresh tick,
 * which reads {@code all()} once a second on the global tick thread; re-querying SQLite there on a miss (after a
 * TTL expiry or an edit's invalidation) would block that thread. Serving from the loaded set keeps both the
 * refresh tick and the {@code /hologram} command checks off the database after the one warm load.
 *
 * <p>Every hologram mutation goes through this repository, so the in-memory set stays complete: a {@code save}
 * writes through to the delegate then publishes a new set with the hologram added, and a {@code delete} writes
 * through then publishes one with it removed. Write-through at the delegate, applied here, never a reload and
 * never a write-back cache that could lose a mutation. The set is held in an {@link AtomicReference} and swapped
 * copy-on-write (the set is tiny and edits are rare operator commands), so a reader on the tick thread always
 * sees a consistent immutable snapshot while a writer publishes the next one. The durable source of truth is
 * always the delegate; the set is rebuilt on the next load after an {@link #invalidateAll()} (a module reload).
 *
 * <p>The MANUAL-visibility viewer set is cached separately, keyed by hologram name in a Caffeine read-cache: it
 * is read on every render (so a refreshing MANUAL hologram queries it each refresh tick) and on every join, yet
 * mutated only on {@code /hologram show|hide}, exactly the small, hot-read, rare-write shape a read cache is
 * for. A {@code showTo}/{@code hideFrom}/{@code delete} writes through the delegate then invalidates that name,
 * so the next read reloads the durable set; the immediate show/hide path never serves a stale set because the
 * mutation evicts before it returns. Caching it keeps the render path off a synchronous SQLite read on the tick
 * thread.
 */
public final class CachedHologramRepository implements HologramRepository {

    private static final long MAX_VIEWER_SETS = 512L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final HologramRepository delegate;
    /** The loaded name → hologram set, or {@code null} until the first load warms it (lazily or via enable). */
    private final AtomicReference<Map<String, Hologram>> loaded = new AtomicReference<>();

    private final ReadThroughCache<String, Set<UUID>> viewers;
    private final ReadThroughCache<String, Set<UUID>> blacklists;

    public CachedHologramRepository(HologramRepository delegate) {
        this(delegate, DEFAULT_TTL);
    }

    public CachedHologramRepository(HologramRepository delegate, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.viewers =
                ReadThroughCache.create(name -> delegate.manualViewers(HologramName.of(name)), MAX_VIEWER_SETS, ttl);
        this.blacklists =
                ReadThroughCache.create(name -> delegate.blacklisted(HologramName.of(name)), MAX_VIEWER_SETS, ttl);
    }

    @Override
    public Optional<Hologram> find(HologramName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(index().get(name.value()));
    }

    @Override
    public List<Hologram> all() {
        return List.copyOf(index().values());
    }

    @Override
    public boolean exists(HologramName name) {
        Objects.requireNonNull(name, "name");
        return index().containsKey(name.value());
    }

    @Override
    public void save(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        delegate.save(hologram);
        republish(next -> next.put(hologram.name().value(), hologram));
    }

    @Override
    public void delete(HologramName name) {
        Objects.requireNonNull(name, "name");
        delegate.delete(name);
        republish(next -> next.remove(name.value()));
        viewers.invalidate(name.value());
        blacklists.invalidate(name.value());
    }

    @Override
    public Set<UUID> manualViewers(HologramName name) {
        Objects.requireNonNull(name, "name");
        // Served from memory after the first load: read on every render (each refresh tick of a refreshing
        // MANUAL hologram) and on every join, so a synchronous SQLite read here would land on the tick thread.
        // A show/hide/delete invalidates this name, so the next read reloads the durable set.
        return viewers.get(name.value());
    }

    @Override
    public void showTo(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        delegate.showTo(name, viewer);
        viewers.invalidate(name.value());
    }

    @Override
    public void hideFrom(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        delegate.hideFrom(name, viewer);
        viewers.invalidate(name.value());
    }

    @Override
    public Set<UUID> blacklisted(HologramName name) {
        Objects.requireNonNull(name, "name");
        // Served from memory like the manual viewer set, read on each spawn and join; an add/remove/delete
        // invalidates this name so the next read reloads the durable set.
        return blacklists.get(name.value());
    }

    @Override
    public void addToBlacklist(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        delegate.addToBlacklist(name, viewer);
        blacklists.invalidate(name.value());
    }

    @Override
    public void removeFromBlacklist(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        delegate.removeFromBlacklist(name, viewer);
        blacklists.invalidate(name.value());
    }

    /** Drop the loaded set and the viewer/blacklist caches; call on a module reload. */
    public void invalidateAll() {
        loaded.set(null);
        viewers.invalidateAll();
        blacklists.invalidateAll();
    }

    /**
     * Re-read one hologram from the durable delegate and update the in-memory set to match, returning its fresh
     * state: present when it now exists (created or edited on a peer), empty when it was deleted there. Unlike
     * {@link #save}/{@link #delete}, the change did not pass through this cache (a peer wrote the shared row), so
     * the in-memory authoritative set is stale for that name; this hops it back in step against the database. The
     * viewer and blacklist caches for the name are invalidated too, so a peer's show/hide/blacklist edit is
     * reflected on the next render. The cross-server listener calls this off the tick thread (the delegate read is
     * a synchronous SQLite query) and re-renders or despawns the live entity from the returned state.
     */
    public Optional<Hologram> reload(HologramName name) {
        Objects.requireNonNull(name, "name");
        Optional<Hologram> fresh = delegate.find(name);
        if (fresh.isPresent()) {
            Hologram hologram = fresh.get();
            republish(next -> next.put(name.value(), hologram));
        } else {
            republish(next -> next.remove(name.value()));
        }
        viewers.invalidate(name.value());
        blacklists.invalidate(name.value());
        return fresh;
    }

    /**
     * The authoritative in-memory set, loaded from the delegate on the first access and reused thereafter. The
     * load is idempotent: a concurrent loser reuses the winner's published set so the next write applies to the
     * live snapshot rather than being lost to a racing reload.
     */
    private Map<String, Hologram> index() {
        Map<String, Hologram> current = loaded.get();
        if (current != null) {
            return current;
        }
        Map<String, Hologram> fresh = loadAll();
        if (loaded.compareAndSet(null, fresh)) {
            return fresh;
        }
        Map<String, Hologram> winner = loaded.get();
        return winner != null ? winner : fresh;
    }

    /** Publish a fresh set with {@code edit} applied, retrying if a racing load or write swapped under us. */
    private void republish(Consumer<Map<String, Hologram>> edit) {
        while (true) {
            Map<String, Hologram> current = index();
            Map<String, Hologram> next = new LinkedHashMap<>(current);
            edit.accept(next);
            if (loaded.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private Map<String, Hologram> loadAll() {
        Map<String, Hologram> byName = new LinkedHashMap<>();
        for (Hologram hologram : delegate.all()) {
            byName.put(hologram.name().value(), hologram);
        }
        return byName;
    }
}
