package com.uxplima.uxmessentials.staff.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffModeStore;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;

/**
 * Finish an interrupted staff-mode exit by restoring an orphaned loadout row, the crash / hard-disconnect
 * recovery path that closes the last item-loss hole in staff mode.
 *
 * <p>The in-memory active marker is lost on a hard crash, but the DB-backed loadout row survives. A player who
 * crashed mid-mode therefore rejoins holding the <i>gadget hotbar</i> (autosaved), while their real loadout is
 * still safe in the row. If they were allowed to re-enter, {@code EnterStaffMode} would capture that gadget
 * hotbar as "real" and overwrite the one true copy, permanent loss. This use case prevents that by treating
 * an existing row as a pending exit and restoring it instead.
 *
 * <p>It is invoked from two places:
 *
 * <ul>
 *   <li>{@code EnterStaffMode}. When {@code /staffmode} is run while a row already exists (so enter never
 *       overwrites it);
 *   <li>the join listener. On connect, when a row exists but the player has no active marker (an orphaned row
 *       from a crash), so the real loadout is back before the player can touch {@code /staffmode}.
 * </ul>
 *
 * <p>The sequence mirrors a normal exit and is keyed on the restore actually reaching an online player: load →
 * restore (reporting reach) → on a confirmed restore delete the row and restore the pre-mode vanish state and
 * notify {@link StaffMessageKey#STAFF_MODE_RECOVERED}. The in-memory marker is cleared either way (a recovery
 * always ends any lingering session). A restore that did not reach the player keeps the row for the next
 * attempt: the item-loss-safe net holds.
 */
public final class RecoverStaffLoadout {

    private final StaffModeStore store;
    private final StaffLoadoutRepository loadoutRepository;
    private final StaffLoadoutCapture capture;
    private final StaffVanish vanish;
    private final Notifier notifier;

    public RecoverStaffLoadout(
            StaffModeStore store,
            StaffLoadoutRepository loadoutRepository,
            StaffLoadoutCapture capture,
            StaffVanish vanish,
            Notifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.loadoutRepository = Objects.requireNonNull(loadoutRepository, "loadoutRepository");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Restore {@code actor}'s orphaned loadout row if one is held, returning ok when a recovery ran and err when
     * there was nothing to recover.
     */
    public Result<Unit, Unit> recover(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        Optional<SavedLoadout> stored = loadoutRepository.load(actor.uuid());
        if (stored.isEmpty()) {
            return Result.err(Unit.INSTANCE);
        }
        SavedLoadout loadout = stored.get();
        boolean restored = capture.restore(actor, loadout);
        if (restored) {
            loadoutRepository.delete(actor.uuid());
        }
        store.clear(actor);
        vanish.setVanished(actor, loadout.vanishedBefore());
        if (restored) {
            notifier.send(actor, StaffMessageKey.STAFF_MODE_RECOVERED);
        }
        return Result.ok();
    }
}
