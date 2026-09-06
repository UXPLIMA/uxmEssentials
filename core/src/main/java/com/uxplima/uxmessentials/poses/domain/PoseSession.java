package com.uxplima.uxmessentials.poses.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * One player's active pose. The immutable record the {@code PoseSessions} registry holds as the single source of
 * truth for who is posing. There is at most one per player (the one-session-per-player invariant the registry
 * enforces): starting a new pose replaces the old, and any of quit / move / damage / teleport / dismount ends it.
 *
 * <p>{@code returnLocation} is where the player stood the instant the pose began, so a {@code return-to-start}
 * server can put them back exactly there when the pose ends. {@code seatHandle} is the adapter's opaque handle for
 * the seat entity or client-side render behind the pose (a Phase-1+ concern). The domain treats it as an opaque
 * token and never interprets it. {@code target} is populated only for {@link PoseType#PLAYER_SIT}: the player being
 * sat upon; it is {@code null} for every other pose.
 *
 * @param subject the player who is posing
 * @param type which pose is held
 * @param returnLocation where the subject stood when the pose began, for return-to-start
 * @param seatHandle the adapter's opaque handle for the seat/render behind the pose
 * @param target the carrier for a {@link PoseType#PLAYER_SIT}, otherwise {@code null}
 * @param startedAt when the pose began
 */
public record PoseSession(
        PlayerRef subject,
        PoseType type,
        Position returnLocation,
        String seatHandle,
        @Nullable PlayerRef target,
        Instant startedAt) {

    public PoseSession {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(returnLocation, "returnLocation");
        Objects.requireNonNull(seatHandle, "seatHandle");
        Objects.requireNonNull(startedAt, "startedAt");
        if (type == PoseType.PLAYER_SIT && target == null) {
            throw new IllegalArgumentException("a PLAYER_SIT session must carry a target");
        }
        if (type != PoseType.PLAYER_SIT && target != null) {
            throw new IllegalArgumentException("only a PLAYER_SIT session may carry a target: " + type);
        }
    }
}
