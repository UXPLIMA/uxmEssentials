package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.event.Fed;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /feed [player]}: an apply-once restore of a player's hunger and saturation. It changes no persisted
 * snapshot (the {@link PlayerEffects} port restores the live player on the owning region thread) then
 * publishes {@link Fed} and notifies the actor and subject.
 */
public final class Feed {

    private final PlayerEffects effects;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public Feed(PlayerEffects effects, Notifier notifier, DomainEventPublisher events, Clock clock) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Feed {@code who} themselves. */
    public void feed(PlayerRef who) {
        feedFor(who, who);
    }

    /** Feed {@code subject} on behalf of {@code actor}. */
    public void feedFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        effects.feed(subject);
        events.publish(new Fed(subject, actor, clock.instant()));
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.FED);
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.FED_OTHER, Map.of("player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.FED);
    }
}
