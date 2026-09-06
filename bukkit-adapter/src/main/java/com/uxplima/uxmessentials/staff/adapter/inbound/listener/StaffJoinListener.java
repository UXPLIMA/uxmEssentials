package com.uxplima.uxmessentials.staff.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import org.jspecify.annotations.NullMarked;

/**
 * The crash-recovery connection listener: it finishes an interrupted staff-mode exit on the player's next join.
 *
 * <p>A hard crash loses the in-memory active marker but not the DB-backed loadout row, so a player who crashed
 * mid-mode rejoins holding the autosaved gadget hotbar while their real loadout still sits safe in the row. On
 * join, when a row exists for the player but they hold no active marker, the signature of an orphaned row, this
 * hands off to {@link com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout} to restore the real
 * loadout, delete the row, and restore the pre-mode vanish state. It runs on the player's entity region thread
 * (the recovery touches the live inventory), and BEFORE the player can reach {@code /staffmode}, so enter never
 * sees the gadget hotbar as their real inventory.
 *
 * <p>The cheap repository pre-check keeps the common case (no orphaned row) off the entity-thread hop entirely;
 * only a join that actually has a row to recover schedules any work.
 */
@NullMarked
public final class StaffJoinListener implements Listener {

    private final StaffServices services;
    private final StaffLoadoutRepository repository;
    private final Scheduler scheduler;

    public StaffJoinListener(StaffServices services, StaffLoadoutRepository repository, Scheduler scheduler) {
        this.services = Objects.requireNonNull(services, "services");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        if (services.store().isActive(who) || repository.load(who.uuid()).isEmpty()) {
            // Either a live session this listener must not disturb, or no orphaned row: nothing to recover.
            return;
        }
        // An orphaned row from a crash / interrupted exit: finish that exit on the entity thread, before the
        // player can run /staffmode and have enter mistake the gadget hotbar for their real inventory.
        scheduler.onEntity(who, () -> services.recover().recover(who));
    }
}
