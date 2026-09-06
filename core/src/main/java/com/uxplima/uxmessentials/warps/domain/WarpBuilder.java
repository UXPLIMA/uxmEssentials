package com.uxplima.uxmessentials.warps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A mutable builder for {@link Warp}, kept package-private so it is purely an internal mechanism: each
 * {@link Warp} {@code with*}/transition reads {@link Warp#toBuilder()}, changes the one field it owns, and calls
 * {@link #build()}, which routes through the canonical {@code Warp} constructor, so every null-check still fires.
 * Extracting the per-field copy boilerplate here keeps {@code Warp} small without changing its public surface.
 */
final class WarpBuilder {

    private WarpName name;
    private Position location;
    private PlayerRef owner;
    private Instant createdAt;
    private WarpCost cost;
    private Optional<String> requiredPermission;
    private long visitors;
    private Optional<String> password;
    private boolean isLocked;
    private java.util.List<WelcomeMessage> welcomeMessages;
    private Optional<String> departureSound;
    private Optional<String> arrivalSound;
    private Optional<String> departureParticle;
    private Optional<String> arrivalParticle;
    private Optional<Double> warmupOverrideSeconds;
    private Optional<Double> cooldownOverrideSeconds;
    private Optional<String> iconMaterial;
    private Optional<String> categoryId;

    WarpBuilder(Warp source) {
        Objects.requireNonNull(source, "source");
        this.name = source.name();
        this.location = source.location();
        this.owner = source.owner();
        this.createdAt = source.createdAt();
        this.cost = source.cost();
        this.requiredPermission = source.requiredPermission();
        this.visitors = source.visitors();
        this.password = source.password();
        this.isLocked = source.isLocked();
        this.welcomeMessages = source.welcomeMessages();
        this.departureSound = source.departureSound();
        this.arrivalSound = source.arrivalSound();
        this.departureParticle = source.departureParticle();
        this.arrivalParticle = source.arrivalParticle();
        this.warmupOverrideSeconds = source.warmupOverrideSeconds();
        this.cooldownOverrideSeconds = source.cooldownOverrideSeconds();
        this.iconMaterial = source.iconMaterial();
        this.categoryId = source.categoryId();
    }

    WarpBuilder location(Position value) {
        this.location = value;
        return this;
    }

    WarpBuilder cost(WarpCost value) {
        this.cost = value;
        return this;
    }

    WarpBuilder visitors(long value) {
        this.visitors = value;
        return this;
    }

    WarpBuilder password(Optional<String> value) {
        this.password = value;
        return this;
    }

    WarpBuilder isLocked(boolean value) {
        this.isLocked = value;
        return this;
    }

    WarpBuilder welcomeMessages(java.util.List<WelcomeMessage> value) {
        this.welcomeMessages = value;
        return this;
    }

    WarpBuilder departureSound(Optional<String> value) {
        this.departureSound = value;
        return this;
    }

    WarpBuilder arrivalSound(Optional<String> value) {
        this.arrivalSound = value;
        return this;
    }

    WarpBuilder departureParticle(Optional<String> value) {
        this.departureParticle = value;
        return this;
    }

    WarpBuilder arrivalParticle(Optional<String> value) {
        this.arrivalParticle = value;
        return this;
    }

    WarpBuilder warmupOverrideSeconds(Optional<Double> value) {
        this.warmupOverrideSeconds = value;
        return this;
    }

    WarpBuilder cooldownOverrideSeconds(Optional<Double> value) {
        this.cooldownOverrideSeconds = value;
        return this;
    }

    WarpBuilder iconMaterial(Optional<String> value) {
        this.iconMaterial = value;
        return this;
    }

    WarpBuilder categoryId(Optional<String> value) {
        this.categoryId = value;
        return this;
    }

    Warp build() {
        return new Warp(
                name,
                location,
                owner,
                createdAt,
                cost,
                requiredPermission,
                visitors,
                password,
                isLocked,
                welcomeMessages,
                departureSound,
                arrivalSound,
                departureParticle,
                arrivalParticle,
                warmupOverrideSeconds,
                cooldownOverrideSeconds,
                iconMaterial,
                categoryId);
    }
}
