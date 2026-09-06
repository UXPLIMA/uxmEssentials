package com.uxplima.uxmessentials.poses.application;

import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The permissive {@link PoseRegionGate}: posing is allowed everywhere. Production wires the claim- and
 * WorldGuard-aware {@link ClaimAwareRegionGate}; this remains the "nothing forbids a pose" gate, the behaviour a
 * server with no region plugin lands on, and the fixture the use-case tests gate with when region checks are not
 * under test.
 */
public final class AllowAllRegionGate implements PoseRegionGate {

    @Override
    public boolean canPose(PlayerRef who, Position where, PoseType type) {
        return true;
    }
}
