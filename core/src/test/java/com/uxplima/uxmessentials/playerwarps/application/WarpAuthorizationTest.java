package com.uxplima.uxmessentials.playerwarps.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The authority resolver: an owner is always {@link WarpRole#OWNER} without a store lookup, a member resolves to
 * their stored role, and a stranger resolves to nothing, so {@link WarpAuthorization#allows} is fail-closed.
 */
class WarpAuthorizationTest {

    private PlayerWarpTestSupport.Repo repository;
    private PlayerWarpTestSupport.Members members;
    private WarpAuthorization authorization;
    private PlayerRef owner;
    private PlayerRef coOwner;
    private PlayerRef stranger;
    private PlayerWarp warp;

    @BeforeEach
    void setUp() {
        repository = new PlayerWarpTestSupport.Repo();
        members = new PlayerWarpTestSupport.Members();
        authorization = new WarpAuthorization(members);
        owner = PlayerWarpTestSupport.ref("Owner");
        coOwner = PlayerWarpTestSupport.ref("CoOwner");
        stranger = PlayerWarpTestSupport.ref("Stranger");
        warp = repository.put(PlayerWarpTestSupport.warp(owner, "hub"));
        members.grant(warp.id().orElseThrow(), coOwner.uuid(), WarpRole.CO_OWNER);
    }

    @Test
    void theOwnerResolvesToOwnerWithoutTouchingTheMemberStore() {
        assertThat(authorization.roleFor(warp, owner.uuid())).contains(WarpRole.OWNER);
    }

    @Test
    void aMemberResolvesToTheirStoredRole() {
        assertThat(authorization.roleFor(warp, coOwner.uuid())).contains(WarpRole.CO_OWNER);
    }

    @Test
    void aStrangerResolvesToNoRole() {
        assertThat(authorization.roleFor(warp, stranger.uuid())).isEmpty();
    }

    @Test
    void allowsFollowsTheRoleCapabilityMatrix() {
        assertThat(authorization.allows(warp, owner.uuid(), WarpCapability.TRANSFER))
                .isTrue();
        assertThat(authorization.allows(warp, coOwner.uuid(), WarpCapability.EDIT_PRICE))
                .isTrue();
        assertThat(authorization.allows(warp, coOwner.uuid(), WarpCapability.TRANSFER))
                .isFalse();
    }

    @Test
    void allowsDeniesAnyActorWithNoRole() {
        assertThat(authorization.allows(warp, stranger.uuid(), WarpCapability.EDIT_METADATA))
                .isFalse();
    }
}
