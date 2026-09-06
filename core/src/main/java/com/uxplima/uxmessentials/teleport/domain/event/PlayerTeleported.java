package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * A player completed a teleport: the warmup (if any) elapsed and the hop was issued. The
 * {@code presence} context's adapter subscribes to the Bukkit-bridged form of this event to clear AFK.
 *
 * @param player the teleported player
 * @param kind why the teleport happened
 * @param from the position the player left, captured for {@code /back}
 * @param to the position the player arrived at
 */
public record PlayerTeleported(PlayerRef player, TeleportKind kind, Position from, Position to)
        implements TeleportEvent {

    public PlayerTeleported {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
