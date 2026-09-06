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

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import org.junit.jupiter.api.Test;

/**
 * Pins the homes cross-server sync seam: the broadcasting decorator publishes a {@link HomeChanged} carrying
 * the affected owner after every local write that changes that owner's set, a {@code save} (a set or a
 * relocate), a {@code deleteSlot}, and the admin {@code deleteAll}, while a read publishes nothing. The
 * listener drops exactly that owner from the {@link CachedHomeRepository} on a remote {@code HomeChanged} so the
 * next {@code /home} reloads the authoritative rows, and a frame of another context leaves the cache untouched.
 * This mirrors {@code PlayerWarpSyncTest} (outbound) and {@code WalletSyncTest} (inbound).
 */
class HomeSyncTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    @Test
    void aSaveAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        HomeRepository repo = HomeSync.repository(new CachedHomeRepository(new RecordingDelegate()), bus);

        repo.save(Home.create(OWNER, HomeSlot.of(0), position(), Instant.EPOCH));

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(HomeChanged.class, frame -> {
            assertThat(frame.owner()).isEqualTo(OWNER.uuid());
            assertThat(frame.originServer()).isEqualTo("survival-1");
        });
    }

    @Test
    void aDeleteSlotAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        HomeRepository repo = HomeSync.repository(new CachedHomeRepository(new RecordingDelegate()), bus);

        repo.deleteSlot(OWNER, HomeSlot.of(0));

        assertThat(bus.published)
                .singleElement()
                .isInstanceOfSatisfying(
                        HomeChanged.class, frame -> assertThat(frame.owner()).isEqualTo(OWNER.uuid()));
    }

    @Test
    void aDeleteAllAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        HomeRepository repo = HomeSync.repository(new CachedHomeRepository(new RecordingDelegate()), bus);

        repo.deleteAll(OWNER);

        assertThat(bus.published)
                .singleElement()
                .isInstanceOfSatisfying(
                        HomeChanged.class, frame -> assertThat(frame.owner()).isEqualTo(OWNER.uuid()));
    }

    @Test
    void aReadPublishesNothing() {
        CapturingBus bus = new CapturingBus("survival-1");
        HomeRepository repo = HomeSync.repository(new CachedHomeRepository(new RecordingDelegate()), bus);

        repo.count(OWNER);

        assertThat(bus.published).isEmpty();
    }

    @Test
    void aRemoteHomeChangedDropsTheOwnerSoTheNextReadReloads() {
        RecordingDelegate delegate = new RecordingDelegate();
        CachedHomeRepository cached = new CachedHomeRepository(delegate);

        cached.load(OWNER); // prime the cache from the delegate
        cached.load(OWNER); // served from cache, no extra delegate read
        assertThat(delegate.reads.get()).isEqualTo(1);

        HomeSync.listener(cached).onRemoteChange(new HomeChanged("peer-2", OWNER.uuid()));

        cached.load(OWNER); // the dropped owner reloads from the delegate
        assertThat(delegate.reads.get()).isEqualTo(2);
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheUntouched() {
        RecordingDelegate delegate = new RecordingDelegate();
        CachedHomeRepository cached = new CachedHomeRepository(delegate);

        cached.load(OWNER);
        assertThat(delegate.reads.get()).isEqualTo(1);

        HomeSync.listener(cached).onRemoteChange(new VaultChanged("peer-2", OWNER.uuid(), 1));

        cached.load(OWNER); // still cached. A non-home frame does not invalidate
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
    private static final class RecordingDelegate implements HomeRepository {

        private final Map<UUID, List<Home>> stored = new HashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public HomeSet load(PlayerRef owner) {
            reads.incrementAndGet();
            return HomeSet.of(owner, List.copyOf(stored.getOrDefault(owner.uuid(), List.of())));
        }

        @Override
        public int count(PlayerRef owner) {
            return stored.getOrDefault(owner.uuid(), List.of()).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return stored.getOrDefault(owner.uuid(), List.of()).stream()
                    .filter(home -> home.slot().equals(slot))
                    .findFirst();
        }

        @Override
        public void save(Home home) {
            stored.computeIfAbsent(home.owner().uuid(), id -> new ArrayList<>()).add(home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            stored.getOrDefault(owner.uuid(), new ArrayList<>())
                    .removeIf(home -> home.slot().equals(slot));
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            stored.remove(owner.uuid());
        }
    }
}
