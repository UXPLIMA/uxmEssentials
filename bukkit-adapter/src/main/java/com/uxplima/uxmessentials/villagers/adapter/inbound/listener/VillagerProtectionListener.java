package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityTransformEvent.TransformReason;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.domain.VillagerProtectionPolicy;
import com.uxplima.uxmessentials.villagers.domain.VillagerThreat;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The villager saver: cancels the deaths a protected villager would otherwise suffer and keeps it loaded so it never
 * despawns. It shields against a zombie infection (an {@link EntityTransformEvent} with reason
 * {@link TransformReason#INFECTION}), a lightning strike (its {@link DamageCause#LIGHTNING} damage and the
 * villager-to-witch transform it triggers), and every other lethal {@link EntityDamageEvent}, suffocation, fire, a
 * mob's blow, and marks the villager persistent so no far-away despawn removes it. Which of those actually cancel is
 * the pure {@link VillagerProtectionPolicy}'s call; this listener only maps each Bukkit cause onto a
 * {@link VillagerThreat} and reads the villager's per-villager protection mark.
 *
 * <h2>Folia</h2>
 * Every handler runs on the region owning the villager the event names: the damage / transform handlers read that one
 * villager's PDC and cancel the event on that same thread, and {@link #onEntityAdd} re-applies persistence to a
 * protected villager as it enters a region, a per-entity, region-local touch with no whole-world enumeration.
 */
@NullMarked
public final class VillagerProtectionListener implements Listener {

    private final VillagerProtectionPolicy policy;
    private final PdcVillagerFlags flags;

    public VillagerProtectionListener(VillagerProtectionPolicy policy, PdcVillagerFlags flags) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.flags = Objects.requireNonNull(flags, "flags");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager
                && policy.cancels(flags.isProtected(villager), threatOf(event.getCause()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        VillagerThreat threat = threatOf(event.getTransformReason());
        if (threat != null && policy.cancels(flags.isProtected(villager), threat)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            applyDespawnGuard(villager);
        }
    }

    /** Keep a protected villager loaded (never despawn) when the no-despawn gate is on; a no-op otherwise. */
    public void applyDespawnGuard(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        if (policy.protectsDespawn(flags.isProtected(villager))) {
            // The persistent flag is the despawn guard: a persistent villager is saved rather than removed and never
            // despawns when no player is nearby.
            villager.setPersistent(true);
        }
    }

    /** The damage cause mapped to a threat: lightning to its own gate, everything else to the general damage gate. */
    private static VillagerThreat threatOf(DamageCause cause) {
        return cause == DamageCause.LIGHTNING ? VillagerThreat.LIGHTNING : VillagerThreat.DAMAGE;
    }

    /** The transform reason mapped to a threat, or {@code null} for a transform we never cancel (e.g. a cure). */
    private static @Nullable VillagerThreat threatOf(TransformReason reason) {
        return switch (reason) {
            case INFECTION -> VillagerThreat.ZOMBIE_CONVERSION;
            case LIGHTNING -> VillagerThreat.LIGHTNING;
            default -> null;
        };
    }
}
