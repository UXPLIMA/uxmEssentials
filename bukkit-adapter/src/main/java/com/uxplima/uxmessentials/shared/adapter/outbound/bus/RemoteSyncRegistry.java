package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The set of {@link RemoteSyncListener}s the bus client dispatches an inbound frame to. Contexts register
 * their listener as they wire (all on the main thread at enable, before any frame can arrive); the bus reads
 * the set when a frame lands. Backed by a {@link CopyOnWriteArrayList} so registration during wiring and the
 * off-tick dispatch never need a shared lock: registration is rare and dispatch is read-only.
 *
 * <p>This is the single seam every context opts into cross-server sync through: homes and economy register
 * here today; warps and vaults register the same way with no change to the bus. The registry is owned by the
 * bus wiring and handed to each context so a disabled bus still accepts registrations harmlessly (they are
 * simply never invoked).
 */
@NullMarked
public final class RemoteSyncRegistry {

    private final List<RemoteSyncListener> listeners = new CopyOnWriteArrayList<>();

    /** Add a listener the bus invokes for every inbound frame from a peer. */
    public void register(RemoteSyncListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Invoke every registered listener for {@code message}; one failing never starves the others. */
    void dispatch(NetworkMessage message, FailureSink onFailure) {
        for (RemoteSyncListener listener : listeners) {
            try {
                listener.onRemoteChange(message);
            } catch (RuntimeException failure) {
                onFailure.onFailure(message, failure);
            }
        }
    }

    /** How the bus reports a listener that threw, so the registry need not know about logging. */
    @FunctionalInterface
    interface FailureSink {
        void onFailure(NetworkMessage message, RuntimeException failure);
    }
}
