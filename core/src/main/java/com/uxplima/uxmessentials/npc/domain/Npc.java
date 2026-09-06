package com.uxplima.uxmessentials.npc.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide fake-player NPC: a {@link NpcName}, the {@link Position} it stands at, its visual
 * {@link NpcAppearance} (skin, entity type, equipment, glow, pose, scale, type-data) and its interactive
 * {@link NpcBehavior} (click command, look-at-player, action list), and the moment it was created. An NPC is a
 * value object. Moving it, re-skinning it, or rebinding its click produces a new instance rather than mutating in
 * place, so the aggregate is always in a valid state and a repository save records a fully-formed snapshot.
 *
 * <p>The visual and behavioural fields are grouped into the {@link NpcAppearance} and {@link NpcBehavior} value
 * objects to keep this aggregate small; {@code Npc} exposes the same per-field transitions and accessors it always
 * did ({@code withSkin}, {@code withEquipment}, {@code skin()}, {@code equipment()}, …) and simply delegates each to
 * the owning value object. The legacy fourteen-argument constructor is retained so the persistence mapper and any
 * caller that builds an NPC field-by-field are unaffected by the grouping.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the NPC's world is
 * read from {@code location().world()} rather than held separately. {@code createdAt} is preserved across every
 * transition (a move, re-skin, rebind, or appearance change).
 *
 * @param name the NPC's canonical, server-unique name
 * @param location where the NPC stands and which way it faces
 * @param appearance the visual half: skin, entity type, equipment, glow, pose, scale, type-data
 * @param behavior the interactive half: click command, look-at-player, action list
 * @param createdAt when the NPC was first created (preserved across every transition)
 * @param owner the UUID of the player who created the NPC, or {@code null} for a server/console-created one with
 *     no owner (preserved across every transition); used by the per-player creation quota
 */
public record Npc(
        NpcName name,
        Position location,
        NpcAppearance appearance,
        NpcBehavior behavior,
        Instant createdAt,
        @Nullable UUID owner) {

    /** The default entity type: a fake player, the one type with the tab-entry + skin path. */
    public static final String DEFAULT_ENTITY_TYPE = NpcAppearance.DEFAULT_ENTITY_TYPE;

    /** The default body pose: the natural upright stance. */
    public static final String DEFAULT_POSE = NpcAppearance.DEFAULT_POSE;

    /** The default size multiplier: the NPC's natural size. */
    public static final double DEFAULT_SCALE = NpcAppearance.DEFAULT_SCALE;

    public Npc {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * The legacy field-by-field constructor, retained so the persistence mapper and field-level callers are
     * unaffected by the appearance/behavior grouping. The visual fields are folded into an {@link NpcAppearance}
     * and the interactive fields into an {@link NpcBehavior}, each of which applies the same validation and
     * defensive copying it always did.
     */
    public Npc(
            NpcName name,
            Position location,
            @Nullable NpcSkin skin,
            @Nullable String clickCommand,
            boolean lookAtPlayer,
            Map<EquipmentSlot, String> equipment,
            boolean glowing,
            @Nullable String glowColor,
            List<ClickAction> actions,
            String entityType,
            String pose,
            double scale,
            Map<String, String> typeData,
            Instant createdAt) {
        this(
                name,
                location,
                new NpcAppearance(skin, entityType, equipment, glowing, glowColor, pose, scale, typeData),
                new NpcBehavior(clickCommand, lookAtPlayer, actions),
                createdAt,
                null);
    }

    /** A new NPC created now at {@code location} with the given (possibly {@code null}) skin, no command, looking. */
    public static Npc create(NpcName name, Position location, @Nullable NpcSkin skin, Instant createdAt) {
        return new Npc(name, location, NpcAppearance.defaults(skin), NpcBehavior.defaults(), createdAt, null);
    }

    // --- Appearance accessors (delegated, so existing call sites are unchanged) ---

    public @Nullable NpcSkin skin() {
        return appearance.skin();
    }

    public Map<EquipmentSlot, String> equipment() {
        return appearance.equipment();
    }

    public boolean glowing() {
        return appearance.glowing();
    }

    public @Nullable String glowColor() {
        return appearance.glowColor();
    }

    public String entityType() {
        return appearance.entityType();
    }

    public String pose() {
        return appearance.pose();
    }

    public double scale() {
        return appearance.scale();
    }

    public Map<String, String> typeData() {
        return appearance.typeData();
    }

    public @Nullable String displayName() {
        return appearance.displayName();
    }

    public boolean mirrorSkin() {
        return appearance.mirrorSkin();
    }

    public boolean collidable() {
        return appearance.collidable();
    }

    public boolean showInTab() {
        return appearance.showInTab();
    }

    public @Nullable Double viewDistance() {
        return appearance.viewDistance();
    }

    public @Nullable Double turnDistance() {
        return appearance.turnDistance();
    }

    public boolean onFire() {
        return appearance.onFire();
    }

    public boolean invisible() {
        return appearance.invisible();
    }

    public boolean silent() {
        return appearance.silent();
    }

    // --- Behavior accessors (delegated) ---

    public @Nullable String clickCommand() {
        return behavior.clickCommand();
    }

    public boolean lookAtPlayer() {
        return behavior.lookAtPlayer();
    }

    public List<ClickAction> actions() {
        return behavior.actions();
    }

    public long interactionCooldownMillis() {
        return behavior.interactionCooldownMillis();
    }

    // --- Transitions (delegated; createdAt and the untouched half are always preserved) ---

    /** A copy re-anchored to {@code newLocation}, keeping everything else. */
    public Npc movedTo(Position newLocation) {
        Objects.requireNonNull(newLocation, "newLocation");
        return new Npc(name, newLocation, appearance, behavior, createdAt, owner);
    }

    /** A copy owned by {@code newOwner} (or {@code null} for no owner), keeping everything else. */
    public Npc withOwner(@Nullable UUID newOwner) {
        return new Npc(name, location, appearance, behavior, createdAt, newOwner);
    }

    /** Whether this NPC has a recorded owner (a server/console-created NPC has none). */
    public boolean hasOwner() {
        return owner != null;
    }

    /** A copy wearing {@code newSkin} (or {@code null} to reset to the default skin), keeping everything else. */
    public Npc withSkin(@Nullable NpcSkin newSkin) {
        return new Npc(name, location, appearance.withSkin(newSkin), behavior, createdAt, owner);
    }

    /** A copy whose click runs {@code newCommand} (or {@code null} to clear it), keeping everything else. */
    public Npc withClickCommand(@Nullable String newCommand) {
        return new Npc(name, location, appearance, behavior.withClickCommand(newCommand), createdAt, owner);
    }

    /** A copy that does or does not rotate to face nearby viewers, keeping everything else. */
    public Npc withLookAtPlayer(boolean newLookAtPlayer) {
        return new Npc(name, location, appearance, behavior.withLookAtPlayer(newLookAtPlayer), createdAt, owner);
    }

    /**
     * A copy rendered as {@code newEntityType} (the uppercase Bukkit {@code EntityType} name), keeping everything
     * else including the skin. The name is upper-cased and must be non-blank; whether it is a real, living type is
     * the adapter's concern, validated at the command boundary before this is called.
     */
    public Npc withEntityType(String newEntityType) {
        return new Npc(name, location, appearance.withEntityType(newEntityType), behavior, createdAt, owner);
    }

    /**
     * A copy with {@code slot} set to {@code itemToken} (or cleared when {@code itemToken} is {@code null} or
     * blank), keeping everything else. The token is stored verbatim and uninterpreted. It is either a legacy
     * material name or a serialized full-item payload, and the adapter resolves it to a real item at render time,
     * so an unresolvable token simply renders no item in that slot rather than failing here.
     */
    public Npc withEquipment(EquipmentSlot slot, @Nullable String itemToken) {
        return new Npc(name, location, appearance.withEquipment(slot, itemToken), behavior, createdAt, owner);
    }

    /** A copy with every equipment slot cleared, keeping everything else. */
    public Npc withEquipmentCleared() {
        return new Npc(name, location, appearance.withEquipmentCleared(), behavior, createdAt, owner);
    }

    /** A copy whose outline does or does not glow, keeping everything else (and its colour). */
    public Npc withGlowing(boolean newGlowing) {
        return new Npc(name, location, appearance.withGlowing(newGlowing), behavior, createdAt, owner);
    }

    /** A copy whose glow outline is tinted {@code newColor} (or {@code null} for the default white), keeping the rest. */
    public Npc withGlowColor(@Nullable String newColor) {
        return new Npc(name, location, appearance.withGlowColor(newColor), behavior, createdAt, owner);
    }

    /**
     * A copy frozen in {@code newPose} (the pose name, upper-cased and required non-blank), keeping everything else.
     * Whether the name is one the renderer can strike is the adapter's concern, validated at the command boundary;
     * an unknown name renders standing rather than failing here.
     */
    public Npc withPose(String newPose) {
        return new Npc(name, location, appearance.withPose(newPose), behavior, createdAt, owner);
    }

    /** A copy resized to {@code newScale} ({@code 1.0} is the natural size), keeping everything else. */
    public Npc withScale(double newScale) {
        return new Npc(name, location, appearance.withScale(newScale), behavior, createdAt, owner);
    }

    /**
     * A copy with the type-data {@code key} set to {@code value} (or cleared when {@code value} is {@code null} or
     * blank), keeping everything else. The key must be non-blank; both key and value are stored verbatim and never
     * interpreted here. Whether the key applies to this NPC's entity type, and how the value parses, is the render
     * adapter's concern, so an unsupported or unparseable pair simply renders nothing rather than failing here.
     */
    public Npc withTypeData(String key, @Nullable String value) {
        return new Npc(name, location, appearance.withTypeData(key, value), behavior, createdAt, owner);
    }

    /** A copy whose shown name is {@code newDisplayName} (or {@code null}/blank to hide it), keeping everything else. */
    public Npc withDisplayName(@Nullable String newDisplayName) {
        return new Npc(name, location, appearance.withDisplayName(newDisplayName), behavior, createdAt, owner);
    }

    /** A copy that does or does not mirror each viewer's own skin, keeping everything else. */
    public Npc withMirrorSkin(boolean newMirrorSkin) {
        return new Npc(name, location, appearance.withMirrorSkin(newMirrorSkin), behavior, createdAt, owner);
    }

    /** A copy that does or does not collide with players, keeping everything else. */
    public Npc withCollidable(boolean newCollidable) {
        return new Npc(name, location, appearance.withCollidable(newCollidable), behavior, createdAt, owner);
    }

    /** A copy that does or does not stay a tab-list entry, keeping everything else. */
    public Npc withShowInTab(boolean newShowInTab) {
        return new Npc(name, location, appearance.withShowInTab(newShowInTab), behavior, createdAt, owner);
    }

    /** A copy with a per-NPC render distance ({@code null} to use the global default), keeping everything else. */
    public Npc withViewDistance(@Nullable Double newViewDistance) {
        return new Npc(name, location, appearance.withViewDistance(newViewDistance), behavior, createdAt, owner);
    }

    /** A copy with a per-NPC look-at distance ({@code null} to use the global default), keeping everything else. */
    public Npc withTurnDistance(@Nullable Double newTurnDistance) {
        return new Npc(name, location, appearance.withTurnDistance(newTurnDistance), behavior, createdAt, owner);
    }

    /** A copy that does or does not render on fire, keeping everything else. */
    public Npc withOnFire(boolean newOnFire) {
        return new Npc(name, location, appearance.withOnFire(newOnFire), behavior, createdAt, owner);
    }

    /** A copy that is or is not invisible, keeping everything else. */
    public Npc withInvisible(boolean newInvisible) {
        return new Npc(name, location, appearance.withInvisible(newInvisible), behavior, createdAt, owner);
    }

    /** A copy that is or is not silent, keeping everything else. */
    public Npc withSilent(boolean newSilent) {
        return new Npc(name, location, appearance.withSilent(newSilent), behavior, createdAt, owner);
    }

    /**
     * A copy with a per-NPC interaction cooldown in milliseconds ({@code 0} to use the module-wide default),
     * keeping everything else. Negative values are rejected by the behavior value object.
     */
    public Npc withInteractionCooldownMillis(long newCooldownMillis) {
        return new Npc(
                name,
                location,
                appearance,
                behavior.withInteractionCooldownMillis(newCooldownMillis),
                createdAt,
                owner);
    }

    /** A copy with {@code action} appended to the end of the action list, keeping everything else. */
    public Npc withActionAdded(ClickAction action) {
        return new Npc(name, location, appearance, behavior.withActionAdded(action), createdAt, owner);
    }

    /**
     * A copy with the action at the 0-based {@code index} removed, keeping everything else. Throws
     * {@link IndexOutOfBoundsException} when {@code index} is outside the current action list.
     */
    public Npc withActionRemovedAt(int index) {
        return new Npc(name, location, appearance, behavior.withActionRemovedAt(index), createdAt, owner);
    }

    /** A copy with no actions, keeping everything else. */
    public Npc withActionsCleared() {
        return new Npc(name, location, appearance, behavior.withActionsCleared(), createdAt, owner);
    }

    /** A copy with {@code action} inserted at the 0-based {@code index} (an {@code index} of the size appends). */
    public Npc withActionInsertedAt(int index, ClickAction action) {
        return new Npc(name, location, appearance, behavior.withActionInsertedAt(index, action), createdAt, owner);
    }

    /** A copy with the action at the 0-based {@code index} replaced by {@code action}, keeping everything else. */
    public Npc withActionSetAt(int index, ClickAction action) {
        return new Npc(name, location, appearance, behavior.withActionSetAt(index, action), createdAt, owner);
    }

    /** A copy with the action at 0-based {@code from} moved to 0-based {@code to}, keeping everything else. */
    public Npc withActionMoved(int from, int to) {
        return new Npc(name, location, appearance, behavior.withActionMoved(from, to), createdAt, owner);
    }

    // --- Predicates (delegated) ---

    /** Whether this NPC renders as a fake player (the default type with the tab-entry + skin path). */
    public boolean isPlayerType() {
        return appearance.isPlayerType();
    }

    /** Whether this NPC carries a skin (a fake player with no skin renders the default Steve). */
    public boolean hasSkin() {
        return appearance.hasSkin();
    }

    /** Whether clicking this NPC runs a command. */
    public boolean hasClickCommand() {
        return behavior.hasClickCommand();
    }

    /** Whether this NPC wears anything in any slot. */
    public boolean hasEquipment() {
        return appearance.hasEquipment();
    }

    /** Whether a glow colour is set (a glowing NPC with no colour renders the default white outline). */
    public boolean hasGlowColor() {
        return appearance.hasGlowColor();
    }

    /** Whether clicking this NPC runs at least one action. */
    public boolean hasActions() {
        return behavior.hasActions();
    }

    /** Whether this NPC is frozen in a non-default pose (a default-posed NPC needs no pose packet). */
    public boolean hasPose() {
        return appearance.hasPose();
    }

    /** Whether this NPC is resized (a natural-size NPC needs no scale packet). */
    public boolean hasScale() {
        return appearance.hasScale();
    }

    /** Whether this NPC carries any per-entity-type appearance metadata (an empty map needs no metadata packet). */
    public boolean hasTypeData() {
        return appearance.hasTypeData();
    }

    /** Whether this NPC shows a display name distinct from its id (an unset or hidden one does not). */
    public boolean hasDisplayName() {
        return appearance.hasDisplayName();
    }

    /**
     * Whether this NPC's label was explicitly cleared, so it must render no name at all. This is distinct from
     * carrying no display name, which falls back to rendering the id.
     */
    public boolean displayNameHidden() {
        return appearance.displayNameHidden();
    }
}
