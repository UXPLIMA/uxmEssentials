package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.persistence.messaging.CachedIgnoreStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.IgnoreChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The messaging context's ignore-list cross-server sync seam, the same shape as {@link HomeSync} and
 * {@link PlayerWarpSync}, keyed by the same {@link CachedIgnoreStore} the {@code /msg} delivery path reads. It
 * does two things:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #store(CachedIgnoreStore, BusPublisher)} wraps the cached store so every local
 *       ignore-list write, an {@code /ignore} ({@link IgnoreStore#ignore}) or an {@code /unignore}
 *       ({@link IgnoreStore#unignore}). Publishes an {@link IgnoreChanged} frame after the durable write
 *       commits, so peers learn the owner's ignore set changed.
 *   <li><b>Inbound</b>: {@link #listener(CachedIgnoreStore)} returns a {@link RemoteSyncListener} that, on a
 *       remote {@code IgnoreChanged}, invalidates exactly that owner's cached set so the next ignore-aware
 *       {@code /msg} or {@code /mail} delivery on this backend resolves the fresh list from the shared DB.
 * </ul>
 *
 * <p>The decorator wraps the <em>same</em> cache the {@code /msg} path reads, so the loop closes: a write here
 * emits a frame, the peer's listener drops the matching owner there. This is {@link PlayerWarpSync} copied for
 * the per-owner ignore set, a write decorator plus an invalidation listener over its own cached store, with
 * no render: the ignore list is plain data read at delivery time.
 */
@NullMarked
public final class IgnoreSync {

    private IgnoreSync() {}

    /** An {@link IgnoreStore} that broadcasts an {@link IgnoreChanged} after every local ignore/unignore write. */
    public static IgnoreStore store(CachedIgnoreStore delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that invalidates the affected owner's cached set on a remote {@link IgnoreChanged}. */
    public static RemoteSyncListener listener(CachedIgnoreStore cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof IgnoreChanged changed) {
                cache.invalidateOwner(changed.owner());
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements IgnoreStore {

        private final IgnoreStore delegate;
        private final BusPublisher bus;

        Broadcasting(IgnoreStore delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public IgnoreList load(PlayerRef owner) {
            return delegate.load(owner);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            delegate.ignore(owner, ignored, scope);
            announce(owner);
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            delegate.unignore(owner, ignored);
            announce(owner);
        }

        private void announce(PlayerRef owner) {
            NetworkMessage frame = new IgnoreChanged(bus.serverId(), owner.uuid());
            bus.publish(frame);
        }
    }
}
