package com.uxplima.uxmessentials.playerstate.application.port;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port for the read-only {@code /getpos} and {@code /ping} queries: it reports a live player's
 * position and round-trip latency without exposing a Bukkit {@code Player}. The adapter resolves the live
 * player and reads the value on the player's owning region thread, mapping the location to the kernel
 * {@link Position} so the use case stays free of Bukkit. An offline target yields an empty result.
 *
 * <p>Split from {@link PlayerEffects} because these never mutate the player. They only read presentation
 * state the use case renders back to the viewer.
 */
public interface PlayerInfo {

    /** The live position of {@code who}, or empty when they are offline. */
    Optional<Position> positionOf(PlayerRef who);

    /** The round-trip latency of {@code who} in milliseconds, or empty when they are offline. */
    Optional<Integer> pingOf(PlayerRef who);

    /** The total time {@code who} has played, or empty when offline. */
    Optional<Duration> playtimeOf(PlayerRef who);
}
