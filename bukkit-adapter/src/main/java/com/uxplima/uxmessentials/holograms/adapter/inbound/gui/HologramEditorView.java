package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
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
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The per-hologram property editor: a thin consumer of the shared {@link EntityEditorView} that exposes every
 * hologram property as one button wired to the existing holograms use case. The properties read their current
 * value fresh from the {@link HologramRepository} on each open (the list-click snapshot would otherwise go
 * stale after an edit), and write through {@link HologramServices}, so the GUI adds no domain logic and stays
 * in lockstep with the {@code /hologram} subcommands.
 */
public final class HologramEditorView {

    private static final long SCALE_FACTOR = 100L;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final HologramRepository repository;
    private final HologramServices services;
    private final TextInput textInput;
    private final PlayerLookup players;
    private final Messages messages;
    private final HologramEditorSubLayouts sub;
    private final ColourPickerLayout colourPicker;
    private final ColourPickerText colourPickerText;
    private final EntityEditorView<Hologram> view;

    public HologramEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            HologramRepository repository,
            HologramServices services,
            TextInput textInput,
            PlayerLookup players,
            Messages messages,
            EntityEditorLayout layout,
            HologramEditorSubLayouts sub,
            ColourPickerLayout colourPicker,
            BiConsumer<Player, PlayerRef> onBack) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.services = Objects.requireNonNull(services, "services");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sub = Objects.requireNonNull(sub, "sub");
        this.colourPicker = Objects.requireNonNull(colourPicker, "colourPicker");
        this.colourPickerText = ColourPickerText.shared();
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(onBack, "onBack");
        this.view = EntityEditorView.<Hologram>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title((viewer, holo) -> title(viewer, holo))
                .valueLore(HologramsMessageKey.HOLOGRAM_GUI_EDITOR_VALUE_LORE)
                .backName(HologramsMessageKey.HOLOGRAM_GUI_EDITOR_BACK)
                .properties(this::properties)
                .onBack(onBack)
                .onDelete(
                        HologramsMessageKey.HOLOGRAM_GUI_EDITOR_DELETE,
                        HologramsMessageKey.HOLOGRAM_GUI_EDITOR_DELETE_CONFIRM,
                        (player, holo) -> services.delete().delete(ref(player), holo.name()))
                .build();
    }

    /** Open the editor for {@code holo}, scheduled on the viewer's entity thread by the framework. */
    public void open(Player player, PlayerRef viewer, Hologram holo) {
        view.open(player, viewer, holo);
    }

    /** The underlying property grid: exposed for tests to resolve a slot to its property without a live click. */
    EntityEditorView<Hologram> grid() {
        return view;
    }

    private Component title(PlayerRef viewer, Hologram holo) {
        return guiText.text(
                viewer,
                HologramsMessageKey.HOLOGRAM_GUI_EDITOR_TITLE,
                Map.of("name", holo.name().value()));
    }

    private List<EditableProperty> properties(Hologram holo) {
        HologramName name = holo.name();
        List<EditableProperty> props = new ArrayList<>();
        props.add(nameProperty(name));
        props.add(moveProperty(name));
        props.add(teleportProperty(name));
        props.add(linesProperty(name));
        props.add(scaleProperty(name));
        props.add(billboardProperty(name));
        props.add(alignmentProperty(name));
        props.add(visibilityModeProperty(name));
        props.add(visibilityDistanceProperty(name));
        props.add(viewRangeProperty(name));
        props.add(lineWidthProperty(name));
        props.add(brightnessProperty(name, true));
        props.add(brightnessProperty(name, false));
        props.add(textShadowProperty(name));
        props.add(seeThroughProperty(name));
        props.add(rotationProperty(name, true));
        props.add(rotationProperty(name, false));
        props.add(refreshProperty(name));
        props.add(growUpProperty(name));
        props.add(clickCommandProperty(name));
        props.add(leaderboardProperty(name));
        props.add(npcLinkProperty(name));
        props.add(blacklistProperty(name));
        props.add(backgroundColourProperty(name));
        props.add(glowColourProperty(name));
        props.add(textOpacityProperty(name));
        props.add(shadowRadiusProperty(name));
        props.add(shadowStrengthProperty(name));
        props.add(translationProperty(name, Axis.X));
        props.add(translationProperty(name, Axis.Y));
        props.add(translationProperty(name, Axis.Z));
        return props;
    }

    /** The translation axis a {@link #translationProperty} edits, mapping onto the {@link Appearance} transform. */
    private enum Axis {
        X,
        Y,
        Z
    }

    // --- identity / position ---

    private EditableProperty nameProperty(HologramName name) {
        return new TextProperty(
                "editor.text-field",
                HologramsMessageKey.HOLOGRAM_GUI_PROP_NAME,
                HologramsMessageKey.HOLOGRAM_GUI_PROP_NAME_PROMPT,
                Material.NAME_TAG,
                () -> currentName(name).value(),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> rename(name, HologramName.of(value)),
                textInput,
                scheduler);
    }

    /**
     * Rename a hologram by duplicating it under the new name and deleting the original. There is no standalone
     * rename use case, so this composes the existing {@code copy} and {@code delete} use cases (the same pair an
     * operator would run by hand). A no-op when the new name equals the old.
     */
    private void rename(HologramName from, HologramName to) {
        if (from.equals(to)) {
            return;
        }
        if (services.copy().copy(GUI_ACTOR, from, to).isOk()) {
            services.delete().delete(GUI_ACTOR, from);
        }
    }

    private EditableProperty moveProperty(HologramName name) {
        String hint =
                currentHologram(name).map(holo -> locationHint(holo.location())).orElse("");
        return new ActionProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_MOVE,
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
     * The inverse of {@link #moveProperty}: teleport the viewer to the hologram's anchor, the GUI twin of
     * {@code /hologram teleport <name>}. Reuses the existing {@code teleport} use case, which hands the
     * destination to the region-aware teleport adapter (Folia-safe {@code teleportAsync}); the editor is not
     * reopened: the viewer is leaving for the hologram, so the menu closes as the inventory click resolves. The
     * value lore shows the destination coordinates, the same hint the move button shows for the current anchor.
     */
    private EditableProperty teleportProperty(HologramName name) {
        String hint =
                currentHologram(name).map(holo -> locationHint(holo.location())).orElse("");
        return new ActionProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_TELEPORT,
                Material.ENDER_PEARL,
                hint,
                (player, reopen) -> {
                    player.closeInventory();
                    scheduler.async(() -> services.teleport().teleport(ref(player), name));
                },
                scheduler);
    }

    /** The hologram's current anchor as a short "world x, y, z" line for the move button's Current value. */
    private static String locationHint(Position at) {
        return at.world().name() + " " + at.blockX() + ", " + at.blockY() + ", " + at.blockZ();
    }

    // --- content ---

    private EditableProperty linesProperty(HologramName name) {
        return new ListProperty(
                "editor.list-entry",
                HologramsMessageKey.HOLOGRAM_GUI_PROP_LINES,
                Material.WRITABLE_BOOK,
                guiText,
                () -> currentLines(name),
                lines -> applyLines(name, lines),
                new ListPropertyText(
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_TITLE,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_ENTRY_NAME,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_ENTRY_HINTS,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_ADD,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_ADD_PROMPT,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_EDIT_PROMPT,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_REMOVE_CONFIRM,
                        HologramsMessageKey.HOLOGRAM_GUI_LINES_BACK),
                sub.listLayout(),
                textInput,
                scheduler);
    }

    /**
     * Apply a wholesale line-list edit through the per-line use cases: a longer list appends the new tail, a
     * shorter list removes from the end, and overlapping lines are set. Each call is the same use case the
     * {@code /hologram addline|setline|removeline} subcommands use, so no new domain logic is introduced.
     */
    private void applyLines(HologramName name, List<String> next) {
        PlayerRef actor = GUI_ACTOR;
        List<HologramLine> current = currentHologram(name).map(Hologram::lines).orElse(List.of());
        for (int i = 0; i < next.size(); i++) {
            HologramLine line = HologramLine.of(next.get(i));
            if (i < current.size()) {
                services.setLine().set(actor, name, i, line);
            } else {
                services.addLine().add(actor, name, line);
            }
        }
        for (int i = current.size() - 1; i >= next.size(); i--) {
            services.removeLine().remove(actor, name, i);
        }
    }

    // --- appearance numbers ---

    private EditableProperty scaleProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_SCALE,
                Material.CLOCK,
                () -> Math.round(currentAppearance(name).scale() * SCALE_FACTOR),
                10,
                10,
                Math.round(0.05f * SCALE_FACTOR),
                Math.round(64.0f * SCALE_FACTOR),
                value -> applyAppearance(name, a -> a.withScale(value / (float) SCALE_FACTOR)),
                scheduler);
    }

    private EditableProperty viewRangeProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_VIEW_RANGE,
                Material.SPYGLASS,
                () -> Math.round(currentAppearance(name).viewRange() * SCALE_FACTOR),
                10,
                10,
                0,
                Math.round(256.0f * SCALE_FACTOR),
                value -> applyAppearance(
                        name, a -> a.withViewRange(Appearance.clampViewRange(value / (float) SCALE_FACTOR))),
                scheduler);
    }

    private EditableProperty lineWidthProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_LINE_WIDTH,
                Material.STRING,
                () -> currentAppearance(name).lineWidth(),
                10,
                5,
                1,
                4000,
                value -> applyAppearance(name, a -> a.withLineWidth(Appearance.clampLineWidth((int) (long) value))),
                scheduler);
    }

    private EditableProperty brightnessProperty(HologramName name, boolean block) {
        MessageKey label = block
                ? HologramsMessageKey.HOLOGRAM_GUI_PROP_BRIGHTNESS_BLOCK
                : HologramsMessageKey.HOLOGRAM_GUI_PROP_BRIGHTNESS_SKY;
        return new NumberProperty(
                label,
                block ? Material.GLOWSTONE_DUST : Material.LIGHT,
                () -> brightnessValue(name, block),
                1,
                1,
                Appearance.DEFAULT_BRIGHTNESS,
                15,
                value -> applyBrightness(name, block, (int) (long) value),
                scheduler);
    }

    private EditableProperty rotationProperty(HologramName name, boolean yaw) {
        MessageKey label = yaw
                ? HologramsMessageKey.HOLOGRAM_GUI_PROP_ROTATION_YAW
                : HologramsMessageKey.HOLOGRAM_GUI_PROP_ROTATION_PITCH;
        return new NumberProperty(
                label,
                Material.COMPASS,
                () -> Math.round(
                        yaw
                                ? currentRotation(name).yaw()
                                : currentRotation(name).pitch()),
                15,
                3,
                0,
                359,
                value -> applyRotation(name, yaw, value),
                scheduler);
    }

    private EditableProperty visibilityDistanceProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_VISIBILITY_DISTANCE,
                Material.ENDER_EYE,
                () -> currentVisibility(name).distance(),
                5,
                5,
                Visibility.UNLIMITED,
                Visibility.MAX_DISTANCE,
                value -> services.visibility().setDistance(GUI_ACTOR, name, (int) (long) value),
                scheduler);
    }

    private EditableProperty refreshProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_REFRESH,
                Material.REPEATER,
                () -> currentHologram(name).map(Hologram::refreshIntervalTicks).orElse(0),
                20,
                5,
                0,
                12000,
                value -> services.refresh().set(GUI_ACTOR, name, (int) (long) value),
                scheduler);
    }

    private EditableProperty leaderboardProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_LEADERBOARD,
                Material.GOLD_INGOT,
                () -> leaderboardLimit(name),
                1,
                5,
                0,
                LeaderboardSpec.MAX_LIMIT,
                value -> applyLeaderboard(name, (int) (long) value),
                scheduler);
    }

    // --- appearance enums / toggles ---

    private EditableProperty billboardProperty(HologramName name) {
        return new EnumProperty<>(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_BILLBOARD,
                HologramsMessageKey.HOLOGRAM_GUI_SELECT_BILLBOARD,
                Material.ITEM_FRAME,
                guiText,
                List.of(Billboard.values()),
                () -> currentAppearance(name).billboard(),
                (viewer, value) -> value.name(),
                value -> applyAppearance(name, a -> a.withBillboard(value)),
                sub.selectorOptionIcon(),
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private EditableProperty alignmentProperty(HologramName name) {
        return new EnumProperty<>(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_ALIGNMENT,
                HologramsMessageKey.HOLOGRAM_GUI_SELECT_ALIGNMENT,
                Material.PAPER,
                guiText,
                List.of(TextAlignment.values()),
                () -> currentAppearance(name).alignment(),
                (viewer, value) -> value.name(),
                value -> applyAppearance(name, a -> a.withAlignment(value)),
                sub.selectorOptionIcon(),
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private EditableProperty visibilityModeProperty(HologramName name) {
        return new EnumProperty<>(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_VISIBILITY,
                HologramsMessageKey.HOLOGRAM_GUI_SELECT_VISIBILITY,
                Material.ENDER_PEARL,
                guiText,
                List.of(Visibility.Mode.ALL, Visibility.Mode.MANUAL),
                () -> currentVisibility(name).mode(),
                (viewer, value) -> value.name(),
                value -> applyVisibilityMode(name, value),
                sub.selectorOptionIcon(),
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private EditableProperty textShadowProperty(HologramName name) {
        return ToggleProperty.ofBoolean(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_TEXT_SHADOW,
                Material.GRAY_DYE,
                () -> currentAppearance(name).textShadow(),
                this::onOff,
                on -> applyAppearance(name, a -> a.withTextShadow(on)),
                scheduler);
    }

    private EditableProperty seeThroughProperty(HologramName name) {
        return ToggleProperty.ofBoolean(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_SEE_THROUGH,
                Material.GLASS,
                () -> currentAppearance(name).seeThrough(),
                this::onOff,
                on -> applyAppearance(name, a -> a.withSeeThrough(on)),
                scheduler);
    }

    private EditableProperty growUpProperty(HologramName name) {
        return ToggleProperty.ofBoolean(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_GROW_UP,
                Material.LADDER,
                () -> currentHologram(name).map(Hologram::growUp).orElse(false),
                this::onOff,
                on -> services.growUp().set(GUI_ACTOR, name, on),
                scheduler);
    }

    // --- text-prompt properties ---

    private EditableProperty clickCommandProperty(HologramName name) {
        return new TextProperty(
                "editor.text-field",
                HologramsMessageKey.HOLOGRAM_GUI_PROP_CLICK_COMMAND,
                HologramsMessageKey.HOLOGRAM_GUI_PROP_CLICK_COMMAND_PROMPT,
                Material.COMMAND_BLOCK,
                () -> currentHologram(name).map(Hologram::clickCommand).orElse(none()),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> applyClickCommand(name, value),
                textInput,
                scheduler);
    }

    private EditableProperty npcLinkProperty(HologramName name) {
        return new TextProperty(
                "editor.text-field",
                HologramsMessageKey.HOLOGRAM_GUI_PROP_NPC_LINK,
                HologramsMessageKey.HOLOGRAM_GUI_PROP_NPC_LINK_PROMPT,
                Material.PLAYER_HEAD,
                () -> currentHologram(name).map(Hologram::linkedNpcName).orElse(none()),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> applyNpcLink(name, value),
                textInput,
                scheduler);
    }

    private EditableProperty blacklistProperty(HologramName name) {
        return new ListProperty(
                "editor.list-entry",
                HologramsMessageKey.HOLOGRAM_GUI_PROP_BLACKLIST,
                Material.BARRIER,
                guiText,
                () -> currentBlacklist(name),
                next -> applyBlacklist(name, next),
                new ListPropertyText(
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_TITLE,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_ENTRY_NAME,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_ENTRY_HINTS,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_ADD,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_ADD_PROMPT,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_EDIT_PROMPT,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_REMOVE_CONFIRM,
                        HologramsMessageKey.HOLOGRAM_GUI_BLACKLIST_BACK),
                sub.listLayout(),
                textInput,
                scheduler);
    }

    // --- appearance colours (the shared colour picker) + opacity / shadow / translation numbers ---

    private EditableProperty backgroundColourProperty(HologramName name) {
        return new ColourProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_BACKGROUND,
                Material.PAINTING,
                () -> currentAppearance(name).backgroundArgb(),
                argb -> applyAppearance(name, a -> a.withBackgroundArgb(argb)),
                () -> applyAppearance(name, a -> a.withBackgroundArgb(Appearance.DEFAULT_BACKGROUND)),
                Appearance.DEFAULT_BACKGROUND,
                this::defaultWord,
                guiText,
                colourPickerText,
                colourPicker,
                textInput,
                scheduler);
    }

    private EditableProperty glowColourProperty(HologramName name) {
        return new ColourProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_GLOW,
                Material.GLOWSTONE,
                () -> currentAppearance(name).glowArgb(),
                argb -> applyAppearance(name, a -> a.withGlowArgb(argb)),
                () -> applyAppearance(name, a -> a.withGlowArgb(Appearance.DEFAULT_GLOW)),
                Appearance.DEFAULT_GLOW,
                this::defaultWord,
                guiText,
                colourPickerText,
                colourPicker,
                textInput,
                scheduler);
    }

    private EditableProperty textOpacityProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_TEXT_OPACITY,
                Material.GLASS_BOTTLE,
                () -> opacityValue(name),
                15,
                4,
                0,
                255,
                value -> applyAppearance(name, a -> a.withTextOpacity(Appearance.clampOpacity((int) (long) value))),
                scheduler);
    }

    private EditableProperty shadowRadiusProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_SHADOW_RADIUS,
                Material.BLACK_DYE,
                () -> Math.round(Math.max(0f, currentAppearance(name).shadowRadius()) * SCALE_FACTOR),
                10,
                10,
                0,
                Math.round(100.0f * SCALE_FACTOR),
                value -> applyAppearance(
                        name, a -> a.withShadowRadius(Appearance.clampShadow(value / (float) SCALE_FACTOR))),
                scheduler);
    }

    private EditableProperty shadowStrengthProperty(HologramName name) {
        return new NumberProperty(
                HologramsMessageKey.HOLOGRAM_GUI_PROP_SHADOW_STRENGTH,
                Material.GRAY_DYE,
                () -> Math.round(Math.max(0f, currentAppearance(name).shadowStrength()) * SCALE_FACTOR),
                10,
                10,
                0,
                Math.round(100.0f * SCALE_FACTOR),
                value -> applyAppearance(
                        name, a -> a.withShadowStrength(Appearance.clampShadow(value / (float) SCALE_FACTOR))),
                scheduler);
    }

    private EditableProperty translationProperty(HologramName name, Axis axis) {
        MessageKey label =
                switch (axis) {
                    case X -> HologramsMessageKey.HOLOGRAM_GUI_PROP_TRANSLATION_X;
                    case Y -> HologramsMessageKey.HOLOGRAM_GUI_PROP_TRANSLATION_Y;
                    case Z -> HologramsMessageKey.HOLOGRAM_GUI_PROP_TRANSLATION_Z;
                };
        return new NumberProperty(
                label,
                Material.LEAD,
                () -> Math.round(translationValue(name, axis) * SCALE_FACTOR),
                5,
                10,
                Math.round(-256.0f * SCALE_FACTOR),
                Math.round(256.0f * SCALE_FACTOR),
                value -> applyTranslation(name, axis, value / (float) SCALE_FACTOR),
                scheduler);
    }

    // --- write helpers (each is the existing use case) ---

    private void applyAppearance(HologramName name, Function<Appearance, Appearance> mutation) {
        services.appearance().apply(GUI_ACTOR, name, mutation::apply);
    }

    private void applyBrightness(HologramName name, boolean block, int value) {
        applyAppearance(
                name,
                a -> block ? a.withBrightness(value, a.brightnessSky()) : a.withBrightness(a.brightnessBlock(), value));
    }

    private void applyTranslation(HologramName name, Axis axis, float value) {
        applyAppearance(name, a -> {
            float clamped = Appearance.clampTranslation(value);
            com.uxplima.uxmessentials.holograms.domain.Transform t = a.transform();
            return switch (axis) {
                case X -> a.withTranslation(clamped, t.translationY(), t.translationZ());
                case Y -> a.withTranslation(t.translationX(), clamped, t.translationZ());
                case Z -> a.withTranslation(t.translationX(), t.translationY(), clamped);
            };
        });
    }

    private void applyRotation(HologramName name, boolean yaw, long value) {
        Rotation current = currentRotation(name);
        Rotation next = yaw ? Rotation.of((float) value, current.pitch()) : Rotation.of(current.yaw(), (float) value);
        services.rotate().rotate(GUI_ACTOR, name, next);
    }

    private void applyVisibilityMode(HologramName name, Visibility.Mode mode) {
        services.visibility()
                .setMode(
                        GUI_ACTOR,
                        name,
                        mode == Visibility.Mode.MANUAL ? Visibility::toManual : Visibility::toEveryone);
    }

    private void applyLeaderboard(HologramName name, int limit) {
        LeaderboardSpec spec = limit <= 0 ? null : new LeaderboardSpec("balance", limit);
        services.leaderboard().set(GUI_ACTOR, name, spec);
    }

    private void applyClickCommand(HologramName name, String raw) {
        String command = raw.equals("-") || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("clear")
                ? null
                : (raw.startsWith("/") ? raw.substring(1) : raw);
        services.clickCommand().set(GUI_ACTOR, name, command);
    }

    private void applyNpcLink(HologramName name, String raw) {
        if (raw.equals("-") || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("clear")) {
            services.unlinkNpc().unlink(GUI_ACTOR, name);
        } else {
            services.linkNpc().link(GUI_ACTOR, name, raw);
        }
    }

    private void applyBlacklist(HologramName name, List<String> next) {
        PlayerRef actor = GUI_ACTOR;
        List<String> current = currentBlacklist(name);
        for (String entry : next) {
            if (!current.contains(entry)) {
                players.findByName(entry)
                        .ifPresent(target -> services.blacklist().blacklist(actor, name, target));
            }
        }
        for (String entry : current) {
            if (!next.contains(entry)) {
                players.findByName(entry)
                        .ifPresent(target -> services.blacklist().unblacklist(actor, name, target));
            }
        }
    }

    // --- fresh-state readers (the list-click snapshot would go stale after an edit) ---

    private Optional<Hologram> currentHologram(HologramName name) {
        return repository.find(name);
    }

    private HologramName currentName(HologramName name) {
        return currentHologram(name).map(Hologram::name).orElse(name);
    }

    private Appearance currentAppearance(HologramName name) {
        return currentHologram(name).map(Hologram::appearance).orElse(Appearance.defaults());
    }

    private Visibility currentVisibility(HologramName name) {
        return currentHologram(name).map(Hologram::visibility).orElse(Visibility.everyone());
    }

    private Rotation currentRotation(HologramName name) {
        return currentHologram(name).map(Hologram::rotation).orElse(Rotation.NONE);
    }

    private List<String> currentLines(HologramName name) {
        List<String> lines = new ArrayList<>();
        for (HologramLine line : currentHologram(name).map(Hologram::lines).orElse(List.of())) {
            lines.add(line.value());
        }
        return lines;
    }

    private List<String> currentBlacklist(HologramName name) {
        List<String> names = new ArrayList<>();
        for (java.util.UUID id : repository.blacklisted(name)) {
            players.findByUuid(id).ifPresent(ref -> names.add(ref.name()));
        }
        return names;
    }

    private long brightnessValue(HologramName name, boolean block) {
        Appearance a = currentAppearance(name);
        return block ? a.brightnessBlock() : a.brightnessSky();
    }

    /** The current per-axis translation offset, defaulting to zero when no transform override is stored. */
    private float translationValue(HologramName name, Axis axis) {
        com.uxplima.uxmessentials.holograms.domain.Transform t =
                currentAppearance(name).transform();
        return switch (axis) {
            case X -> t.translationX();
            case Y -> t.translationY();
            case Z -> t.translationZ();
        };
    }

    /**
     * The opacity value the stepper shows and steps from: an explicit 0 to 255 override when one is set, otherwise
     * the vanilla fully-opaque 255 (the sentinel reads as opaque so a first step lands at a real value rather
     * than the magic literal).
     */
    private long opacityValue(HologramName name) {
        Appearance a = currentAppearance(name);
        return a.hasTextOpacity() ? a.textOpacity() : 255;
    }

    private long leaderboardLimit(HologramName name) {
        return currentHologram(name)
                .map(Hologram::leaderboard)
                .map(LeaderboardSpec::limit)
                .orElse(0);
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? HologramsMessageKey.HOLOGRAM_GUI_VALUE_ON : HologramsMessageKey.HOLOGRAM_GUI_VALUE_OFF,
                Map.of());
    }

    /** The catalog "none" word, shown in a text property's value lore when the underlying value is unset. */
    private String none() {
        return messages.resolve(GUI_ACTOR, HologramsMessageKey.HOLOGRAM_GUI_VALUE_NONE, Map.of());
    }

    /** The catalog "default" word, shown in a colour property's value lore when no override is set. */
    private String defaultWord(PlayerRef viewer) {
        return messages.resolve(viewer, HologramsMessageKey.HOLOGRAM_GUI_VALUE_DEFAULT, Map.of());
    }

    private static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /**
     * The stable synthetic actor every GUI-originated hologram write is attributed to. A GUI edit has no command
     * sender, and the use cases read the actor only for the (visually redundant in a GUI) chat feedback and the
     * audit trail; the stored hologram row carries no actor. A fixed ref keeps the attribution consistent without
     * binding the write to whichever viewer happens to hold the menu open.
     */
    private static final PlayerRef GUI_ACTOR = new PlayerRef(new java.util.UUID(0L, 0L), "hologram-gui");
}
