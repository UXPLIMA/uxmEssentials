package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /burn <seconds> [player]}: set a player on fire for a number of seconds (the inverse of {@code /ext}).
 * A live-only effect through the {@link PlayerEffects} port. The duration is clamped to a sane range in the
 * domain ({@link BurnDuration}) so a typo cannot set someone alight indefinitely. The actor is confirmed and,
 * for a staff target, the subject is told too.
 */
public final class Burn {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public Burn(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code who} on fire for {@code duration}. */
    public void burn(PlayerRef who, BurnDuration duration) {
        burnFor(who, who, duration);
    }

    /** Set {@code subject} on fire for {@code duration} on behalf of {@code actor}. */
    public void burnFor(PlayerRef actor, PlayerRef subject, BurnDuration duration) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(duration, "duration");
        effects.setFire(subject, duration);
        String seconds = Integer.toString(duration.seconds());
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.BURN_SET, Map.of("seconds", seconds));
            return;
        }
        notifier.send(
                actor, PlayerstateMessageKey.BURN_SET_OTHER, Map.of("seconds", seconds, "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.BURN_SET, Map.of("seconds", seconds));
    }
}
