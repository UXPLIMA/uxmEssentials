package com.uxplima.uxmessentials.poses.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/poses/config.conf}: the enable gate, the per-verb feature switches,
 * and the sit/pose tunables (sittable materials, seat reach, return-to-start, snoring, and the two region gates).
 * It is resolved once from the module's scoped {@link ConfigStore} when the module starts and, per the atomic-reload
 * rule, swapped whole on reload, so a command dispatched mid-reload sees one coherent snapshot.
 *
 * <p>The HOCON keys are kebab-case ({@code player-sit}, {@code sit-on-blocks}, {@code max-distance}); the record
 * components are the camelCase views the application reads. Every knob carries the default the bundled config ships,
 * so an operator who deletes a line falls back to the shipped value rather than to zero.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param features the per-verb feature switches
 * @param sitOnBlocks whether right-clicking a sittable block seats the player ({@code sit-on-blocks}, default true)
 * @param sittableMaterials the block materials (wildcards allowed) a player may sit on ({@code sittable-materials})
 * @param maxDistance the furthest a player may be from a seat block to sit on it ({@code max-distance}, default 2.0)
 * @param returnToStart whether a pose returns the player to where it began ({@code return-to-start}, default true)
 * @param snore whether sleeping-pose snoring is emitted, resource-pack-free ({@code snore}, default true)
 * @param respectClaims whether a claim plugin's build/interact permission gates posing ({@code respect-claims})
 * @param respectWorldguard whether WorldGuard region flags gate posing ({@code respect-worldguard}, default true)
 */
public record PosesConfig(
        boolean enabled,
        Features features,
        boolean sitOnBlocks,
        List<String> sittableMaterials,
        double maxDistance,
        boolean returnToStart,
        boolean snore,
        boolean respectClaims,
        boolean respectWorldguard) {

    /** The default sittable-material set (stairs, slabs, and carpets) matching the bundled config. */
    private static final List<String> DEFAULT_SITTABLE = List.of("*_STAIRS", "*_SLAB", "*_CARPET");

    /** The default seat reach in blocks. */
    private static final double DEFAULT_MAX_DISTANCE = 2.0;

    public PosesConfig {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(sittableMaterials, "sittableMaterials");
        sittableMaterials = List.copyOf(sittableMaterials);
        if (!Double.isFinite(maxDistance) || maxDistance <= 0) {
            throw new IllegalArgumentException("max-distance must be a positive, finite number: " + maxDistance);
        }
    }

    /** Resolve the poses config from the module's scoped {@link ConfigStore} ({@code modules.poses}). */
    public static PosesConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new PosesConfig(
                config.getBoolean("enabled", true),
                Features.from(config),
                config.getBoolean("sit-on-blocks", true),
                config.getStringList("sittable-materials", DEFAULT_SITTABLE),
                config.getDouble("max-distance", DEFAULT_MAX_DISTANCE),
                config.getBoolean("return-to-start", true),
                config.getBoolean("snore", true),
                config.getBoolean("respect-claims", true),
                config.getBoolean("respect-worldguard", true));
    }

    /**
     * The per-verb feature switches under {@code features { … }}. Each guards one pose verb so an operator can
     * enable exactly the poses they want. {@code playerSit} ships {@code false} because sitting on another player is
     * the one verb that touches a second player, so it is opt-in; the rest ship {@code true}.
     *
     * @param sit {@code /sit} and right-click-to-sit ({@code features.sit}, default true)
     * @param playerSit sitting on another player ({@code features.player-sit}, default {@code false})
     * @param lay {@code /lay} ({@code features.lay}, default true)
     * @param bellyflop {@code /bellyflop} ({@code features.bellyflop}, default true)
     * @param spin {@code /spin} ({@code features.spin}, default true)
     * @param crawl {@code /crawl} ({@code features.crawl}, default true)
     */
    public record Features(
            boolean sit, boolean playerSit, boolean lay, boolean bellyflop, boolean spin, boolean crawl) {

        /** Read the feature switches from the module's scoped config, honouring each verb's default. */
        public static Features from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new Features(
                    config.getBoolean("features.sit", true),
                    config.getBoolean("features.player-sit", false),
                    config.getBoolean("features.lay", true),
                    config.getBoolean("features.bellyflop", true),
                    config.getBoolean("features.spin", true),
                    config.getBoolean("features.crawl", true));
        }
    }
}
