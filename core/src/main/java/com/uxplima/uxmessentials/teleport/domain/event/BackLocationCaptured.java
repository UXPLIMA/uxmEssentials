package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.BackCause;

/**
 * A player's {@code /back} return point was (re)captured, before a teleport hop or on death. The most
 * recent capture is what {@code /back} returns to.
 *
 * @param player the player whose return point was recorded
 * @param position the captured position
 * @param cause whether the capture was a pre-teleport snapshot or a death point
 */
public record BackLocationCaptured(PlayerRef player, Position position, BackCause cause) implements TeleportEvent {

    public BackLocationCaptured {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cause, "cause");
    }
}
