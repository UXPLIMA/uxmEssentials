package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;

/**
 * Resolves an actor's authority over a warp and answers the one question every management use case asks: may this
 * actor perform this capability? Ownership is the aggregate's own owner field. The owner always resolves to
 * {@link WarpRole#OWNER} without a store round-trip, and a non-owner's role is read from the {@link WarpMemberStore}
 * (co-owner or manager). A stranger resolves to no role at all, so {@link #allows} denies them by default: the gate
 * is fail-closed.
 *
 * <p>Keeping the owner→OWNER and member→role mapping here, rather than in each use case, means the capability
 * policy ({@link WarpRole#can}) is consulted through exactly one path and a use case never re-implements "is this
 * the owner?" by hand.
 */
public final class WarpAuthorization {

    private final WarpMemberStore members;

    public WarpAuthorization(WarpMemberStore members) {
        this.members = Objects.requireNonNull(members, "members");
    }

    /**
     * The role {@code actor} holds on {@code warp}: {@link WarpRole#OWNER} when they own it, otherwise their
     * membership role if any, otherwise empty. A warp with no surrogate id yet (never saved) can only be matched
     * by ownership, there are no member rows to key on, so a non-owner resolves to empty there.
     */
    public Optional<WarpRole> roleFor(PlayerWarp warp, UUID actor) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(actor, "actor");
        if (warp.owner().uuid().equals(actor)) {
            return Optional.of(WarpRole.OWNER);
        }
        return warp.id().flatMap(id -> members.roleOf(id, actor));
    }

    /** Whether {@code actor} may perform {@code capability} on {@code warp}; denies any actor with no role. */
    public boolean allows(PlayerWarp warp, UUID actor, WarpCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return roleFor(warp, actor).map(role -> role.can(capability)).orElse(false);
    }
}
