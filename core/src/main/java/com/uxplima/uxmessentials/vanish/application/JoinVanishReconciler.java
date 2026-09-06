package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Seeds a joining player's local vanish state from the network-wide view on arrival, so a player vanished on another
 * backend is <em>already marked vanished</em> in this server's {@link VanishStore} before the join listener hides them.
 * This is the destination half of a cross-server hop: server A published the player's vanish over the bus, every
 * backend's {@link NetworkVanishStore} recorded it, and when the player lands on server B this reconciler reads that
 * network view and marks them locally, after which the normal join flow ({@code SetVanishLevel#reapply}) re-derives
 * their level from B's permissions and hides them.
 *
 * <p>Pure and side-effect-scoped: it only reads the network view and, when it reports the joiner vanished, writes the
 * one local store entry. A joiner the network does not report vanished is left untouched, and with {@code cross-server}
 * off the network view is {@link NetworkVanishStore#empty() empty} so this is a no-op. The single-server join path is
 * unchanged.
 */
public final class JoinVanishReconciler {

    private final NetworkVanishStore network;
    private final VanishStore store;

    public JoinVanishReconciler(NetworkVanishStore network, VanishStore store) {
        this.network = Objects.requireNonNull(network, "network");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Seed {@code joiner}'s local vanish state from the network view; returns the level they were seeded at, or empty
     * when the network does not report them vanished (nothing to seed).
     */
    public Optional<VanishLevel> reconcile(UUID joiner) {
        Objects.requireNonNull(joiner, "joiner");
        Optional<VanishLevel> networkLevel = network.levelOf(joiner);
        networkLevel.ifPresent(level -> store.vanish(joiner, level));
        return networkLevel;
    }
}
