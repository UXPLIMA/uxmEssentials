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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /worlds editor} world picker (opened by {@code /worlds editor} / {@code /world gui}, or the
 * worlds entry on the {@code /uxmess gui} hub) with the menu engine and opens it. A small class that registers the
 * per-world list source, the placeholders its entries need, the edit/teleport/create click actions, then loads the
 * {@code world-list} spec and hands it to {@link Menus}.
 *
 * <p>Unlike the other list migrations, the per-world loaded flag is a Bukkit read ({@code server.getWorld(...)})
 * that may not run in the engine's off-thread list source, so {@link #open} pre-computes the rows: each managed
 * world paired with its live loaded state. On the viewer's region thread and hands them to the engine as the menu
 * subject, mirroring {@code EntityCountMenu}. The {@code worlds:list} source then only reads that subject, and the
 * {@code world_icon} / {@code world_name} / {@code world_environment} / {@code world_loaded} placeholders read the
 * bound row, so the menu touches no Bukkit API off-thread.
 *
 * <p>A left click runs {@code worlds:edit}, opening that world's engine-rendered {@link WorldMainMenu} hub; a right
 * click runs {@code worlds:teleport}, the forced staff hand-off {@code /worlds tp <self>} drives (it skips the access
 * gate and entry fee, and loads-then-teleports on the global region thread); the create button runs
 * {@code worlds:create}, opening the engine-rendered {@link WorldCreateMenu}, so the list adds no domain logic of its
 * own.
 */
@NullMarked
public final class WorldListMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "world-list";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/worlds/gui/world-list.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldTeleportService worldTeleport;
    private final WorldMainMenu mainMenu;
    private final WorldCreateMenu createMenu;

    public WorldListMenu(
            Menus menus,
            Scheduler scheduler,
            WorldRepository repository,
            WorldEngine engine,
            WorldTeleportService worldTeleport,
            WorldMainMenu mainMenu,
            WorldCreateMenu createMenu) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.worldTeleport = Objects.requireNonNull(worldTeleport, "worldTeleport");
        this.mainMenu = Objects.requireNonNull(mainMenu, "mainMenu");
        this.createMenu = Objects.requireNonNull(createMenu, "createMenu");
    }

    /** Register the bindings the spec names and the spec itself; called once at worlds wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("worlds:list", ctx -> ctx.subject(WorldGrid.class).rows());
        bindings.placeholder("world_icon", ctx -> envMaterial(rowOf(ctx).world()));
        bindings.placeholder("world_name", ctx -> rowOf(ctx).world().name().value());
        bindings.placeholder("world_environment", ctx -> environment(rowOf(ctx)).name());
        bindings.placeholder("world_loaded", ctx -> String.valueOf(rowOf(ctx).loaded()));
        bindings.action("worlds:edit", this::edit);
        bindings.action("worlds:teleport", this::teleport);
        bindings.action("worlds:create", this::create);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the world picker for {@code viewer}. The per-world loaded flag is a Bukkit read, so the rows are computed
     * on the viewer's entity thread (where {@code open} is called from) and handed to the engine as the subject; the
     * engine then resolves the list source and renders off that snapshot without touching the Bukkit API.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, snapshot()));
    }

    /** The current managed worlds paired with their live loaded state, read on the calling region thread. */
    private WorldGrid snapshot() {
        List<WorldRow> rows = new ArrayList<>();
        for (ManagedWorld world : repository.all()) {
            rows.add(new WorldRow(world, engine.isLoaded(world.name())));
        }
        return new WorldGrid(rows);
    }

    /** The bound list row for the slot being rendered or clicked. */
    private WorldRow rowOf(MenuContext ctx) {
        return ctx.entry(WorldRow.class);
    }

    private static WorldEnvironment environment(WorldRow row) {
        return row.world().spec().environment();
    }

    /** Left-click a world icon: open that world's engine main editor hub on the viewer's entity thread. */
    private void edit(MenuActionContext ctx) {
        mainMenu.open(
                ctx.player(), ctx.viewer(), ctx.entry(WorldRow.class).world().name());
    }

    /**
     * Right-click a world icon: teleport the viewer there, mirroring the old picker's {@code /worlds tp <self>}
     * override. The staff override skips the access gate and entry fee (the editor is already permission-gated), and
     * the load-then-teleport runs on the global region thread because loading a world is legal under Folia only there.
     */
    private void teleport(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        com.uxplima.uxmessentials.worlds.domain.WorldName world =
                ctx.entry(WorldRow.class).world().name();
        ctx.player().closeInventory();
        scheduler.onGlobal(() -> worldTeleport.forced(viewer, viewer, world));
    }

    /** Left-click the create button: open the engine new-world configuration screen, exactly as the old button did. */
    private void create(MenuActionContext ctx) {
        createMenu.open(ctx.player(), ctx.viewer());
    }

    private static String envMaterial(ManagedWorld world) {
        return switch (world.spec().environment()) {
            case NETHER -> "NETHERRACK";
            case THE_END -> "END_STONE";
            case NORMAL -> "GRASS_BLOCK";
        };
    }

    /**
     * The subject of an open world picker: the managed worlds paired with their live loaded state, snapshotted on the
     * region thread before the open so the engine renders without a Bukkit read. The list source and the placeholders
     * read this, so the menu carries no world scan of its own.
     *
     * @param rows the managed worlds with their loaded flag, in repository order
     */
    public record WorldGrid(List<WorldRow> rows) {

        public WorldGrid {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /**
     * One cell of the picker grid: a managed world and whether it is currently loaded. The loaded flag is resolved on
     * the region thread at snapshot time, so the placeholders never read the Bukkit API.
     *
     * @param world the managed world this cell stands for
     * @param loaded whether that world was loaded when the snapshot was taken
     */
    public record WorldRow(ManagedWorld world, boolean loaded) {

        public WorldRow {
            Objects.requireNonNull(world, "world");
        }
    }
}
