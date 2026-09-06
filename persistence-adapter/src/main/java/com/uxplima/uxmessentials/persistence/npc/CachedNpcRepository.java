package com.uxplima.uxmessentials.persistence.npc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;

/**
 * An in-memory-authoritative decorator over a delegate {@link NpcRepository}. The full NPC set is small and
 * server-wide, and is loaded once, the renderer's spawn-on-enable reads {@link #all()}, and thereafter the
 * loaded set is the authoritative answer for {@code find}/{@code exists}/{@code all}: a name not in the set is
 * absent rather than a trigger to re-query the database. The {@code /npc} use cases run those existence checks on
 * the command (tick/region) thread, so re-querying SQLite on a miss would block that thread; serving from the
 * loaded set keeps the lookup off the database entirely after the one warm load.
 *
 * <p>Every NPC mutation goes through this repository, so the in-memory set stays complete: a {@code save} writes
 * through to the delegate then publishes a new set with the NPC added (immediately findable), and a {@code
 * delete} writes through then publishes one with it removed (immediately absent), write-through at the delegate,
 * applied here, never a reload and never a write-back cache that could lose a mutation. The set is held in an
 * {@link AtomicReference} and swapped copy-on-write (the set is tiny and edits are rare operator commands), so a
 * reader on a command thread always sees a consistent immutable snapshot while a writer publishes the next one.
 * The durable source of truth is always the delegate; the set is rebuilt from it on the next load after an
 * {@link #invalidateAll()} (a module reload), which the next read triggers lazily.
 */
public final class CachedNpcRepository implements NpcRepository {

    private final NpcRepository delegate;
    /** The loaded name → npc set, or {@code null} until the first load warms it (lazily, or via spawn-on-enable). */
    private final AtomicReference<Map<String, Npc>> loaded = new AtomicReference<>();

    public CachedNpcRepository(NpcRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<Npc> find(NpcName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(index().get(name.value()));
    }

    @Override
    public List<Npc> all() {
        return List.copyOf(index().values());
    }

    @Override
    public boolean exists(NpcName name) {
        Objects.requireNonNull(name, "name");
        return index().containsKey(name.value());
    }

    @Override
    public void save(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        delegate.save(npc);
        republish(next -> next.put(npc.name().value(), npc));
    }

    @Override
    public void delete(NpcName name) {
        Objects.requireNonNull(name, "name");
        delegate.delete(name);
        republish(next -> next.remove(name.value()));
    }

    /** Drop the loaded set so the next read rebuilds it from the delegate; call on a module reload. */
    public void invalidateAll() {
        loaded.set(null);
    }

    /**
     * Re-read one NPC from the durable delegate and update the in-memory set to match, returning its fresh state
     *: present when it now exists (created or edited on a peer), empty when it was deleted there. Unlike
     * {@link #save}/{@link #delete}, the change did not pass through this cache (a peer wrote the shared row), so
     * the in-memory authoritative set is stale for that name; this hops it back in step against the database. The
     * cross-server listener calls this off the tick thread (the delegate read is a synchronous SQLite query) and
     * re-renders or despawns the live fake player from the returned state.
     */
    public Optional<Npc> reload(NpcName name) {
        Objects.requireNonNull(name, "name");
        Optional<Npc> fresh = delegate.find(name);
        if (fresh.isPresent()) {
            Npc npc = fresh.get();
            republish(next -> next.put(name.value(), npc));
        } else {
            republish(next -> next.remove(name.value()));
        }
        return fresh;
    }

    /**
     * The authoritative in-memory set, loaded from the delegate on the first access and reused thereafter. The
     * load is idempotent: a concurrent loser discards its own snapshot and reads the winner's, so the next write
     * is applied to the published set rather than lost to a racing reload.
     */
    private Map<String, Npc> index() {
        Map<String, Npc> current = loaded.get();
        if (current != null) {
            return current;
        }
        Map<String, Npc> fresh = loadAll();
        if (loaded.compareAndSet(null, fresh)) {
            return fresh;
        }
        // A racing load won; reuse its published set so a later write applies to the live snapshot, not ours.
        Map<String, Npc> winner = loaded.get();
        return winner != null ? winner : fresh;
    }

    /** Publish a fresh set with {@code edit} applied, retrying if a racing load or write swapped under us. */
    private void republish(Consumer<Map<String, Npc>> edit) {
        while (true) {
            Map<String, Npc> current = index();
            Map<String, Npc> next = new LinkedHashMap<>(current);
            edit.accept(next);
            if (loaded.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private Map<String, Npc> loadAll() {
        Map<String, Npc> byName = new LinkedHashMap<>();
        for (Npc npc : delegate.all()) {
            byName.put(npc.name().value(), npc);
        }
        return byName;
    }
}
