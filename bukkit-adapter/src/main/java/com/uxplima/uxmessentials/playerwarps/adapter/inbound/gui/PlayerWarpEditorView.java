package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The per-warp property editor: a thin consumer of the shared {@link EntityEditorView} that exposes every
 * player-warp property as one button wired to the same write path the {@code /pwarp} subcommands use. A warp is
 * keyed {@code (owner, name)}, so the editor is generic over an {@link OwnedWarp} and every property reads the
 * live row fresh from the {@link PlayerWarpRepository} on each open (the list-click snapshot would otherwise go
 * stale after an edit) and writes back against that same owner. An operator never edits anyone else's warp by
 * accident, and a player only ever reaches their own through the list's owner filter.
 *
 * <p>Most fields are immutable {@code with*} transitions on the {@link PlayerWarp} aggregate persisted through
 * {@code repository.save}; visibility flows through {@link SetPlayerWarpVisibility} (the same use case the
 * {@code /pwarp public|private} subcommands call), move-here re-anchors at the operator's feet, and delete
 * archives the warp through {@link ArchivePlayerWarp} behind the framework's confirm gate (recoverable, the row
 * is retired, not dropped).
 */
@NullMarked
public final class PlayerWarpEditorView {

    /** Overrides are edited as integer tenths of a second so the stepper has no float precision drift. */
    private static final long SECONDS_FACTOR = 10L;

    private static final String CLEAR_TOKEN = "-";

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final PlayerWarpRepository repository;
    private final SetPlayerWarpVisibility visibility;
    private final TextInput textInput;
    private final Messages messages;
    private final PlayerWarpEditorSubLayouts sub;
    private final EntityEditorView<OwnedWarp> view;

    public PlayerWarpEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            PlayerWarpRepository repository,
            SetPlayerWarpVisibility visibility,
            ArchivePlayerWarp archivePlayerWarp,
            TextInput textInput,
            Messages messages,
            EntityEditorLayout layout,
            PlayerWarpEditorSubLayouts sub,
            BiConsumer<Player, PlayerRef> onBack) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(archivePlayerWarp, "archivePlayerWarp");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sub = Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(onBack, "onBack");
        this.view = EntityEditorView.<OwnedWarp>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(this::title)
                .valueLore(PlayerwarpsMessageKey.PWARP_GUI_EDITOR_VALUE_LORE)
                .backName(PlayerwarpsMessageKey.PWARP_GUI_EDITOR_BACK)
                .properties(this::properties)
                .onBack(onBack)
                .onDelete(
                        PlayerwarpsMessageKey.PWARP_GUI_EDITOR_DELETE,
                        PlayerwarpsMessageKey.PWARP_GUI_EDITOR_DELETE_CONFIRM,
                        (player, owned) -> archivePlayerWarp.archive(
                                owned.owner(), owned.warp().name()))
                .build();
    }

    /** Open the editor for {@code owned}, scheduled on the viewer's entity thread by the framework. */
    public void open(Player player, PlayerRef viewer, OwnedWarp owned) {
        view.open(player, viewer, owned);
    }

    /** The underlying property grid: exposed for tests to resolve a slot to its property without a live click. */
    EntityEditorView<OwnedWarp> grid() {
        return view;
    }

    private Component title(PlayerRef viewer, OwnedWarp owned) {
        return guiText.text(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_EDITOR_TITLE,
                Map.of("name", owned.warp().name().value()));
    }

    private List<EditableProperty> properties(OwnedWarp owned) {
        PlayerRef owner = owned.owner();
        PlayerWarpName name = owned.warp().name();
        List<EditableProperty> props = new ArrayList<>();
        props.add(nameProperty(owner, name));
        props.add(moveProperty(owner, name));
        props.add(iconProperty(owner, name));
        props.add(visibilityProperty(owner, name));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_SOUND,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_SOUND_PROMPT,
                Material.NOTE_BLOCK,
                warp -> warp.effects().departureSound(),
                PlayerWarpEditorView::withDepartureSound));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_SOUND,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_SOUND_PROMPT,
                Material.JUKEBOX,
                warp -> warp.effects().arrivalSound(),
                PlayerWarpEditorView::withArrivalSound));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_PARTICLE,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_PARTICLE_PROMPT,
                Material.BLAZE_POWDER,
                warp -> warp.effects().departureParticle(),
                PlayerWarpEditorView::withDepartureParticle));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_PARTICLE,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_PARTICLE_PROMPT,
                Material.GLOWSTONE_DUST,
                warp -> warp.effects().arrivalParticle(),
                PlayerWarpEditorView::withArrivalParticle));
        props.add(secondsProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_WARMUP,
                warp -> warp.timing().warmupSeconds(),
                PlayerWarpEditorView::withWarmup));
        props.add(secondsProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_COOLDOWN,
                warp -> warp.timing().cooldownSeconds(),
                PlayerWarpEditorView::withCooldown));
        return props;
    }

    // --- facet setters: rebuild the WarpEffects / WarpTimingOverrides value objects with one field swapped ---

    private static PlayerWarp withDepartureSound(PlayerWarp warp, Optional<String> sound) {
        WarpEffects e = warp.effects();
        return warp.withEffects(
                new WarpEffects(sound, e.arrivalSound(), e.departureParticle(), e.arrivalParticle()), Instant.now());
    }

    private static PlayerWarp withArrivalSound(PlayerWarp warp, Optional<String> sound) {
        WarpEffects e = warp.effects();
        return warp.withEffects(
                new WarpEffects(e.departureSound(), sound, e.departureParticle(), e.arrivalParticle()), Instant.now());
    }

    private static PlayerWarp withDepartureParticle(PlayerWarp warp, Optional<String> particle) {
        WarpEffects e = warp.effects();
        return warp.withEffects(
                new WarpEffects(e.departureSound(), e.arrivalSound(), particle, e.arrivalParticle()), Instant.now());
    }

    private static PlayerWarp withArrivalParticle(PlayerWarp warp, Optional<String> particle) {
        WarpEffects e = warp.effects();
        return warp.withEffects(
                new WarpEffects(e.departureSound(), e.arrivalSound(), e.departureParticle(), particle), Instant.now());
    }

    private static PlayerWarp withWarmup(PlayerWarp warp, Optional<Double> seconds) {
        return warp.withTiming(new WarpTimingOverrides(seconds, warp.timing().cooldownSeconds()), Instant.now());
    }

    private static PlayerWarp withCooldown(PlayerWarp warp, Optional<Double> seconds) {
        return warp.withTiming(new WarpTimingOverrides(warp.timing().warmupSeconds(), seconds), Instant.now());
    }

    // --- identity / position ---

    private EditableProperty nameProperty(PlayerRef owner, PlayerWarpName name) {
        return new TextProperty(
                "editor.text-field",
                PlayerwarpsMessageKey.PWARP_GUI_PROP_NAME,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_NAME_PROMPT,
                Material.NAME_TAG,
                name::value,
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> rename(owner, name, value),
                textInput,
                scheduler);
    }

    /**
     * Rename a warp by re-saving the live row under the new name. The warp now carries a durable surrogate id, so a
     * save with the same id and a different name updates that one row in place. No copy-then-delete is needed the
     * way it was when identity was {@code (owner, name)}. A no-op when the new name equals the old or no such warp
     * exists; a name another warp already holds is rejected by the repository's global-unique constraint just as
     * {@code /setpwarp} onto a taken name is.
     */
    private void rename(PlayerRef owner, PlayerWarpName from, String rawTo) {
        PlayerWarpName to = PlayerWarpName.of(rawTo);
        if (from.equals(to)) {
            return;
        }
        current(owner, from).ifPresent(warp -> repository.save(withName(warp, to)));
    }

    /** A copy of {@code warp} under {@code name}, keeping every other field including its surrogate id. */
    private static PlayerWarp withName(PlayerWarp warp, PlayerWarpName name) {
        return new PlayerWarp(
                warp.id(),
                warp.owner(),
                warp.ownerName(),
                name,
                warp.displayName(),
                warp.location(),
                warp.serverId(),
                warp.categoryId(),
                warp.description(),
                warp.icon(),
                warp.access(),
                warp.passwordSet(),
                warp.status(),
                warp.price(),
                warp.earnings(),
                warp.ratings(),
                warp.visits(),
                warp.favouriteCount(),
                warp.sponsorship(),
                warp.rent(),
                warp.effects(),
                warp.timing(),
                warp.createdAt(),
                Instant.now());
    }

    private EditableProperty moveProperty(PlayerRef owner, PlayerWarpName name) {
        return new PlayerWarpActionButton(
                PlayerwarpsMessageKey.PWARP_GUI_PROP_MOVE,
                Material.COMPASS,
                "",
                (player, reopen) -> {
                    Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
                    scheduler.async(() -> {
                        mutate(owner, name, warp -> warp.movedTo(at, Instant.now()));
                        scheduler.onEntity(BukkitRefs.toRef(player), reopen);
                    });
                },
                scheduler);
    }

    // --- appearance / access ---

    private EditableProperty iconProperty(PlayerRef owner, PlayerWarpName name) {
        return new TextProperty(
                "editor.text-field",
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ICON,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ICON_PROMPT,
                iconButtonMaterial(owner, name),
                () -> current(owner, name)
                        .flatMap(warp -> warp.icon().map(IconSpec::value))
                        .orElseGet(this::none),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> mutate(
                        owner, name, warp -> warp.withIcon(optional(value).map(IconSpec::of), Instant.now())),
                textInput,
                scheduler);
    }

    /**
     * The icon button's own material: the warp's configured icon, so the button shows what it sets rather than a
     * fixed stand-in. The property list is rebuilt from the live row on each open, so resolving here keeps the
     * button in step with the value. An unset or unparseable icon falls back to {@code ITEM_FRAME}.
     */
    private Material iconButtonMaterial(PlayerRef owner, PlayerWarpName name) {
        return current(owner, name)
                .flatMap(warp -> warp.icon().map(IconSpec::value))
                .map(Material::matchMaterial)
                .filter(material -> material != Material.AIR)
                .orElse(Material.ITEM_FRAME);
    }

    private EditableProperty visibilityProperty(PlayerRef owner, PlayerWarpName name) {
        return new EnumProperty<>(
                PlayerwarpsMessageKey.PWARP_GUI_PROP_VISIBILITY,
                PlayerwarpsMessageKey.PWARP_GUI_SELECT_VISIBILITY,
                Material.ENDER_EYE,
                guiText,
                List.of(Boolean.TRUE, Boolean.FALSE),
                () -> current(owner, name)
                        .map(warp -> warp.access() == WarpAccess.PUBLIC)
                        .orElse(false),
                (viewer, isPublic) -> visibilityWord(viewer, isPublic),
                isPublic -> applyVisibility(owner, name, isPublic),
                sub.selectorOptionIcon(),
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private void applyVisibility(PlayerRef owner, PlayerWarpName name, boolean isPublic) {
        if (isPublic) {
            visibility.setPublic(owner, name);
        } else {
            visibility.setPrivate(owner, name);
        }
    }

    // --- effects (sounds / particles) ---

    private EditableProperty soundProperty(
            PlayerRef owner,
            PlayerWarpName name,
            MessageKey label,
            MessageKey prompt,
            Material icon,
            java.util.function.Function<PlayerWarp, Optional<String>> getter,
            java.util.function.BiFunction<PlayerWarp, Optional<String>, PlayerWarp> setter) {
        return new TextProperty(
                "editor.text-field",
                label,
                prompt,
                icon,
                () -> current(owner, name).flatMap(getter).orElseGet(this::none),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> mutate(owner, name, warp -> setter.apply(warp, optional(value))),
                textInput,
                scheduler);
    }

    // --- warmup / cooldown overrides ---

    private EditableProperty secondsProperty(
            PlayerRef owner,
            PlayerWarpName name,
            MessageKey label,
            java.util.function.Function<PlayerWarp, Optional<Double>> getter,
            java.util.function.BiFunction<PlayerWarp, Optional<Double>, PlayerWarp> setter) {
        return new NumberProperty(
                label,
                Material.CLOCK,
                () -> Math.round(current(owner, name).flatMap(getter).orElse(0.0) * SECONDS_FACTOR),
                SECONDS_FACTOR, // a click steps one whole second
                5,
                0,
                Math.round(3600.0 * SECONDS_FACTOR),
                value -> mutate(owner, name, warp -> setter.apply(warp, secondsOverride(value))),
                scheduler);
    }

    /** Zero clears the override (matching "no override"); anything positive is the override in seconds. */
    private static Optional<Double> secondsOverride(long tenths) {
        return tenths <= 0 ? Optional.empty() : Optional.of(tenths / (double) SECONDS_FACTOR);
    }

    // --- write helper: read the live row, apply the transition, save it owner-scoped ---

    private void mutate(PlayerRef owner, PlayerWarpName name, java.util.function.UnaryOperator<PlayerWarp> change) {
        current(owner, name).map(change).ifPresent(repository::save);
    }

    /**
     * The live row under {@code name}, scoped to {@code owner}. Names are globally unique so the lookup keys on the
     * name alone; the owner filter keeps an edit from ever touching another player's warp that happens to share the
     * resolved name.
     */
    private Optional<PlayerWarp> current(PlayerRef owner, PlayerWarpName name) {
        return repository.findByName(name).filter(warp -> warp.owner().uuid().equals(owner.uuid()));
    }

    private static Optional<String> optional(String value) {
        String trimmed = value.strip();
        return trimmed.equals(CLEAR_TOKEN) || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("clear")
                ? Optional.empty()
                : Optional.of(trimmed);
    }

    // --- localised value words ---

    private String visibilityWord(PlayerRef viewer, boolean isPublic) {
        return messages.resolve(
                viewer,
                isPublic ? PlayerwarpsMessageKey.PWARP_GUI_VALUE_PUBLIC : PlayerwarpsMessageKey.PWARP_GUI_VALUE_PRIVATE,
                Map.of());
    }

    private String none() {
        return word(PlayerwarpsMessageKey.PWARP_GUI_VALUE_NONE);
    }

    private String word(MessageKey key) {
        return messages.resolve(GUI_ACTOR, key, Map.of());
    }

    /**
     * The stable synthetic actor a fixed-text value word is resolved for. A button's value word has no live
     * viewer when the editor builds the property list off the click thread; the word is the same in every locale
     * the catalog ships, so a fixed ref keeps it consistent without binding it to whoever holds the menu.
     */
    private static final PlayerRef GUI_ACTOR = new PlayerRef(new java.util.UUID(0L, 0L), "pwarp-gui");
}
