package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link com.uxplima.uxmessentials.playerstate.application.port.StateReconciler} implementation. It pushes
 * an immutable {@link PlayerStateSnapshot} onto the live Bukkit player, hopping to the player's owning
 * region/entity thread through the injected {@link Scheduler} port: the Bukkit mutators
 * ({@code setInvulnerable}, {@code setAllowFlight}, {@code setGameMode}, {@code setWalkSpeed},
 * {@code setFlySpeed}) are only valid there on Folia (docs/02-concurrency §playerstate reconciliation). An
 * offline player is a silent no-op (the entity scheduler refuses a despawned entity).
 *
 * <p>The reconciler is the single place domain state crosses to the Bukkit API; the use cases never touch a
 * live {@code Player}. Disabling fly while the player is airborne also clears their in-flight state so they do
 * not hang in the air. Switching into a flight-capable mode (creative or spectator) puts the player into the
 * air at once rather than waiting for a double-jump, which is what operators expect from {@code /gm 1}; leaving
 * such a mode is left to vanilla's own handling of {@code setGameMode}.
 */
@NullMarked
public final class BukkitStateReconciler
        implements com.uxplima.uxmessentials.playerstate.application.port.StateReconciler {

    private final Scheduler scheduler;

    public BukkitStateReconciler(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void reconcile(PlayerRef who, PlayerStateSnapshot snapshot) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(snapshot, "snapshot");
        scheduler.onEntity(who, () -> apply(who, snapshot));
    }

    private void apply(PlayerRef who, PlayerStateSnapshot snapshot) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setInvulnerable(snapshot.god());
        applyFlight(player, snapshot.fly());
        snapshot.gameMode().ifPresent(mode -> applyGameMode(player, mode));
        player.setWalkSpeed(snapshot.walkSpeed().toWalkMultiplier());
        player.setFlySpeed(snapshot.flySpeed().toFlyMultiplier());
    }

    private static void applyFlight(Player player, boolean allowed) {
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    private static void applyGameMode(Player player, GameModeRef mode) {
        player.setGameMode(toBukkit(mode));
        // A flight-capable mode should lift the player straight away; vanilla only allows flight here, it does
        // not start it, so the player would otherwise stay grounded until a double-jump. Done after setGameMode
        // because the mode switch resets flight, and setFlying(true) requires the allowance to already be set.
        if (mode.flies()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
    }

    private static GameMode toBukkit(GameModeRef mode) {
        return switch (mode) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }
}
