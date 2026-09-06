package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Ends a pose on the exits a posing player can take: they quit, teleport away, die, change world, take damage,
 * dismount the seat, or start sneaking. {@link StopPose} cleans up whatever the pose rode on each: it removes the
 * seat entity for a sit and releases the hold on a crawl, so nothing is left behind. The quit, teleport, death, and world-change exits pass {@code allowReturn = false} (the player is leaving
 * or already moving, so the {@code return-to-start} teleport would be wrong there); the others return the player
 * when the server is configured to. Every branch is guarded by the session registry so a non-posing player is
 * untouched.
 *
 * <p>Sneak is the exit a player reaches for first, and it arrives two different ways. A player standing free sends
 * a sneak, so {@link #onSneak} ends the pose. A player riding a seat entity sends no sneak at all: the client turns
 * the sneak key into a dismount, which arrives as {@link EntityDismountEvent}. That event covers every seat, not
 * only the {@code Vehicle} types a vehicle-exit event would, so an armour-stand seat is caught here too; it is the
 * exit that makes sneak work for a sit, a lie-down, a bellyflop, and a spin alike.
 *
 * <p>A crawl is the exception on one exit: a crawler is actively moving and may take damage without meaning to
 * stand up, so {@link #onDamage} leaves a crawl running while it still ends a sit, lay, or spin. Sneak does end a
 * crawl, matching every other pose. Teleport, death, world-change, and quit end a crawl too, releasing its hold
 * through {@code StopPose}, so no exit can leave a player pinned prone.
 *
 * <p>A quit also ends the pose of anyone stacked <em>on</em> the leaver: when a player-sit carrier logs out, Bukkit
 * drops the rider as a passenger, but the rider's {@link PoseSession} would linger unless it is ended too. The quit
 * handler stops each of the carrier's direct riders, so no rider is left with a stuck session once its carrier is
 * gone (a rider stacked on that rider is in turn stopped when its own carrier's session ends).
 */
@NullMarked
public final class PoseCancelListener implements Listener {

    private final StopPose stopPose;
    private final PoseSessions sessions;

    public PoseCancelListener(StopPose stopPose, PoseSessions sessions) {
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        // Always attempt cleanup on quit so no seat can outlive the player; no return for a leaver.
        stopPose.stop(who, false);
        // End the pose of anyone stacked on the leaver too, so no rider is left with a session once its carrier is
        // gone. Snapshot first, then stop each: stopping mutates the registry ridersOf reads.
        for (PlayerRef rider : sessions.ridersOf(who)) {
            stopPose.stop(rider, false);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        endIfPosing(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // A crawler has no seat to be dismounted from on death (that path ends a sit), so end any pose here and
        // never return a corpse to where its pose began.
        endIfPosing(event.getEntity(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Backstop for the teleport exit: a cross-world move already fires a teleport, but a portal or plugin move
        // that lands here still ends the pose, so no crawl is left held down in the world just left.
        endIfPosing(event.getPlayer(), false);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            endUnlessCrawling(player);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            endIfPosing(player, true);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            endIfPosing(event.getPlayer(), true);
        }
    }

    private void endIfPosing(Player player, boolean allowReturn) {
        PlayerRef who = BukkitRefs.toRef(player);
        if (sessions.isPosing(who)) {
            stopPose.stop(who, allowReturn);
        }
    }

    /**
     * End the player's pose unless they are crawling: a crawler actively moves and may take damage without meaning
     * to stand up, so that exit leaves a crawl running while still ending a sit, lay, or spin.
     */
    private void endUnlessCrawling(Player player) {
        PlayerRef who = BukkitRefs.toRef(player);
        Optional<PoseSession> session = sessions.current(who);
        if (session.isPresent() && session.get().type() != PoseType.CRAWL) {
            stopPose.stop(who, true);
        }
    }
}
