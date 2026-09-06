package com.uxplima.uxmessentials.poses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link PoseSession} structural invariants: the required fields are non-null, and a {@code target} is
 * carried by, and only by, a {@link PoseType#PLAYER_SIT} session (sitting on another player is the one pose with a
 * second party).
 */
class PoseSessionTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 0, 64, 0);

    @Test
    void aSitSessionHoldsItsFields() {
        PlayerRef alice = ref("Alice");
        PoseSession session = new PoseSession(alice, PoseType.SIT, AT, "seat-1", null, Instant.EPOCH);

        assertThat(session.subject()).isEqualTo(alice);
        assertThat(session.type()).isEqualTo(PoseType.SIT);
        assertThat(session.target()).isNull();
    }

    @Test
    void aPlayerSitSessionRequiresATarget() {
        PlayerRef alice = ref("Alice");

        assertThatThrownBy(() -> new PoseSession(alice, PoseType.PLAYER_SIT, AT, "seat-1", null, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLAYER_SIT");
    }

    @Test
    void onlyAPlayerSitSessionMayCarryATarget() {
        PlayerRef alice = ref("Alice");
        PlayerRef bob = ref("Bob");

        assertThatThrownBy(() -> new PoseSession(alice, PoseType.SIT, AT, "seat-1", bob, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLAYER_SIT");
    }

    @Test
    void aPlayerSitSessionCarriesItsCarrier() {
        PlayerRef alice = ref("Alice");
        PlayerRef bob = ref("Bob");
        PoseSession session = new PoseSession(alice, PoseType.PLAYER_SIT, AT, "seat-1", bob, Instant.EPOCH);

        assertThat(session.target()).isEqualTo(bob);
    }

    private static PlayerRef ref(String name) {
        return new PlayerRef(UUID.randomUUID(), name);
    }
}
