package com.uxplima.uxmessentials.teleport.domain;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Which party moves when a teleport request is accepted.
 *
 * <p>{@code /tpa} asks "let me come to you": the <em>requester</em> moves to the target on accept.
 * {@code /tpahere} asks "come to me": the <em>target</em> moves to the requester. The direction is
 * fixed when the request is created and decides whose position the destination follows and whose
 * warmup/cooldown tier gates the hop ({@link #mover} always names the player who actually teleports).
 */
public enum RequestDirection {

    /** {@code /tpa}: the requester teleports to the target. */
    TO_TARGET,

    /** {@code /tpahere}: the target teleports to the requester. */
    TO_REQUESTER;

    /** The player who actually teleports when this request is accepted. */
    public PlayerRef mover(PlayerRef requester, PlayerRef target) {
        return this == TO_TARGET ? requester : target;
    }

    /** The player the {@link #mover(PlayerRef, PlayerRef)} lands at. */
    public PlayerRef anchor(PlayerRef requester, PlayerRef target) {
        return this == TO_TARGET ? target : requester;
    }
}
