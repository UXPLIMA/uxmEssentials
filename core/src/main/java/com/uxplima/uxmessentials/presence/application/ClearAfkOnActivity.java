package com.uxplima.uxmessentials.presence.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.event.ReturnedFromAfk;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Records player activity (a move, a chat line, a command) so the idle clock resets and an AFK player returns
 * on their first sign of life. Called from the sync activity listeners on the hot path, so it does the cheap
 * work in the common case, re-stamp the last-activity instant, and only fires the {@link ReturnedFromAfk}
 * event and the back-broadcast when the player was actually AFK.
 *
 * <p>The whole transition runs inside the {@link PresenceStore}'s atomic {@code compute}: the new aggregate is
 * computed from the old one, so a concurrent auto-AFK flip from the sweep and this clear never interleave into
 * a half-applied state. The pre-transition AFK flag is read first to decide whether a return actually occurred.
 */
public final class ClearAfkOnActivity {

    private final PresenceStore store;
    private final PresenceAudience audience;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ClearAfkOnActivity(
            PresenceStore store,
            PresenceAudience audience,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Record that {@code who} was active just now, returning them from AFK if they were AFK. */
    public void recordActivity(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean wasAfk = store.current(who).afk();
        store.update(who, presence -> presence.cleared(clock.instant()));
        if (wasAfk) {
            announceReturn(who);
        }
    }

    private void announceReturn(PlayerRef who) {
        events.publish(new ReturnedFromAfk(who, clock.instant()));
        notifier.send(who, PresenceMessageKey.AFK_RETURNED);
        Map<String, String> placeholders = Map.of("player", who.name());
        audience.online().stream()
                .filter(viewer -> !viewer.equals(who))
                .forEach(viewer -> notifier.send(viewer, PresenceMessageKey.AFK_BROADCAST_BACK, placeholders));
    }
}
