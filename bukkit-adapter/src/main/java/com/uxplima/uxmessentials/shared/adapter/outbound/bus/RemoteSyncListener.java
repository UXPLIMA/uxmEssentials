package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import com.uxplima.uxmessentials.shared.network.NetworkMessage;

/**
 * Applies a sync frame that originated on another backend. The bus client has already verified the frame did
 * not originate on this backend (the loop sentinel), so a listener acts unconditionally on a genuine remote
 * change: typically by invalidating a Caffeine cache so the next read reflects the shared DB. A context
 * registers one of these to opt into cross-server sync; the same shape serves economy, homes, warps, and
 * vaults, so adding a context is registering one more listener, not extending the bus.
 *
 * <p>A listener runs off the tick thread (cache invalidation needs no Bukkit API). If a listener does need a
 * Bukkit touch it must hop through the injected {@code Scheduler} port itself. The bus client never invokes
 * a listener on a region thread.
 */
@FunctionalInterface
public interface RemoteSyncListener {

    /** React to a frame that genuinely originated on another backend. */
    void onRemoteChange(NetworkMessage message);
}
