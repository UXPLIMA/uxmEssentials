package com.uxplima.uxmessentials.nametags.domain;

import java.util.Objects;
import java.util.OptionalDouble;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * The visibility rules of a nametag: whether it shows at all for a wearer, and the per-(wearer, viewer)
 * suppressions the adapter evaluates each render. {@code showWhen} is a {@link DisplayCondition} gating the
 * wearer. When it fails the wearer carries no nametag at all (the same condition model the scoreboard and
 * tablist use). {@code hideWhileSneaking} drops the nametag for every viewer while the wearer is sneaking,
 * {@code respectVanish} hides it from viewers who cannot see the wearer through the vanish port, and
 * {@code viewerDistance} bounds the per-tick eligible-viewer cull to a block radius around the wearer.
 *
 * <p>Only {@code showWhen} is evaluated in {@code :core} (it is a pure {@link DisplayCondition#matches}). The
 * other three are read by the adapter when it computes the eligible viewer set per render, because sneak state,
 * vanish visibility, and viewer distance are Bukkit-side runtime facts the domain does not see.
 *
 * <p>{@code viewerDistance} is deliberately distinct from {@link NametagAppearance#viewRange()}: the appearance
 * view-range is Paper's {@code Display#setViewRange} render-distance multiplier (around {@code 1.0}), whereas this
 * is a flat block radius for the renderer's own candidate cull. An absent value means "use the renderer's default
 * radius"; a value of {@code 0} disables the distance cull entirely (every online viewer is a candidate, subject to
 * the wearer/sneak/vanish exclusions).
 *
 * @param showWhen the per-wearer gate; {@link DisplayCondition#always()} for an unconditional nametag
 * @param hideWhileSneaking whether to hide the nametag from all viewers while the wearer sneaks
 * @param respectVanish whether to hide the nametag from viewers who cannot see the wearer
 * @param viewerDistance the cull radius in blocks (empty → renderer default, {@code 0} → no cull); non-negative
 */
public record NametagVisibility(
        DisplayCondition showWhen, boolean hideWhileSneaking, boolean respectVanish, OptionalDouble viewerDistance) {

    public NametagVisibility {
        Objects.requireNonNull(showWhen, "showWhen");
        Objects.requireNonNull(viewerDistance, "viewerDistance");
        if (viewerDistance.isPresent()) {
            double blocks = viewerDistance.getAsDouble();
            if (!Double.isFinite(blocks) || blocks < 0) {
                throw new IllegalArgumentException(
                        "viewerDistance must be a finite non-negative block radius: " + blocks);
            }
        }
    }

    /** The default visibility: shown unconditionally, never hidden on sneak, honoring vanish, default cull radius. */
    public static NametagVisibility alwaysVisible() {
        return new NametagVisibility(DisplayCondition.always(), false, true, OptionalDouble.empty());
    }
}
