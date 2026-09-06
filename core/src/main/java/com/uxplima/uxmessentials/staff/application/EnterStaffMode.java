package com.uxplima.uxmessentials.staff.application;

import java.util.Objects;

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
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;

/**
 * {@code /staffmode} (entering): put a staff member into staff mode. Staff mode is purely orchestration: it
 * captures the player's real loadout, hands them the gadget hotbar, and vanishes them; it never mutes, bans,
 * kicks, or warns.
 *
 * <p><b>Commit-before-swap invariant (item-loss safety).</b> The order is load-bearing and the whole reason
 * the loadout is DB-backed rather than PDC: the real loadout is captured and <i>persisted to the repository
 * first</i>, and only after that durable commit is the live inventory overwritten by the gadget hotbar. So
 * if the server crashes between the swap and the next exit, the real loadout is still recoverable from the
 * database: it was never the only copy. The sequence is exactly:
 *
 * <ol>
 *   <li>{@code capture.capture(actor)}, snapshot the real loadout (pure value);
 *   <li>{@code loadoutRepository.save(uuid, loadout)}, COMMIT it durably (before any swap);
 *   <li>{@code store.setActive(actor, modeName)}, mark the player in staff mode;
 *   <li>{@code events.publish(StaffModeEntered)}, announce the (already-durable) state change;
 *   <li>{@code capture.applyGadgetHotbar(actor, modeName)}, only now overwrite the live inventory;
 *   <li>{@code vanish.setVanished(actor, vanishOnEnter)}: vanish through the soft-coupled presence seam.
 * </ol>
 *
 * <p>Entering while already in staff mode is a no-op: no second capture, no second save, no double-swap
 * the existing committed loadout is the one true copy and must not be overwritten by the gadget hotbar.
 *
 * <p><b>Enter never overwrites an existing row (crash safety).</b> The in-memory active marker is lost on a
 * hard crash, but the loadout row survives. A player who crashed mid-mode rejoins holding the gadget hotbar
 * (autosaved) with the active marker gone; a plain {@code enter} would then capture that gadget hotbar as
 * "real" and overwrite the one true copy, permanent loss. So {@code enter} checks the repository first: when a
 * row already exists it is an un-restored loadout from an interrupted exit, and rather than capturing anything
 * it hands off to {@link RecoverStaffLoadout} to finish that exit (restore the real loadout, delete the row,
 * reveal). The capture path runs only when there is genuinely no prior row to protect.
 */
public final class EnterStaffMode {

    private final StaffModeStore store;
    private final StaffLoadoutRepository loadoutRepository;
    private final StaffLoadoutCapture capture;
    private final StaffVanish vanish;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final RecoverStaffLoadout recover;
    private final String modeName;
    private final boolean vanishOnEnter;

    public EnterStaffMode(
            StaffModeStore store,
            StaffLoadoutRepository loadoutRepository,
            StaffLoadoutCapture capture,
            StaffVanish vanish,
            Notifier notifier,
            DomainEventPublisher events,
            RecoverStaffLoadout recover,
            String modeName,
            boolean vanishOnEnter) {
        this.store = Objects.requireNonNull(store, "store");
        this.loadoutRepository = Objects.requireNonNull(loadoutRepository, "loadoutRepository");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.recover = Objects.requireNonNull(recover, "recover");
        this.modeName = Objects.requireNonNull(modeName, "modeName");
        this.vanishOnEnter = vanishOnEnter;
        if (modeName.isBlank()) {
            throw new IllegalArgumentException("modeName must not be blank");
        }
    }

    /** Put {@code actor} into staff mode, or report they are already in it. */
    public Result<Unit, Unit> enter(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        if (store.isActive(actor)) {
            // Already in staff mode: do nothing destructive: the committed loadout is the one true copy.
            notifier.send(actor, StaffMessageKey.STAFF_MODE_ALREADY);
            return Result.err(Unit.INSTANCE);
        }
        if (loadoutRepository.load(actor.uuid()).isPresent()) {
            // A row with no active marker is an un-restored loadout from an interrupted exit / crash. NEVER
            // capture over it, recover the real loadout instead, so the one true copy is never overwritten.
            recover.recover(actor);
            return Result.err(Unit.INSTANCE);
        }
        SavedLoadout loadout = capture.capture(actor);
        // COMMIT FIRST: the real loadout is durable before the live inventory is swapped away.
        loadoutRepository.save(actor.uuid(), loadout);
        store.setActive(actor, modeName);
        events.publish(new StaffModeEntered(actor));
        capture.applyGadgetHotbar(actor, modeName);
        vanish.setVanished(actor, vanishOnEnter);
        notifier.send(actor, StaffMessageKey.STAFF_MODE_ON);
        return Result.ok();
    }
}
