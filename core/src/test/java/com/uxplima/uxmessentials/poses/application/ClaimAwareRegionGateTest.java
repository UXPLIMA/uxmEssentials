package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.port.PoseRegionFlags;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ClaimAwareRegionGate}: the two seams gate independently, each honours its {@code respect-*} toggle, and
 * the WorldGuard seam is consulted with the pose type the caller passed (so the adapter can map SIT / PLAYER_SIT /
 * LAY|BELLYFLOP|SPIN / CRAWL to its own flags). All backends are fakes, no Bukkit, no claim plugin.
 */
class ClaimAwareRegionGateTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position WHERE = new Position(WORLD, 10.5, 64.0, 20.5, 0f, 0f);

    private final PoseRegionFlags allowAllFlags = (where, type) -> false;

    @Test
    void allowsWhenNeitherBackendObjects() {
        ClaimAwareRegionGate gate = new ClaimAwareRegionGate(new AlwaysAllowClaimService(), allowAllFlags, true, true);

        assertThat(gate.canPose(WHO, WHERE, PoseType.SIT)).isTrue();
    }

    @Test
    void deniesWhenTheClaimServiceRefusesAccess() {
        ClaimAwareRegionGate gate =
                new ClaimAwareRegionGate(denyingClaims(ClaimDecision.DENIED_FOREIGN), allowAllFlags, true, true);

        assertThat(gate.canPose(WHO, WHERE, PoseType.SIT)).isFalse();
    }

    @Test
    void ignoresTheClaimServiceWhenRespectClaimsIsOff() {
        ClaimAwareRegionGate gate =
                new ClaimAwareRegionGate(denyingClaims(ClaimDecision.DENIED_FOREIGN), allowAllFlags, false, true);

        assertThat(gate.canPose(WHO, WHERE, PoseType.SIT)).isTrue();
    }

    @Test
    void deniesWhenTheWorldGuardFlagIsSet() {
        ClaimAwareRegionGate gate = new ClaimAwareRegionGate(new AlwaysAllowClaimService(), denyAll(), true, true);

        assertThat(gate.canPose(WHO, WHERE, PoseType.LAY)).isFalse();
    }

    @Test
    void ignoresTheWorldGuardFlagWhenRespectWorldguardIsOff() {
        ClaimAwareRegionGate gate = new ClaimAwareRegionGate(new AlwaysAllowClaimService(), denyAll(), true, false);

        assertThat(gate.canPose(WHO, WHERE, PoseType.LAY)).isTrue();
    }

    @Test
    void consultsTheWorldGuardSeamWithTheCallersPoseType() {
        RecordingFlags flags = new RecordingFlags();
        ClaimAwareRegionGate gate = new ClaimAwareRegionGate(new AlwaysAllowClaimService(), flags, true, true);

        gate.canPose(WHO, WHERE, PoseType.SIT);
        gate.canPose(WHO, WHERE, PoseType.PLAYER_SIT);
        gate.canPose(WHO, WHERE, PoseType.CRAWL);

        assertThat(flags.consulted).containsExactly(PoseType.SIT, PoseType.PLAYER_SIT, PoseType.CRAWL);
    }

    private static ClaimService denyingClaims(ClaimDecision decision) {
        return new ClaimService() {
            @Override
            public ClaimDecision canPlace(PlayerRef who, Position at) {
                return decision;
            }

            @Override
            public ClaimDecision canAccess(PlayerRef who, Position at) {
                return decision;
            }
        };
    }

    private static PoseRegionFlags denyAll() {
        return (where, type) -> true;
    }

    /** Records which pose types the gate asked the WorldGuard seam about, and never denies. */
    private static final class RecordingFlags implements PoseRegionFlags {
        private final List<PoseType> consulted = new ArrayList<>();

        @Override
        public boolean deniesPose(Position where, PoseType type) {
            consulted.add(type);
            return false;
        }
    }
}
