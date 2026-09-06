package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * Application-layer port for claim-based placement and access checks. The homes context asks this
 * service before setting or teleporting to a home; the adapter wires it to a {@link ClaimPolicy}
 * backed by whatever claim plugin is active, or to {@link
 * com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService} when none is.
 */
@NullMarked
public interface ClaimService {

    /**
     * Returns whether {@code who} may place a home at {@code at}. The decision reflects the
     * operator-configured claim policy (require-claim, block-foreign-claims, proximity distance).
     */
    ClaimDecision canPlace(PlayerRef who, Position at);

    /**
     * Returns whether {@code who} may teleport to {@code at}. The decision reflects whether the
     * player still has access to the claim covering that position.
     */
    ClaimDecision canAccess(PlayerRef who, Position at);

    /**
     * Returns whether {@code at} falls inside <em>any</em> claim, regardless of owner or trust. Unlike {@link
     * #canPlace} and {@link #canAccess}, this asks no "may this player…" question and consults none of the
     * placement knobs. It is the player-agnostic "is this land claimed" check that random-teleport uses to keep
     * its shared, no-owner pre-warmed pool out of claimed land (wilderness only). A service with no claim plugin
     * behind it reports {@code false}, nothing is protected, which is why the default is {@code false} and the
     * real, provider-backed implementation overrides it.
     */
    default boolean isProtected(Position at) {
        return false;
    }
}
