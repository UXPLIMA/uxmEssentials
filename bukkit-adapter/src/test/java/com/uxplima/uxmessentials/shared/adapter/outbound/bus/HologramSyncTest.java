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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.holograms.CachedHologramRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.HologramChanged;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * Pins the holograms cross-server sync seam. The broadcasting decorator publishes a {@link HologramChanged}
 * naming the affected hologram after every local write that changes one, a {@code save} (create, move, line
 * edit, appearance/visibility/model/page/leaderboard/action change all upsert the same row), a {@code delete},
 * a manual {@code showTo}/{@code hideFrom}, and a blacklist add/remove. On a remote frame the listener reloads
 * exactly that hologram from the shared DB into the same cache the commands and renderer read, then re-renders
 * it: a hologram that still exists is rendered (the local edit path's {@link HologramView#render}), one that was
 * deleted on the peer is despawned ({@link HologramView#despawn}). The reload runs off the tick thread via the
 * injected {@link Scheduler}; a frame of another type leaves the cache and renderer untouched.
 */
class HologramSyncTest {

    private static final HologramName SPAWN = HologramName.of("spawn");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UUID VIEWER = UUID.randomUUID();

    private static Hologram hologram(HologramName name) {
        return Hologram.create(
                name, Position.of(WORLD, 1, 64, 1), List.of(new HologramLine("line")), Instant.ofEpochMilli(1_000));
    }

    @Test
    void aSaveAnnouncesThatHologramToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        HologramRepository repo = HologramSync.repository(new CachedHologramRepository(new RecordingDelegate()), bus);

        repo.save(hologram(SPAWN));

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(HologramChanged.class, frame -> {
            assertThat(frame.name()).isEqualTo("spawn");
            assertThat(frame.originServer()).isEqualTo("survival-1");
        });
    }

    @Test
    void aDeleteAnnouncesThatHologramToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        HologramRepository repo = HologramSync.repository(new CachedHologramRepository(new RecordingDelegate()), bus);

        repo.delete(SPAWN);

        assertThat(bus.published)
                .singleElement()
                .isInstanceOfSatisfying(
                        HologramChanged.class, frame -> assertThat(frame.name()).isEqualTo("spawn"));
    }

    @Test
    void aManualShowAndHideEachAnnounceThatHologram() {
        CapturingBus bus = new CapturingBus("survival-1");
        HologramRepository repo = HologramSync.repository(new CachedHologramRepository(new RecordingDelegate()), bus);

        repo.showTo(SPAWN, VIEWER);
        repo.hideFrom(SPAWN, VIEWER);

        assertThat(bus.published)
                .hasSize(2)
                .allSatisfy(
                        frame -> assertThat(((HologramChanged) frame).name()).isEqualTo("spawn"));
    }

    @Test
    void aBlacklistAddAndRemoveEachAnnounceThatHologram() {
        CapturingBus bus = new CapturingBus("survival-1");
        HologramRepository repo = HologramSync.repository(new CachedHologramRepository(new RecordingDelegate()), bus);

        repo.addToBlacklist(SPAWN, VIEWER);
        repo.removeFromBlacklist(SPAWN, VIEWER);

        assertThat(bus.published)
                .hasSize(2)
                .allSatisfy(
                        frame -> assertThat(((HologramChanged) frame).name()).isEqualTo("spawn"));
    }

    @Test
    void aReadNeverAnnounces() {
        CapturingBus bus = new CapturingBus("survival-1");
        RecordingDelegate delegate = new RecordingDelegate();
        delegate.store(hologram(SPAWN));
        HologramRepository repo = HologramSync.repository(new CachedHologramRepository(delegate), bus);

        repo.all();
        repo.find(SPAWN);
        repo.exists(SPAWN);
        repo.manualViewers(SPAWN);
        repo.blacklisted(SPAWN);

        assertThat(bus.published).isEmpty();
    }

    @Test
    void aRemoteCreateOrEditReloadsThenRendersThatHologram() {
        // The peer has created/edited "spawn"; this backend has not seen it yet (its delegate gains the row to
        // simulate the shared DB the reload reads, the cache has not loaded it).
        RecordingDelegate delegate = new RecordingDelegate();
        CachedHologramRepository cached = new CachedHologramRepository(delegate);
        cached.all(); // warm load over the empty set, so the in-memory set is stale for "spawn"
        delegate.store(hologram(SPAWN)); // the peer's durable write, visible to the reload only

        RecordingRenderer renderer = new RecordingRenderer();
        InlineScheduler scheduler = new InlineScheduler();
        HologramSync.listener(cached, renderer, scheduler).onRemoteChange(new HologramChanged("peer-2", "spawn"));

        // The reload ran off the tick thread, re-read the row from the shared DB, and re-rendered the hologram.
        assertThat(scheduler.asyncRuns).isEqualTo(1);
        assertThat(delegate.findReads.get()).isEqualTo(1);
        assertThat(renderer.rendered).extracting(h -> h.name().value()).containsExactly("spawn");
        assertThat(renderer.despawned).isEmpty();
        assertThat(cached.find(SPAWN)).isPresent(); // the in-memory set now reflects the peer's create
    }

    @Test
    void aRemoteDeleteReloadsThenDespawnsThatHologram() {
        // The hologram exists here; the peer has deleted it from the shared DB, so the reload finds it gone.
        RecordingDelegate delegate = new RecordingDelegate();
        delegate.store(hologram(SPAWN));
        CachedHologramRepository cached = new CachedHologramRepository(delegate);
        cached.all(); // warm load, so the in-memory set holds "spawn"
        delegate.delete(SPAWN); // the peer's durable delete, visible to the reload

        RecordingRenderer renderer = new RecordingRenderer();
        InlineScheduler scheduler = new InlineScheduler();
        HologramSync.listener(cached, renderer, scheduler).onRemoteChange(new HologramChanged("peer-2", "spawn"));

        assertThat(scheduler.asyncRuns).isEqualTo(1);
        assertThat(renderer.despawned).extracting(HologramName::value).containsExactly("spawn");
        assertThat(renderer.rendered).isEmpty();
        assertThat(cached.find(SPAWN)).isEmpty(); // the in-memory set now reflects the peer's delete
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheAndRendererUntouched() {
        RecordingDelegate delegate = new RecordingDelegate();
        delegate.store(hologram(SPAWN));
        CachedHologramRepository cached = new CachedHologramRepository(delegate);
        cached.all();
        RecordingRenderer renderer = new RecordingRenderer();
        InlineScheduler scheduler = new InlineScheduler();

        HologramSync.listener(cached, renderer, scheduler).onRemoteChange(new HomeChanged("peer-2", VIEWER));

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
    private static final class RecordingRenderer implements HologramView {

        private final List<Hologram> rendered = new ArrayList<>();
        private final List<HologramName> despawned = new ArrayList<>();

        @Override
        public void render(Hologram hologram) {
            rendered.add(hologram);
        }

        @Override
        public void despawn(HologramName name) {
            despawned.add(name);
        }

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
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

    /** A delegate that counts {@code find} reads and holds the holograms in memory, standing in for the DB. */
    private static final class RecordingDelegate implements HologramRepository {

        private final Map<String, Hologram> stored = new LinkedHashMap<>();
        private final AtomicInteger findReads = new AtomicInteger();

        void store(Hologram hologram) {
            stored.put(hologram.name().value(), Objects.requireNonNull(hologram, "hologram"));
        }

        @Override
        public Optional<Hologram> find(HologramName name) {
            findReads.incrementAndGet();
            return Optional.ofNullable(stored.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return stored.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            store(hologram);
        }

        @Override
        public void delete(HologramName name) {
            stored.remove(name.value());
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return Set.of();
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {}

        @Override
        public void hideFrom(HologramName name, UUID viewer) {}

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return Set.of();
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {}

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {}
    }
}
