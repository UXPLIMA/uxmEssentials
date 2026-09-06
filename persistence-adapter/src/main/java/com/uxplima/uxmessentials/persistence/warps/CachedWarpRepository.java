package com.uxplima.uxmessentials.persistence.warps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * An in-memory-authoritative decorator over a delegate {@link WarpRepository}. Warps are server-wide and the
 * full set is small and bounded, and it is read on nearly every {@code /warp}, {@code /warp list}, and
 * tab-complete, all on the command (tick/region) thread, so re-querying SQLite on a miss would block that
 * thread. The set is loaded once (the wiring's warm load on enable reads {@link #all()}) and thereafter the
 * loaded set is the authoritative answer for {@code find}/{@code exists}/{@code all}: a name not in the set is
 * absent rather than a trigger to re-query the database. After that one warm load the lookup never touches the
 * database again on a command thread.
 *
 * <p>Every warp mutation goes through this repository, so the in-memory set stays complete: a {@code save}
 * writes through to the delegate then publishes a new set with the warp added (immediately findable), and a
 * {@code delete} writes through then publishes one with it removed (immediately absent), write-through at the
 * delegate, applied here, never a reload and never a write-back cache that could lose a mutation. The set is
 * held in an {@link AtomicReference} and swapped copy-on-write (the set is tiny and edits are rare operator
 * commands), so a reader on a command thread always sees a consistent immutable snapshot while a writer
 * publishes the next one. The durable source of truth is always the delegate; the set is rebuilt from it on the
 * next read after an {@link #invalidateAll()} (a module reload, or a cross-server peer reporting a change),
 * which the next read triggers lazily.
 *
 * <p>Ratings are not part of the cached set. {@code rate}/{@code averageRating} pass straight through to the
 * delegate, as a rating change does not alter the warp's identity or location the command thread resolves.
 */
public final class CachedWarpRepository implements WarpRepository {

    private final WarpRepository delegate;
    /** The loaded name → warp set, or {@code null} until the first load warms it (lazily, or via the wiring). */
    private final AtomicReference<Map<String, Warp>> loaded = new AtomicReference<>();

    public CachedWarpRepository(WarpRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<Warp> find(WarpName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(index().get(name.value()));
    }

    @Override
    public List<Warp> all() {
        return List.copyOf(index().values());
    }

    @Override
    public boolean exists(WarpName name) {
        Objects.requireNonNull(name, "name");
        return index().containsKey(name.value());
    }

    @Override
    public void save(Warp warp) {
        Objects.requireNonNull(warp, "warp");
        delegate.save(warp);
        republish(next -> next.put(warp.name().value(), warp));
    }

    @Override
    public void delete(WarpName name) {
        Objects.requireNonNull(name, "name");
        delegate.delete(name);
        republish(next -> next.remove(name.value()));
    }

    @Override
    public void rate(WarpName name, java.util.UUID player, double rating) {
        delegate.rate(name, player, rating);
    }

    @Override
    public double averageRating(WarpName name) {
        return delegate.averageRating(name);
    }

    /** Drop the loaded set so the next read rebuilds it from the delegate; call on a module reload or peer sync. */
    public void invalidateAll() {
        loaded.set(null);
    }

    /**
     * The authoritative in-memory set, loaded from the delegate on the first access and reused thereafter. The
     * load is idempotent: a concurrent loser discards its own snapshot and reads the winner's, so the next write
     * is applied to the published set rather than lost to a racing reload.
     */
    private Map<String, Warp> index() {
        Map<String, Warp> current = loaded.get();
        if (current != null) {
            return current;
        }
        Map<String, Warp> fresh = loadAll();
        if (loaded.compareAndSet(null, fresh)) {
            return fresh;
        }
        // A racing load won; reuse its published set so a later write applies to the live snapshot, not ours.
        Map<String, Warp> winner = loaded.get();
        return winner != null ? winner : fresh;
    }

    /** Publish a fresh set with {@code edit} applied, retrying if a racing load or write swapped under us. */
    private void republish(Consumer<Map<String, Warp>> edit) {
        while (true) {
            Map<String, Warp> current = index();
            Map<String, Warp> next = new LinkedHashMap<>(current);
            edit.accept(next);
            if (loaded.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private Map<String, Warp> loadAll() {
        Map<String, Warp> byName = new LinkedHashMap<>();
        for (Warp warp : delegate.all()) {
            byName.put(warp.name().value(), warp);
        }
        return byName;
    }
}
