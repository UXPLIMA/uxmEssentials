package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmlib.nametag.Alignment;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.Billboard;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Maps the core {@link NametagAppearance} (operator strings and primitives, no rendering library) onto the packet
 * renderer's {@link Appearance}. This is the adapter seam that keeps the appearance model itself in {@code :core}
 * the domain only validates the structural and range invariants, and the platform interpretation lives here.
 *
 * <p>Tolerant throughout, by design: a server admin's typo in a colour, billboard, or out-of-range number must never
 * throw and blank every nametag. An unparseable billboard falls back to {@link Billboard#CENTER}; an unparseable
 * background colour is dropped (the transparent renderer default is kept) rather than substituted.
 *
 * <h2>Background</h2>
 *
 * The packet {@link Appearance} carries the background as a single ARGB int ({@code 0} = the renderer's transparent
 * default), so an operator's {@code background-color} and {@code background-opacity} are folded into one int: the
 * colour supplies the RGB, the opacity (or the colour's own authored alpha, or full opacity) supplies the A. A
 * background opacity with no colour to carry it is dropped, exactly as the old hologram mapper did.
 *
 * <h2>Offset and scale</h2>
 *
 * {@code y-offset} becomes the {@code translation} Y (the renderer spawns the line stack at the target's feet and
 * lifts it by this), and {@code scale} becomes a uniform {@code scale} vector. The packet {@link Appearance} has no
 * glow field, so an authored {@code glow-color} is not carried. Glow is a whole-entity outline the packet path does
 * not drive per viewer; it is dropped tolerantly rather than failing the format.
 */
@NullMarked
public final class NametagAppearanceMapper {

    // The alpha an occluded line fades to when hide-through-blocks is on and the operator authored no obscured-opacity:
    // a dim-but-legible quarter opacity, matching the packet renderer's own default.
    private static final int DEFAULT_OBSCURED_OPACITY = 64;

    private NametagAppearanceMapper() {}

    /** Build the packet {@link Appearance} for {@code appearance}, leaving every unauthored field at the lib default. */
    public static Appearance toAppearance(NametagAppearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        Appearance defaults = Appearance.defaults();
        // The lib Appearance rejects a negative line width; the domain does not range-check it, so clamp here to stay
        // tolerant (a typo'd negative width keeps the default rather than throwing and blanking the format).
        int lineWidth = Math.max(0, appearance.lineWidth().orElse(defaults.lineWidth()));
        float viewRange = appearance.viewRange().isPresent()
                ? (float) appearance.viewRange().getAsDouble()
                : defaults.viewRange();
        int obscured = appearance.obscuredOpacity().orElse(DEFAULT_OBSCURED_OPACITY);
        return new Appearance(
                billboard(appearance.billboard()),
                backgroundArgb(appearance.backgroundColor(), appearance),
                appearance.textShadow(),
                appearance.seeThrough(),
                Alignment.CENTER,
                lineWidth,
                viewRange,
                new Vector3f(0f, (float) appearance.yOffset(), 0f),
                new Vector3f((float) appearance.scale()),
                defaults.interpolationDurationTicks(),
                appearance.hideThroughBlocks(),
                obscured);
    }

    /** Parse {@code raw} into a packet billboard, tolerant of case and unknown values (→ {@link Billboard#CENTER}). */
    static Billboard billboard(String raw) {
        try {
            return Billboard.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Billboard.CENTER;
        }
    }

    // The background ARGB the renderer reads: the parsed colour supplies the RGB and the operator opacity (else the
    // colour's authored alpha, else full opacity) supplies the A. With no background colour authored there is nothing
    // to carry the opacity, so the transparent renderer default (0) is kept rather than inventing a colour.
    static int backgroundArgb(Optional<String> rawColor, NametagAppearance appearance) {
        Argb base = parseArgb(rawColor);
        if (base == null) {
            return Appearance.defaults().backgroundArgb();
        }
        int alpha = appearance.backgroundOpacity().orElse(base.alpha());
        return (clampByte(alpha) << 24) | (base.red() << 16) | (base.green() << 8) | base.blue();
    }

    // Parse "#RRGGBB" or "#AARRGGBB" (the leading # optional) into RGBA components, tolerant: a blank/absent/
    // unparseable value yields null so the caller keeps the renderer default rather than substituting a wrong colour.
    private static @Nullable Argb parseArgb(Optional<String> raw) {
        if (raw.isEmpty()) {
            return null;
        }
        String hex = raw.get().trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            long argb = Long.parseLong(hex, 16);
            // No alpha authored in the string means full opacity; an 8-digit string carries its own alpha.
            int alpha = hex.length() == 6 ? 255 : (int) (argb >> 24) & 0xFF;
            return new Argb(alpha, (int) (argb >> 16) & 0xFF, (int) (argb >> 8) & 0xFF, (int) argb & 0xFF);
        } catch (NumberFormatException notHex) {
            return null;
        }
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record Argb(int alpha, int red, int green, int blue) {}
}
