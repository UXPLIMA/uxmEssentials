package com.uxplima.uxmessentials.nametags.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The visual settings of an above-head nametag, held as raw primitives and strings so the model stays in
 * {@code :core}. None of these values are interpreted here. The bukkit adapter maps them onto uxmLib's packet
 * {@code Appearance} for the text-display stack (billboard parsed to the lib billboard enum, colours folded into a
 * packed ARGB int, view range to a float, …). The domain only validates the operator-authored values it can check
 * without a rendering library: the structural and range invariants.
 *
 * <p>The optional fields are "leave the platform default": an absent {@code backgroundColor} keeps the
 * vanilla text-display background, an absent {@code glowColor} leaves the entity unglowing, an absent
 * {@code lineWidth} keeps the default wrap width, and an absent {@code viewRange} keeps the default render
 * distance. {@code scale} multiplies the text-display transform (so it must be positive), and {@code yOffset}
 * lifts the nametag above the wearer's head (the adapter maps it to the line stack's base translation).
 *
 * <p>{@code hideThroughBlocks} and {@code obscuredOpacity} are the per-viewer line-of-sight controls the packet
 * renderer adds: when {@code hideThroughBlocks} is on, a line whose path to a viewer is blocked is faded to
 * {@code obscuredOpacity} (0..255, defaulting in the adapter) for that viewer only, so a nametag behind a wall dims
 * for the players it is occluded from while staying solid for those with a clear view. These are distinct from
 * {@code seeThrough}, which is the static text-display flag that renders the glyphs through geometry for everyone.
 *
 * @param billboard how the display faces the viewer ({@code "FIXED"}/{@code "VERTICAL"}/{@code "HORIZONTAL"}/{@code "CENTER"}); interpreted in the adapter
 * @param seeThrough whether the text renders through blocks
 * @param backgroundColor the background color as an operator string (hex/named), or empty for the vanilla default
 * @param backgroundOpacity the background alpha 0..255, or empty for the vanilla default
 * @param textShadow whether the text is drawn with a drop shadow
 * @param glowColor the glow color as an operator string (hex/named), or empty for no glow; retained for config
 *     back-compat but not rendered by the packet renderer (it drives no per-viewer glow outline)
 * @param lineWidth the text wrap width in pixels, or empty for the default
 * @param viewRange the render distance multiplier, or empty for the default
 * @param scale the uniform text-display scale; strictly positive
 * @param yOffset the vertical offset above the wearer's head; finite
 * @param hideThroughBlocks whether a line occluded from a viewer fades for that viewer (per-viewer line of sight)
 * @param obscuredOpacity the alpha 0..255 an occluded line fades to, or empty for the renderer default
 */
public record NametagAppearance(
        String billboard,
        boolean seeThrough,
        Optional<String> backgroundColor,
        OptionalInt backgroundOpacity,
        boolean textShadow,
        Optional<String> glowColor,
        OptionalInt lineWidth,
        OptionalDouble viewRange,
        double scale,
        double yOffset,
        boolean hideThroughBlocks,
        OptionalInt obscuredOpacity) {

    public NametagAppearance {
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(backgroundColor, "backgroundColor");
        Objects.requireNonNull(backgroundOpacity, "backgroundOpacity");
        Objects.requireNonNull(glowColor, "glowColor");
        Objects.requireNonNull(lineWidth, "lineWidth");
        Objects.requireNonNull(viewRange, "viewRange");
        Objects.requireNonNull(obscuredOpacity, "obscuredOpacity");
        if (scale <= 0) {
            throw new IllegalArgumentException("scale must be > 0: " + scale);
        }
        if (backgroundOpacity.isPresent()) {
            int opacity = backgroundOpacity.getAsInt();
            if (opacity < 0 || opacity > 255) {
                throw new IllegalArgumentException("backgroundOpacity must be 0..255: " + opacity);
            }
        }
        if (obscuredOpacity.isPresent()) {
            int opacity = obscuredOpacity.getAsInt();
            if (opacity < 0 || opacity > 255) {
                throw new IllegalArgumentException("obscuredOpacity must be 0..255: " + opacity);
            }
        }
        if (!Double.isFinite(yOffset)) {
            throw new IllegalArgumentException("yOffset must be finite: " + yOffset);
        }
    }

    /**
     * The out-of-the-box appearance: a center-billboarded nametag floating {@code 0.3} above the wearer's head at
     * unit scale, with no background/glow/line-width/view-range override, no see-through, and no text shadow, and no
     * through-block fading. The adapter renders these as the vanilla text-display defaults.
     */
    public static NametagAppearance defaults() {
        return new NametagAppearance(
                "CENTER",
                false,
                Optional.empty(),
                OptionalInt.empty(),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                0.3,
                false,
                OptionalInt.empty());
    }
}
