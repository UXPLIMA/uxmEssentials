package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /foodlevel <amount> [player]}: set a player's hunger to a specific value. A live-only effect through
 * the {@link PlayerEffects} port. The requested amount is clamped to {@code 0..20} in the domain
 * ({@link FoodLevel}), so the adapter only ever sets a value the server accepts. Distinct from {@code /feed},
 * which always restores to full. The actor is confirmed and, for a staff target, the subject is told too.
 */
public final class SetFoodLevel {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public SetFoodLevel(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code who}'s own food level. */
    public void set(PlayerRef who, FoodLevel food) {
        setFor(who, who, food);
    }

    /** Set {@code subject}'s food level on behalf of {@code actor}. */
    public void setFor(PlayerRef actor, PlayerRef subject, FoodLevel food) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(food, "food");
        effects.setFoodLevel(subject, food);
        String amount = Integer.toString(food.value());
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.FOOD_SET, Map.of("amount", amount));
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.FOOD_SET_OTHER, Map.of("amount", amount, "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.FOOD_SET, Map.of("amount", amount));
    }
}
