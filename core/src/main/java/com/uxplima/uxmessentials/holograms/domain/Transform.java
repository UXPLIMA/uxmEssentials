package com.uxplima.uxmessentials.holograms.domain;

/**
 * A hologram's spatial display transform, separate from the rest of the {@link Appearance} styling: a
 * display-space translation offset (x, y, z) and a per-axis scale. Pure value object (no Bukkit), the adapter
 * maps it onto the native display transformation at the rendering boundary. A uniform scale is the common case,
 * so {@link #ofUniformScale(float)} keeps the single-value form an operator and the stored {@code SCALE} column
 * both use, while {@link #withScale(float, float, float)} sets each axis independently.
 *
 * <p>The scale is clamped to the {@link Appearance} scale range so an out-of-range axis never reaches the
 * renderer; the translation must be finite. The default is no offset and unit scale on every axis.
 *
 * @param translationX the display-space offset along X (0 = no offset)
 * @param translationY the display-space offset along Y (0 = no offset)
 * @param translationZ the display-space offset along Z (0 = no offset)
 * @param scaleX the scale factor along X (1.0 = default size)
 * @param scaleY the scale factor along Y (1.0 = default size)
 * @param scaleZ the scale factor along Z (1.0 = default size)
 */
public record Transform(
        float translationX, float translationY, float translationZ, float scaleX, float scaleY, float scaleZ) {

    private static final float UNIT_SCALE = 1.0f;
    private static final float MAX_TRANSLATION = 256.0f;

    /** No offset, unit scale on every axis: what a default and an un-transformed stored row both resolve to. */
    public static final Transform DEFAULT = new Transform(0f, 0f, 0f, UNIT_SCALE, UNIT_SCALE, UNIT_SCALE);

    public Transform {
        requireFiniteScale(scaleX, "scaleX");
        requireFiniteScale(scaleY, "scaleY");
        requireFiniteScale(scaleZ, "scaleZ");
        requireFiniteTranslation(translationX, "translationX");
        requireFiniteTranslation(translationY, "translationY");
        requireFiniteTranslation(translationZ, "translationZ");
    }

    /** A transform with a uniform scale on every axis and no translation. */
    public static Transform ofUniformScale(float scale) {
        return new Transform(0f, 0f, 0f, scale, scale, scale);
    }

    /** This transform with each scale axis replaced. */
    public Transform withScale(float x, float y, float z) {
        return new Transform(translationX, translationY, translationZ, x, y, z);
    }

    /** This transform with a uniform scale on every axis (keeping the translation). */
    public Transform withUniformScale(float scale) {
        return new Transform(translationX, translationY, translationZ, scale, scale, scale);
    }

    /** This transform with the translation offset replaced (keeping the scale). */
    public Transform withTranslation(float x, float y, float z) {
        return new Transform(x, y, z, scaleX, scaleY, scaleZ);
    }

    /** Whether the scale is the same on every axis (the uniform case the {@code SCALE} column round-trips). */
    public boolean isUniformScale() {
        return scaleX == scaleY && scaleY == scaleZ;
    }

    /** Whether any translation axis is non-zero. */
    public boolean hasTranslation() {
        return translationX != 0f || translationY != 0f || translationZ != 0f;
    }

    private static void requireFiniteScale(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        if (value < Appearance.MIN_SCALE || value > Appearance.MAX_SCALE) {
            throw new IllegalArgumentException(
                    name + " out of range [" + Appearance.MIN_SCALE + ", " + Appearance.MAX_SCALE + "]: " + value);
        }
    }

    private static void requireFiniteTranslation(float value, String name) {
        if (!Float.isFinite(value) || Math.abs(value) > MAX_TRANSLATION) {
            throw new IllegalArgumentException(
                    name + " out of range [-" + MAX_TRANSLATION + ", " + MAX_TRANSLATION + "]: " + value);
        }
    }
}
