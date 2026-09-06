package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.vaults.CachedVaultRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.NullMarked;

/**
 * The vaults context's cross-server sync seam, the same shape as {@link HomeSync} and {@link WalletSync},
 * applied to a per-owner, per-index vault:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedVaultRepository, BusPublisher)} wraps the cached repository
 *       so every local vault save (a vault window closed and written through) <em>or delete</em> publishes a
 *       {@link VaultChanged} frame after the durable write commits. Peers learn that owner's vault changed and
 *       drop their cached copy (a deleted vault read from the shared DB now resolves empty).
 *   <li><b>Inbound</b>: {@link #listener(CachedVaultRepository)} returns a {@link RemoteSyncListener} that, on a
 *       remote {@code VaultChanged}, invalidates exactly that {@code (owner, index)} so the next open on this
 *       backend reads the fresh contents from the shared DB.
 * </ul>
 *
 * <p>A vault is keyed by {@link VaultId}, so the frame carries both owner and index and the listener invalidates
 * that one vault: a different vault of the same owner keeps its cached payload. The decorator wraps the
 * <em>same</em> cache the vault GUI reads, so the loop closes: a save here emits a frame, the peer's listener
 * drops the matching vault there.
 */
@NullMarked
public final class VaultSync {

    private VaultSync() {}

    /** A {@link VaultRepository} that broadcasts a {@link VaultChanged} after every local save or delete. */
    public static VaultRepository repository(CachedVaultRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that invalidates the affected vault on a remote {@link VaultChanged}. */
    public static RemoteSyncListener listener(CachedVaultRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof VaultChanged changed) {
                cache.invalidate(new VaultId(changed.owner(), changed.vaultIndex()));
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each save and delete on the bus. */
    private static final class Broadcasting implements VaultRepository {

        private final VaultRepository delegate;
        private final BusPublisher bus;

        Broadcasting(VaultRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            return delegate.find(id);
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            return delegate.ownedIndices(owner);
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            return delegate.summaries(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return delegate.count(owner);
        }

        @Override
        public void save(Vault vault) {
            delegate.save(vault);
            announce(vault.id());
        }

        @Override
        public void delete(VaultId id) {
            delegate.delete(id);
            announce(id);
        }

        @Override
        public int deleteUntouchedBefore(java.time.Instant cutoff) {
            // A bulk inactive-vault purge against the one shared DB: the cached delegate clears its cache, and a
            // peer that next opens one of the purged ids re-reads an empty vault straight from the backend. The
            // purged ids are not known per-row, so there is no per-vault VaultChanged to announce: the shared
            // store is the source of truth, which keeps this correct everywhere without a broadcast.
            return delegate.deleteUntouchedBefore(cutoff);
        }

        private void announce(VaultId id) {
            bus.publish(new VaultChanged(bus.serverId(), id.owner(), id.index()));
        }
    }
}
