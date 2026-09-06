package com.uxplima.uxmessentials.vanish.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Outbound port that applies a player's vanish state to the live server view: hiding a vanished player from every
 * viewer whose see level is below the player's use level, and revealing them again on unvanish. The adapter prefers
 * packet-level hiding (drop the player entity + tablist entry for each ineligible viewer), falling back to Bukkit's
 * {@code hidePlayer} / {@code showPlayer} graph, which also removes the tablist entry and is what the
 * {@code canSee}-reading surfaces observe.
 *
 * <p>Every method hops to the owning region/entity thread through the {@code Scheduler} port inside the adapter; the
 * use cases call these from any thread and never touch a live player themselves. The store is the authority for
 * <em>who</em> is vanished (and at what level); this port is only the visible effect of that state.
 */
public interface VanishView {

    /**
     * Reconcile the visibility of {@code who}, vanished at {@code level}, across every viewer: reveal them to a viewer
     * whose see level clears {@code level} and hide them from one whose does not. Called when {@code who} vanishes and
     * whenever their level is re-applied, so a level raise or drop settles without a blanket flash.
     */
    void hide(PlayerRef who, VanishLevel level);

    /** Reveal {@code who} to everyone again. Called when {@code who} unvanishes. */
    void reveal(PlayerRef who);
}
