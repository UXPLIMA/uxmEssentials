package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.persistence.playerwarps.CachedPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PlayerWarpChanged;
import org.junit.jupiter.api.Test;

/**
 * Pins the player-warps cross-server sync seam: the broadcasting decorator publishes a {@link PlayerWarpChanged}
 * carrying the affected owner after every local write that changes that owner's set, a {@code save} (a set, a
 * move, or a visibility flip all upsert the same row) and a {@code deleteById}, and the listener drops exactly that
 * owner from the {@link CachedPlayerWarpRepository} on a remote frame so the next {@code /pwarp} reloads the
 * authoritative row. A frame of another type leaves the cache untouched. This mirrors {@code WalletSyncTest}.
 */
class PlayerWarpSyncTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    @Test
    void aSaveAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        PlayerWarpRepository repo =
                PlayerWarpSync.repository(new CachedPlayerWarpRepository(new RecordingDelegate()), bus);

        repo.save(PlayerWarp.create(OWNER, "owner", PlayerWarpName.of("base"), position(), Instant.EPOCH));

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(PlayerWarpChanged.class, frame -> {
            assertThat(frame.owner()).isEqualTo(OWNER.uuid());
            assertThat(frame.originServer()).isEqualTo("survival-1");
        });
    }

    @Test
    void aMoveAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        PlayerWarpRepository repo =
                PlayerWarpSync.repository(new CachedPlayerWarpRepository(new RecordingDelegate()), bus);
        PlayerWarp warp = PlayerWarp.create(OWNER, "owner", PlayerWarpName.of("base"), position(), Instant.EPOCH);

        // A move/relocate upserts the same row through save, so it announces like any other set.
        repo.save(warp.movedTo(Position.of(WORLD, 9, 9, 9), Instant.EPOCH));

        assertThat(bus.published).singleElement().isInstanceOf(PlayerWarpChanged.class);
        assertThat(((PlayerWarpChanged) bus.published.get(0)).owner()).isEqualTo(OWNER.uuid());
    }

    @Test
    void aVisibilityFlipAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        PlayerWarpRepository repo =
                PlayerWarpSync.repository(new CachedPlayerWarpRepository(new RecordingDelegate()), bus);
        PlayerWarp warp = PlayerWarp.create(OWNER, "owner", PlayerWarpName.of("base"), position(), Instant.EPOCH);

        // A /setpwarp public/private flip also upserts the same row through save, so it announces.
        repo.save(warp.withAccess(WarpAccess.PUBLIC, Instant.EPOCH));

        assertThat(bus.published).singleElement().isInstanceOf(PlayerWarpChanged.class);
        assertThat(((PlayerWarpChanged) bus.published.get(0)).owner()).isEqualTo(OWNER.uuid());
    }

    @Test
    void aDeleteAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        RecordingDelegate delegate = new RecordingDelegate();
        // Seed the warp straight on the delegate (no announcement) so the delete stands alone; the decorator resolves
        // the owner from the surrogate id before removing the row, which is why the row must already carry one.
        PlayerWarpId id =
                delegate.save(PlayerWarp.create(OWNER, "owner", PlayerWarpName.of("base"), position(), Instant.EPOCH));
        PlayerWarpRepository repo = PlayerWarpSync.repository(new CachedPlayerWarpRepository(delegate), bus);

        repo.deleteById(id);

        assertThat(bus.published)
                .singleElement()
                .isInstanceOfSatisfying(
                        PlayerWarpChanged.class,
                        frame -> assertThat(frame.owner()).isEqualTo(OWNER.uuid()));
    }

    @Test
    void recordingAVisitDoesNotAnnounceToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        PlayerWarpRepository repo =
                PlayerWarpSync.repository(new CachedPlayerWarpRepository(new RecordingDelegate()), bus);

        // A visit count is high-frequency, eventually-consistent data, so the decorator forwards it without a
        // frame: peers are not invalidated per teleport.
        repo.recordVisit(PlayerWarpId.of(1));

        assertThat(bus.published).isEmpty();
    }

    @Test
    void aRemotePlayerWarpChangedDropsTheOwnerSoTheNextReadReloads() {
        RecordingDelegate delegate = new RecordingDelegate();
        CachedPlayerWarpRepository cached = new CachedPlayerWarpRepository(delegate);

        cached.ownedBy(OWNER); // prime the cache from the delegate
        cached.ownedBy(OWNER); // served from cache, no extra delegate read
        assertThat(delegate.reads.get()).isEqualTo(1);

        PlayerWarpSync.listener(cached).onRemoteChange(new PlayerWarpChanged("peer-2", OWNER.uuid()));

        cached.ownedBy(OWNER); // the dropped owner reloads from the delegate
        assertThat(delegate.reads.get()).isEqualTo(2);
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheUntouched() {
        RecordingDelegate delegate = new RecordingDelegate();
        CachedPlayerWarpRepository cached = new CachedPlayerWarpRepository(delegate);

        cached.ownedBy(OWNER);
        assertThat(delegate.reads.get()).isEqualTo(1);

        PlayerWarpSync.listener(cached).onRemoteChange(new HomeChanged("peer-2", OWNER.uuid()));

        cached.ownedBy(OWNER); // still cached. A non-player-warp frame does not invalidate
        assertThat(delegate.reads.get()).isEqualTo(1);
    }

    private static Position position() {
        return Position.of(WORLD, 1, 2, 3);
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

    /** A delegate that counts owner reads so a cache hit is observable as the absence of a read. */
    private static final class RecordingDelegate implements PlayerWarpRepository {

        private final Map<UUID, List<PlayerWarp>> stored = new HashMap<>();
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return stored.values().stream()
                    .flatMap(List::stream)
                    .filter(warp -> warp.name().equals(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return stored.values().stream()
                    .flatMap(List::stream)
                    .filter(warp -> warp.id().filter(id::equals).isPresent())
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            reads.incrementAndGet();
            return List.copyOf(stored.getOrDefault(owner.uuid(), List.of()));
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return stored.getOrDefault(owner.uuid(), List.of()).size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return findByName(name).isPresent();
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            PlayerWarpId id = warp.id().orElseGet(() -> PlayerWarpId.of(ids.incrementAndGet()));
            PlayerWarp stamped = warp.id().isPresent() ? warp : warp.withId(id);
            stored.computeIfAbsent(warp.owner().uuid(), key -> new ArrayList<>())
                    .add(stamped);
            return id;
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            stored.values()
                    .forEach(list ->
                            list.removeIf(warp -> warp.id().filter(id::equals).isPresent()));
        }

        @Override
        public void recordVisit(PlayerWarpId id) {}

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {}

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {}
    }
}
