package com.uxplima.uxmessentials.holograms.domain;

import java.util.Objects;

/**
 * A mutable builder for {@link Appearance}, kept package-private so it is purely an internal mechanism: each
 * {@link Appearance} {@code with*} transition reads {@link Appearance#toBuilder()}, changes the one field it owns,
 * and calls {@link #build()}, which routes through the canonical {@code Appearance} constructor, so every
 * validation and range check still fires. Extracting the per-field copy boilerplate here keeps {@code Appearance}
 * itself small without changing its public surface (no new public type, no new public method beyond the
 * unchanged {@code with*}).
 */
final class AppearanceBuilder {

    private Billboard billboard;
    private int backgroundArgb;
    private boolean textShadow;
    private int brightnessBlock;
    private int brightnessSky;
    private Transform transform;
    private int lineWidth;
    private float viewRange;
    private boolean seeThrough;
    private TextAlignment alignment;
    private float shadowRadius;
    private float shadowStrength;
    private int glowArgb;
    private int textOpacity;

    AppearanceBuilder(Appearance source) {
        Objects.requireNonNull(source, "source");
        this.billboard = source.billboard();
        this.backgroundArgb = source.backgroundArgb();
        this.textShadow = source.textShadow();
        this.brightnessBlock = source.brightnessBlock();
        this.brightnessSky = source.brightnessSky();
        this.transform = source.transform();
        this.lineWidth = source.lineWidth();
        this.viewRange = source.viewRange();
        this.seeThrough = source.seeThrough();
        this.alignment = source.alignment();
        this.shadowRadius = source.shadowRadius();
        this.shadowStrength = source.shadowStrength();
        this.glowArgb = source.glowArgb();
        this.textOpacity = source.textOpacity();
    }

    AppearanceBuilder billboard(Billboard value) {
        this.billboard = value;
        return this;
    }

    AppearanceBuilder backgroundArgb(int value) {
        this.backgroundArgb = value;
        return this;
    }

    AppearanceBuilder textShadow(boolean value) {
        this.textShadow = value;
        return this;
    }

    AppearanceBuilder brightness(int block, int sky) {
        this.brightnessBlock = block;
        this.brightnessSky = sky;
        return this;
    }

    AppearanceBuilder transform(Transform value) {
        this.transform = value;
        return this;
    }

    AppearanceBuilder lineWidth(int value) {
        this.lineWidth = value;
        return this;
    }

    AppearanceBuilder viewRange(float value) {
        this.viewRange = value;
        return this;
    }

    AppearanceBuilder seeThrough(boolean value) {
        this.seeThrough = value;
        return this;
    }

    AppearanceBuilder alignment(TextAlignment value) {
        this.alignment = value;
        return this;
    }

    AppearanceBuilder shadowRadius(float value) {
        this.shadowRadius = value;
        return this;
    }

    AppearanceBuilder shadowStrength(float value) {
        this.shadowStrength = value;
        return this;
    }

    AppearanceBuilder glowArgb(int value) {
        this.glowArgb = value;
        return this;
    }

    AppearanceBuilder textOpacity(int value) {
        this.textOpacity = value;
        return this;
    }

    Appearance build() {
        return new Appearance(
                billboard,
                backgroundArgb,
                textShadow,
                brightnessBlock,
                brightnessSky,
                transform,
                lineWidth,
                viewRange,
                seeThrough,
                alignment,
                shadowRadius,
                shadowStrength,
                glowArgb,
                textOpacity);
    }
}
