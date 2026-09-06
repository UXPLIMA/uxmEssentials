package com.uxplima.uxmessentials.poses.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.ClaimAwareRegionGate;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the WorldGuard side of the poses region gate on a server with <em>no</em> WorldGuard: the
 * present-guard short-circuits, so {@link WorldGuardPoseFlags} loads none of the {@code com.sk89q} classes and
 * reports "not denied", and the composed {@link ClaimAwareRegionGate} allows the pose (the graceful default). The
 * claim seam and the two {@code respect-*} toggles are exercised over the same absent-WorldGuard flag seam, and the
 * static {@link WorldGuardPoseFlags#flagName} mapping is pinned per pose type.
 */
class WorldGuardPoseFlagsTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");

    private ServerMock server;
    private Position where;
    private WorldGuardPoseFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("world");
        where = Position.of(new WorldRef(world.getUID(), world.getName()), 10.5, 64.0, 20.5);
        flags = new WorldGuardPoseFlags(server, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mapsEachPoseTypeToItsCustomFlag() {
        assertThat(WorldGuardPoseFlags.flagName(PoseType.SIT)).isEqualTo("sit");
        assertThat(WorldGuardPoseFlags.flagName(PoseType.PLAYER_SIT)).isEqualTo("playersit");
        assertThat(WorldGuardPoseFlags.flagName(PoseType.LAY)).isEqualTo("pose");
        assertThat(WorldGuardPoseFlags.flagName(PoseType.BELLYFLOP)).isEqualTo("pose");
        assertThat(WorldGuardPoseFlags.flagName(PoseType.SPIN)).isEqualTo("pose");
        assertThat(WorldGuardPoseFlags.flagName(PoseType.CRAWL)).isEqualTo("crawl");
    }

    @Test
    void reportsNotDeniedWhenWorldGuardIsAbsent() {
        for (PoseType type : PoseType.values()) {
            assertThat(flags.deniesPose(where, type)).isFalse();
        }
    }

    @Test
    void gateAllowsWhenNoClaimPluginAndNoWorldGuardArePresent() {
        PoseRegionGate gate = new ClaimAwareRegionGate(new AlwaysAllowClaimService(), flags, true, true);

        assertThat(gate.canPose(WHO, where, PoseType.SIT)).isTrue();
    }

    @Test
    void gateDeniesWhenTheClaimServiceDenies() {
        PoseRegionGate gate = new ClaimAwareRegionGate(denying(), flags, true, true);

        assertThat(gate.canPose(WHO, where, PoseType.SIT)).isFalse();
    }

    @Test
    void gateBypassesTheClaimServiceWhenRespectClaimsIsOff() {
        PoseRegionGate gate = new ClaimAwareRegionGate(denying(), flags, false, true);

        assertThat(gate.canPose(WHO, where, PoseType.SIT)).isTrue();
    }

    private static ClaimService denying() {
        return new ClaimService() {
            @Override
            public ClaimDecision canPlace(PlayerRef who, Position at) {
                return ClaimDecision.DENIED_FOREIGN;
            }

            @Override
            public ClaimDecision canAccess(PlayerRef who, Position at) {
                return ClaimDecision.DENIED_ACCESS;
            }
        };
    }

    /** A logger that swallows everything: the absent-WorldGuard path emits nothing anyway. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
