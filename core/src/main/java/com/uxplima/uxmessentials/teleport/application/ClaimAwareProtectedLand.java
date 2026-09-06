package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.port.ProtectedLand;

/**
 * The real {@link ProtectedLand} for random-teleport: a spot is protected when a land claim covers it, or when a
 * WorldGuard region does. It composes two seams. The shared {@link ClaimService} the homes and warps contexts
 * already consult (asked here through its player-agnostic {@link ClaimService#isProtected}, since a shared pool has
 * no owner to run an access check for) and the reflective WorldGuard region {@link ProtectedLand}, behind the two
 * {@code respect-*} config toggles, so it stays a thin, Bukkit-free decision the adapter feeds real backends into.
 * This mirrors the poses context's {@code ClaimAwareRegionGate}.
 *
 * <p>Each toggle is honoured independently: with {@code respect-claims} off the claim seam is skipped even when a
 * real claim service is wired, and likewise for {@code respect-worldguard}. With both off, or when neither backend
 * objects: the spot is treated as unprotected, the graceful default a server with no region plugin also lands on.
 * The claim check runs first because it is the cheaper, in-memory lookup and short-circuits the region query.
 */
public final class ClaimAwareProtectedLand implements ProtectedLand {

    private final ClaimService claims;
    private final ProtectedLand regions;
    private final boolean respectClaims;
    private final boolean respectWorldguard;

    public ClaimAwareProtectedLand(
            ClaimService claims, ProtectedLand regions, boolean respectClaims, boolean respectWorldguard) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.respectClaims = respectClaims;
        this.respectWorldguard = respectWorldguard;
    }

    @Override
    public boolean isProtected(Position where) {
        Objects.requireNonNull(where, "where");
        if (respectClaims && claims.isProtected(where)) {
            return true;
        }
        return respectWorldguard && regions.isProtected(where);
    }
}
