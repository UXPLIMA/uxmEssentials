package com.uxplima.uxmessentials.worlds.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.persistence.worlds.WorldRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryWorldsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.WorldsPlaceholders;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommands;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldCreateMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGenerationMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGridMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldListMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainMenu;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.AnimalSpawnListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.ForceGamemodeListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.VoidRescueListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.WorldAccessListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.WorldPortalListener;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitChunkGenSource;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitGameRuleCatalog;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldArchive;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldEngine;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldPregen;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldSettingApplier;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldTeleporter;
import com.uxplima.uxmessentials.worlds.adapter.outbound.ChunkGenSource;
import com.uxplima.uxmessentials.worlds.adapter.outbound.ForcedWorldEntryMarker;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InFlightScheduler;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InMemoryPendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InMemoryPendingRestoreRegistry;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldArchiver;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldAutoUnloadSweep;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import com.uxplima.uxmessentials.worlds.adapter.outbound.api.WorldApiWrites;
import com.uxplima.uxmessentials.worlds.application.ApplyWorldSettingsOnLoad;
import com.uxplima.uxmessentials.worlds.application.BackupWorld;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListBackups;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.PregenWorld;
import com.uxplima.uxmessentials.worlds.application.ReconcileWorldsOnEnable;
import com.uxplima.uxmessentials.worlds.application.ResolvePortalDestination;
import com.uxplima.uxmessentials.worlds.application.ResolveVoidRescue;
import com.uxplima.uxmessentials.worlds.application.RestoreWorld;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.RescueTargets;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldPregen;
import com.uxplima.uxmessentials.worlds.application.port.WorldSettingApplier;
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the worlds context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the Bukkit {@link Server}, and produces the Brigadier command list the plugin registers. This
 * is the one place the worlds context is wired: nothing else news up its classes.
 *
 * <p>The repository is the cached jOOQ adapter; its in-memory snapshot is warmed once on enable so every
 * later {@code /worlds} resolve and tab-complete is served from memory rather than a synchronous SQLite
 * read on the command thread. The enable-time reconcile runs on the global region thread (Bukkit world
 * handles require it), then invalidates the warmed snapshot, its adopt/auto-load phases mutated rows, so
 * the next read reloads, and refreshes the import-folder snapshot off-tick.
 */
@NullMarked
public final class WorldsWiring {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private WorldsWiring() {}

    public static Wired wire(
            ModuleContext ctx,
            Persistence persistence,
            Server server,
            InProcessDomainEventPublisher events,
            TeleportEngine teleportEngine,
            WorldEntryFee entryFee,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings,
            RescueTargets rescueTargets,
            Path dataFolder) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(entryFee, "entryFee");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(rescueTargets, "rescueTargets");
        Objects.requireNonNull(dataFolder, "dataFolder");
        KernelPorts kernel = ctx.kernel();

        CachedWorldRepository repository = WorldRepositories.cachedConcrete(persistence);
        repository.all(); // warm the in-memory snapshot once on enable (tick-safe reads thereafter)

        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        WorldsSettings settings = new WorldsSettings(ctx.config());
        WorldGeneratorResolver resolver = new WorldGeneratorResolver(
                settings.flatLayers(), settings.voidBiome(), settings.flatBiome(), kernel.log());
        BukkitWorldEngine engine = new BukkitWorldEngine(server, kernel.log(), resolver);
        InMemoryPendingDeletionRegistry pending = new InMemoryPendingDeletionRegistry();
        Clock clock = Clock.systemUTC();

        AtomicInteger inFlight = new AtomicInteger();
        Scheduler tracked = new InFlightScheduler(kernel.scheduler(), inFlight);

        GameRuleCatalog ruleCatalog = new BukkitGameRuleCatalog();
        WorldSettingApplier applier = new BukkitWorldSettingApplier(server, ruleCatalog, kernel.log());
        ApplyWorldSettingsOnLoad onLoad = new ApplyWorldSettingsOnLoad(repository, applier);

        WorldAccessPolicy policy = new WorldAccessPolicy(kernel.permissions(), engine);
        // One marker shared between the teleporter (which marks staff /worlds tp and login-redirect hand-offs)
        // and the access listener (which consumes the mark to exempt those worlds-initiated teleports).
        ForcedWorldEntryMarker forcedEntries = new ForcedWorldEntryMarker();
        BukkitWorldTeleporter teleporter = new BukkitWorldTeleporter(teleportEngine, forcedEntries);
        WorldTeleportService worldTeleport = new WorldTeleportService(
                repository,
                engine,
                policy,
                teleporter,
                entryFee,
                kernel.permissions(),
                kernel.events(),
                notifier,
                tracked);
        WorldAccessListener accessListener = new WorldAccessListener(
                repository,
                policy,
                worldTeleport,
                engine,
                kernel.events(),
                kernel.scheduler(),
                notifier,
                forcedEntries,
                settings.redirectOnRestrictedJoin());
        ResolveVoidRescue voidRescue =
                new ResolveVoidRescue(repository, rescueTargets, kernel.worldLookup(), kernel.log(), clock);
        VoidRescueListener voidRescueListener = new VoidRescueListener(voidRescue, teleporter, kernel.permissions());
        ResolvePortalDestination resolvePortal = new ResolvePortalDestination(repository);
        WorldPortalListener portalListener = new WorldPortalListener(resolvePortal, server, server.getLogger());

        // The pregen engine drives a tick-paced repeating loop, so it takes the raw kernel scheduler rather
        // than the in-flight-counting decorator (which only wraps async); its last arg is the project logger.
        ChunkGenSource chunkGen = new BukkitChunkGenSource(server);
        WorldPregen pregen = new BukkitWorldPregen(
                chunkGen, kernel.scheduler(), engine, kernel.messages(), notifier, settings, kernel.log());
        PregenWorld pregenWorld = new PregenWorld(repository, engine, pregen, settings, notifier, tracked);

        WorldArchiver archiver = new WorldArchiver();
        InMemoryPendingRestoreRegistry pendingRestore = new InMemoryPendingRestoreRegistry();
        BukkitWorldArchive archive = new BukkitWorldArchive(
                server,
                kernel.scheduler(),
                engine,
                repository,
                archiver,
                worldTeleport,
                forcedEntries,
                settings,
                notifier,
                kernel.log(),
                dataFolder);
        BackupWorld backupWorld = new BackupWorld(repository, archive, notifier, tracked);
        ListBackups listBackups = new ListBackups(repository, archive);
        RestoreWorld restoreWorld = new RestoreWorld(repository, engine, archive, pendingRestore, notifier, tracked);

        // The idle auto-unload sweep is opt-in: start() schedules nothing and returns a no-op handle when disabled,
        // so a disabled sweep holds no runtime state. Its tick runs on the global region thread (where unloading a
        // world handle is legal), so it takes the raw kernel scheduler rather than the in-flight async decorator.
        WorldAutoUnloadSweep sweep = new WorldAutoUnloadSweep(
                kernel.scheduler(), engine, kernel.events(), settings, kernel.log(), Clock.systemUTC());
        AutoCloseable sweepHandle = sweep.start();
        WorldsPlaceholders worldsPlaceholders = new RepositoryWorldsPlaceholders(repository, engine);

        // The whole editor, the world picker, the per-world hub, the create screen, the read-only generation summary,
        // and the shared rules/access grid: now renders through the menu engine. The picker is reopened by the hub
        // and the create screen, so a one-element holder breaks that cycle, exactly the grid<->action seam the homes
        // and npc list migrations use. The reopen seam every "back" path calls resolves to the picker once it is built.
        WorldListMenu[] listHolder = new WorldListMenu[1];
        BiConsumer<Player, PlayerRef> reopenList = (player, viewer) -> listHolder[0].open(player, viewer);
        WorldMainMenu mainMenu = new WorldMainMenu(menus, tracked, repository, engine, reopenList);
        WorldsServices services = assemble(
                kernel,
                tracked,
                repository,
                notifier,
                engine,
                pending,
                settings,
                clock,
                ruleCatalog,
                worldTeleport,
                pregenWorld,
                backupWorld,
                listBackups,
                restoreWorld,
                reopenList,
                mainMenu::open);
        // The create screen reopens the engine list on cancel/create through the shared reopen seam, runs the started
        // CreateWorld use case, and captures text through the shared input seam.
        WorldCreateMenu createMenu =
                new WorldCreateMenu(menus, tracked, services.createWorld(), notifier, textInput, reopenList);
        WorldGenerationMenu generationMenu = new WorldGenerationMenu(menus, tracked, repository);
        WorldGridMenu gridMenu = new WorldGridMenu(menus, tracked, repository, services.setWorldProperty());
        // The hub's toggle button flips the world's loaded state: the load/unload use case reaches Bukkit world
        // handles,
        // which Folia permits only on the global region thread, so hop there, then re-open the hub (which
        // self-schedules
        // back onto the viewer's entity thread) so the toggled state is reflected.
        mainMenu.bind(
                gridMenu,
                generationMenu,
                (viewer, world) -> tracked.onGlobal(() -> {
                    if (engine.isLoaded(world)) {
                        services.unloadWorld().unload(viewer, world, true);
                    } else {
                        services.loadWorld().load(viewer, world);
                    }
                    org.bukkit.entity.Player live = server.getPlayer(viewer.uuid());
                    if (live != null) {
                        mainMenu.open(live, viewer, world);
                    }
                }));
        generationMenu.bind(mainMenu);
        gridMenu.bind(mainMenu);
        // Build the engine list over the engine screens it launches (left-click opens the hub, the create button opens
        // the create screen) and the forced-teleport service (right-click), then close the cycle and register every
        // editor screen's bindings and spec with the menu engine.
        WorldListMenu worldListMenu =
                new WorldListMenu(menus, tracked, repository, engine, worldTeleport, mainMenu, createMenu);
        listHolder[0] = worldListMenu;
        worldListMenu.register(menuBindings, dataFolder, kernel.log());
        mainMenu.register(menuBindings, dataFolder, kernel.log());
        createMenu.register(menuBindings, dataFolder, kernel.log());
        generationMenu.register(menuBindings, dataFolder, kernel.log());
        gridMenu.register(menuBindings, dataFolder, kernel.log());
        ReconcileWorldsOnEnable reconcile = new ReconcileWorldsOnEnable(
                repository, engine, kernel.events(), clock, settings::autoAdoptLoaded, settings::autoLoadRegistered);

        // Re-apply a world's stored settings to the live world on load and after any setting change, on the
        // global region thread (the applier touches Bukkit world handles). Subscribed for the module's lifetime
        // and dropped on stop so a reload never leaves a stale listener firing against torn-down state.
        Consumer<DomainEvent> applySubscriber = event -> {
            if (event instanceof WorldLoaded loaded) {
                kernel.scheduler().onGlobal(() -> onLoad.apply(loaded.name()));
            } else if (event instanceof WorldSettingChanged changed) {
                kernel.scheduler().onGlobal(() -> onLoad.apply(changed.name()));
            }
        };
        events.subscribe(applySubscriber);

        List<Listener> listeners = List.of(
                new ForceGamemodeListener(repository, kernel.scheduler()),
                new AnimalSpawnListener(repository),
                accessListener,
                voidRescueListener,
                portalListener);
        List<CommandRegistration> commands = WorldCommands.all(services, kernel.messages());
        Runnable startReconcile = () -> kernel.scheduler().onGlobal(() -> {
            reconcile.run();
            repository.invalidateAll(); // reconciliation mutated rows; drop the warm snapshot so the next read reloads
            services.refreshImportableFolders();
        });
        return new Wired(
                commands,
                listeners,
                startReconcile,
                () -> {
                    events.unsubscribe(applySubscriber);
                    closeQuietly(sweepHandle, kernel); // stop the idle auto-unload sweep before the module tears down
                    pregen.stopAll(); // cancel every running pre-generation loop before the module tears down
                    awaitDrain(inFlight);
                },
                resolver,
                worldsPlaceholders,
                reopenList,
                repository,
                engine,
                policy,
                WorldApiWrites.of(services));
    }

    /** Close a stop-time handle, logging any failure rather than stranding the rest of the teardown. */
    private static void closeQuietly(AutoCloseable handle, KernelPorts kernel) {
        try {
            handle.close();
        } catch (Exception e) {
            kernel.log().error("failed to stop the world auto-unload sweep", e);
        }
    }

    /** Spin-wait until the off-tick write tails finish, bounded by {@link #DRAIN_TIMEOUT}, on stop/reload. */
    private static void awaitDrain(AtomicInteger inFlight) {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static WorldsServices assemble(
            KernelPorts kernel,
            Scheduler scheduler,
            com.uxplima.uxmessentials.worlds.application.port.WorldRepository repository,
            Notifier notifier,
            BukkitWorldEngine engine,
            InMemoryPendingDeletionRegistry pending,
            WorldsSettings settings,
            Clock clock,
            GameRuleCatalog ruleCatalog,
            WorldTeleportService worldTeleport,
            PregenWorld pregenWorld,
            BackupWorld backupWorld,
            ListBackups listBackups,
            RestoreWorld restoreWorld,
            BiConsumer<Player, PlayerRef> openWorldList,
            com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainOpener openWorldMain) {
        CreateWorld createWorld = new CreateWorld(repository, engine, notifier, kernel.events(), scheduler, clock);
        ImportWorld importWorld = new ImportWorld(repository, engine, notifier, kernel.events(), scheduler, clock);
        LoadWorld loadWorld = new LoadWorld(repository, engine, notifier, kernel.events(), scheduler);
        UnloadWorld unloadWorld = new UnloadWorld(engine, notifier, kernel.events(), settings::protectDefaultWorld);
        UnregisterWorld unregisterWorld = new UnregisterWorld(repository, notifier, kernel.events(), scheduler);
        DeleteWorld deleteWorld = new DeleteWorld(
                repository,
                engine,
                pending,
                notifier,
                kernel.events(),
                scheduler,
                clock,
                settings::protectDefaultWorld);
        ListWorlds listWorlds = new ListWorlds(repository, engine);
        WorldInfo worldInfo = new WorldInfo(repository);
        SetWorldProperty setProperty = new SetWorldProperty(repository, notifier, kernel.events(), scheduler);
        SetGamerule setGamerule = new SetGamerule(repository, ruleCatalog, notifier, kernel.events(), scheduler);
        SetWorldSpawn setSpawn = new SetWorldSpawn(repository, notifier, kernel.events(), scheduler);
        SetWorldAlias setAlias = new SetWorldAlias(repository, notifier, kernel.events(), scheduler);
        return new WorldsServices(
                createWorld,
                importWorld,
                loadWorld,
                unloadWorld,
                unregisterWorld,
                deleteWorld,
                listWorlds,
                worldInfo,
                setProperty,
                setGamerule,
                setSpawn,
                setAlias,
                pregenWorld,
                ruleCatalog,
                repository,
                kernel.scheduler(),
                engine::onDiskWorldNames,
                worldTeleport,
                backupWorld,
                listBackups,
                restoreWorld,
                openWorldList,
                openWorldMain);
    }

    /**
     * The wired worlds adapters handed back to bootstrap: commands, listeners, the reconcile kick, the
     * stop hook (which unsubscribes the live-apply listener, stops the idle auto-unload sweep, and drains
     * in-flight off-tick writes), the built-in generator resolver, and the worlds placeholder seam. The
     * resolver is non-null whenever worlds wires (it is always built from the config); bootstrap captures it
     * onto the holder the plugin retains so {@code getDefaultWorldGenerator} can serve {@code generator:
     * uxmEssentials:void|flat} to any world configured in {@code bukkit.yml}, the default world included. The placeholder seam is registered onto
     * the shared placeholder contexts so the {@code worlds_*} tokens resolve while the module is enabled. The
     * open-world-list seam opens the engine-rendered {@code /world gui} world picker the management hub also opens.
     * The register, the engine and the entry gate are handed back so bootstrap can build the published worlds query
     * over the very same three the commands run on.
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            Runnable startReconcile,
            Runnable stop,
            WorldGeneratorResolver generatorResolver,
            WorldsPlaceholders worldsPlaceholders,
            BiConsumer<Player, PlayerRef> openWorldList,
            com.uxplima.uxmessentials.worlds.application.port.WorldRepository repository,
            com.uxplima.uxmessentials.worlds.application.port.WorldEngine engine,
            WorldAccessPolicy accessPolicy,
            WorldApiWrites apiWrites) {
        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(startReconcile, "startReconcile");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(generatorResolver, "generatorResolver");
            Objects.requireNonNull(worldsPlaceholders, "worldsPlaceholders");
            Objects.requireNonNull(openWorldList, "openWorldList");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(accessPolicy, "accessPolicy");
            Objects.requireNonNull(apiWrites, "apiWrites");
        }
    }
}
