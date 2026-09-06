package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import org.junit.jupiter.api.Test;

class ExitStaffModeTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Mod");

    private ExitStaffMode exitMode(StaffTestFakes fakes) {
        return new ExitStaffMode(
                fakes.store, fakes.repository, fakes.capture, fakes.vanish, fakes.notifier(), fakes.events);
    }

    @Test
    void loadsRestoresDeletesAndClearsInThatOrder() {
        StaffTestFakes fakes = new StaffTestFakes();
        SavedLoadout stored = StaffTestFakes.sampleLoadout();
        fakes.store.active.put(ACTOR.uuid(), "default");
        fakes.repository.rows.put(ACTOR.uuid(), stored);

        var result = exitMode(fakes).exit(ACTOR);

        assertThat(result.isOk()).isTrue();
        // Restore-then-delete: the real loadout is back before the durable copy is dropped.
        assertThat(fakes.calls)
                .containsExactly("load", "restore", "delete", "clear", "vanish:false", "publish:StaffModeExited");
        assertThat(fakes.capture.restored).containsExactly(stored);
        assertThat(fakes.repository.rows).doesNotContainKey(ACTOR.uuid());
        assertThat(fakes.store.isActive(ACTOR)).isFalse();
        assertThat(fakes.events.published).containsExactly(new StaffModeExited(ACTOR));
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_OFF);
    }

    @Test
    void notInStaffModeIsANoOp() {
        StaffTestFakes fakes = new StaffTestFakes();

        var result = exitMode(fakes).exit(ACTOR);

        assertThat(result.isErr()).isTrue();
        assertThat(fakes.calls).isEmpty();
        assertThat(fakes.events.published).isEmpty();
        assertThat(fakes.sentKeys).isEmpty();
    }

    @Test
    void offlineRestoreDoesNotDeleteTheRow() {
        // A disconnect race: the player is gone by the time the restore runs, so it reaches no one and reports
        // false. The row must NOT be deleted, it is the only copy of the real loadout, but the marker is cleared
        // so the join-recovery path re-arms.
        StaffTestFakes fakes = new StaffTestFakes();
        fakes.store.active.put(ACTOR.uuid(), "default");
        fakes.repository.rows.put(ACTOR.uuid(), StaffTestFakes.sampleLoadout());
        fakes.capture.restoreReachesPlayer = false;

        var result = exitMode(fakes).exit(ACTOR);

        assertThat(result.isOk()).isTrue();
        assertThat(fakes.calls).doesNotContain("delete");
        assertThat(fakes.repository.rows).containsKey(ACTOR.uuid());
        assertThat(fakes.store.isActive(ACTOR)).isFalse();
    }

    @Test
    void restoresThePreModeVanishStateRatherThanRevealing() {
        // A player vanished via /vanish BEFORE entering stays vanished on exit, exit sets vanishedBefore, not false.
        StaffTestFakes fakes = new StaffTestFakes();
        fakes.store.active.put(ACTOR.uuid(), "default");
        fakes.repository.rows.put(ACTOR.uuid(), loadoutVanishedBefore(true));

        exitMode(fakes).exit(ACTOR);

        assertThat(fakes.vanish.states).containsExactly(true);
    }

    private static SavedLoadout loadoutVanishedBefore(boolean vanished) {
        return new SavedLoadout(
                LoadoutBlob.of(new byte[] {1}),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0f,
                "SURVIVAL",
                false,
                false,
                LoadoutBlob.empty(),
                vanished);
    }
}
