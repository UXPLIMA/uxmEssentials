package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;

/**
 * A pending warmup was cancelled before it elapsed. The player moved, rotated, or took damage,
 * depending on the per-axis cancel toggles. No teleport follows.
 *
 * @param player the player whose warmup aborted
 * @param kind the teleport that will not happen
 * @param reason which cancel axis tripped
 */
public record WarmupCancelled(PlayerRef player, TeleportKind kind, WarmupCancelReason reason) implements TeleportEvent {

    public WarmupCancelled {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
    }
}
