package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * One parsed {@code animations { <name> { … } }} entry as the codec hands it to the {@link AnimationRegistry}: the pure
 * {@link AnimationSpec} (name, type, frames, interval) plus the extra parameters the uxmLib animators need that the
 * pure spec does not model. {@code AnimationSpec} is a {@code :core} value object with no rendering library, so the
 * {@link AnimationSpec.AnimationType#SCROLL SCROLL} window/separator and the
 * {@link AnimationSpec.AnimationType#GRADIENT GRADIENT} colour-stops/steps live here, in the adapter, where the
 * registry binds uxmLib's {@code ScrollingText}/{@code GradientText}.
 *
 * <p>The extra fields are only consulted for the matching type, {@link #scroll} for SCROLL, {@link #gradient} for
 * GRADIENT, and are empty for FRAMES, which the spec resolves on its own. Both are {@link Optional} so a missing block
 * falls back to the animator's own defaults rather than failing the parse.
 *
 * @param spec the pure animation spec; its {@code intervalTicks} drives the step cadence for every type
 * @param scroll the SCROLL window/separator, present only for a SCROLL spec
 * @param gradient the GRADIENT colour stops and sweep length, present only for a GRADIENT spec
 */
@NullMarked
public record AnimationDef(AnimationSpec spec, Optional<Scroll> scroll, Optional<Gradient> gradient) {

    public AnimationDef {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(scroll, "scroll");
        Objects.requireNonNull(gradient, "gradient");
    }

    /** A FRAMES animation, which needs no extra adapter parameters. */
    public static AnimationDef frames(AnimationSpec spec) {
        return new AnimationDef(spec, Optional.empty(), Optional.empty());
    }

    /** A SCROLL animation with the marquee window width and the wrap separator. */
    public static AnimationDef scroll(AnimationSpec spec, Scroll params) {
        return new AnimationDef(spec, Optional.of(params), Optional.empty());
    }

    /** A GRADIENT animation with its colour stops and sweep length. */
    public static AnimationDef gradient(AnimationSpec spec, Gradient params) {
        return new AnimationDef(spec, Optional.empty(), Optional.of(params));
    }

    /**
     * Parse an {@code animations { <name> { type, frames, interval-ticks, … } }} block into {@link AnimationDef}s. This
     * is the single shared parse every HUD codec (scoreboard, tablist, …) calls so the animation grammar lives in one
     * place and never drifts between them. Tolerant by design: an entry with an unknown {@code type}, no frames, or
     * invalid params is skipped with a one-line warning rather than failing the whole parse, so a single bad animation
     * never blanks the display. An absent, virtual, or non-map block yields no animations.
     *
     * @param node the {@code animations} node (already navigated to by the caller)
     * @param log reports each skipped entry with its name and reason
     */
    public static List<AnimationDef> parseAll(ConfigurationNode node, Logger log) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(log, "log");
        if (node.virtual() || !node.isMap()) {
            return List.of();
        }
        List<AnimationDef> defs = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey());
            parseOne(name, entry.getValue(), log).ifPresent(defs::add);
        }
        return List.copyOf(defs);
    }

    private static Optional<AnimationDef> parseOne(String name, ConfigurationNode node, Logger log) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        AnimationSpec.AnimationType type = animationType(node.node("type"));
        if (type == null) {
            log.warn(
                    "event=animation_skipped name={} reason=unknown_type type={}",
                    name,
                    node.node("type").getString(""));
            return Optional.empty();
        }
        List<String> frames = strings(node.node("frames"));
        int interval = animationInterval(node.node("interval-ticks"));
        try {
            AnimationSpec spec = new AnimationSpec(name, type, frames, interval);
            return Optional.of(fromConfig(spec, node));
        } catch (IllegalArgumentException invalid) {
            log.warn("event=animation_skipped name={} reason={}", name, String.valueOf(invalid.getMessage()));
            return Optional.empty();
        }
    }

    private static AnimationDef fromConfig(AnimationSpec spec, ConfigurationNode node) {
        return switch (spec.type()) {
            case FRAMES -> frames(spec);
            case SCROLL ->
                scroll(
                        spec,
                        new Scroll(
                                node.node("window").getInt(16),
                                node.node("separator").getString(" ")));
            case GRADIENT ->
                gradient(
                        spec,
                        new Gradient(
                                strings(node.node("colors")), node.node("steps").getInt(20)));
        };
    }

    private static AnimationSpec.@Nullable AnimationType animationType(ConfigurationNode node) {
        String raw = node.getString("");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return AnimationSpec.AnimationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static int animationInterval(ConfigurationNode node) {
        int interval = node.getInt(1);
        return interval < 1 ? 1 : interval;
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            // A blank entry is kept so an operator can author a spacer frame; a missing value is skipped.
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /** The SCROLL marquee parameters: the visible window width in characters and the wrap-gap separator. */
    public record Scroll(int window, String separator) {
        public Scroll {
            Objects.requireNonNull(separator, "separator");
            if (window < 1) {
                throw new IllegalArgumentException("scroll window must be >= 1: " + window);
            }
        }
    }

    /** The GRADIENT parameters: the hex/named colour stops (at least two) and how many steps a full sweep takes. */
    public record Gradient(List<String> colors, int steps) {
        public Gradient {
            Objects.requireNonNull(colors, "colors");
            colors = List.copyOf(colors);
            if (colors.size() < 2) {
                throw new IllegalArgumentException("a gradient needs at least two colour stops");
            }
            if (steps < 1) {
                throw new IllegalArgumentException("gradient steps must be >= 1: " + steps);
            }
        }
    }
}
