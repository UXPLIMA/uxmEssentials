package com.uxplima.uxmessentials.shared.application.claim;

import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * No-op {@link ClaimService} used when no claim plugin is active. Every check returns
 * {@link ClaimDecision#ALLOWED}, matching the behaviour of a {@link ClaimPolicy} backed by an
 * inactive provider.
 */
@NullMarked
public final class AlwaysAllowClaimService implements ClaimService {

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
        // No claim plugin is active, so no land is claimed: nothing to keep random-teleport out of.
        return false;
    }
}
