package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.event.Healed;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /heal [player]}: an apply-once restore of a player's health, optionally clearing active potion
 * effects per the {@code heal-remove-effects} toggle (§15.6). It changes no persisted snapshot, the
 * {@link PlayerEffects} port restores the live player on the owning region thread, then publishes
 * {@link Healed} and notifies the actor and subject.
 */
public final class Heal {

    private final PlayerEffects effects;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final boolean removeEffects;

    public Heal(
            PlayerEffects effects, Notifier notifier, DomainEventPublisher events, Clock clock, boolean removeEffects) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.removeEffects = removeEffects;
    }

    /** Heal {@code who} themselves. */
    public void heal(PlayerRef who) {
        healFor(who, who);
    }

    /** Heal {@code subject} on behalf of {@code actor}. */
    public void healFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        effects.heal(subject, removeEffects);
        events.publish(new Healed(subject, actor, clock.instant()));
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.HEALED);
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.HEALED_OTHER, Map.of("player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.HEALED);
    }
}
