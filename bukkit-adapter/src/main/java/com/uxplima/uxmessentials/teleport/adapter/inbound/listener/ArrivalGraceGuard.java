package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.domain.ArrivalGraceSettings;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ArrivalGrace} implementation and the fall-damage guard behind it. When an {@code /rtp} lands, it
 * shields the player for the configured window so they do not die before the far terrain finishes rendering:
 * a Resistance and/or Slow-Falling potion effect for the duration, and, while the window is open, a
 * no-fall-damage guard that cancels the fall {@link EntityDamageEvent}. All three are individually toggled by
 * {@link ArrivalGraceSettings}; a disabled or zero-length grace is a silent no-op.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code shieldedUntil} is a {@link ConcurrentHashMap} keyed by
 * player uuid holding the epoch-millis the window closes; the potion application is hopped to the player's
 * region thread through the {@link Scheduler} port, and the damage listener (region thread) reads the map and
 * lazily evicts an expired entry, so the window opens and closes without a scheduled cleanup task racing the
 * event.
 */
@NullMarked
public final class ArrivalGraceGuard implements ArrivalGrace, Listener {

    // Resistance V is near-immunity to the incidental damage a fresh drop into far terrain risks; Slow-Falling
    // cushions the descent while the ground streams in. The no-fall-damage guard covers the landing itself.
    private static final int RESISTANCE_AMPLIFIER = 4;
    private static final int SLOW_FALLING_AMPLIFIER = 0;

    private final Server server;
    private final Scheduler scheduler;
    private final Supplier<ArrivalGraceSettings> settings;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Long> shieldedUntil = new ConcurrentHashMap<>();

    public ArrivalGraceGuard(Server server, Scheduler scheduler, Supplier<ArrivalGraceSettings> settings, Clock clock) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void applyOnArrival(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        ArrivalGraceSettings grace = settings.get();
        if (!grace.enabled()) {
            return;
        }
        scheduler.onEntity(who, () -> shield(who.uuid(), grace));
    }

    private void shield(UUID who, ArrivalGraceSettings grace) {
        Player player = server.getPlayer(who);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (grace.resistance()) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.RESISTANCE, grace.durationTicks(), RESISTANCE_AMPLIFIER));
        }
        if (grace.slowFalling()) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW_FALLING, grace.durationTicks(), SLOW_FALLING_AMPLIFIER));
        }
        if (grace.blockFallDamage()) {
            shieldedUntil.put(who, clock.millis() + grace.seconds() * 1000L);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.FALL) {
            return;
        }
        if (event.getEntity() instanceof Player player && shieldsFallDamage(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Whether {@code who}'s no-fall-damage window is still open, lazily evicting it once it has closed. */
    public boolean shieldsFallDamage(UUID who) {
        Long until = shieldedUntil.get(who);
        if (until == null) {
            return false;
        }
        if (clock.millis() >= until) {
            shieldedUntil.remove(who);
            return false;
        }
        return true;
    }

    /** Drop every open grace window on module stop. */
    public void clear() {
        shieldedUntil.clear();
    }
}
