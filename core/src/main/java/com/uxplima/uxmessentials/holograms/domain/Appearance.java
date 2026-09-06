package com.uxplima.uxmessentials.holograms.domain;

import java.util.Objects;

/**
 * The visual styling of a hologram, kept separate from its text so the {@link Hologram} aggregate stays small
 * and a styling change is one transition rather than a rebuild. Every field is a primitive, a domain enum, or a
 * pure {@link Transform} value object so the value object stays pure (no Bukkit); the adapter maps each one onto
 * the native {@code Display} / {@code TextDisplay} setters at the rendering boundary.
 *
 * <p>Each numeric field carries a documented sentinel for "leave Paper's default" so a stored row that never
 * set a property round-trips back to the default rather than a magic literal: {@link #DEFAULT_BACKGROUND} for a
 * transparent/default panel, {@link #DEFAULT_BRIGHTNESS} (-1) for the block and sky light overrides,
 * {@link #DEFAULT_SHADOW} for the drop-shadow radius/strength. Scale, line width and view range default to the
 * vanilla values (1.0, 200, 1.0); the spatial scale and translation live in a {@link Transform} (the legacy
 * single {@code scale} is the uniform case, preserved through {@link #scale()} and the {@code SCALE} column).
 * {@link #defaults()} is what a new hologram and an un-styled stored row both resolve to.
 *
 * @param billboard how the display faces the viewer ({@link Billboard#CENTER} by default)
 * @param backgroundArgb the text-panel background as a packed ARGB int, or {@link #DEFAULT_BACKGROUND}
 * @param textShadow whether the text casts a per-glyph drop shadow
 * @param brightnessBlock the block-light override 0 to 15, or {@link #DEFAULT_BRIGHTNESS} for the world value
 * @param brightnessSky the sky-light override 0 to 15, or {@link #DEFAULT_BRIGHTNESS} for the world value
 * @param transform the spatial display transform (per-axis scale + translation offset)
 * @param lineWidth the maximum line width in pixels before wrapping (200 = vanilla)
 * @param viewRange the view-distance multiplier (1.0 = vanilla)
 * @param seeThrough whether the text renders through blocks
 * @param alignment how a TEXT hologram's lines align within their panel ({@link TextAlignment#CENTER} default)
 * @param shadowRadius the display drop-shadow radius on the ground, or {@link #DEFAULT_SHADOW} (the vanilla 0)
 * @param shadowStrength the display drop-shadow strength, or {@link #DEFAULT_SHADOW} (the vanilla 1.0 falloff)
 * @param glowArgb the glowing-outline colour as a packed ARGB int, or {@link #DEFAULT_GLOW} for no glow
 * @param textOpacity the text opacity 0 to 255, or {@link #DEFAULT_OPACITY} for the vanilla fully-opaque text
 */
public record Appearance(
        Billboard billboard,
        int backgroundArgb,
        boolean textShadow,
        int brightnessBlock,
        int brightnessSky,
        Transform transform,
        int lineWidth,
        float viewRange,
        boolean seeThrough,
        TextAlignment alignment,
        float shadowRadius,
        float shadowStrength,
        int glowArgb,
        int textOpacity) {

    /** The background sentinel meaning "no override", the vanilla translucent panel. */
    public static final int DEFAULT_BACKGROUND = Integer.MIN_VALUE;

    /** The brightness sentinel meaning "no override": the text takes the world light at its position. */
    public static final int DEFAULT_BRIGHTNESS = -1;

    /** The shadow sentinel meaning "no override", the display uses the vanilla drop-shadow. */
    public static final float DEFAULT_SHADOW = -1.0f;

    /** The glow sentinel meaning "no glow": the text carries no glowing outline. */
    public static final int DEFAULT_GLOW = Integer.MIN_VALUE;

    /** The opacity sentinel meaning "no override", the vanilla fully-opaque text. */
    public static final int DEFAULT_OPACITY = Integer.MIN_VALUE;

    private static final int MIN_OPACITY = 0;
    private static final int MAX_OPACITY = 255;

    private static final float DEFAULT_SCALE = 1.0f;
    private static final int DEFAULT_LINE_WIDTH = 200;
    private static final float DEFAULT_VIEW_RANGE = 1.0f;

    static final float MIN_SCALE = 0.05f;
    static final float MAX_SCALE = 64.0f;
    private static final int MIN_LINE_WIDTH = 1;
    private static final int MAX_LINE_WIDTH = 4000;
    private static final float MIN_VIEW_RANGE = 0.0f;
    private static final float MAX_VIEW_RANGE = 256.0f;
    private static final int MAX_LIGHT = 15;
    private static final float MIN_SHADOW = 0.0f;
    private static final float MAX_SHADOW = 100.0f;

    public Appearance {
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(alignment, "alignment");
        if (lineWidth < MIN_LINE_WIDTH || lineWidth > MAX_LINE_WIDTH) {
            throw new IllegalArgumentException(
                    "lineWidth out of range [" + MIN_LINE_WIDTH + ", " + MAX_LINE_WIDTH + "]: " + lineWidth);
        }
        if (viewRange < MIN_VIEW_RANGE || viewRange > MAX_VIEW_RANGE) {
            throw new IllegalArgumentException(
                    "viewRange out of range [" + MIN_VIEW_RANGE + ", " + MAX_VIEW_RANGE + "]: " + viewRange);
        }
        if (!isDefaultBrightness(brightnessBlock) && (brightnessBlock < 0 || brightnessBlock > MAX_LIGHT)) {
            throw new IllegalArgumentException("brightnessBlock out of range [0, 15]: " + brightnessBlock);
        }
        if (!isDefaultBrightness(brightnessSky) && (brightnessSky < 0 || brightnessSky > MAX_LIGHT)) {
            throw new IllegalArgumentException("brightnessSky out of range [0, 15]: " + brightnessSky);
        }
        if (!isDefaultShadow(shadowRadius) && (shadowRadius < MIN_SHADOW || shadowRadius > MAX_SHADOW)) {
            throw new IllegalArgumentException("shadowRadius out of range [0, 100]: " + shadowRadius);
        }
        if (!isDefaultShadow(shadowStrength) && (shadowStrength < MIN_SHADOW || shadowStrength > MAX_SHADOW)) {
            throw new IllegalArgumentException("shadowStrength out of range [0, 100]: " + shadowStrength);
        }
        if (textOpacity != DEFAULT_OPACITY && (textOpacity < MIN_OPACITY || textOpacity > MAX_OPACITY)) {
            throw new IllegalArgumentException("textOpacity out of range [0, 255]: " + textOpacity);
        }
    }

    /**
     * The legacy field-by-field constructor, retained so the persistence mapper and field-level callers are
     * unaffected by the new properties: the single {@code scale} is the uniform spatial scale (its dedicated
     * {@code SCALE} column), and the new properties take their defaults (no see-through, centred alignment, no
     * drop-shadow override, no translation).
     */
    public Appearance(
            Billboard billboard,
            int backgroundArgb,
            boolean textShadow,
            int brightnessBlock,
            int brightnessSky,
            float scale,
            int lineWidth,
            float viewRange) {
        this(
                billboard,
                backgroundArgb,
                textShadow,
                brightnessBlock,
                brightnessSky,
                Transform.ofUniformScale(scale),
                lineWidth,
                viewRange,
                false,
                TextAlignment.CENTER,
                DEFAULT_SHADOW,
                DEFAULT_SHADOW,
                DEFAULT_GLOW,
                DEFAULT_OPACITY);
    }

    /** The default styling: centred billboard, no overrides, vanilla scale/width/view-range. */
    public static Appearance defaults() {
        return new Appearance(
                Billboard.CENTER,
                DEFAULT_BACKGROUND,
                false,
                DEFAULT_BRIGHTNESS,
                DEFAULT_BRIGHTNESS,
                DEFAULT_SCALE,
                DEFAULT_LINE_WIDTH,
                DEFAULT_VIEW_RANGE);
    }

    /** Clamp a raw scale to the accepted range, so operator input never throws at the command boundary. */
    public static float clampScale(float raw) {
        return Math.min(MAX_SCALE, Math.max(MIN_SCALE, raw));
    }

    /** Clamp a raw line width to the accepted range. */
    public static int clampLineWidth(int raw) {
        return Math.min(MAX_LINE_WIDTH, Math.max(MIN_LINE_WIDTH, raw));
    }

    /** Clamp a raw view range to the accepted range. */
    public static float clampViewRange(float raw) {
        return Math.min(MAX_VIEW_RANGE, Math.max(MIN_VIEW_RANGE, raw));
    }

    /** Clamp a raw shadow radius/strength to the accepted range. */
    public static float clampShadow(float raw) {
        return Math.min(MAX_SHADOW, Math.max(MIN_SHADOW, raw));
    }

    /** Clamp a raw translation offset to the accepted range on every axis. */
    public static float clampTranslation(float raw) {
        float limit = MAX_VIEW_RANGE;
        return Math.min(limit, Math.max(-limit, raw));
    }

    /** Clamp a raw text opacity to the accepted 0 to 255 range, so operator input never throws at the boundary. */
    public static int clampOpacity(int raw) {
        return Math.min(MAX_OPACITY, Math.max(MIN_OPACITY, raw));
    }

    /** Whether {@code brightness} is the "no override" sentinel. */
    public static boolean isDefaultBrightness(int brightness) {
        return brightness == DEFAULT_BRIGHTNESS;
    }

    /** Whether {@code shadow} is the "no override" sentinel. */
    public static boolean isDefaultShadow(float shadow) {
        return shadow == DEFAULT_SHADOW;
    }

    /** The uniform spatial scale (the X axis), the value the {@code SCALE} column stores and round-trips. */
    public float scale() {
        return transform.scaleX();
    }

    /** Whether {@code argb} is the "no background override" sentinel. */
    public boolean hasBackground() {
        return backgroundArgb != DEFAULT_BACKGROUND;
    }

    /** Whether either light channel carries an override. */
    public boolean hasBrightness() {
        return !isDefaultBrightness(brightnessBlock) || !isDefaultBrightness(brightnessSky);
    }

    /** Whether the drop-shadow radius carries an override. */
    public boolean hasShadowRadius() {
        return !isDefaultShadow(shadowRadius);
    }

    /** Whether the drop-shadow strength carries an override. */
    public boolean hasShadowStrength() {
        return !isDefaultShadow(shadowStrength);
    }

    /** Whether the text carries a glowing-outline colour. */
    public boolean hasGlow() {
        return glowArgb != DEFAULT_GLOW;
    }

    /** Whether the text carries an opacity override. */
    public boolean hasTextOpacity() {
        return textOpacity != DEFAULT_OPACITY;
    }

    /** A pre-filled builder for the internal {@code with*} transitions; the public surface is unchanged. */
    AppearanceBuilder toBuilder() {
        return new AppearanceBuilder(this);
    }

    public Appearance withBillboard(Billboard value) {
        return toBuilder().billboard(Objects.requireNonNull(value, "value")).build();
    }

    public Appearance withBackgroundArgb(int value) {
        return toBuilder().backgroundArgb(value).build();
    }

    public Appearance withTextShadow(boolean value) {
        return toBuilder().textShadow(value).build();
    }

    public Appearance withBrightness(int block, int sky) {
        return toBuilder().brightness(block, sky).build();
    }

    /** A copy with a uniform scale on every axis (keeping any translation). */
    public Appearance withScale(float value) {
        return withTransform(transform.withUniformScale(value));
    }

    /** A copy with a per-axis scale (keeping any translation). */
    public Appearance withScale(float x, float y, float z) {
        return withTransform(transform.withScale(x, y, z));
    }

    /** A copy with a display-space translation offset (keeping the scale). */
    public Appearance withTranslation(float x, float y, float z) {
        return withTransform(transform.withTranslation(x, y, z));
    }

    /** A copy with a new spatial {@link Transform}. */
    public Appearance withTransform(Transform value) {
        return toBuilder().transform(Objects.requireNonNull(value, "value")).build();
    }

    public Appearance withLineWidth(int value) {
        return toBuilder().lineWidth(value).build();
    }

    public Appearance withViewRange(float value) {
        return toBuilder().viewRange(value).build();
    }

    public Appearance withSeeThrough(boolean value) {
        return toBuilder().seeThrough(value).build();
    }

    public Appearance withAlignment(TextAlignment value) {
        return toBuilder().alignment(Objects.requireNonNull(value, "value")).build();
    }

    public Appearance withShadowRadius(float value) {
        return toBuilder().shadowRadius(value).build();
    }

    public Appearance withShadowStrength(float value) {
        return toBuilder().shadowStrength(value).build();
    }

    /** A copy with a glowing-outline colour (packed ARGB), or {@link #DEFAULT_GLOW} to clear the glow. */
    public Appearance withGlowArgb(int value) {
        return toBuilder().glowArgb(value).build();
    }

    /** A copy with a text-opacity override (0 to 255), or {@link #DEFAULT_OPACITY} to clear it. */
    public Appearance withTextOpacity(int value) {
        return toBuilder().textOpacity(value).build();
    }
}
