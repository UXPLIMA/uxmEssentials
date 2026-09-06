package com.uxplima.uxmessentials.poses.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.domain.PoseSession;

/**
 * A player's pose ended. By their own command, or because they moved, took damage, teleported, dismounted, or
 * quit. Carries the {@link PoseSession} that was active so a consumer can restore state (return-to-start, clear a
 * placeholder) from the same snapshot the pose started with.
 *
 * @param session the pose that just ended
 */
public record PoseEnded(PoseSession session) implements PoseEvent {

    public PoseEnded {
        Objects.requireNonNull(session, "session");
    }
}
