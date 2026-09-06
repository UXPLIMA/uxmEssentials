package com.uxplima.uxmessentials.playerstate.application.port;

import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that reconciles a player's live Bukkit state with their immutable {@link PlayerStateSnapshot}.
 * The adapter resolves the live {@code Player} and applies the snapshot's flags ({@code setInvulnerable},
 * {@code setAllowFlight}, {@code setGameMode}, {@code setWalkSpeed}/{@code setFlySpeed}) on the player's
 * <em>owning region/entity thread</em> via the injected {@code Scheduler} port, never inline, so the call
 * is valid on Folia (docs/02-concurrency §playerstate reconciliation). An offline player is a silent no-op.
 *
 * <p>The use cases never touch the live player directly: they mutate the snapshot in the
 * {@link PlayerStateStore} and then ask this port to push the new value out. Reconciliation is the single
 * point where domain state crosses to the Bukkit API.
 */
public interface StateReconciler {

    /** Apply {@code snapshot} to {@code who}'s live player on their owning thread; no-op when offline. */
    void reconcile(PlayerRef who, PlayerStateSnapshot snapshot);
}
