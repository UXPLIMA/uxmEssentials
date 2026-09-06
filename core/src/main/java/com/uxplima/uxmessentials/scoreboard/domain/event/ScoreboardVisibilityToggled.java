package com.uxplima.uxmessentials.scoreboard.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player flipped whether their scoreboard display is shown: the result of {@code /scoreboard} (alias {@code /sb}).
 *
 * @param who the player who toggled their display
 * @param hidden the new visibility state: {@code true} when the display is now hidden, {@code false} when shown
 */
public record ScoreboardVisibilityToggled(PlayerRef who, boolean hidden) implements ScoreboardEvent {

    public ScoreboardVisibilityToggled {
        Objects.requireNonNull(who, "who");
    }
}
