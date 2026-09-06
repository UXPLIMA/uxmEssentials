package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The damage/healing-indicator feature's configuration and text formatting. A small floating number that pops at
 * an entity when it is hurt or healed and vanishes after a short duration. It is an adapter-only feature (a
 * reaction to Bukkit combat events rendered as an ephemeral {@code TextDisplay}); there is no persisted aggregate,
 * so the config and the pure {@link #format} live here beside the listener that consumes them, the same way
 * {@code VoteDiscordSettings} carries its own {@code fromConfig}.
 *
 * <p>The feature ships <strong>disabled</strong>: {@link #fromConfig} reads {@code enabled} defaulting to false, so
 * a default server registers no listener and pays nothing. The format strings are MiniMessage sources carrying an
 * {@code {amount}} token (config data, not a user-message literal. Like the scoreboard/tablist content lines), and
 * {@link #format} substitutes the rounded magnitude in, picking the crit format for a critical hit.
 *
 * @param enabled whether the feature shows any indicator at all
 * @param showForPlayers whether a player taking damage/healing shows an indicator
 * @param showForMobs whether a non-player living entity taking damage/healing shows an indicator
 * @param showHeal whether healing (not just damage) shows an indicator
 * @param durationTicks how long the indicator lives before it is removed, in ticks (> 0)
 * @param damageFormat the MiniMessage source for an ordinary hit (carries {@code {amount}})
 * @param critFormat the MiniMessage source for a critical hit
 * @param healFormat the MiniMessage source for healing
 */
@NullMarked
public record DamageIndicatorConfig(
        boolean enabled,
        boolean showForPlayers,
        boolean showForMobs,
        boolean showHeal,
        int durationTicks,
        String damageFormat,
        String critFormat,
        String healFormat) {

    /** Which indicator a value renders as: picks the matching format string. */
    public enum Kind {
        DAMAGE,
        CRIT,
        HEAL
    }

    private static final String AMOUNT_TOKEN = "{amount}";

    public DamageIndicatorConfig {
        Objects.requireNonNull(damageFormat, "damageFormat");
        Objects.requireNonNull(critFormat, "critFormat");
        Objects.requireNonNull(healFormat, "healFormat");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive: " + durationTicks);
        }
    }

    /** The off-by-default configuration: the shipped state, used as the test baseline. */
    public static DamageIndicatorConfig disabled() {
        return new DamageIndicatorConfig(
                false, true, true, true, 20, "<red>-{amount}", "<gold><bold>-{amount} ✦", "<green>+{amount}");
    }

    /** Read the {@code damage-indicators.*} subtree of a hologram module's scoped config, off by default. */
    public static DamageIndicatorConfig fromConfig(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new DamageIndicatorConfig(
                config.getBoolean("damage-indicators.enabled", false),
                config.getBoolean("damage-indicators.show-for-players", true),
                config.getBoolean("damage-indicators.show-for-mobs", true),
                config.getBoolean("damage-indicators.show-heal", true),
                config.getInt("damage-indicators.duration-ticks", 20),
                config.getString("damage-indicators.damage-format", "<red>-{amount}"),
                config.getString("damage-indicators.crit-format", "<gold><bold>-{amount} ✦"),
                config.getString("damage-indicators.heal-format", "<green>+{amount}"));
    }

    /** The MiniMessage source for {@code amount} as a {@code kind} indicator, with the magnitude rounded in. */
    public String format(double amount, Kind kind) {
        Objects.requireNonNull(kind, "kind");
        String template =
                switch (kind) {
                    case CRIT -> critFormat;
                    case HEAL -> healFormat;
                    case DAMAGE -> damageFormat;
                };
        return template.replace(AMOUNT_TOKEN, roundedMagnitude(amount));
    }

    /** The absolute amount rounded to one decimal, dropping a trailing {@code .0} so a whole hit reads cleanly. */
    private static String roundedMagnitude(double amount) {
        double rounded = Math.round(Math.abs(amount) * 10.0) / 10.0;
        if (rounded == Math.floor(rounded)) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }
}
