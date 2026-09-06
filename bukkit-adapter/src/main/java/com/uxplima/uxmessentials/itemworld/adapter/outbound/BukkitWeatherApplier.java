package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Objects;

import org.bukkit.World;

import com.uxplima.uxmessentials.itemworld.domain.WeatherSpec;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a validated {@link WeatherSpec} onto a world's storm/thunder flags. The one side-effecting step
 * the {@code /weather}, {@code /sun}, {@code /rain} and {@code /thunder} verbs share. The domain owns the closed
 * {@link WeatherSpec.Kind} set and the optional duration; this applier maps each kind to the two boolean world
 * flags and, when a duration is given, sets the matching weather-duration in ticks so the change times out.
 *
 * <p>Setting world weather is global game state, so the caller schedules this on the global region thread
 * through the {@code Scheduler} port.
 */
@NullMarked
public final class BukkitWeatherApplier {

    private static final int TICKS_PER_SECOND = 20;

    private BukkitWeatherApplier() {}

    /** Apply {@code spec} to {@code world}'s storm/thunder flags, honouring an optional time-box in seconds. */
    public static void apply(World world, WeatherSpec spec) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spec, "spec");
        switch (spec.kind()) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case THUNDER -> {
                world.setStorm(true);
                world.setThundering(true);
            }
        }
        spec.durationSeconds().ifPresent(seconds -> {
            int ticks = seconds * TICKS_PER_SECOND;
            world.setWeatherDuration(ticks);
            world.setThunderDuration(ticks);
        });
    }
}
