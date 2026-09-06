package com.uxplima.uxmessentials.playerstate.application.port;

import java.util.List;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for {@code /near}: the players within a radius of a viewer, ordered nearest-first. The
 * adapter reads the viewer's live location and the rest of the roster's positions on the global region thread
 * (each player's position is owned by its own region under Folia, so the viewer's region thread cannot read
 * them), maps each hit to a {@link Nearby} (the ref plus the integer block distance), and pushes the result to
 * {@code onResolved} once it is computed: never blocking the calling region thread on a foreign read.
 * Application code never iterates {@code Bukkit.getOnlinePlayers()}: it asks this port.
 */
public interface NearbyPlayers {

    /**
     * Resolve the players within {@code radius} blocks of {@code viewer} (excluding the viewer), nearest
     * first, and hand the result to {@code onResolved}. The resolution runs on whichever thread the adapter
     * needs to read the roster safely; {@code onResolved} is therefore invoked on that thread, so it must do
     * no more than forward the result to a sink that re-targets each delivery. The list is empty when the
     * viewer is offline or nobody is in range.
     */
    void within(PlayerRef viewer, int radius, Consumer<List<Nearby>> onResolved);

    /**
     * One nearby player and their block distance from the viewer.
     *
     * @param who the nearby player
     * @param distance the rounded block distance from the viewer
     */
    record Nearby(PlayerRef who, int distance) {}
}
