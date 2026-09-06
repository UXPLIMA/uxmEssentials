package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Transform;
import com.uxplima.uxmlib.hologram.Holograms;
import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption mapping from the domain {@link Appearance} (pure enums and primitives) onto uxmLib's
 * {@code Holograms.Builder} setters. The builder owns the native {@code TextDisplay} translation; this class
 * only bridges our value object to it, and maps our {@link Billboard} / {@link TextAlignment} enums onto Paper's
 * {@code Display.Billboard} / {@code TextDisplay.TextAlignment}.
 *
 * <p>A sentinel value ("no override") is skipped so the underlying display keeps Paper's default rather than a
 * magic literal. Background ARGB maps through {@link Color#fromARGB(int)}, brightness through a
 * {@link Display.Brightness} built from the block/sky channels (each defaulting to 0 when only the other is set).
 * The spatial {@link Transform} (per-axis scale + translation) maps onto the builder's scale/translation setters;
 * the alignment and see-through are text-only and apply on the text path alone, while the drop-shadow
 * radius/strength are display-shared and apply on every path.
 */
@NullMarked
final class HologramAppearances {

    private HologramAppearances() {}

    /** Apply every non-default field of {@code appearance} onto a text {@code builder}. */
    static void apply(Holograms.Builder builder, Appearance appearance) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(appearance, "appearance");
        builder.billboard(toBukkit(appearance.billboard()));
        builder.textShadow(appearance.textShadow());
        builder.seeThrough(appearance.seeThrough());
        builder.alignment(toBukkitAlignment(appearance.alignment()));
        applyScale(builder, appearance.transform());
        applyTranslation(builder, appearance.transform());
        builder.lineWidth(appearance.lineWidth());
        builder.viewRange(appearance.viewRange());
        if (appearance.hasBackground()) {
            builder.background(Color.fromARGB(appearance.backgroundArgb()));
        }
        if (appearance.hasBrightness()) {
            builder.brightness(brightness(appearance));
        }
        if (appearance.hasGlow()) {
            builder.glow(Color.fromARGB(appearance.glowArgb()));
        }
        if (appearance.hasTextOpacity()) {
            builder.textOpacity((byte) appearance.textOpacity());
        }
        applyShadow(builder, appearance);
    }

    /**
     * Apply the {@link Display}-shared fields of {@code appearance}, billboard, scale, translation, view range,
     * brightness and the drop-shadow, onto an item or block {@code builder}. The text-only fields (background,
     * line width, text shadow, see-through, alignment) have no meaning for an item or block display, so they are
     * deliberately left off this path.
     */
    static void applyDisplay(Holograms.ModelBuilder<?> builder, Appearance appearance) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(appearance, "appearance");
        builder.billboard(toBukkit(appearance.billboard()));
        applyScale(builder, appearance.transform());
        applyTranslation(builder, appearance.transform());
        builder.viewRange(appearance.viewRange());
        if (appearance.hasBrightness()) {
            builder.brightness(brightness(appearance));
        }
        applyShadow(builder, appearance);
    }

    private static void applyScale(Holograms.Builder builder, Transform transform) {
        if (transform.isUniformScale()) {
            builder.scale(transform.scaleX());
        } else {
            builder.scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
        }
    }

    private static void applyScale(Holograms.ModelBuilder<?> builder, Transform transform) {
        if (transform.isUniformScale()) {
            builder.scale(transform.scaleX());
        } else {
            builder.scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
        }
    }

    private static void applyTranslation(Holograms.Builder builder, Transform transform) {
        if (transform.hasTranslation()) {
            builder.translation(transform.translationX(), transform.translationY(), transform.translationZ());
        }
    }

    private static void applyTranslation(Holograms.ModelBuilder<?> builder, Transform transform) {
        if (transform.hasTranslation()) {
            builder.translation(transform.translationX(), transform.translationY(), transform.translationZ());
        }
    }

    private static void applyShadow(Holograms.Builder builder, Appearance appearance) {
        if (appearance.hasShadowRadius()) {
            builder.shadowRadius(appearance.shadowRadius());
        }
        if (appearance.hasShadowStrength()) {
            builder.shadowStrength(appearance.shadowStrength());
        }
    }

    private static void applyShadow(Holograms.ModelBuilder<?> builder, Appearance appearance) {
        if (appearance.hasShadowRadius()) {
            builder.shadowRadius(appearance.shadowRadius());
        }
        if (appearance.hasShadowStrength()) {
            builder.shadowStrength(appearance.shadowStrength());
        }
    }

    private static Display.Brightness brightness(Appearance appearance) {
        int block = Appearance.isDefaultBrightness(appearance.brightnessBlock()) ? 0 : appearance.brightnessBlock();
        int sky = Appearance.isDefaultBrightness(appearance.brightnessSky()) ? 0 : appearance.brightnessSky();
        return new Display.Brightness(block, sky);
    }

    static Display.Billboard toBukkit(Billboard billboard) {
        return switch (billboard) {
            case CENTER -> Display.Billboard.CENTER;
            case FIXED -> Display.Billboard.FIXED;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
        };
    }

    static TextDisplay.TextAlignment toBukkitAlignment(TextAlignment alignment) {
        return switch (alignment) {
            case CENTER -> TextDisplay.TextAlignment.CENTER;
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
        };
    }
}
