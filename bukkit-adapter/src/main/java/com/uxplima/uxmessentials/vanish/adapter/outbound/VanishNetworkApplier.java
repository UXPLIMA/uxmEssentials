package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import org.jspecify.annotations.NullMarked;

/**
 * The vanish context's inbound cross-server consumer: it applies a peer's {@link VanishSync} to the network-wide view
 * and, for the rare case where the affected player is online on <em>this</em> backend, reconciles their local
 * visibility too. It is the handler {@link BusVanishBus} feeds from the bus's off-tick dispatch thread.
 *
 * <p><b>Folia-safe.</b> Updating the {@link NetworkVanishStore} is a pure lock-free map write with no Bukkit touch, so
 * it runs inline on the dispatch thread. Any player/packet work. Re-hiding or revealing a player who happens to be
 * online here. Is marshalled onto that player's own entity region through the injected {@link Scheduler}; the
 * scheduler silently no-ops when the player is offline on this backend (the common case for a remote frame, since a
 * player is online on exactly one backend), so the callback never touches the Bukkit API off-region.
 */
@NullMarked
public final class VanishNetworkApplier implements Consumer<VanishSync> {

    private final NetworkVanishStore network;
    private final VanishStore store;
    private final VanishView view;
    private final Scheduler scheduler;

    public VanishNetworkApplier(NetworkVanishStore network, VanishStore store, VanishView view, Scheduler scheduler) {
        this.network = Objects.requireNonNull(network, "network");
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void accept(VanishSync change) {
        Objects.requireNonNull(change, "change");
        network.apply(change);
        PlayerRef target = new PlayerRef(change.player(), change.playerName());
        scheduler.onEntity(target, () -> reconcileOnline(target, change));
    }

    /** On the target's region: re-hide or reveal a player who is actually online on this backend. */
    private void reconcileOnline(PlayerRef target, VanishSync change) {
        if (change.vanished()) {
            store.vanish(target.uuid(), change.level());
            view.hide(target, change.level());
        } else {
            store.reveal(target.uuid());
            view.reveal(target);
        }
    }
}
