package com.uxplima.uxmessentials.playerstate.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /ptime <value|reset>}: set a per-player client-side time without changing world time. Self-only, a
 * client-side presentation override the player sets for themselves. The {@link PlayerEffects} port applies it
 * on the player's owning region thread; the use case sends the matching set/reset confirmation. The raw
 * argument is parsed to a {@link PersonalTime} in the adapter, which renders {@link PlayerstateMessageKey#PTIME_INVALID}
 * itself when parsing fails: this use case only ever receives a valid value.
 */
public final class SetPersonalTime {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public SetPersonalTime(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Apply {@code time} to {@code who} and confirm. */
    public void apply(PlayerRef who, PersonalTime time) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(time, "time");
        effects.applyTime(who, time);
        if (time.reset()) {
            notifier.send(who, PlayerstateMessageKey.PTIME_RESET);
            return;
        }
        notifier.send(who, PlayerstateMessageKey.PTIME_SET, Map.of("ticks", Long.toString(time.ticks())));
    }
}
