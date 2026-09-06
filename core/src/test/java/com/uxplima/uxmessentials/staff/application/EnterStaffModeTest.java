package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import org.junit.jupiter.api.Test;

class EnterStaffModeTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Mod");

    private EnterStaffMode enterMode(StaffTestFakes fakes, boolean vanishOnEnter) {
        RecoverStaffLoadout recover =
                new RecoverStaffLoadout(fakes.store, fakes.repository, fakes.capture, fakes.vanish, fakes.notifier());
        return new EnterStaffMode(
                fakes.store,
                fakes.repository,
                fakes.capture,
                fakes.vanish,
                fakes.notifier(),
                fakes.events,
                recover,
                "default",
                vanishOnEnter);
    }

    @Test
    void persistsTheLoadoutBeforeSettingActiveAndBeforeApplyingTheGadgetHotbar() {
        StaffTestFakes fakes = new StaffTestFakes();

        var result = enterMode(fakes, true).enter(ACTOR);

        assertThat(result.isOk()).isTrue();
        // The leading load is the enter-never-overwrites guard (no prior row), then the commit-before-swap
        // invariant: capture → save (durable) → set-active → publish → apply-hotbar.
        assertThat(fakes.calls)
                .containsExactly(
                        "load",
                        "capture",
                        "save",
                        "setActive",
                        "publish:StaffModeEntered",
                        "applyGadgetHotbar",
                        "vanish:true");
        // The loadout is the captured one, and it was saved before the hotbar swap.
        assertThat(fakes.repository.rows.get(ACTOR.uuid())).isEqualTo(fakes.capture.toCapture);
        assertThat(fakes.calls.indexOf("save")).isLessThan(fakes.calls.indexOf("applyGadgetHotbar"));
    }

    @Test
    void publishesTheEnteredEventAndNotifiesTheActor() {
        StaffTestFakes fakes = new StaffTestFakes();

        enterMode(fakes, false).enter(ACTOR);

        assertThat(fakes.events.published).containsExactly(new StaffModeEntered(ACTOR));
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_ON);
        // vanish-on-enter false still calls the seam, with false.
        assertThat(fakes.vanish.states).containsExactly(false);
    }

    @Test
    void alreadyInStaffModeIsANoOpWithNoSecondCaptureOrSave() {
        StaffTestFakes fakes = new StaffTestFakes();
        fakes.store.active.put(ACTOR.uuid(), "default");

        var result = enterMode(fakes, true).enter(ACTOR);

        assertThat(result.isErr()).isTrue();
        // No capture, no save, no swap: the committed loadout is the one true copy.
        assertThat(fakes.calls).isEmpty();
        assertThat(fakes.repository.rows).doesNotContainKey(ACTOR.uuid());
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_ALREADY);
    }

    @Test
    void enterWithAPreExistingRowRecoversItRatherThanOverwriting() {
        // The crash signature: a loadout row survives but the in-memory active marker is gone. Entering must NOT
        // capture the (gadget-hotbar) live inventory over the one true copy: it must recover the existing row.
        StaffTestFakes fakes = new StaffTestFakes();
        SavedLoadout real = StaffTestFakes.sampleLoadout();
        fakes.repository.rows.put(ACTOR.uuid(), real);

        var result = enterMode(fakes, true).enter(ACTOR);

        assertThat(result.isErr()).isTrue();
        // It recovered (the guard load, then recovery's load → restore → delete → clear → vanish) and never
        // captured or saved over the row.
        assertThat(fakes.calls).doesNotContain("capture", "save", "applyGadgetHotbar");
        assertThat(fakes.calls).containsExactly("load", "load", "restore", "delete", "clear", "vanish:false");
        assertThat(fakes.capture.restored).containsExactly(real);
        assertThat(fakes.repository.rows).doesNotContainKey(ACTOR.uuid());
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_RECOVERED);
    }
}
