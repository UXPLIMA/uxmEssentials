package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link PoseSessions} registry: the single source of truth for who is posing. Covers put / get / remove /
 * isPosing and the one-session-per-player invariant (starting a second pose replaces the first, so a player is never
 * in two poses at once), plus the clear-on-stop behaviour a disable/reload relies on to leave no residual state.
 */
class PoseSessionsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    @Test
    void startRecordsASessionAndIsPosingSeesIt() {
        PoseSessions sessions = new PoseSessions();
        PlayerRef alice = ref("Alice");

        assertThat(sessions.isPosing(alice)).isFalse();
        assertThat(sessions.start(session(alice, PoseType.SIT))).isEmpty();

        assertThat(sessions.isPosing(alice)).isTrue();
        assertThat(sessions.current(alice)).map(PoseSession::type).contains(PoseType.SIT);
        assertThat(sessions.size()).isEqualTo(1);
    }

    @Test
    void startingASecondPoseReplacesTheFirst() {
        PoseSessions sessions = new PoseSessions();
        PlayerRef alice = ref("Alice");
        sessions.start(session(alice, PoseType.SIT));

        // The one-session-per-player invariant: the second start returns the replaced session and leaves exactly one.
        assertThat(sessions.start(session(alice, PoseType.LAY)))
                .map(PoseSession::type)
                .contains(PoseType.SIT);
        assertThat(sessions.current(alice)).map(PoseSession::type).contains(PoseType.LAY);
        assertThat(sessions.size()).isEqualTo(1);
    }

    @Test
    void stopRemovesTheSessionAndReturnsIt() {
        PoseSessions sessions = new PoseSessions();
        PlayerRef alice = ref("Alice");
        sessions.start(session(alice, PoseType.SIT));

        assertThat(sessions.stop(alice)).map(PoseSession::type).contains(PoseType.SIT);
        assertThat(sessions.isPosing(alice)).isFalse();
        assertThat(sessions.stop(alice)).isEmpty();
    }

    @Test
    void sessionsAreKeyedPerPlayer() {
        PoseSessions sessions = new PoseSessions();
        PlayerRef alice = ref("Alice");
        PlayerRef bob = ref("Bob");
        sessions.start(session(alice, PoseType.SIT));
        sessions.start(session(bob, PoseType.SPIN));

        assertThat(sessions.current(alice)).map(PoseSession::type).contains(PoseType.SIT);
        assertThat(sessions.current(bob)).map(PoseSession::type).contains(PoseType.SPIN);
        assertThat(sessions.stop(alice)).isPresent();
        assertThat(sessions.isPosing(bob)).isTrue();
    }

    @Test
    void clearDropsEverySession() {
        PoseSessions sessions = new PoseSessions();
        sessions.start(session(ref("Alice"), PoseType.SIT));
        sessions.start(session(ref("Bob"), PoseType.LAY));

        sessions.clear();

        assertThat(sessions.size()).isZero();
    }

    private static PoseSession session(PlayerRef subject, PoseType type) {
        Position at = Position.of(WORLD, 0, 64, 0);
        return new PoseSession(subject, type, at, "seat-" + subject.name(), null, Instant.EPOCH);
    }

    private static PlayerRef ref(String name) {
        return new PlayerRef(UUID.randomUUID(), name);
    }
}
