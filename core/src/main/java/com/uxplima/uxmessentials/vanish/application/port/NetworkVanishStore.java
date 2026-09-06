package com.uxplima.uxmessentials.vanish.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The network-wide vanish view a backend keeps of who is vanished across the <em>whole cluster</em>, not just those
 * hidden on this server. It is fed only by the cross-server bus, a peer's {@link VanishSync}, and is separate from
 * the local {@link VanishStore} (which holds who this backend is actively hiding, and is dropped on quit). Keeping the
 * two apart is what lets a player hop {@code survival-1 -> survival-2} without the quit on the origin erasing the
 * network-wide fact that they are still vanished: the origin's local store drops them, but every backend's network
 * view still knows, so the destination re-hides them on arrival ({@code JoinVanishReconciler}).
 *
 * <p>The name rides along so a network-wide {@code /vanish list} can render a hidden player who is not online on the
 * reading backend. A reveal removes the player entirely. When {@code cross-server} is off the module wires
 * {@link #empty()}, so the reconcile and list paths read an always-empty view and cross-server stays inert.
 */
public interface NetworkVanishStore {

    /** Apply a peer's change: mark the player vanished at their level, or drop them from the view on a reveal. */
    void apply(VanishSync change);

    /** The network-wide use level {@code who} is vanished at, or empty when no backend reports them vanished. */
    Optional<VanishLevel> levelOf(UUID who);

    /** The last-known name of a network-vanished {@code who}, for a roster render; empty when not tracked. */
    Optional<String> nameOf(UUID who);

    /** A point-in-time copy of every network-vanished player keyed by uuid, for the aggregated {@code /vanish list}. */
    Map<UUID, VanishLevel> levels();

    /** Drop every entry; called on module stop so a disable or reload leaves zero residual network state. */
    void clear();

    /** An always-empty view wired when {@code cross-server} is off, so the reconcile/list paths stay inert. */
    static NetworkVanishStore empty() {
        return Empty.INSTANCE;
    }

    /** The inert view: apply is dropped and every query is empty. */
    enum Empty implements NetworkVanishStore {
        INSTANCE;

        @Override
        public void apply(VanishSync change) {
            // Nothing to track with cross-server off.
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.empty();
        }

        @Override
        public Optional<String> nameOf(UUID who) {
            return Optional.empty();
        }

        @Override
        public Map<UUID, VanishLevel> levels() {
            return Map.of();
        }

        @Override
        public void clear() {
            // Nothing to clear.
        }
    }
}
