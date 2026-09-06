package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcSkinByName;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;

/**
 * The per-NPC property editor: a thin consumer of the shared {@link EntityEditorView} that exposes every NPC
 * property as one button wired to the existing npc use case. The properties read their current value fresh from
 * the {@link NpcRepository} on each open (the list-click snapshot would otherwise go stale after an edit), and
 * write through {@link NpcServices}, so the GUI adds no domain logic and stays in lockstep with the {@code /npc}
 * subcommands.
 *
 * <p>The skin property accepts a player name and resolves it off-thread through the same {@link NpcSkinByName}
 * the {@code /npc skin name:<player>} subcommand uses; a {@code -} clears the skin. The actions property edits the
 * typed click-action chain through the generic {@code ListProperty} via the {@link NpcActionLines} one-line codec.
 */
public final class NpcEditorView {

    /** Scale is edited as an integer hundredth (×100) so the stepper has no float precision drift. */
    private static final long SCALE_FACTOR = 100L;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final NpcRepository repository;
    private final NpcServices services;
    private final NpcSkinByName skinByName;
    private final TextInput textInput;
    private final Messages messages;
    private final NpcEditorSubLayouts sub;
    private final ColourPickerLayout colourPicker;
    private final ColourPickerText colourPickerText;
    private final EntityEditorView<Npc> view;

    public NpcEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            NpcRepository repository,
            NpcServices services,
            NpcSkinByName skinByName,
            TextInput textInput,
            Messages messages,
            EntityEditorLayout layout,
            NpcEditorSubLayouts sub,
            ColourPickerLayout colourPicker,
            BiConsumer<Player, PlayerRef> onBack) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.services = Objects.requireNonNull(services, "services");
        this.skinByName = Objects.requireNonNull(skinByName, "skinByName");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sub = Objects.requireNonNull(sub, "sub");
        this.colourPicker = Objects.requireNonNull(colourPicker, "colourPicker");
        this.colourPickerText = ColourPickerText.shared();
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(onBack, "onBack");
        this.view = EntityEditorView.<Npc>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(this::title)
                .valueLore(NpcMessageKey.NPC_GUI_EDITOR_VALUE_LORE)
                .backName(NpcMessageKey.NPC_GUI_EDITOR_BACK)
                .properties(this::properties)
                .onBack(onBack)
                .onDelete(
                        NpcMessageKey.NPC_GUI_EDITOR_DELETE,
                        NpcMessageKey.NPC_GUI_EDITOR_DELETE_CONFIRM,
                        (player, npc) -> services.delete().delete(ref(player), npc.name()))
                .build();
    }

    /** Open the editor for {@code npc}, scheduled on the viewer's entity thread by the framework. */
    public void open(Player player, PlayerRef viewer, Npc npc) {
        view.open(player, viewer, npc);
    }

    /** The underlying property grid: exposed for tests to resolve a slot to its property without a live click. */
    EntityEditorView<Npc> grid() {
        return view;
    }

    private Component title(PlayerRef viewer, Npc npc) {
        return guiText.text(
                viewer,
                NpcMessageKey.NPC_GUI_EDITOR_TITLE,
                Map.of("name", npc.name().value()));
    }

    private List<EditableProperty> properties(Npc npc) {
        NpcName name = npc.name();
        List<EditableProperty> props = new ArrayList<>();
        props.add(nameProperty(name));
        props.add(skinProperty(name));
        props.add(typeProperty(name));
        props.add(equipmentProperty(name));
        props.add(poseProperty(name));
        props.add(lookProperty(name));
        props.add(collidableProperty(name));
        props.add(glowProperty(name));
        props.add(glowColorProperty(name));
        props.add(displayNameProperty(name));
        props.add(mirrorProperty(name));
        props.add(scaleProperty(name));
        props.add(stateProperty(
                name, SetNpcState.Flag.ON_FIRE, NpcMessageKey.NPC_GUI_PROP_ON_FIRE, Material.FLINT_AND_STEEL));
        props.add(
                stateProperty(name, SetNpcState.Flag.INVISIBLE, NpcMessageKey.NPC_GUI_PROP_INVISIBLE, Material.GLASS));
        props.add(stateProperty(name, SetNpcState.Flag.SILENT, NpcMessageKey.NPC_GUI_PROP_SILENT, Material.BARRIER));
        props.add(showInTabProperty(name));
        props.add(actionsProperty(name));
        props.add(moveProperty(name));
        props.add(teleportProperty(name));
        return props;
    }

    // --- identity / skin ---

    private EditableProperty nameProperty(NpcName name) {
        return new TextProperty(
                "editor.text-field",
                NpcMessageKey.NPC_GUI_PROP_NAME,
                NpcMessageKey.NPC_GUI_PROP_NAME_PROMPT,
                Material.NAME_TAG,
                name::value,
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> rename(name, NpcName.of(value)),
                textInput,
                scheduler);
    }

    /**
     * Rename an NPC by copying it under the new name and deleting the original. There is no standalone rename
     * use case, so this composes the existing {@code copy} and {@code delete} use cases (the same pair an operator
     * would run by hand). A no-op when the new name equals the old. The copy lands at the original's position.
     */
    private void rename(NpcName from, NpcName to) {
        if (from.equals(to)) {
            return;
        }
        Optional<Npc> existing = repository.find(from);
        if (existing.isEmpty()) {
            return;
        }
        Position at = existing.get().location();
        if (services.copy().copy(GUI_ACTOR, from, to, at).isOk()) {
            services.delete().delete(GUI_ACTOR, from);
        }
    }

    private EditableProperty skinProperty(NpcName name) {
        return new TextProperty(
                "editor.text-field",
                NpcMessageKey.NPC_GUI_PROP_SKIN,
                NpcMessageKey.NPC_GUI_PROP_SKIN_PROMPT,
                Material.PLAYER_HEAD,
                () -> skinSummary(name),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> applySkin(name, value),
                textInput,
                scheduler);
    }

    /**
     * Apply a skin edit: a clearing token ({@code -}, {@code none}, {@code clear}) clears the skin through the
     * skin use case, anything else is treated as a player name and resolved off-thread through the same
     * {@link NpcSkinByName} the {@code /npc skin name:<player>} command uses. The skin-by-name flow runs on its
     * own async chain (it hops to the global thread to save), so this returns immediately.
     */
    private void applySkin(NpcName name, String raw) {
        String trimmed = raw.strip();
        if (trimmed.equals("-") || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("clear")) {
            services.skin().clearSkin(GUI_ACTOR, name);
            return;
        }
        skinByName.apply(GUI_ACTOR, name, trimmed);
    }

    // --- appearance enums ---

    private EditableProperty typeProperty(NpcName name) {
        // Each selectable type draws as its own spawn egg (the fake player as a head), so the picker reads like the
        // vanilla creative menu; selectorOptionIcon stays the fallback for a type with no spawn egg of its own.
        return new EnumProperty<>(
                NpcMessageKey.NPC_GUI_PROP_TYPE,
                NpcMessageKey.NPC_GUI_SELECT_TYPE,
                Material.VILLAGER_SPAWN_EGG,
                guiText,
                renderableTypes(),
                () -> currentType(name),
                (viewer, value) -> value.name(),
                value -> services.type().setEntityType(GUI_ACTOR, name, value.name()),
                sub.selectorOptionIcon(),
                NpcTypeIcon::iconFor,
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private EditableProperty poseProperty(NpcName name) {
        return new EnumProperty<>(
                NpcMessageKey.NPC_GUI_PROP_POSE,
                NpcMessageKey.NPC_GUI_SELECT_POSE,
                Material.ARMOR_STAND,
                guiText,
                POSES,
                () -> currentPose(name),
                (viewer, value) -> value,
                value -> services.pose().setPose(GUI_ACTOR, name, value),
                sub.selectorOptionIcon(),
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private EditableProperty glowColorProperty(NpcName name) {
        // The same glass colour-picker the hologram editor uses. An NPC's glow is a scoreboard-team colour, so a
        // chosen ARGB is snapped to the nearest of the sixteen named colours; the clear button drops the override.
        return new ColourProperty(
                NpcMessageKey.NPC_GUI_PROP_GLOW_COLOR,
                Material.GLOWSTONE,
                () -> glowArgb(name),
                argb -> setGlowColor(
                        name,
                        NamedTextColor.nearestTo(TextColor.color(argb & RGB_MASK))
                                .toString()),
                () -> setGlowColor(name, ""),
                NO_GLOW_OVERRIDE,
                this::defaultWord,
                guiText,
                colourPickerText,
                colourPicker,
                textInput,
                scheduler);
    }

    /** The current glow colour as an opaque ARGB int, or {@link #NO_GLOW_OVERRIDE} when no override is set. */
    private int glowArgb(NpcName name) {
        String raw = currentGlowColorRaw(name);
        if (raw.isBlank()) {
            return NO_GLOW_OVERRIDE;
        }
        NamedTextColor colour = NamedTextColor.NAMES.value(raw.toLowerCase(Locale.ROOT));
        return colour == null ? NO_GLOW_OVERRIDE : (OPAQUE_ALPHA | colour.value());
    }

    // --- toggles ---

    private EditableProperty lookProperty(NpcName name) {
        return ToggleProperty.ofBoolean(
                NpcMessageKey.NPC_GUI_PROP_LOOK,
                Material.ENDER_EYE,
                () -> current(name).map(Npc::lookAtPlayer).orElse(true),
                this::onOff,
                on -> services.look().setLookAtPlayer(GUI_ACTOR, name, on),
                scheduler);
    }

    private EditableProperty collidableProperty(NpcName name) {
        return ToggleProperty.ofBoolean(
                NpcMessageKey.NPC_GUI_PROP_COLLIDABLE,
                Material.SHIELD,
                () -> current(name).map(Npc::collidable).orElse(false),
                this::onOff,
                on -> services.collidable().setCollidable(GUI_ACTOR, name, on),
                scheduler);
    }

    private EditableProperty glowProperty(NpcName name) {
        return ToggleProperty.ofBoolean(
                NpcMessageKey.NPC_GUI_PROP_GLOW,
                Material.GLOWSTONE_DUST,
                () -> current(name).map(Npc::glowing).orElse(false),
                this::onOff,
                on -> services.glow().setGlowing(GUI_ACTOR, name, on, currentGlowColorRaw(name)),
                scheduler);
    }

    private EditableProperty mirrorProperty(NpcName name) {
        return ToggleProperty.ofBoolean(
                NpcMessageKey.NPC_GUI_PROP_MIRROR,
                Material.GLASS_PANE,
                () -> current(name).map(Npc::mirrorSkin).orElse(false),
                this::onOff,
                on -> services.mirror().setMirror(GUI_ACTOR, name, on),
                scheduler);
    }

    private EditableProperty stateProperty(NpcName name, SetNpcState.Flag flag, MessageKey label, Material icon) {
        return ToggleProperty.ofBoolean(
                label,
                icon,
                () -> stateValue(name, flag),
                this::onOff,
                on -> services.state().setState(GUI_ACTOR, name, flag, on),
                scheduler);
    }

    private EditableProperty showInTabProperty(NpcName name) {
        return ToggleProperty.ofBoolean(
                NpcMessageKey.NPC_GUI_PROP_SHOW_IN_TAB,
                Material.PAPER,
                () -> current(name).map(Npc::showInTab).orElse(false),
                this::onOff,
                on -> services.showInTab().setShowInTab(GUI_ACTOR, name, on),
                scheduler);
    }

    // --- numbers / text / sub-editors ---

    private EditableProperty scaleProperty(NpcName name) {
        return new NumberProperty(
                NpcMessageKey.NPC_GUI_PROP_SCALE,
                Material.CLOCK,
                () -> Math.round(current(name).map(Npc::scale).orElse(Npc.DEFAULT_SCALE) * SCALE_FACTOR),
                10,
                10,
                Math.round(0.0625 * SCALE_FACTOR),
                Math.round(16.0 * SCALE_FACTOR),
                value -> services.scale().setScale(GUI_ACTOR, name, value / (double) SCALE_FACTOR),
                scheduler);
    }

    private EditableProperty displayNameProperty(NpcName name) {
        return new TextProperty(
                "editor.text-field",
                NpcMessageKey.NPC_GUI_PROP_DISPLAY_NAME,
                NpcMessageKey.NPC_GUI_PROP_DISPLAY_NAME_PROMPT,
                Material.OAK_SIGN,
                () -> current(name)
                        .map(Npc::displayName)
                        .filter(s -> !s.isBlank())
                        .orElseGet(this::none),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> applyDisplayName(name, value),
                textInput,
                scheduler);
    }

    private EditableProperty equipmentProperty(NpcName name) {
        return new NpcEquipmentProperty(
                guiText,
                messages,
                sub.equipment(),
                () -> current(name).map(Npc::equipment).orElse(Map.of()),
                (slot, token) -> services.equip().setEquipment(GUI_ACTOR, name, slot, token),
                slot -> {
                    services.equip().setEquipment(GUI_ACTOR, name, slot, "");
                    return null;
                },
                scheduler);
    }

    private EditableProperty actionsProperty(NpcName name) {
        return new ListProperty(
                "editor.list-entry",
                NpcMessageKey.NPC_GUI_PROP_ACTIONS,
                Material.COMMAND_BLOCK,
                guiText,
                () -> currentActionLines(name),
                lines -> applyActions(name, lines),
                new ListPropertyText(
                        NpcMessageKey.NPC_GUI_ACTIONS_TITLE,
                        NpcMessageKey.NPC_GUI_ACTIONS_ENTRY_NAME,
                        NpcMessageKey.NPC_GUI_ACTIONS_ENTRY_HINTS,
                        NpcMessageKey.NPC_GUI_ACTIONS_ADD,
                        NpcMessageKey.NPC_GUI_ACTIONS_ADD_PROMPT,
                        NpcMessageKey.NPC_GUI_ACTIONS_EDIT_PROMPT,
                        NpcMessageKey.NPC_GUI_ACTIONS_REMOVE_CONFIRM,
                        NpcMessageKey.NPC_GUI_ACTIONS_BACK),
                sub.actions(),
                textInput,
                scheduler);
    }

    private EditableProperty moveProperty(NpcName name) {
        String hint = current(name).map(npc -> locationHint(npc.location())).orElse("");
        return new NpcActionButton(
                NpcMessageKey.NPC_GUI_PROP_MOVE,
                Material.COMPASS,
                hint,
                (player, reopen) -> {
                    Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
                    scheduler.async(() -> {
                        services.move().move(ref(player), name, at);
                        scheduler.onEntity(ref(player), reopen);
                    });
                },
                scheduler);
    }

    /**
     * The inverse of {@link #moveProperty}: teleport the viewer to the NPC's location, the GUI twin of
     * {@code /npc teleport <name>}. Reuses the existing {@code teleport} use case, which hands the destination to
     * the region-aware teleport adapter (Folia-safe {@code teleportAsync}); the editor is not reopened, the
     * viewer is leaving for the NPC, so the menu closes as the inventory click resolves. The value lore shows the
     * destination coordinates, the same hint the move button shows for the current anchor.
     */
    private EditableProperty teleportProperty(NpcName name) {
        String hint = current(name).map(npc -> locationHint(npc.location())).orElse("");
        return new NpcActionButton(
                NpcMessageKey.NPC_GUI_PROP_TELEPORT,
                Material.ENDER_PEARL,
                hint,
                (player, reopen) -> {
                    player.closeInventory();
                    scheduler.async(() -> services.teleport().teleport(ref(player), name));
                },
                scheduler);
    }

    /** The NPC's current anchor as a short "world x, y, z" line for the move button's Current value. */
    private static String locationHint(Position at) {
        return at.world().name() + " " + at.blockX() + ", " + at.blockY() + ", " + at.blockZ();
    }

    // --- write helpers (each is the existing use case) ---

    /**
     * Rewrite the whole action chain from the edited lines: clear, then re-add each line that parses (a line the
     * codec rejects is dropped). The clear + per-action add are the same use cases {@code /npc action clear|add}
     * use, so no new domain logic is introduced.
     */
    private void applyActions(NpcName name, List<String> lines) {
        services.clearActions().clear(GUI_ACTOR, name);
        for (String line : lines) {
            NpcActionLines.parse(line).ifPresent(action -> services.addAction().add(GUI_ACTOR, name, action));
        }
    }

    /**
     * Hand the typed name straight to the use case. The clear words ({@code -}, {@code none}, {@code clear},
     * {@code empty}) and the reset words ({@code default}, {@code reset}) are the appearance's to interpret, so the
     * anvil field behaves exactly like {@code /npc displayname} rather than carrying its own copy of the rules.
     */
    private void applyDisplayName(NpcName name, String raw) {
        services.displayName().setDisplayName(GUI_ACTOR, name, raw);
    }

    /** Set (or, with a blank {@code color}, clear) the glow-colour override, keeping the glowing flag as-is. */
    private void setGlowColor(NpcName name, String color) {
        boolean glowing = current(name).map(Npc::glowing).orElse(false);
        services.glow().setGlowing(GUI_ACTOR, name, glowing, color);
    }

    // --- fresh-state readers (the list-click snapshot would go stale after an edit) ---

    private Optional<Npc> current(NpcName name) {
        return repository.find(name);
    }

    private EntityType currentType(NpcName name) {
        String type = current(name).map(Npc::entityType).orElse(Npc.DEFAULT_ENTITY_TYPE);
        try {
            return EntityType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return EntityType.PLAYER;
        }
    }

    private String currentPose(NpcName name) {
        return current(name).map(Npc::pose).orElse(Npc.DEFAULT_POSE);
    }

    private String currentGlowColorRaw(NpcName name) {
        return current(name).map(Npc::glowColor).filter(Objects::nonNull).orElse("");
    }

    private boolean stateValue(NpcName name, SetNpcState.Flag flag) {
        return current(name)
                .map(npc -> switch (flag) {
                    case ON_FIRE -> npc.onFire();
                    case INVISIBLE -> npc.invisible();
                    case SILENT -> npc.silent();
                })
                .orElse(false);
    }

    private List<String> currentActionLines(NpcName name) {
        List<ClickAction> actions = current(name).map(Npc::actions).orElse(List.of());
        return new ArrayList<>(NpcActionLines.render(actions));
    }

    private String skinSummary(NpcName name) {
        Optional<Npc> npc = current(name);
        if (npc.isEmpty() || npc.get().skin() == null) {
            return none();
        }
        NpcSkin skin = Objects.requireNonNull(npc.get().skin());
        return skin.slim() ? "slim" : "classic";
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer, on ? NpcMessageKey.NPC_GUI_VALUE_ON : NpcMessageKey.NPC_GUI_VALUE_OFF, Map.of());
    }

    private String none() {
        return messages.resolve(GUI_ACTOR, NpcMessageKey.NPC_GUI_VALUE_NONE, Map.of());
    }

    private String defaultWord(PlayerRef viewer) {
        return messages.resolve(viewer, NpcMessageKey.NPC_GUI_VALUE_DEFAULT, Map.of());
    }

    private static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** The renderable entity types offered in the type selector (player plus the living/display types we render). */
    private static List<EntityType> renderableTypes() {
        List<EntityType> types = new ArrayList<>();
        types.add(EntityType.PLAYER);
        for (EntityType type : EntityType.values()) {
            if (type == EntityType.PLAYER) {
                continue;
            }
            if (isRenderable(type)) {
                types.add(type);
            }
        }
        return types;
    }

    private static boolean isRenderable(EntityType type) {
        if (type == EntityType.INTERACTION
                || type == EntityType.BLOCK_DISPLAY
                || type == EntityType.ITEM_DISPLAY
                || type == EntityType.TEXT_DISPLAY) {
            return true;
        }
        Class<?> entityClass = type.getEntityClass();
        return entityClass != null && org.bukkit.entity.LivingEntity.class.isAssignableFrom(entityClass);
    }

    /** The body poses the selector offers: the same names {@code /npc pose} accepts (uxmLib {@code NpcPose}). */
    private static final List<String> POSES =
            List.of("STANDING", "SLEEPING", "SWIMMING", "GLIDING", "CROUCHING", "SPIN_ATTACK", "SITTING");

    /** Sentinel ARGB meaning "no glow-colour override"; never collides with an opaque colour (alpha 0xFF). */
    private static final int NO_GLOW_OVERRIDE = 0;
    /** Opaque alpha bits OR'd onto a named colour's RGB to form the ARGB the colour picker reads. */
    private static final int OPAQUE_ALPHA = 0xFF000000;
    /** Strips the alpha byte from a picked ARGB before snapping it to the nearest named colour. */
    private static final int RGB_MASK = 0xFFFFFF;

    /**
     * The stable synthetic actor every GUI-originated npc write is attributed to. A GUI edit has no command
     * sender, and the use cases read the actor only for the (visually redundant in a GUI) chat feedback; the
     * stored NPC row carries no actor. A fixed ref keeps the attribution consistent without binding the write to
     * whichever viewer happens to hold the menu open.
     */
    private static final PlayerRef GUI_ACTOR = new PlayerRef(new UUID(0L, 0L), "npc-gui");
}
