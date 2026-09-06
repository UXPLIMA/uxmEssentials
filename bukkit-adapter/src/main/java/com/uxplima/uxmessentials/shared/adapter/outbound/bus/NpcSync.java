package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.npc.CachedNpcRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.NpcChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The npc context's cross-server sync seam. It is the same write-decorator-plus-listener shape as
 * {@link HologramSync}: an NPC is its own per-server fake-player entity on every backend, so a peer must do
 * more than drop a cache entry on a remote change. It must reload the named NPC from the shared DB and
 * re-render the live fake player so the world matches.
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedNpcRepository, BusPublisher)} wraps the cached repository so
 *       every local mutating write announces it to peers. NPCs are keyed by name and every edit, a create, a
 *       move, a re-skin, an equipment/glow/pose/scale/look/type/action change, an owner rebind, upserts the
 *       same row through {@code save}, so that one case (plus {@code delete}) covers every mutation; the frame
 *       names the affected NPC and is published after the durable write commits.
 *   <li><b>Inbound</b>: {@link #listener(CachedNpcRepository, NpcView, Scheduler)} returns a
 *       {@link RemoteSyncListener} that, on a remote {@link NpcChanged}, reloads exactly that NPC from the
 *       shared DB into the in-memory set and re-renders it: an NPC that now exists (created or edited on the
 *       peer) is rendered through {@link NpcView#render}, the same force-render the local edit path runs, and
 *       one that no longer exists (deleted on the peer) is despawned through {@link NpcView#despawn}, exactly as
 *       the local delete path does.
 * </ul>
 *
 * <p>The reload is a synchronous SQLite read, so the listener hops onto the injected {@link Scheduler}'s async
 * thread first. It then calls {@code render}/{@code despawn}, which the renderer routes onto the global region
 * thread internally (Folia). The same scheduling the local {@code /npc} edit path relies on, so the remote
 * re-render and the local one converge to one in-world result. The decorator wraps the <em>same</em> cache the
 * {@code /npc} commands and the renderer read, so the loop closes: a write here emits a frame, the peer reloads
 * and re-renders that NPC there.
 */
@NullMarked
public final class NpcSync {

    private NpcSync() {}

    /** An {@link NpcRepository} that broadcasts an {@link NpcChanged} after every local mutating write. */
    public static NpcRepository repository(CachedNpcRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /**
     * A listener that, on a remote {@link NpcChanged}, reloads the named NPC from the shared DB and re-renders
     * it (or despawns it when it was deleted), off the tick thread via {@code scheduler}.
     */
    public static RemoteSyncListener listener(CachedNpcRepository cache, NpcView renderer, Scheduler scheduler) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(scheduler, "scheduler");
        return message -> {
            if (!(message instanceof NpcChanged changed)) {
                return;
            }
            NpcName name = NpcName.of(changed.name());
            // Reload off the tick thread, the cache reload is a synchronous SQLite read, then re-render through
            // the renderer, which hops onto the global region thread itself (the local edit path's route).
            scheduler.async(() -> reRender(cache, renderer, name));
        };
    }

    /** Reload the named NPC from the shared DB and render it if present, despawn it if it was deleted. */
    private static void reRender(CachedNpcRepository cache, NpcView renderer, NpcName name) {
        Optional<Npc> fresh = cache.reload(name);
        if (fresh.isPresent()) {
            renderer.render(fresh.get());
        } else {
            renderer.despawn(name);
        }
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements NpcRepository {

        private final NpcRepository delegate;
        private final BusPublisher bus;

        Broadcasting(NpcRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<Npc> find(NpcName name) {
            return delegate.find(name);
        }

        @Override
        public List<Npc> all() {
            return delegate.all();
        }

        @Override
        public boolean exists(NpcName name) {
            return delegate.exists(name);
        }

        @Override
        public void save(Npc npc) {
            delegate.save(npc);
            announce(npc.name());
        }

        @Override
        public void delete(NpcName name) {
            delegate.delete(name);
            announce(name);
        }

        private void announce(NpcName name) {
            bus.publish(new NpcChanged(bus.serverId(), name.value()));
        }
    }
}
