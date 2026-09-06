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

import com.uxplima.uxmessentials.persistence.vaults.CachedVaultRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.Test;

/**
 * Pins the vaults cross-server sync seam: the broadcasting decorator publishes a {@link VaultChanged} carrying
 * the affected owner and index after a local {@code save} or {@code delete}, while a read and the bulk
 * {@code deleteUntouchedBefore} purge publish nothing (the purge cannot enumerate removed ids, so it announces
 * no per-vault frame by design). The listener drops exactly that {@code (owner, index)} from the
 * {@link CachedVaultRepository} on a remote {@code VaultChanged} so the next open reloads the authoritative
 * contents; a frame for a different index of the same owner leaves the originally-primed vault cached, and a
 * frame of another context leaves the cache untouched. This mirrors {@code PlayerWarpSyncTest} (outbound) and
 * {@code WalletSyncTest} (inbound).
 */
class VaultSyncTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");
    private static final Instant AT = Instant.parse("2026-06-14T00:00:00Z");

    @Test
    void aSaveAnnouncesThatVaultToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        VaultRepository repo = VaultSync.repository(new CachedVaultRepository(new CountingDelegate()), bus);

        repo.save(Vault.allocate(VaultId.of(OWNER, 2), new VaultSize(3), AT));

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(VaultChanged.class, frame -> {
            assertThat(frame.owner()).isEqualTo(OWNER.uuid());
            assertThat(frame.vaultIndex()).isEqualTo(2);
            assertThat(frame.originServer()).isEqualTo("survival-1");
        });
    }

    @Test
    void aDeleteAnnouncesThatVaultToPeers() {
        CapturingBus bus = new CapturingBus("survival-1");
        VaultRepository repo = VaultSync.repository(new CachedVaultRepository(new CountingDelegate()), bus);

        repo.delete(VaultId.of(OWNER, 2));

        assertThat(bus.published).singleElement().isInstanceOfSatisfying(VaultChanged.class, frame -> {
            assertThat(frame.owner()).isEqualTo(OWNER.uuid());
            assertThat(frame.vaultIndex()).isEqualTo(2);
        });
    }

    @Test
    void aBulkPurgeAnnouncesNothing() {
        CapturingBus bus = new CapturingBus("survival-1");
        VaultRepository repo = VaultSync.repository(new CachedVaultRepository(new CountingDelegate()), bus);

        // A bulk inactive-vault purge cannot enumerate the removed ids, so it announces no per-vault frame by
        // design: the shared DB stays authoritative and a peer re-reads an empty vault on its next open.
        repo.deleteUntouchedBefore(AT);

        assertThat(bus.published).isEmpty();
    }

    @Test
    void aReadPublishesNothing() {
        CapturingBus bus = new CapturingBus("survival-1");
        VaultRepository repo = VaultSync.repository(new CachedVaultRepository(new CountingDelegate()), bus);

        repo.find(VaultId.of(OWNER, 1));

        assertThat(bus.published).isEmpty();
    }

    @Test
    void aRemoteVaultChangedDropsThatVaultSoTheNextOpenReloads() {
        CountingDelegate delegate = new CountingDelegate();
        CachedVaultRepository cached = new CachedVaultRepository(delegate);
        VaultId id = VaultId.of(OWNER, 1);

        cached.find(id); // prime the cache from the delegate
        cached.find(id); // served from cache, no extra delegate read
        assertThat(delegate.reads.get()).isEqualTo(1);

        VaultSync.listener(cached).onRemoteChange(new VaultChanged("peer-2", OWNER.uuid(), 1));

        cached.find(id); // the dropped vault reloads from the delegate
        assertThat(delegate.reads.get()).isEqualTo(2);
    }

    @Test
    void aRemoteVaultChangedForAnotherIndexLeavesThePrimedVaultCached() {
        CountingDelegate delegate = new CountingDelegate();
        CachedVaultRepository cached = new CachedVaultRepository(delegate);
        VaultId primed = VaultId.of(OWNER, 1);

        cached.find(primed);
        assertThat(delegate.reads.get()).isEqualTo(1);

        // VaultSync invalidates exactly (owner, index); a different index of the same owner is untouched.
        VaultSync.listener(cached).onRemoteChange(new VaultChanged("peer-2", OWNER.uuid(), 2));

        cached.find(primed); // still cached. Only vault 2 would have been dropped
        assertThat(delegate.reads.get()).isEqualTo(1);
    }

    @Test
    void aFrameForAnotherContextLeavesTheCacheUntouched() {
        CountingDelegate delegate = new CountingDelegate();
        CachedVaultRepository cached = new CachedVaultRepository(delegate);
        VaultId id = VaultId.of(OWNER, 1);

        cached.find(id);
        assertThat(delegate.reads.get()).isEqualTo(1);

        VaultSync.listener(cached).onRemoteChange(new HomeChanged("peer-2", OWNER.uuid()));

        cached.find(id); // still cached. A non-vault frame does not invalidate
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

    /** A delegate that counts keyed vault reads so a cache hit is observable as the absence of a read. */
    private static final class CountingDelegate implements VaultRepository {

        private final Map<VaultId, Vault> stored = new HashMap<>();
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public Optional<Vault> find(VaultId id) {
            reads.incrementAndGet();
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return List.of();
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return 0;
        }

        @Override
        public void save(Vault vault) {
            stored.put(vault.id(), vault);
        }

        @Override
        public void delete(VaultId id) {
            stored.remove(id);
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            return 0;
        }
    }
}
