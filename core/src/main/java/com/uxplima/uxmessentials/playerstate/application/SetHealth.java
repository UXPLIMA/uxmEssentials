package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /health <amount> [player]}: set a player's health to a specific value. A live-only effect through the
 * {@link PlayerEffects} port. The requested amount is floored at {@code 0} in the domain ({@link HealthLevel})
 * and capped to the player's live maximum health in the adapter, so a request of {@code 0} kills. Distinct from
 * {@code /heal}, which always restores to full. The actor is confirmed and, for a staff target, the subject is
 * told too.
 */
public final class SetHealth {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public SetHealth(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code who}'s own health. */
    public void set(PlayerRef who, HealthLevel value) {
        setFor(who, who, value);
    }

    /** Set {@code subject}'s health on behalf of {@code actor}. */
    public void setFor(PlayerRef actor, PlayerRef subject, HealthLevel value) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(value, "value");
        effects.setHealth(subject, value);
        String amount = String.valueOf((int) Math.round(value.value()));
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.HEALTH_SET, Map.of("amount", amount));
            return;
        }
        notifier.send(
                actor, PlayerstateMessageKey.HEALTH_SET_OTHER, Map.of("amount", amount, "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.HEALTH_SET, Map.of("amount", amount));
    }
}
