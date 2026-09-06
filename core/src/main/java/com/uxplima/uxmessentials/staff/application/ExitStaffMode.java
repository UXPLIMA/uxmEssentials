package com.uxplima.uxmessentials.staff.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffModeStore;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;

/**
 * {@code /staffmode} (leaving): take a staff member out of staff mode and put their real loadout back.
 *
 * <p><b>Restore-then-delete invariant (item-loss safety).</b> The committed loadout row is the one true copy
 * of the player's real inventory while they are in staff mode, so it is loaded and restored onto the player,
 * and the database row is dropped <i>only once the items are confirmed safely back on the player</i>. The
 * restore reports whether it actually reached an online player; the delete is keyed on that confirmation so a
 * disconnect race (the player gone between the schedule and the restore) never drops the row over an inventory
 * that was never written back. The sequence is:
 *
 * <ol>
 *   <li>{@code loadoutRepository.load(uuid)} → {@code capture.restore(actor, loadout)}, put the real loadout
 *       back, reporting whether it reached an online player;
 *   <li>on a confirmed restore: {@code loadoutRepository.delete(uuid)}. Now drop the stored copy;
 *   <li>{@code store.clear(actor)}. Clear the in-memory staff-mode marker (always, since the session is over);
 *   <li>{@code vanish.setVanished(actor, vanishedBefore)}, restore the pre-mode vanish state through the
 *       presence seam (a player vanished before entering stays vanished; one who was visible is revealed);
 *   <li>{@code events.publish(StaffModeExited)}: announce the restored state.
 * </ol>
 *
 * <p>When the restore did not reach the player (offline at restore), the row is <i>kept</i>, not deleted, the
 * item-loss-safe net, so {@code RecoverStaffLoadout} finishes the interrupted exit on the player's next join.
 * The in-memory marker is still cleared (the session has ended), which is exactly what arms that join-recovery
 * path: an orphaned row with no active marker.
 *
 * <p>Leaving while not in staff mode is a no-op: nothing to restore, nothing to delete.
 */
public final class ExitStaffMode {

    private final StaffModeStore store;
    private final StaffLoadoutRepository loadoutRepository;
    private final StaffLoadoutCapture capture;
    private final StaffVanish vanish;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public ExitStaffMode(
            StaffModeStore store,
            StaffLoadoutRepository loadoutRepository,
            StaffLoadoutCapture capture,
            StaffVanish vanish,
            Notifier notifier,
            DomainEventPublisher events) {
        this.store = Objects.requireNonNull(store, "store");
        this.loadoutRepository = Objects.requireNonNull(loadoutRepository, "loadoutRepository");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Take {@code actor} out of staff mode and restore their real loadout. */
    public Result<Unit, Unit> exit(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        if (!store.isActive(actor)) {
            // Not in staff mode: nothing to restore, nothing to delete.
            return Result.err(Unit.INSTANCE);
        }
        Optional<SavedLoadout> stored = loadoutRepository.load(actor.uuid());
        // RESTORE FIRST: put the real loadout back, then delete the durable copy ONLY when the restore reached
        // an online player. An offline restore (a disconnect race) keeps the row for the join-recovery path.
        boolean restored =
                stored.map(loadout -> capture.restore(actor, loadout)).orElse(true);
        if (restored) {
            loadoutRepository.delete(actor.uuid());
        }
        store.clear(actor);
        vanish.setVanished(actor, stored.map(SavedLoadout::vanishedBefore).orElse(false));
        events.publish(new StaffModeExited(actor));
        notifier.send(actor, StaffMessageKey.STAFF_MODE_OFF);
        return Result.ok();
    }
}
