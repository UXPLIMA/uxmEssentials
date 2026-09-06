package com.uxplima.uxmessentials.messaging.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One row of an owner's ignore list: the player being ignored and the {@link IgnoreScope} of the block. The
 * owner is the {@link IgnoreList} that holds the entry, so it is not repeated here; the entry is identified
 * by its {@link #ignored} player within that list, which is why a second {@code /ignore} of the same player
 * re-scopes the existing entry rather than adding a duplicate.
 *
 * @param ignored the player whose traffic this entry suppresses
 * @param scope the kind of traffic suppressed
 */
public record IgnoreEntry(PlayerRef ignored, IgnoreScope scope) {

    public IgnoreEntry {
        Objects.requireNonNull(ignored, "ignored");
        Objects.requireNonNull(scope, "scope");
    }

    /** An entry ignoring {@code ignored} across every channel, the {@code /ignore <player>} default. */
    public static IgnoreEntry all(PlayerRef ignored) {
        return new IgnoreEntry(ignored, IgnoreScope.ALL);
    }
}
