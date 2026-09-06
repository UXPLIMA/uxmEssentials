package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.npc.CachedNpcRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NpcChanged;
import org.junit.jupiter.api.Test;

/**
 * Pins the npc cross-server sync seam. The broadcasting decorator publishes an {@link NpcChanged} naming the
 * affected NPC after every local mutating write, a {@code save} (a create, a move, a re-skin, and every other
 * edit upsert the same row through {@code save}) and a {@code delete}. On a remote frame the listener reloads
 * exactly that NPC from the shared DB into the same cache the commands and renderer read, then re-renders it: an
 * NPC that still exists is rendered (the local edit path's {@link NpcView#render}), one that was deleted on the
 * peer is despawned ({@link NpcView#despawn}). The reload runs off the tick thread via the injected
 * {@link Scheduler}; a frame of another type leaves the cache and renderer untouched.
 */
class NpcSyncTest {

    private static final NpcName GUIDE = NpcName.of("guide");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private static Npc npc(NpcName name) {
        return Npc.create(name, Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1_000));
    }

    @Test
    void aSaveAnnouncesThatNpcToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        NpcRepository repo = NpcSync.repository(new CachedNpcRepository(new RecordingDelegate()), bus);

        repo.save(npc(GUIDE));

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(NpcChanged.class, frame -> {
            assertThat(frame.name()).isEqualTo("guide");
            assertThat(frame.originServer()).isEqualTo("survival-1");
        });
    }

    @Test
    void aDeleteAnnouncesThatNpcToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        NpcRepository repo = NpcSync.repository(new CachedNpcRepository(new RecordingDelegate()), bus);

        repo.delete(GUIDE);

        assertThat(bus.published)
                .singleElement()
                .isInstanceOfSatisfying(
                        NpcChanged.class, frame -> assertThat(frame.name()).isEqualTo("guide"));
    }

    @Test
    void aReadNeverAnnounces() {
        CapturingBus bus = new CapturingBus("survival-1");
        RecordingDelegate delegate = new RecordingDelegate();
        delegate.store(npc(GUIDE));
        NpcRepository repo = NpcSync.repository(new CachedNpcRepository(delegate), bus);

        repo.all();
        repo.find(GUIDE);
        repo.exists(GUIDE);

        assertThat(bus.published).isEmpty();
    }

    @Test
    void aRemoteCreateOrEditReloadsThenRendersThatNpc() {
        // The peer has created/edited "guide"; this backend has not seen it yet (its delegate gains the row to
        // simulate the shared DB the reload reads, the cache has not loaded it).
        RecordingDelegate delegate = new RecordingDelegate();
        CachedNpcRepository cached = new CachedNpcRepository(delegate);
        cached.all(); // warm load over the empty set, so the in-memory set is stale for "guide"
        delegate.store(npc(GUIDE)); // the peer's durable write, visible to the reload only

        RecordingRenderer renderer = new RecordingRenderer();
        InlineScheduler scheduler = new InlineScheduler();
        NpcSync.listener(cached, renderer, scheduler).onRemoteChange(new NpcChanged("peer-2", "guide"));

        // The reload ran off the tick thread, re-read the row from the shared DB, and re-rendered the NPC.
        assertThat(scheduler.asyncRuns).isEqualTo(1);
        assertThat(delegate.findReads.get()).isEqualTo(1);
        assertThat(renderer.rendered).extracting(n -> n.name().value()).containsExactly("guide");
        assertThat(renderer.despawned).isEmpty();
        assertThat(cached.find(GUIDE)).isPresent(); // the in-memory set now reflects the peer's create
    }

    @Test
    void aRemoteDeleteReloadsThenDespawnsThatNpc() {
        // The NPC exists here; the peer has deleted it from the shared DB, so the reload finds it gone.
        RecordingDelegate delegate = new RecordingDelegate();
        delegate.store(npc(GUIDE));
        CachedNpcRepository cached = new CachedNpcRepository(delegate);
        cached.all(); // warm load, so the in-memory set holds "guide"
        delegate.delete(GUIDE); // the peer's durable delete, visible to the reload

        RecordingRenderer renderer = new RecordingRenderer();
        InlineScheduler scheduler = new InlineScheduler();
        NpcSync.listener(cached, renderer, scheduler).onRemoteChange(new NpcChanged("peer-2", "guide"));

        assertThat(scheduler.asyncRuns).isEqualTo(1);
        assertThat(renderer.despawned).extracting(NpcName::value).containsExactly("guide");
        assertThat(renderer.rendered).isEmpty();
        assertThat(cached.find(GUIDE)).isEmpty(); // the in-memory set now reflects the peer's delete
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheAndRendererUntouched() {
        RecordingDelegate delegate = new RecordingDelegate();
        delegate.store(npc(GUIDE));
        CachedNpcRepository cached = new CachedNpcRepository(delegate);
        cached.all();
        RecordingRenderer renderer = new RecordingRenderer();
        InlineScheduler scheduler = new InlineScheduler();

        NpcSync.listener(cached, renderer, scheduler).onRemoteChange(new HomeChanged("peer-2", UUID.randomUUID()));

        assertThat(scheduler.asyncRuns).isZero();
        assertThat(renderer.rendered).isEmpty();
        assertThat(renderer.despawned).isEmpty();
    }

    /** A {@link BusPublisher} that records every published frame so a decorator's announcement is observable. */
    private static final class CapturingBus implements BusPublisher {

        private final String serverId;
        private final List<NetworkMessage> published = new ArrayList<>();

        CapturingBus(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return serverId;
        }
    }

    /** Records the render/despawn calls so the listener's re-render path is observable. */
    private static final class RecordingRenderer implements NpcView {

        private final List<Npc> rendered = new ArrayList<>();
        private final List<NpcName> despawned = new ArrayList<>();

        @Override
        public void render(Npc npc) {
            rendered.add(npc);
        }

        @Override
        public void despawn(NpcName name) {
            despawned.add(name);
        }
    }

    /** Runs an {@code async} task inline (counting the hops) so the off-tick reload fires in the test thread. */
    private static final class InlineScheduler implements Scheduler {

        private int asyncRuns;

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            asyncRuns++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** A delegate that counts {@code find} reads and holds the NPCs in memory, standing in for the DB. */
    private static final class RecordingDelegate implements NpcRepository {

        private final Map<String, Npc> stored = new LinkedHashMap<>();
        private final AtomicInteger findReads = new AtomicInteger();

        void store(Npc npc) {
            stored.put(npc.name().value(), Objects.requireNonNull(npc, "npc"));
        }

        @Override
        public Optional<Npc> find(NpcName name) {
            findReads.incrementAndGet();
            return Optional.ofNullable(stored.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return stored.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            store(npc);
        }

        @Override
        public void delete(NpcName name) {
            stored.remove(name.value());
        }
    }
}
