package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.CycleAction;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldPropertyCycle;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the shared property grid that drives both the rules and access screens with the menu engine and opens it.
 * A six-row panel with one button per settable {@link WorldProperty}: each shows the property's label, a value-lore
 * reporting the world's current encoded setting, and a cycle hint. Which property set a window shows is the subject's
 * choice (the hub opens it with the rules set for the rules screen and the access set for the access screen) so one
 * spec serves both, the way one confirm spec serves every sanction. A click cycles the clicked property's value (left
 * next, right previous, shift clears) through {@link WorldPropertyCycle}, persists it via {@link SetWorldProperty}
 * (which writes off-tick), then re-opens the grid with the new value applied so the change shows immediately. A back
 * button returns to the per-world hub.
 *
 * <p>The {@code (world, property set, rows)} pair is handed in as the {@link GridSubject}, snapshotted on the viewer's
 * entity thread at open, so the {@code worlds:grid} list source reads only that subject and the placeholders read the
 * bound row: the engine touches no port off-thread. A cycle is optimistic: the new value is written through the use
 * case and the re-opened subject carries it, so the viewer sees the change without re-reading the cache the async save
 * updates. This replays the old bespoke {@code WorldPropertyGridView} verbatim through the engine. The per-world hub
 * the back button returns to is injected through {@link #bind} after this menu so their re-open cycle is broken.
 */
@NullMarked
public final class WorldGridMenu {

    /** The engine spec id this grid registers and opens under. */
    public static final String SPEC_ID = "world-grid";

    private static final String SPEC_RESOURCE = "modules/worlds/gui/world-grid.conf";
    private static final int ROWS = 6;

    /** The properties the rules screen shows, in render order. */
    static final List<WorldProperty<?>> RULES_PROPERTIES = List.of(
            WorldProperties.PVP,
            WorldProperties.DIFFICULTY,
            WorldProperties.FORCE_GAMEMODE,
            WorldProperties.SPAWN_ANIMALS,
            WorldProperties.SPAWN_MONSTERS,
            WorldProperties.TIME,
            WorldProperties.WEATHER);

    /** The properties the access screen shows, in render order. */
    static final List<WorldProperty<?>> ACCESS_PROPERTIES = List.of(
            WorldProperties.ACCESS_RESTRICTED,
            WorldProperties.PLAYER_LIMIT,
            WorldProperties.ENTRY_FEE,
            WorldProperties.PORTAL_NETHER_LINK,
            WorldProperties.PORTAL_END_LINK);

    private final Menus menus;
    private final Scheduler scheduler;
    private final WorldRepository repository;
    private final SetWorldProperty setProperty;

    /** The per-world hub the back button returns to; injected after this menu via {@link #bind} to break the cycle. */
    private @Nullable WorldMainMenu mainMenu;

    public WorldGridMenu(Menus menus, Scheduler scheduler, WorldRepository repository, SetWorldProperty setProperty) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.setProperty = Objects.requireNonNull(setProperty, "setProperty");
    }

    /** Wire the per-world hub the back button returns to; breaks the hub-drills-here, here-returns-to-hub cycle. */
    public void bind(WorldMainMenu mainMenu) {
        this.mainMenu = Objects.requireNonNull(mainMenu, "mainMenu");
    }

    /** Register the grid list source, the subject placeholders, the cycle/back actions, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("worlds:grid", ctx -> subject(ctx).rows());
        bindings.placeholder("world_grid_world", ctx -> subject(ctx).world().value());
        bindings.placeholder(
                "world_grid_title_key", ctx -> titleKey(subject(ctx).rules()));
        bindings.placeholder("world_grid_material", ctx -> row(ctx).material());
        bindings.placeholder("world_grid_label_key", ctx -> row(ctx).labelKey());
        bindings.placeholder("world_grid_value", ctx -> row(ctx).value());
        bindings.action("worlds:grid-cycle-next", ctx -> cycle(ctx, CycleAction.FORWARD));
        bindings.action("worlds:grid-cycle-prev", ctx -> cycle(ctx, CycleAction.BACKWARD));
        bindings.action("worlds:grid-cycle-clear", ctx -> cycle(ctx, CycleAction.CLEAR));
        bindings.action("worlds:grid-back", this::back);
        menus.registerSpec(SPEC_ID, WorldEditorSpecs.load(SPEC_RESOURCE, ROWS, dataFolder, log));
    }

    /**
     * Open the grid for {@code world} showing the rules set when {@code rules} is true, else the access set. The
     * world's settings are read from the repository cache on the viewer's entity thread and snapshotted into the
     * subject's rows, so the engine renders without a port read of its own.
     */
    public void open(Player player, PlayerRef viewer, WorldName world, boolean rules) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(world, "world");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, snapshot(world, rules)));
    }

    private GridSubject snapshot(WorldName world, boolean rules) {
        WorldSettings settings =
                repository.find(world).map(ManagedWorld::settings).orElse(WorldSettings.defaults());
        List<GridRow> rows = new ArrayList<>();
        for (WorldProperty<?> property : properties(rules)) {
            rows.add(rowOf(property, encode(settings, property)));
        }
        return new GridSubject(world, rules, List.copyOf(rows));
    }

    /**
     * Cycle the clicked property and re-open the grid with the new value applied. The next value is computed purely,
     * persisted through the use case off-tick, and carried optimistically on the re-opened subject so the change shows
     * immediately without re-reading the cache the async save updates. An unchanged value (a no-op step) still
     * re-opens so the window stays live.
     */
    private void cycle(MenuActionContext ctx, CycleAction action) {
        GridSubject subject = subject(ctx);
        GridRow clicked = row(ctx);
        WorldProperty<?> property = clicked.property();
        String next =
                WorldPropertyCycle.next(property, clicked.value(), effective(action, ctx.clickKind()), worldNames());
        if (!next.equals(clicked.value())) {
            setProperty.set(ctx.viewer(), subject.world(), property.key(), next);
        }
        menus.open(ctx.viewer(), SPEC_ID, subject.withValue(property.key(), next));
    }

    private void back(MenuActionContext ctx) {
        if (mainMenu != null) {
            mainMenu.open(ctx.player(), ctx.viewer(), subject(ctx).world());
        }
    }

    /** The world names the cycle uses for world-name properties (the portal links), read from the tick-safe cache. */
    private List<String> worldNames() {
        return repository.all().stream().map(world -> world.name().value()).toList();
    }

    /** A shift gesture clears to the default; otherwise the requested step direction applies. */
    private static CycleAction effective(CycleAction requested, ClickKind clickKind) {
        if (clickKind == ClickKind.SHIFT_LEFT || clickKind == ClickKind.SHIFT_RIGHT) {
            return CycleAction.CLEAR;
        }
        return requested;
    }

    private static List<WorldProperty<?>> properties(boolean rules) {
        return rules ? RULES_PROPERTIES : ACCESS_PROPERTIES;
    }

    private static <T> String encode(WorldSettings settings, WorldProperty<T> property) {
        return property.encode(settings.get(property));
    }

    private static GridRow rowOf(WorldProperty<?> property, String value) {
        return new GridRow(property, materialFor(property), labelKey(property), value);
    }

    private static String titleKey(boolean rules) {
        return rules ? WorldEditorMessageKey.RULES_TITLE.key() : WorldEditorMessageKey.ACCESS_TITLE.key();
    }

    private static String labelKey(WorldProperty<?> property) {
        return switch (property.key()) {
            case "pvp" -> WorldEditorMessageKey.PROP_PVP.key();
            case "difficulty" -> WorldEditorMessageKey.PROP_DIFFICULTY.key();
            case "force-gamemode" -> WorldEditorMessageKey.PROP_FORCE_GAMEMODE.key();
            case "spawn-animals" -> WorldEditorMessageKey.PROP_SPAWN_ANIMALS.key();
            case "spawn-monsters" -> WorldEditorMessageKey.PROP_SPAWN_MONSTERS.key();
            case "time" -> WorldEditorMessageKey.PROP_TIME.key();
            case "weather" -> WorldEditorMessageKey.PROP_WEATHER.key();
            case "access-restricted" -> WorldEditorMessageKey.PROP_ACCESS_RESTRICTED.key();
            case "player-limit" -> WorldEditorMessageKey.PROP_PLAYER_LIMIT.key();
            case "entry-fee" -> WorldEditorMessageKey.PROP_ENTRY_FEE.key();
            case "portal-nether-link" -> WorldEditorMessageKey.PROP_PORTAL_NETHER_LINK.key();
            case "portal-end-link" -> WorldEditorMessageKey.PROP_PORTAL_END_LINK.key();
            default -> throw new IllegalStateException("no label for " + property.key());
        };
    }

    private static String materialFor(WorldProperty<?> property) {
        return switch (property.key()) {
            case "pvp" -> "DIAMOND_SWORD";
            case "difficulty" -> "ZOMBIE_HEAD";
            case "force-gamemode" -> "COMMAND_BLOCK";
            case "spawn-animals" -> "EGG";
            case "spawn-monsters" -> "ROTTEN_FLESH";
            case "time" -> "CLOCK";
            case "weather" -> "WATER_BUCKET";
            case "access-restricted" -> "IRON_DOOR";
            case "player-limit" -> "PLAYER_HEAD";
            case "entry-fee" -> "GOLD_INGOT";
            case "portal-nether-link", "portal-end-link" -> "OBSIDIAN";
            default -> "PAPER";
        };
    }

    private GridSubject subject(MenuContext ctx) {
        return ctx.subject(GridSubject.class);
    }

    private GridSubject subject(MenuActionContext ctx) {
        return ctx.subject(GridSubject.class);
    }

    private GridRow row(MenuContext ctx) {
        return ctx.entry(GridRow.class);
    }

    private GridRow row(MenuActionContext ctx) {
        return ctx.entry(GridRow.class);
    }

    /**
     * The subject of an open grid: the world being edited, whether it shows the rules set (else access), and the
     * snapshotted property rows. The list source returns the rows and the placeholders read the bound row, so the
     * render touches no port; a cycle re-opens with a fresh subject carrying the new value.
     *
     * @param world the world this grid edits
     * @param rules whether this grid shows the rules set; false means the access set
     * @param rows the property rows in render order, each carrying its current encoded value
     */
    public record GridSubject(WorldName world, boolean rules, List<GridRow> rows) {

        public GridSubject {
            Objects.requireNonNull(world, "world");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }

        /** A copy with {@code propKey}'s row updated to {@code value}, leaving every other row untouched. */
        GridSubject withValue(String propKey, String value) {
            List<GridRow> updated = new ArrayList<>(rows.size());
            for (GridRow row : rows) {
                updated.add(row.property().key().equals(propKey) ? row.withValue(value) : row);
            }
            return new GridSubject(world, rules, updated);
        }
    }

    /**
     * One property cell: the property descriptor, its button material and label catalog key, and its current encoded
     * value. The placeholders read these; the cycle action reads the property to compute the next value.
     *
     * @param property the property this cell cycles
     * @param material the button material name
     * @param labelKey the property label's catalog key
     * @param value the property's current encoded value
     */
    public record GridRow(WorldProperty<?> property, String material, String labelKey, String value) {

        public GridRow {
            Objects.requireNonNull(property, "property");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(value, "value");
        }

        GridRow withValue(String newValue) {
            return new GridRow(property, material, labelKey, newValue);
        }
    }
}
