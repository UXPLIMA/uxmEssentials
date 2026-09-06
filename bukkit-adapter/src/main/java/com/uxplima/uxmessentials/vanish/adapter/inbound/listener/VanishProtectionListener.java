package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishPickupPreferences;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the passive protections a vanished player carries, each behind its config toggle read from the live
 * {@link VanishConfig} snapshot:
 *
 * <ul>
 *   <li><b>no-damage</b>. Cancel any incoming {@link EntityDamageEvent} on a vanished player, so they are invulnerable
 *       while hidden.
 *   <li><b>no-hunger</b>. Cancel a {@link FoodLevelChangeEvent} that would <em>lower</em> a vanished player's food, so
 *       hunger stops draining while still allowing a deliberate eat to top it back up.
 *   <li><b>mob-target</b>. Drop a mob's target the moment it acquires a vanished player, cancelling the
 *       {@link EntityTargetLivingEntityEvent}, so no mob paths to or attacks a hidden player.
 *   <li><b>pickup</b>. Cancel a vanished player's item pickup unless their {@link VanishPickupPreferences} say
 *       otherwise (the {@code /vanish pickup} toggle, defaulting to the {@code pickup-items} config value), covering
 *       both the attempt and entity pickup entry points.
 * </ul>
 *
 * <p>Each handler runs on the region thread its event fires on and only reads the {@link VanishStore} (a lock-free
 * concurrent map) and the config snapshot, so there is no cross-thread hazard. A toggle left {@code false} short-
 * circuits its handler, leaving vanilla behaviour untouched.
 */
@NullMarked
public final class VanishProtectionListener implements Listener {

    private final VanishStore store;
    private final VanishConfig config;
    private final VanishPickupPreferences pickup;

    public VanishProtectionListener(VanishStore store, VanishConfig config, VanishPickupPreferences pickup) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.pickup = Objects.requireNonNull(pickup, "pickup");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (config.noDamage() && event.getEntity() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!config.noHunger() || !(event.getEntity() instanceof Player player) || !isVanished(player)) {
            return;
        }
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true); // block the drain; a deliberate eat (a rise) is left alone
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (config.mobTarget() && event.getTarget() instanceof Player player && isVanished(player)) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (pickupBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && pickupBlocked(player)) {
            event.setCancelled(true);
        }
    }

    /** Whether {@code player}'s pickup is cancelled: vanished, and their preference says not to pick up. */
    private boolean pickupBlocked(Player player) {
        return isVanished(player) && !pickup.picksUp(BukkitRefs.toRef(player));
    }

    private boolean isVanished(Player player) {
        return store.isVanished(player.getUniqueId());
    }
}
