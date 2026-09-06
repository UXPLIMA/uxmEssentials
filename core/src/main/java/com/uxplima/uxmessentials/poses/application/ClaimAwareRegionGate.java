package com.uxplima.uxmessentials.poses.application;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.PoseRegionFlags;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The real {@link PoseRegionGate} wired from Phase 5: a pose is allowed unless a land claim or a WorldGuard region
 * flag forbids it here. It composes two seams. The shared {@link ClaimService} the homes and warps contexts already
 * consult, and the poses {@link PoseRegionFlags} WorldGuard seam. Behind the two {@code respect-*} config toggles,
 * so it stays a thin, Bukkit-free decision the adapter feeds real backends into.
 *
 * <p>Claims use {@link ClaimService#canAccess canAccess} rather than {@code canPlace}: sitting is being present at a
 * spot, so the question is "may this player act in the claim covering it", not "may they build a home here", the
 * latter would also fire the require-a-claim and proximity rules and wrongly forbid sitting out in the wilderness.
 * A non-{@code ALLOWED} decision denies the pose. Each toggle is honoured independently: with {@code respect-claims}
 * off the claim seam is skipped even when a real claim service is wired, and likewise for {@code respect-worldguard}.
 * When both are off, or neither backend objects, the pose is allowed, the graceful default a server with no region
 * plugin also lands on.
 */
public final class ClaimAwareRegionGate implements PoseRegionGate {

    private final ClaimService claims;
    private final PoseRegionFlags flags;
    private final boolean respectClaims;
    private final boolean respectWorldguard;

    public ClaimAwareRegionGate(
            ClaimService claims, PoseRegionFlags flags, boolean respectClaims, boolean respectWorldguard) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.respectClaims = respectClaims;
        this.respectWorldguard = respectWorldguard;
    }

    @Override
    public boolean canPose(PlayerRef who, Position where, PoseType type) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(type, "type");
        if (respectClaims && !claims.canAccess(who, where).allowed()) {
            return false;
        }
        if (respectWorldguard && flags.deniesPose(where, type)) {
            return false;
        }
        return true;
    }
}
