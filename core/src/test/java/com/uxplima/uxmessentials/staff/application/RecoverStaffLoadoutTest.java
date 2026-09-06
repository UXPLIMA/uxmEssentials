package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.junit.jupiter.api.Test;

class RecoverStaffLoadoutTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Mod");

    private RecoverStaffLoadout recovery(StaffTestFakes fakes) {
        return new RecoverStaffLoadout(fakes.store, fakes.repository, fakes.capture, fakes.vanish, fakes.notifier());
    }

    @Test
    void orphanedRowIsRestoredThenDeletedThenTheMarkerCleared() {
        StaffTestFakes fakes = new StaffTestFakes();
        SavedLoadout real = StaffTestFakes.sampleLoadout();
        fakes.repository.rows.put(ACTOR.uuid(), real);

        var result = recovery(fakes).recover(ACTOR);

        assertThat(result.isOk()).isTrue();
        // Restore-then-delete, exactly the interrupted exit finished: load → restore → delete → clear → vanish.
        assertThat(fakes.calls).containsExactly("load", "restore", "delete", "clear", "vanish:false");
        assertThat(fakes.capture.restored).containsExactly(real);
        assertThat(fakes.repository.rows).doesNotContainKey(ACTOR.uuid());
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_RECOVERED);
    }

    @Test
    void nothingToRecoverIsANoOp() {
        StaffTestFakes fakes = new StaffTestFakes();

        var result = recovery(fakes).recover(ACTOR);

        assertThat(result.isErr()).isTrue();
        assertThat(fakes.calls).containsExactly("load");
        assertThat(fakes.sentKeys).isEmpty();
    }

    @Test
    void restoresThePreModeVanishStateRatherThanRevealing() {
        StaffTestFakes fakes = new StaffTestFakes();
        // The orphaned row recorded the player was vanished before entering, so recovery re-vanishes them.
        fakes.repository.rows.put(ACTOR.uuid(), loadoutVanishedBefore(true));

        recovery(fakes).recover(ACTOR);

        assertThat(fakes.vanish.states).containsExactly(true);
    }

    @Test
    void anOfflineRestoreKeepsTheRowForTheNextAttempt() {
        StaffTestFakes fakes = new StaffTestFakes();
        fakes.repository.rows.put(ACTOR.uuid(), StaffTestFakes.sampleLoadout());
        fakes.capture.restoreReachesPlayer = false; // a disconnect race. The restore reached no online player

        recovery(fakes).recover(ACTOR);

        // The row is kept (no delete), the marker is still cleared, and no "recovered" feedback is sent.
        assertThat(fakes.calls).containsExactly("load", "restore", "clear", "vanish:false");
        assertThat(fakes.repository.rows).containsKey(ACTOR.uuid());
        assertThat(fakes.sentKeys).isEmpty();
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
