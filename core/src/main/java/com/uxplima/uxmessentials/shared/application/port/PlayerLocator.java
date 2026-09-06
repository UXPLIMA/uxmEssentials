package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port that reads a player's current {@link Position}.
 *
 * <p>The teleport context captures the requester's pre-teleport position for {@code /back}, records a
 * warmup's origin block for the move-cancel comparison, and reads the {@code /tphere} target's
 * location through this port. Reading a live location is region-bound work. The adapter performs it
 * on the player's region thread and returns empty when the player is offline.
 */
public interface PlayerLocator {

    /** The player's current position, or empty when the player is offline. */
    Optional<Position> locate(PlayerRef who);
}
