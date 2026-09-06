package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /air <seconds> [player]}: set a player's remaining air. A live-only effect through the
 * {@link PlayerEffects} port. The requested value is clamped to the player's maximum air in the domain
 * ({@link AirAmount}), so the adapter only ever sets a value the server accepts. The actor is confirmed and,
 * for a staff target, the subject is told too.
 */
public final class SetAir {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public SetAir(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code who}'s own remaining air. */
    public void set(PlayerRef who, AirAmount air) {
        setFor(who, who, air);
    }

    /** Set {@code subject}'s remaining air on behalf of {@code actor}. */
    public void setFor(PlayerRef actor, PlayerRef subject, AirAmount air) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(air, "air");
        effects.setRemainingAir(subject, air);
        String seconds = Integer.toString(air.seconds());
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.AIR_SET, Map.of("seconds", seconds));
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.AIR_SET_OTHER, Map.of("seconds", seconds, "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.AIR_SET, Map.of("seconds", seconds));
    }
}
