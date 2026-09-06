package com.uxplima.uxmessentials.holograms.domain;

/**
 * A hologram's stored display rotation: a yaw (about the vertical axis) and a pitch (about the horizontal
 * axis), both in degrees. Pure value object (no Bukkit), the adapter maps it onto the native display
 * transform at the rendering boundary, like the rest of the {@link Appearance}.
 *
 * <p>A stored rotation is kept separate from the hologram's location look-direction (the {@code yaw}/{@code
 * pitch} on its {@link com.uxplima.uxmessentials.shared.domain.Position}, which only orients a centred
 * billboard's anchor): this is the explicit operator-set spin of the display itself, and it is only visually
 * meaningful with a {@link Billboard#FIXED} billboard. A billboard that always faces the viewer overrides any
 * transform rotation. The operator sets the billboard to {@code FIXED} separately; the renderer applies this
 * rotation regardless and lets the billboard decide whether it shows.
 *
 * <p>Angles are normalised to the {@code [0, 360)} range at construction so a stored value round-trips
 * predictably and {@code 360} and {@code 0} compare equal. The values must be finite.
 *
 * @param yaw the rotation about the vertical axis, in degrees, normalised to {@code [0, 360)}
 * @param pitch the rotation about the horizontal axis, in degrees, normalised to {@code [0, 360)}
 */
public record Rotation(float yaw, float pitch) {

    /** No rotation: both angles zero; what a new hologram and an un-rotated stored row both resolve to. */
    public static final Rotation NONE = new Rotation(0f, 0f);

    private static final float FULL_TURN = 360f;

    public Rotation {
        yaw = normalise(yaw);
        pitch = normalise(pitch);
    }

    /** A rotation built from raw operator input, wrapping each angle into {@code [0, 360)}. */
    public static Rotation of(float yaw, float pitch) {
        return new Rotation(yaw, pitch);
    }

    /** Whether either angle is non-zero: i.e. this rotation is anything other than {@link #NONE}. */
    public boolean isRotated() {
        return yaw != 0f || pitch != 0f;
    }

    private static float normalise(float degrees) {
        if (!Float.isFinite(degrees)) {
            throw new IllegalArgumentException("rotation angle must be finite: " + degrees);
        }
        float wrapped = degrees % FULL_TURN;
        return wrapped < 0f ? wrapped + FULL_TURN : wrapped;
    }
}
