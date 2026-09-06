package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.adapter.outbound.DamageIndicatorConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Spawns a short-lived floating number at an entity when it is hurt or healed (the damage-indicator feature, off
 * by default). On a combat hit it shows the final damage. In the crit format when the attacker's state matches a
 * vanilla critical hit, and on a heal it shows the gained health, gated per victim kind (players / mobs) by the
 * {@link DamageIndicatorConfig}. The indicator is an ephemeral, non-persistent {@link TextDisplay} spawned on the
 * event's region thread and removed after the configured duration: the removal waits off-thread, then hops back
 * onto the entity's region thread (Folia) so the despawn is always region-correct, mirroring how the renderer
 * removes its display entities. No per-tick rise/fade. The indicator simply pops and is removed (the scheduler
 * port carries no per-region repeating task), and nothing is persisted, so a crash leaves no stray entity.
 */
@NullMarked
public final class DamageIndicatorListener implements Listener {

    private final DamageIndicatorConfig config;
    private final Scheduler scheduler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public DamageIndicatorListener(DamageIndicatorConfig config, Scheduler scheduler) {
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        double amount = event.getFinalDamage();
        Entity victim = event.getEntity();
        if (amount <= 0 || !shouldShow(victim)) {
            return;
        }
        DamageIndicatorConfig.Kind kind =
                isCriticalHit(event.getDamager()) ? DamageIndicatorConfig.Kind.CRIT : DamageIndicatorConfig.Kind.DAMAGE;
        spawn(victim, config.format(amount, kind));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!config.showHeal()) {
            return;
        }
        double amount = event.getAmount();
        Entity victim = event.getEntity();
        if (amount <= 0 || !shouldShow(victim)) {
            return;
        }
        spawn(victim, config.format(amount, DamageIndicatorConfig.Kind.HEAL));
    }

    private boolean shouldShow(Entity entity) {
        if (entity instanceof Player) {
            return config.showForPlayers();
        }
        return entity instanceof LivingEntity && config.showForMobs();
    }

    /**
     * The vanilla critical-hit condition for a melee attacker: a player who is descending (a positive fall
     * distance, which the client resets to zero the moment they land, so it already implies airborne), not
     * riding anything, and not sprinting (a sprint hit is knockback, never a crit). Read entirely from the live
     * attacker, so it stays a cheap heuristic with no NMS.
     */
    private static boolean isCriticalHit(Entity damager) {
        return damager instanceof Player attacker
                && attacker.getFallDistance() > 0.0f
                && attacker.getVehicle() == null
                && !attacker.isSprinting();
    }

    private void spawn(Entity victim, String source) {
        Component text = miniMessage.deserialize(source);
        World world = victim.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = victim.getLocation()
                .add(random.nextDouble(-0.3, 0.3), victim.getHeight() + 0.4, random.nextDouble(-0.3, 0.3));
        TextDisplay display = world.spawn(at, TextDisplay.class, spawned -> {
            spawned.text(text);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(true);
            spawned.setPersistent(false);
        });
        Position pos = BukkitRefs.toPosition(at);
        scheduler.asyncAfter(
                Duration.ofMillis(config.durationTicks() * 50L), () -> scheduler.onRegion(pos, display::remove));
    }
}
