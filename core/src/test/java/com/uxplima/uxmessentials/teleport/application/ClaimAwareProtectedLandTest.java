package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import com.uxplima.uxmessentials.teleport.application.port.ProtectedLand;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ClaimAwareProtectedLand}: the RTP land-protection composite that keeps the shared pre-warmed pool out
 * of claimed land and WorldGuard regions. The two seams. The player-agnostic {@link ClaimService#isProtected} and
 * the WorldGuard {@link ProtectedLand}. Gate independently, each honours its {@code respect-*} toggle, and with both
 * off nothing is ever reported protected. All backends are fakes: no Bukkit, no claim plugin, no WorldGuard.
 */
class ClaimAwareProtectedLandTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position WHERE = new Position(WORLD, 100.5, 64.0, 200.5, 0f, 0f);

    private static final ProtectedLand REGION_FREE = where -> false;
    private static final ProtectedLand REGION_PROTECTED = where -> true;

    @Test
    void wildernessWithNeitherBackendObjectingIsNotProtected() {
        ClaimAwareProtectedLand land = new ClaimAwareProtectedLand(claims(false), REGION_FREE, true, true);

        assertThat(land.isProtected(WHERE)).isFalse();
    }

    @Test
    void aClaimedSpotIsProtectedWhenRespectClaimsIsOn() {
        ClaimAwareProtectedLand land = new ClaimAwareProtectedLand(claims(true), REGION_FREE, true, true);

        assertThat(land.isProtected(WHERE)).isTrue();
    }

    @Test
    void aClaimedSpotIsBypassedWhenRespectClaimsIsOff() {
        ClaimAwareProtectedLand land = new ClaimAwareProtectedLand(claims(true), REGION_FREE, false, true);

        assertThat(land.isProtected(WHERE)).isFalse();
    }

    @Test
    void aWorldGuardRegionIsProtectedWhenRespectWorldguardIsOn() {
        ClaimAwareProtectedLand land = new ClaimAwareProtectedLand(claims(false), REGION_PROTECTED, true, true);

        assertThat(land.isProtected(WHERE)).isTrue();
    }

    @Test
    void aWorldGuardRegionIsBypassedWhenRespectWorldguardIsOff() {
        ClaimAwareProtectedLand land = new ClaimAwareProtectedLand(claims(false), REGION_PROTECTED, true, false);

        assertThat(land.isProtected(WHERE)).isFalse();
    }

    @Test
    void theTwoTogglesAreIndependent() {
        // Claims on but WorldGuard off: only the claim seam contributes.
        ClaimAwareProtectedLand claimsOnly = new ClaimAwareProtectedLand(claims(true), REGION_PROTECTED, true, false);
        assertThat(claimsOnly.isProtected(WHERE)).isTrue();

        // WorldGuard on but claims off: only the region seam contributes.
        ClaimAwareProtectedLand wgOnly = new ClaimAwareProtectedLand(claims(true), REGION_FREE, false, true);
        assertThat(wgOnly.isProtected(WHERE)).isFalse();
    }

    @Test
    void withBothTogglesOffNothingIsProtected() {
        ClaimAwareProtectedLand land = new ClaimAwareProtectedLand(claims(true), REGION_PROTECTED, false, false);

        assertThat(land.isProtected(WHERE)).isFalse();
    }

    /** A claim service whose player-agnostic protection answer is fixed; the placement/access calls are unused here. */
    private static ClaimService claims(boolean protectedLand) {
        return new ClaimService() {
            @Override
            public ClaimDecision canPlace(PlayerRef who, Position at) {
                return ClaimDecision.ALLOWED;
            }

            @Override
            public ClaimDecision canAccess(PlayerRef who, Position at) {
                return ClaimDecision.ALLOWED;
            }

            @Override
            public boolean isProtected(Position at) {
                return protectedLand;
            }
        };
    }
}
