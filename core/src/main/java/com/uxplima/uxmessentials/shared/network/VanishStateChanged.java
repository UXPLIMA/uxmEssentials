package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's vanish state changed on the origin backend. They vanished, reappeared, or had their use level
 * re-resolved. Unlike the DB-backed {@code *Changed} frames (which only tell a peer to drop a cached row), the
 * vanish state is transient in-memory state with no shared table, so this frame <strong>carries the value</strong>:
 * the affected player, their name (so a network-wide {@code /vanish list} can render a hidden player who is not
 * online on the reading backend), the vanished flag, and the resolved use level. A peer applies it to its own
 * network-vanish view so a player vanished on {@code survival-1} is already hidden the instant they switch to
 * {@code survival-2}.
 *
 * <p>The origin-loop sentinel ({@code docs/02-concurrency.md}) still applies: the broker skips the origin backend
 * and a backend drops any frame whose {@link #originServer()} equals its own id, so a state change made here reaches
 * every peer once and never echoes back.
 *
 * @param originServer the backend that produced the change
 * @param player the player whose vanish state changed
 * @param playerName the player's name at the moment of the change, for a network-wide roster render
 * @param vanished {@code true} when the player is now vanished, {@code false} when they reappeared
 * @param level the player's resolved use level (always {@code >= 1}); meaningful only when {@code vanished} is true
 */
public record VanishStateChanged(String originServer, UUID player, String playerName, boolean vanished, int level)
        implements NetworkMessage {

    public VanishStateChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        if (originServer.isBlank()) {
            throw new IllegalArgumentException("originServer must not be blank");
        }
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
    }

    @Override
    public MessageType type() {
        return MessageType.VANISH_STATE_CHANGED;
    }
}
