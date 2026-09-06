package com.uxplima.uxmessentials.scoreboard.application;

import java.util.Objects;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.event.ScoreboardVisibilityToggled;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@code /scoreboard} use case: flip whether the invoking player's display is shown, confirm the new state, and
 * publish the {@link ScoreboardVisibilityToggled} domain event. Returns the new hidden state so the calling command
 * can immediately render or tear down the player's board rather than waiting for the next refresh tick.
 *
 * <p>The visibility bit lives in the {@link ScoreboardVisibilityStore} (PDC-backed in the adapter, surviving relog);
 * the confirmation is one of the plugin's own {@link ScoreboardMessageKey} strings through the
 * {@link Notifier}; the event is the seam other plugins observe. Nothing here renders: that is the
 * adapter's render path, keeping the use case pure and free of any HUD type.
 */
public final class ToggleScoreboard {

    private final ScoreboardVisibilityStore store;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public ToggleScoreboard(ScoreboardVisibilityStore store, Notifier notifier, DomainEventPublisher events) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Flip {@code who}'s display, confirm the new state, publish the event, and report whether the display is now
     * hidden ({@code true}) or shown ({@code false}).
     */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean hidden = store.toggle(who);
        notifier.send(who, hidden ? ScoreboardMessageKey.SCOREBOARD_HIDDEN : ScoreboardMessageKey.SCOREBOARD_SHOWN);
        events.publish(new ScoreboardVisibilityToggled(who, hidden));
        return hidden;
    }
}
