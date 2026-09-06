package com.uxplima.uxmessentials.npc.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The "how an NPC looks" half of the {@link Npc} aggregate: its skin, the entity type it renders as, worn
 * equipment, the glow outline and its colour, the body pose, the size multiplier, and the per-entity-type
 * appearance metadata, plus the shown display name, the mirror-skin toggle, the collision and tab-visibility
 * toggles, the per-NPC view/turn distances, and the on-fire / invisible / silent state flags. Grouping these
 * visual fields into one immutable value object keeps the {@link Npc} aggregate small while leaving the public
 * surface unchanged: {@code Npc} delegates every visual transition and accessor here. An appearance is a value
 * object: each {@code with*} produces a new instance rather than mutating.
 *
 * <p>{@code skin} is the fake player's skin, or {@code null} for the default (Steve). {@code entityType} is the
 * uppercase Bukkit {@code EntityType} name the NPC renders as ({@code "PLAYER"} by default, the one type with the
 * tab-entry + skin path); it is a plain string so the domain stays Bukkit-free, and the adapter resolves it. The
 * skin is kept across a type change, so flipping a mob back to {@code PLAYER} restores its skin.
 *
 * <p>{@code equipment} maps each worn {@link EquipmentSlot} to an opaque item <em>token</em> stored verbatim
 * either a legacy material name ({@code DIAMOND_HELMET}) or a serialized full-item payload: that the render
 * adapter alone resolves to a real Bukkit item. A slot absent from the map is empty. {@code glowing} toggles the
 * outline; {@code glowColor} is the colour name ({@code RED}) it is tinted, or {@code null} for the default white.
 *
 * <p>{@code pose} is the uppercase pose name the NPC is frozen in ({@code "STANDING"} by default); an unknown name
 * renders standing rather than failing here. {@code scale} resizes the NPC ({@code 1.0} natural) and must be finite
 * and positive. {@code typeData} is the per-entity-type appearance metadata as opaque key/value strings the render
 * adapter alone interprets. The equipment and type-data maps are copied defensively so a stored snapshot is immutable.
 *
 * <p>{@code displayName} is the name shown above the NPC, distinct from its id, and carries three states rather than
 * two: {@code null} is unset and falls back to rendering the id (the default), a blank value is the explicitly
 * cleared sentinel that renders no name at all, and any other value is the shown label. The tab and profile name
 * stay the id in every case; only the rendered label changes. {@code mirrorSkin} renders
 * each viewer's own skin on the NPC (per-viewer, resolved at render time). {@code collidable} toggles whether the
 * NPC pushes players. {@code showInTab} keeps the NPC as a tab-list entry instead of hiding it after spawn.
 * {@code viewDistance}/{@code turnDistance} are per-NPC overrides of the module's render/look ranges, or
 * {@code null} to use the global default. {@code onFire}/{@code invisible}/{@code silent} are the shared-flags-and
 * -silence state toggles the adapter composes into one metadata frame.
 */
public record NpcAppearance(
        @Nullable NpcSkin skin,
        String entityType,
        Map<EquipmentSlot, String> equipment,
        boolean glowing,
        @Nullable String glowColor,
        String pose,
        double scale,
        Map<String, String> typeData,
        @Nullable String displayName,
        boolean mirrorSkin,
        boolean collidable,
        boolean showInTab,
        @Nullable Double viewDistance,
        @Nullable Double turnDistance,
        boolean onFire,
        boolean invisible,
        boolean silent) {

    /** The default entity type: a fake player, the one type with the tab-entry + skin path. */
    public static final String DEFAULT_ENTITY_TYPE = "PLAYER";

    /** The default body pose: the natural upright stance. */
    public static final String DEFAULT_POSE = "STANDING";

    /** The default size multiplier: the NPC's natural size. */
    public static final double DEFAULT_SCALE = 1.0;

    public NpcAppearance {
        equipment = copyEquipment(equipment);
        entityType = normalizeType(entityType);
        pose = normalizePose(pose);
        scale = validateScale(scale);
        typeData = copyTypeData(typeData);
        displayName = normalizeDisplayName(displayName);
        viewDistance = validateDistance(viewDistance, "viewDistance");
        turnDistance = validateDistance(turnDistance, "turnDistance");
    }

    /**
     * The legacy eight-field constructor, retained so the {@link Npc} compat constructor and any caller that builds
     * the visual half field-by-field default the later appearance additions (display name, mirror, collidable,
     * show-in-tab, view/turn distance, state flags) to their natural values.
     */
    public NpcAppearance(
            @Nullable NpcSkin skin,
            String entityType,
            Map<EquipmentSlot, String> equipment,
            boolean glowing,
            @Nullable String glowColor,
            String pose,
            double scale,
            Map<String, String> typeData) {
        this(
                skin,
                entityType,
                equipment,
                glowing,
                glowColor,
                pose,
                scale,
                typeData,
                null,
                false,
                false,
                false,
                null,
                null,
                false,
                false,
                false);
    }

    /** The default appearance for a freshly created NPC carrying the given (possibly {@code null}) skin. */
    static NpcAppearance defaults(@Nullable NpcSkin skin) {
        return new NpcAppearance(
                skin, DEFAULT_ENTITY_TYPE, Map.of(), false, null, DEFAULT_POSE, DEFAULT_SCALE, Map.of());
    }

    /** A pre-filled builder for the internal {@code with*} transitions; the public surface is unchanged. */
    NpcAppearanceBuilder toBuilder() {
        return new NpcAppearanceBuilder(this);
    }

    NpcAppearance withSkin(@Nullable NpcSkin newSkin) {
        return toBuilder().skin(newSkin).build();
    }

    NpcAppearance withEntityType(String newEntityType) {
        return toBuilder().entityType(newEntityType).build();
    }

    NpcAppearance withEquipment(EquipmentSlot slot, @Nullable String itemToken) {
        Objects.requireNonNull(slot, "slot");
        // An EnumMap copy-constructor rejects an empty source map, so build it by class and fill it.
        Map<EquipmentSlot, String> updated = new EnumMap<>(EquipmentSlot.class);
        updated.putAll(equipment);
        if (itemToken == null || itemToken.isBlank()) {
            updated.remove(slot);
        } else {
            updated.put(slot, itemToken);
        }
        return toBuilder().equipment(updated).build();
    }

    NpcAppearance withEquipmentCleared() {
        return toBuilder().equipment(Map.of()).build();
    }

    NpcAppearance withGlowing(boolean newGlowing) {
        return toBuilder().glowing(newGlowing).build();
    }

    NpcAppearance withGlowColor(@Nullable String newColor) {
        return toBuilder()
                .glowColor(newColor == null || newColor.isBlank() ? null : newColor)
                .build();
    }

    NpcAppearance withPose(String newPose) {
        return toBuilder().pose(newPose).build();
    }

    NpcAppearance withScale(double newScale) {
        return toBuilder().scale(newScale).build();
    }

    NpcAppearance withDisplayName(@Nullable String newDisplayName) {
        return toBuilder().displayName(newDisplayName).build();
    }

    NpcAppearance withMirrorSkin(boolean newMirrorSkin) {
        return toBuilder().mirrorSkin(newMirrorSkin).build();
    }

    NpcAppearance withCollidable(boolean newCollidable) {
        return toBuilder().collidable(newCollidable).build();
    }

    NpcAppearance withShowInTab(boolean newShowInTab) {
        return toBuilder().showInTab(newShowInTab).build();
    }

    NpcAppearance withViewDistance(@Nullable Double newViewDistance) {
        return toBuilder().viewDistance(newViewDistance).build();
    }

    NpcAppearance withTurnDistance(@Nullable Double newTurnDistance) {
        return toBuilder().turnDistance(newTurnDistance).build();
    }

    NpcAppearance withOnFire(boolean newOnFire) {
        return toBuilder().onFire(newOnFire).build();
    }

    NpcAppearance withInvisible(boolean newInvisible) {
        return toBuilder().invisible(newInvisible).build();
    }

    NpcAppearance withSilent(boolean newSilent) {
        return toBuilder().silent(newSilent).build();
    }

    NpcAppearance withTypeData(String key, @Nullable String value) {
        String trimmedKey = Objects.requireNonNull(key, "key").strip();
        if (trimmedKey.isEmpty()) {
            throw new IllegalArgumentException("type-data key must not be blank");
        }
        Map<String, String> updated = new LinkedHashMap<>(typeData);
        if (value == null || value.isBlank()) {
            updated.remove(trimmedKey);
        } else {
            updated.put(trimmedKey, value);
        }
        return toBuilder().typeData(updated).build();
    }

    boolean isPlayerType() {
        return DEFAULT_ENTITY_TYPE.equals(entityType);
    }

    boolean hasSkin() {
        return skin != null;
    }

    boolean hasEquipment() {
        return !equipment.isEmpty();
    }

    boolean hasGlowColor() {
        return glowColor != null && !glowColor.isBlank();
    }

    boolean hasPose() {
        return !DEFAULT_POSE.equals(pose);
    }

    boolean hasScale() {
        return Double.compare(scale, DEFAULT_SCALE) != 0;
    }

    boolean hasTypeData() {
        return !typeData.isEmpty();
    }

    boolean hasDisplayName() {
        return displayName != null && !displayName.isBlank();
    }

    /** Whether the label was explicitly cleared (the blank sentinel) rather than never set. */
    boolean displayNameHidden() {
        return displayName != null && displayName.isBlank();
    }

    /** Upper-case the entity-type name and reject a blank one: the type is always a non-blank uppercase name. */
    private static String normalizeType(String entityType) {
        Objects.requireNonNull(entityType, "entityType");
        String trimmed = entityType.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /** Upper-case the pose name and reject a blank one: the pose is always a non-blank uppercase name. */
    private static String normalizePose(String pose) {
        Objects.requireNonNull(pose, "pose");
        String trimmed = pose.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("pose must not be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /** Reject a non-finite or non-positive scale: the size multiplier is always a finite, positive number. */
    private static double validateScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and positive, was " + scale);
        }
        return scale;
    }

    /** A per-NPC distance override is either absent ({@code null}) or a finite, non-negative number of blocks. */
    private static @Nullable Double validateDistance(@Nullable Double distance, String name) {
        if (distance == null) {
            return null;
        }
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative, was " + distance);
        }
        return distance;
    }

    private static @Nullable String normalizeDisplayName(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.equals("-")
                || stripped.equalsIgnoreCase("none")
                || stripped.equalsIgnoreCase("clear")
                || stripped.equalsIgnoreCase("empty")
                || stripped.isEmpty()) {
            return " ";
        }
        if (stripped.equalsIgnoreCase("default") || stripped.equalsIgnoreCase("reset")) {
            return null;
        }
        return stripped;
    }

    /** An immutable, empty-tolerant copy of the equipment map keyed in slot order. */
    private static Map<EquipmentSlot, String> copyEquipment(@Nullable Map<EquipmentSlot, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new EnumMap<>(source));
    }

    /** An immutable, empty-tolerant copy of the type-data map. */
    private static Map<String, String> copyTypeData(@Nullable Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }
}
