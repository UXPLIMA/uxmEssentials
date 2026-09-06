package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.domain.BackCapturePolicy;
import com.uxplima.uxmessentials.teleport.domain.TeleportCauseCategory;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import org.jspecify.annotations.NullMarked;

/**
 * The teleport context's Bukkit listeners. Thin adapters that translate an event to a domain call and
 * return. They own the <b>move-cancels-warmup</b> invariant ({@link PlayerMoveEvent} compares block
 * coordinates and flips the pending warmup), the optional damage- and interact-cancel axes, the
 * {@code /back} capture for both deaths and vanilla teleports (gated by the {@link BackCapturePolicy} so
 * an ender pearl does not clobber the return point), and the per-player cleanup on quit. Each handler is
 * cheap-rejection-first: a move within the same block never touches the tracker.
 *
 * <p>Events fire on the player's region thread, so the warmup tracker's {@code computeIfPresent} and the
 * {@code /back} capture run where the live position is valid: no scheduler hop is needed here.
 */
@NullMarked
public final class TeleportListeners implements Listener {

    private final WarmupTracker warmupTracker;
    private final CaptureBack captureBack;
    private final ConfigStore config;
    private final Supplier<BackCapturePolicy> backCapturePolicy;

    public TeleportListeners(
            WarmupTracker warmupTracker,
            CaptureBack captureBack,
            ConfigStore config,
            Supplier<BackCapturePolicy> backCapturePolicy) {
        this.warmupTracker = Objects.requireNonNull(warmupTracker, "warmupTracker");
        this.captureBack = Objects.requireNonNull(captureBack, "captureBack");
        this.config = Objects.requireNonNull(config, "config");
        this.backCapturePolicy = Objects.requireNonNull(backCapturePolicy, "backCapturePolicy");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event)) {
            return; // sub-block jitter (look-only or within-block) never cancels a warmup
        }
        warmupTracker.onMove(event.getPlayer().getUniqueId(), BukkitRefs.toPosition(event.getTo()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!config.getBoolean("warmup.cancel-on-damage", false)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            warmupTracker.onAction(player.getUniqueId(), WarmupCancelReason.DAMAGED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.getBoolean("warmup.cancel-on-interact", false)) {
            return;
        }
        warmupTracker.onAction(event.getPlayer().getUniqueId(), WarmupCancelReason.INTERACTED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        captureBack.captureOnDeath(BukkitRefs.toRef(player), TeleportRefs.positionOf(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return; // our own hops capture /back through the executor; do not double-capture here
        }
        TeleportCauseCategory cause = categoryOf(event.getCause());
        if (!backCapturePolicy.get().capturesOn(cause)) {
            return; // an ignored cause (ender pearl, chorus fruit, …) must not clobber /back
        }
        Player player = event.getPlayer();
        captureBack.captureBeforeTeleport(BukkitRefs.toRef(player), BukkitRefs.toPosition(event.getFrom()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warmupTracker.forget(event.getPlayer().getUniqueId());
    }

    private static TeleportCauseCategory categoryOf(PlayerTeleportEvent.TeleportCause cause) {
        // Paper folded the old CHORUS_FRUIT cause into CONSUMABLE_EFFECT in 1.21.5; eating a chorus fruit
        // now arrives as CONSUMABLE_EFFECT, so that is the constant the chorus-fruit category maps from.
        return switch (cause) {
            case ENDER_PEARL -> TeleportCauseCategory.ENDER_PEARL;
            case CONSUMABLE_EFFECT -> TeleportCauseCategory.CHORUS_FRUIT;
            case COMMAND -> TeleportCauseCategory.COMMAND;
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> TeleportCauseCategory.PORTAL;
            case DISMOUNT, EXIT_BED -> TeleportCauseCategory.DISMOUNT;
            default -> TeleportCauseCategory.OTHER;
        };
    }

    private static boolean sameBlock(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return to != null
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && Objects.equals(from.getWorld(), to.getWorld());
    }
}
