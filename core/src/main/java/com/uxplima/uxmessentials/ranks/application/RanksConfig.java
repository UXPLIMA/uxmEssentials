package com.uxplima.uxmessentials.ranks.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/ranks/config.conf}: the module enable gate plus the two
 * feature switches, prestige and autorank, later phases (P3, P4) gate their behaviour on. The ladder itself
 * lives in a sibling {@code ranks.conf} and is read by {@link RankLadders}, not here, so this record holds only
 * the module-level tunables. It is resolved once from the module's scoped {@link ConfigStore} on start and, per
 * the atomic-reload rule, swapped whole on reload, so a reader always sees one coherent snapshot.
 *
 * <p>Prestige ships {@code true} so the {@code /prestige} verb is published out of the box; it no-ops sensibly
 * until a ladder and prestige rewards are authored. Autorank ships {@code false}: it is an opt-in an operator
 * turns on once their ladder is authored. An operator who deletes a line falls back to the shipped default.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param prestige the prestige settings ({@code prestige.*}); {@link PrestigeSettings#enabled()} ships {@code true}
 * @param autorank the autorank scan settings ({@code autorank.*}); {@link AutorankSettings#enabled()} ships {@code false}
 * @param gui the {@code /ranks} panel settings ({@code gui.*}); {@link GuiSettings#enabled()} ships {@code true}
 */
public record RanksConfig(boolean enabled, PrestigeSettings prestige, AutorankSettings autorank, GuiSettings gui) {

    public RanksConfig {
        Objects.requireNonNull(prestige, "prestige");
        Objects.requireNonNull(autorank, "autorank");
        Objects.requireNonNull(gui, "gui");
    }

    /** Resolve the ranks config from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
    public static RanksConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new RanksConfig(
                config.getBoolean("enabled", true),
                PrestigeSettings.from(config),
                AutorankSettings.from(config),
                GuiSettings.from(config));
    }

    /**
     * The {@code /ranks} panel's one tunable ({@code gui.*}): whether the ladder GUI is on. Unlike prestige and
     * autorank this ships {@code true}. The panel is a self-service window every player opens to see their standing
     * and rank up, so a fresh install has it available; an operator flips {@code gui.enabled = false} to drop the
     * GUI (and its {@code /ranks} no-argument open) entirely, leaving only the admin {@code /ranks setrank}.
     *
     * @param enabled whether the {@code /ranks} ladder panel is registered ({@code gui.enabled}, default {@code true})
     */
    public record GuiSettings(boolean enabled) {

        /** Resolve the GUI settings from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
        public static GuiSettings from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new GuiSettings(config.getBoolean("gui.enabled", true));
        }
    }

    /**
     * The prestige mechanic's tunables ({@code prestige.*}): the enable gate, the highest reachable level, the
     * cost charged on a prestige, the requirement and action lines run around it, and the per-level reward
     * multiplier. The requirement and action lines are operator content parsed by the same requirement/action
     * machinery a rankup uses; the numbers are validated here so a nonsensical negative never reaches the domain.
     *
     * @param enabled whether the prestige mechanic is on ({@code prestige.enabled}, default {@code true})
     * @param maxLevel the highest prestige level reachable, {@code 0} for no cap ({@code prestige.max-level})
     * @param cost the amount charged through the economy on a prestige ({@code prestige.cost}, {@code 0} for free)
     * @param requirements the raw requirement lines that gate a prestige ({@code prestige.requirements})
     * @param actions the raw action lines run after a prestige ({@code prestige.actions})
     * @param rewardMultiplier the per-level reward multiplier ({@code prestige.reward-multiplier}, default {@code 1.0})
     */
    public record PrestigeSettings(
            boolean enabled,
            int maxLevel,
            long cost,
            List<String> requirements,
            List<String> actions,
            double rewardMultiplier) {

        public PrestigeSettings {
            Objects.requireNonNull(requirements, "requirements");
            Objects.requireNonNull(actions, "actions");
            if (maxLevel < 0) {
                throw new IllegalArgumentException("prestige max-level must not be negative: " + maxLevel);
            }
            if (cost < 0) {
                throw new IllegalArgumentException("prestige cost must not be negative: " + cost);
            }
            requirements = List.copyOf(requirements);
            actions = List.copyOf(actions);
        }

        /** Resolve the prestige settings from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
        public static PrestigeSettings from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new PrestigeSettings(
                    config.getBoolean("prestige.enabled", true),
                    Math.max(0, config.getInt("prestige.max-level", 0)),
                    Math.max(0L, config.getLong("prestige.cost", 0L)),
                    config.getStringList("prestige.requirements", List.of()),
                    config.getStringList("prestige.actions", List.of()),
                    config.getDouble("prestige.reward-multiplier", 1.0));
        }
    }

    /**
     * The autorank mechanic's tunables ({@code autorank.*}): the enable gate, the scan interval, and whether the
     * scan charges each promotion's cost. Autorank promotes an online player the moment they already meet their
     * next rank's requirements, without them running {@code /rankup}; the scan reuses the same rankup pipeline, so
     * {@code charge-cost} simply decides whether that pipeline's cost step runs. The interval is clamped to at
     * least one second so a misconfigured {@code 0} cannot spin the scheduler.
     *
     * @param enabled whether the scheduled autorank scan runs ({@code autorank.enabled}, default {@code false})
     * @param intervalSeconds seconds between scans ({@code autorank.interval-seconds}, default {@code 300}, min {@code 1})
     * @param chargeCost whether an autorank promotion charges the rank's cost ({@code autorank.charge-cost}, default {@code true})
     */
    public record AutorankSettings(boolean enabled, int intervalSeconds, boolean chargeCost) {

        public AutorankSettings {
            if (intervalSeconds < 1) {
                throw new IllegalArgumentException("autorank interval-seconds must be at least 1: " + intervalSeconds);
            }
        }

        /** Resolve the autorank settings from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
        public static AutorankSettings from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new AutorankSettings(
                    config.getBoolean("autorank.enabled", false),
                    Math.max(1, config.getInt("autorank.interval-seconds", 300)),
                    config.getBoolean("autorank.charge-cost", true));
        }

        /** The scan interval as a {@link Duration}, both the initial delay and the fixed period the scan repeats at. */
        public Duration interval() {
            return Duration.ofSeconds(intervalSeconds);
        }
    }
}
