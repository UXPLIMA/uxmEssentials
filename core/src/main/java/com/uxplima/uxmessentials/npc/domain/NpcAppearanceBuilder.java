package com.uxplima.uxmessentials.npc.domain;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A mutable builder for {@link NpcAppearance}, kept package-private so it is purely an internal mechanism: each
 * {@link NpcAppearance} {@code with*} transition reads {@link NpcAppearance#toBuilder()}, changes the one field it
 * owns, and calls {@link #build()}, which routes through the canonical {@code NpcAppearance} constructor, so every
 * normalisation and range check (entity-type/pose upper-casing, scale and distance validation, the defensive map
 * copies) still fires. Extracting the per-field copy boilerplate here keeps {@code NpcAppearance} small without
 * changing its public surface.
 */
final class NpcAppearanceBuilder {

    private @Nullable NpcSkin skin;
    private String entityType;
    private Map<EquipmentSlot, String> equipment;
    private boolean glowing;
    private @Nullable String glowColor;
    private String pose;
    private double scale;
    private Map<String, String> typeData;
    private @Nullable String displayName;
    private boolean mirrorSkin;
    private boolean collidable;
    private boolean showInTab;
    private @Nullable Double viewDistance;
    private @Nullable Double turnDistance;
    private boolean onFire;
    private boolean invisible;
    private boolean silent;

    NpcAppearanceBuilder(NpcAppearance source) {
        Objects.requireNonNull(source, "source");
        this.skin = source.skin();
        this.entityType = source.entityType();
        this.equipment = source.equipment();
        this.glowing = source.glowing();
        this.glowColor = source.glowColor();
        this.pose = source.pose();
        this.scale = source.scale();
        this.typeData = source.typeData();
        this.displayName = source.displayName();
        this.mirrorSkin = source.mirrorSkin();
        this.collidable = source.collidable();
        this.showInTab = source.showInTab();
        this.viewDistance = source.viewDistance();
        this.turnDistance = source.turnDistance();
        this.onFire = source.onFire();
        this.invisible = source.invisible();
        this.silent = source.silent();
    }

    NpcAppearanceBuilder skin(@Nullable NpcSkin value) {
        this.skin = value;
        return this;
    }

    NpcAppearanceBuilder entityType(String value) {
        this.entityType = value;
        return this;
    }

    NpcAppearanceBuilder equipment(Map<EquipmentSlot, String> value) {
        this.equipment = value;
        return this;
    }

    NpcAppearanceBuilder glowing(boolean value) {
        this.glowing = value;
        return this;
    }

    NpcAppearanceBuilder glowColor(@Nullable String value) {
        this.glowColor = value;
        return this;
    }

    NpcAppearanceBuilder pose(String value) {
        this.pose = value;
        return this;
    }

    NpcAppearanceBuilder scale(double value) {
        this.scale = value;
        return this;
    }

    NpcAppearanceBuilder typeData(Map<String, String> value) {
        this.typeData = value;
        return this;
    }

    NpcAppearanceBuilder displayName(@Nullable String value) {
        this.displayName = value;
        return this;
    }

    NpcAppearanceBuilder mirrorSkin(boolean value) {
        this.mirrorSkin = value;
        return this;
    }

    NpcAppearanceBuilder collidable(boolean value) {
        this.collidable = value;
        return this;
    }

    NpcAppearanceBuilder showInTab(boolean value) {
        this.showInTab = value;
        return this;
    }

    NpcAppearanceBuilder viewDistance(@Nullable Double value) {
        this.viewDistance = value;
        return this;
    }

    NpcAppearanceBuilder turnDistance(@Nullable Double value) {
        this.turnDistance = value;
        return this;
    }

    NpcAppearanceBuilder onFire(boolean value) {
        this.onFire = value;
        return this;
    }

    NpcAppearanceBuilder invisible(boolean value) {
        this.invisible = value;
        return this;
    }

    NpcAppearanceBuilder silent(boolean value) {
        this.silent = value;
        return this;
    }

    NpcAppearance build() {
        return new NpcAppearance(
                skin,
                entityType,
                equipment,
                glowing,
                glowColor,
                pose,
                scale,
                typeData,
                displayName,
                mirrorSkin,
                collidable,
                showInTab,
                viewDistance,
                turnDistance,
                onFire,
                invisible,
                silent);
    }
}
