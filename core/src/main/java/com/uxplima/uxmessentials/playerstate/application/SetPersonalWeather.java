package com.uxplima.uxmessentials.playerstate.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /pweather <clear|rain|reset>}: set a per-player client-side weather without changing world weather.
 * Self-only, a client-side presentation override. The {@link PlayerEffects} port applies it on the player's
 * owning region thread; the use case sends the matching set/reset confirmation. The raw argument is parsed to
 * a {@link PersonalWeather} in the adapter, which renders the invalid-input message itself when parsing fails.
 */
public final class SetPersonalWeather {

    private final PlayerEffects effects;
    private final Notifier notifier;

    public SetPersonalWeather(PlayerEffects effects, Notifier notifier) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Apply {@code weather} to {@code who} and confirm. */
    public void apply(PlayerRef who, PersonalWeather weather) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(weather, "weather");
        effects.applyWeather(who, weather);
        if (weather == PersonalWeather.RESET) {
            notifier.send(who, PlayerstateMessageKey.PWEATHER_RESET);
            return;
        }
        notifier.send(
                who,
                PlayerstateMessageKey.PWEATHER_SET,
                Map.of("weather", weather.name().toLowerCase(Locale.ROOT)));
    }
}
