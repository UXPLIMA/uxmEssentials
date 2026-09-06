package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.persistence.messaging.CachedIgnoreStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.IgnoreChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * Pins the messaging ignore-list cross-server sync seam: the broadcasting decorator publishes an
 * {@link IgnoreChanged} carrying the affected owner after every local write that changes that owner's set, an
 * {@code /ignore} and an {@code /unignore}, and the listener drops exactly that owner from the
 * {@link CachedIgnoreStore} on a remote frame so the next ignore-aware delivery reloads the authoritative
 * rows. A frame of another type leaves the cache untouched. This mirrors {@code PlayerWarpSyncTest}.
 */
class IgnoreSyncTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");
    private static final PlayerRef IGNORED = new PlayerRef(UUID.randomUUID(), "ignored");

    @Test
    void anIgnoreAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        IgnoreStore store = IgnoreSync.store(new CachedIgnoreStore(new RecordingDelegate()), bus);

        store.ignore(OWNER, IGNORED, IgnoreScope.ALL);

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(IgnoreChanged.class, frame -> {
            assertThat(frame.owner()).isEqualTo(OWNER.uuid());
            assertThat(frame.originServer()).isEqualTo("survival-1");
        });
    }

    @Test
    void anUnignoreAnnouncesThatOwnerToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        IgnoreStore store = IgnoreSync.store(new CachedIgnoreStore(new RecordingDelegate()), bus);

        store.unignore(OWNER, IGNORED);

        assertThat(bus.published)
                .singleElement()
                .isInstanceOfSatisfying(
                        IgnoreChanged.class, frame -> assertThat(frame.owner()).isEqualTo(OWNER.uuid()));
    }

    @Test
    void aRemoteIgnoreChangedDropsTheOwnerSoTheNextReadReloads() {
        RecordingDelegate delegate = new RecordingDelegate();
        CachedIgnoreStore cached = new CachedIgnoreStore(delegate);

        cached.load(OWNER); // prime the cache from the delegate
        cached.load(OWNER); // served from cache, no extra delegate read
        assertThat(delegate.reads.get()).isEqualTo(1);

        IgnoreSync.listener(cached).onRemoteChange(new IgnoreChanged("peer-2", OWNER.uuid()));

        cached.load(OWNER); // the dropped owner reloads from the delegate
        assertThat(delegate.reads.get()).isEqualTo(2);
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheUntouched() {
        RecordingDelegate delegate = new RecordingDelegate();
        CachedIgnoreStore cached = new CachedIgnoreStore(delegate);

        cached.load(OWNER);
        assertThat(delegate.reads.get()).isEqualTo(1);

        IgnoreSync.listener(cached).onRemoteChange(new HomeChanged("peer-2", OWNER.uuid()));

        cached.load(OWNER); // still cached. A non-ignore frame does not invalidate
        assertThat(delegate.reads.get()).isEqualTo(1);
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
    private static final class RecordingDelegate implements IgnoreStore {

        private final Map<UUID, Map<UUID, IgnoreScope>> stored = new HashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public IgnoreList load(PlayerRef owner) {
            reads.incrementAndGet();
            return IgnoreList.empty(owner);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            stored.computeIfAbsent(owner.uuid(), id -> new LinkedHashMap<>()).put(ignored.uuid(), scope);
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            stored.getOrDefault(owner.uuid(), new LinkedHashMap<>()).remove(ignored.uuid());
        }
    }
}
