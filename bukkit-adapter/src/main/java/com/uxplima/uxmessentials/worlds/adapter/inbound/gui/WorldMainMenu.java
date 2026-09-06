package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the per-world editor hub with the menu engine and opens it. A three-row panel for one managed world
 * reached from the engine world picker (a left click on a world icon) and the {@code /world gui <world>} command: a
 * read-only summary, buttons drilling into the rules, generation and access sub-screens, a load/unload toggle whose
 * label and dye follow the world's live loaded state, and a back button to the picker. Drilling opens that sub-screen
 * with the same world as its subject; the toggle runs the load/unload use case on the global region thread, then
 * re-opens this hub so the new loaded state shows.
 *
 * <p>The world's summary is read on the viewer's entity thread at open and handed in as the {@link Subject}, so the
 * title and summary line fill from the {@code world_main_*} placeholders without the renderer touching a port. The hub
 * holds no new domain logic: it replays the old bespoke {@code WorldMainView} verbatim through the engine. The
 * sub-screen collaborators and the toggle handler are injected through {@link #bind} after this menu so their re-open
 * cycle is broken, mirroring the warp editor's {@code bind}. Every visible string resolves from the worlds catalog.
 */
@NullMarked
public final class WorldMainMenu {

    /** The engine spec id this hub registers and opens under. */
    public static final String SPEC_ID = "world-main";

    private static final String SPEC_RESOURCE = "modules/worlds/gui/world-main.conf";
    private static final int ROWS = 3;

    private final Menus menus;
    private final Scheduler scheduler;
    private final WorldRepository repository;
    private final WorldEngine engine;
    private final BiConsumer<Player, PlayerRef> reopenList;

    /** The sub-screen collaborators and toggle handler the buttons run; injected after this menu via {@link #bind}. */
    private @Nullable WorldGridMenu gridMenu;

    private @Nullable WorldGenerationMenu generationMenu;
    private @Nullable BiConsumer<PlayerRef, WorldName> toggleLoad;

    public WorldMainMenu(
            Menus menus,
            Scheduler scheduler,
            WorldRepository repository,
            WorldEngine engine,
            BiConsumer<Player, PlayerRef> reopenList) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.reopenList = Objects.requireNonNull(reopenList, "reopenList");
    }

    /**
     * Wire the sub-screens the hub's drill buttons open and the load/unload toggle handler. Each reopens this hub
     * after its work, so they hold this menu and this menu holds them; this setter breaks that cycle.
     */
    public void bind(
            WorldGridMenu gridMenu, WorldGenerationMenu generationMenu, BiConsumer<PlayerRef, WorldName> toggle) {
        this.gridMenu = Objects.requireNonNull(gridMenu, "gridMenu");
        this.generationMenu = Objects.requireNonNull(generationMenu, "generationMenu");
        this.toggleLoad = Objects.requireNonNull(toggle, "toggle");
    }

    /** Register the subject placeholders, the drill/back/toggle actions, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        registerPlaceholders(bindings);
        bindings.action("worlds:editor-rules", ctx -> drillGrid(ctx, true));
        bindings.action("worlds:editor-access", ctx -> drillGrid(ctx, false));
        bindings.action("worlds:editor-generation", this::drillGeneration);
        bindings.action("worlds:editor-main-back", ctx -> reopenList.accept(ctx.player(), ctx.viewer()));
        bindings.action("worlds:editor-toggle-load", this::toggle);
        menus.registerSpec(SPEC_ID, WorldEditorSpecs.load(SPEC_RESOURCE, ROWS, dataFolder, log));
    }

    private void registerPlaceholders(MenuBindings bindings) {
        bindings.placeholder("world_main_name", ctx -> subject(ctx).world().value());
        bindings.placeholder("world_main_environment", ctx -> subject(ctx).environment());
        bindings.placeholder("world_main_type", ctx -> subject(ctx).type());
        bindings.placeholder(
                "world_main_loaded", ctx -> String.valueOf(subject(ctx).loaded()));
        bindings.placeholder(
                "world_main_players", ctx -> String.valueOf(subject(ctx).players()));
        bindings.placeholder("world_main_alias", ctx -> subject(ctx).alias());
        bindings.placeholder("world_main_toggle_material", ctx -> subject(ctx).loaded() ? "LIME_DYE" : "GRAY_DYE");
        bindings.placeholder(
                "world_main_toggle_key", ctx -> toggleKey(subject(ctx).loaded()));
    }

    /**
     * Open the hub for {@code world}. The world's summary is a Bukkit read (loaded state, player count), so it is
     * snapshotted on the viewer's entity thread and handed to the engine as the subject; the engine then renders off
     * that snapshot without a Bukkit read of its own. A world missing from the repository still opens with a minimal
     * snapshot so the viewer is never trapped on a blank window.
     */
    public void open(Player player, PlayerRef viewer, WorldName world) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(world, "world");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, snapshot(world)));
    }

    /** The world's summary, read on the calling entity thread so the engine renders without touching Bukkit off-thread. */
    private Subject snapshot(WorldName world) {
        Optional<ManagedWorld> managed = repository.find(world);
        WorldSpec spec = managed.map(ManagedWorld::spec).orElseGet(WorldSpec::normal);
        String alias = managed.flatMap(ManagedWorld::alias).orElse("-");
        return new Subject(
                world,
                spec.environment().name(),
                spec.worldType().name(),
                engine.isLoaded(world),
                engine.playerCount(world),
                alias);
    }

    private void drillGrid(MenuActionContext ctx, boolean rules) {
        if (gridMenu != null) {
            gridMenu.open(ctx.player(), ctx.viewer(), subject(ctx).world(), rules);
        }
    }

    private void drillGeneration(MenuActionContext ctx) {
        if (generationMenu != null) {
            generationMenu.open(ctx.player(), ctx.viewer(), subject(ctx).world());
        }
    }

    private void toggle(MenuActionContext ctx) {
        if (toggleLoad != null) {
            toggleLoad.accept(ctx.viewer(), subject(ctx).world());
        }
    }

    private static String toggleKey(boolean loaded) {
        return loaded ? WorldEditorMessageKey.NAV_UNLOAD.key() : WorldEditorMessageKey.NAV_LOAD.key();
    }

    private Subject subject(MenuContext ctx) {
        return ctx.subject(Subject.class);
    }

    private Subject subject(MenuActionContext ctx) {
        return ctx.subject(Subject.class);
    }

    /**
     * The subject of an open hub: the world being edited and its summary snapshot. Environment, type, loaded state,
     * player count and alias. Read on the viewer's entity thread before the open so the engine renders without a
     * Bukkit read. The placeholders read this directly, so the render touches no port.
     *
     * @param world the world this hub edits
     * @param environment the world's environment name
     * @param type the world's generation type name
     * @param loaded whether the world was loaded when the snapshot was taken
     * @param players the world's player count when the snapshot was taken
     * @param alias the world's alias, or {@code "-"} when none is set
     */
    public record Subject(WorldName world, String environment, String type, boolean loaded, int players, String alias) {

        public Subject {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(alias, "alias");
        }
    }
}
