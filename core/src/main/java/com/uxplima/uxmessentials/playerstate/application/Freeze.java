package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /ice <player> [seconds]}: apply the powder-snow freezing effect to a player for a number of seconds
 * (the cosmetic opposite of {@code /burn}). A live-only effect through the {@link PlayerEffects} port, the
 * duration is clamped to a sane range in the domain ({@link FreezeDuration}) so a typo cannot freeze someone
 * indefinitely. The actor is confirmed and, for a staff target, the subject is told too.
 */
public final class Freeze {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public Freeze(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Freeze {@code who} for {@code duration}. */
    public void freeze(PlayerRef who, FreezeDuration duration) {
        freezeFor(who, who, duration);
    }

    /** Freeze {@code subject} for {@code duration} on behalf of {@code actor}. */
    public void freezeFor(PlayerRef actor, PlayerRef subject, FreezeDuration duration) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(duration, "duration");
        effects.setFreeze(subject, duration);
        String seconds = Integer.toString(duration.seconds());
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.FREEZE_SET, Map.of("seconds", seconds));
            return;
        }
        notifier.send(
                actor, PlayerstateMessageKey.FREEZE_SET_OTHER, Map.of("seconds", seconds, "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.FREEZE_SET, Map.of("seconds", seconds));
    }
}
