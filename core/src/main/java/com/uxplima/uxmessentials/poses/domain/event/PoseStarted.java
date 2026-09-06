package com.uxplima.uxmessentials.poses.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.poses.domain.PoseSession;

/**
 * A player began a pose. Carries the whole {@link PoseSession} so a consumer, a placeholder refresh, an audit
 * line, a bridged Bukkit event, sees the pose type, the subject, and the target without querying the registry
 * back.
 *
 * @param session the pose that just started
 */
public record PoseStarted(PoseSession session) implements PoseEvent {

    public PoseStarted {
        Objects.requireNonNull(session, "session");
    }
}
