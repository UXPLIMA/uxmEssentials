package com.uxplima.uxmessentials.holograms.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.adapter.inbound.command.HologramCommands;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramEditorSubLayouts;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramEditorView;
import com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListMenu;
import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.DamageIndicatorListener;
import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramClickListener;
import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramVisibilityListener;
import com.uxplima.uxmessentials.holograms.adapter.outbound.DamageIndicatorConfig;
import com.uxplima.uxmessentials.holograms.adapter.outbound.EventDrivenNpcLocator;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramPageState;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRefreshTask;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRenderer;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramSymbols;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramTextOverrides;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramViewers;
import com.uxplima.uxmessentials.holograms.application.AddHologramAction;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.AddHologramPage;
import com.uxplima.uxmessentials.holograms.application.CenterHologram;
import com.uxplima.uxmessentials.holograms.application.ClearHologramActions;
import com.uxplima.uxmessentials.holograms.application.CopyHologram;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.DescribeHologram;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.application.InsertHologramAction;
import com.uxplima.uxmessentials.holograms.application.InsertHologramLine;
import com.uxplima.uxmessentials.holograms.application.LinkHologramToNpc;
import com.uxplima.uxmessentials.holograms.application.ListHologramActions;
import com.uxplima.uxmessentials.holograms.application.ListHologramPages;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.ManageHologramBlacklist;
import com.uxplima.uxmessentials.holograms.application.ManageHologramViewer;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.MoveHologramAction;
import com.uxplima.uxmessentials.holograms.application.NearbyHolograms;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramAction;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramPage;
import com.uxplima.uxmessentials.holograms.application.RotateHologram;
import com.uxplima.uxmessentials.holograms.application.SetHologramAction;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramGrowUp;
import com.uxplima.uxmessentials.holograms.application.SetHologramLeaderboard;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramModel;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
import com.uxplima.uxmessentials.holograms.application.TeleportToHologram;
import com.uxplima.uxmessentials.holograms.application.UnlinkHologramFromNpc;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.persistence.holograms.HologramRepositories;
import com.uxplima.uxmessentials.persistence.npc.NpcRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BlockedCommands;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.FilteredClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.miniplaceholders.MiniPlaceholdersSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.teleport.BukkitDirectTeleporter;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.DirectTeleporter;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import com.uxplima.uxmlib.packet.display.internal.NmsDisplayTextPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the holograms context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the uxmLib native-Display hologram API, and produces the Brigadier command the plugin registers.
 * This is the one place the holograms context is wired: nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the
 * delegate, invalidate in the cache). The renderer holds a fresh {@link HologramManager}; the uxmLib lifecycle
 * listener is installed once here so per-player viewer caches stay honest. On wire, every stored hologram is
 * spawned (each on its own region thread through the kernel {@code Scheduler}) so a restart brings the
 * holograms back exactly as configured. On stop the {@code Wired} bundle despawns them all so no display
 * entity is orphaned across a reload.
 */
@NullMarked
public final class HologramsWiring {

    private HologramsWiring() {}

    /** The smallest cadence the refresh timer fires at: one second, the floor a refresh interval rounds to. */
    private static final Duration REFRESH_BASE = Duration.ofSeconds(1);

    private static final int REFRESH_BASE_TICKS = 20;

    /** Build the holograms adapters and use cases, and spawn the stored holograms. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus bus,
            com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders leaderboards,
            Optional<ClickActionEconomy> economy,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(leaderboards, "leaderboards");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        // The concrete cache is what the cross-server listener reloads per name; the broadcasting decorator wraps
        // that same cache so a local hologram write announces it to peers, and the listener reloads + re-renders
        // the named hologram there. The renderer is built below, so the listener is registered after it exists.
        com.uxplima.uxmessentials.persistence.holograms.CachedHologramRepository cached =
                HologramRepositories.cachedConcrete(persistence);
        HologramRepository repository =
                com.uxplima.uxmessentials.shared.adapter.outbound.bus.HologramSync.repository(cached, bus.publisher());
        HologramManager manager = new HologramManager();
        manager.installLifecycleListener(plugin);
        // A hologram is one shared TextDisplay. Its broadcast text resolves placeholders server-globally (online,
        // time, TPS); the identity transform when PlaceholderAPI is absent, so a default server pays nothing. On
        // top of that base, a line embedding a placeholder additionally renders per viewer through the text-override
        // collaborator below: each viewer sees their own resolved values over the one shared entity.
        // The per-viewer current page of each multi-page hologram, shared by the text-override collaborator (which
        // resolves a viewer's current page) and the renderer (which advances it on a click and clears it on despawn).
        HologramPageState pageState = new HologramPageState();
        // Operator-defined text macros applied beneath the placeholder bridges, so a line's :token: expands on
        // both the shared base render and each viewer's override; the identity transform when none are configured.
        HologramSymbols symbols = HologramSymbols.fromConfig(ctx.config());
        Function<UUID, UnaryOperator<String>> perViewerBridge =
                viewer -> symbols.wrap(PlaceholderApiSupport.messageBridge(viewer));
        HologramTextOverrides textOverrides = new HologramTextOverrides(
                perViewerTextPackets(),
                perViewerBridge,
                MiniMessage.miniMessage(),
                MiniPlaceholdersSupport::globalResolver,
                pageState,
                kernel.log());
        HologramViewers viewers =
                new HologramViewers(plugin, kernel.permissions(), repository::manualViewers, repository::blacklisted);
        // The NPC-link seam: a locator over the npc context's stored set kept current by the domain-event bus,
        // re-anchoring a linked hologram when its NPC moves or is removed. The locator reads the npc persistence
        // directly (the npc table ships in the persistence V38 baseline, always applied), so a hologram may link to
        // an NPC even with the npc module disabled: it simply sees no live moves in that case.
        NpcRepository npcRepository = NpcRepositories.cached(persistence);
        // Break the renderer↔locator construction cycle: the locator's re-anchor forwards through a holder the
        // renderer is written into once it exists, so a move event re-renders only the holograms linked to that NPC.
        RendererHolder rendererHolder = new RendererHolder();
        EventDrivenNpcLocator npcLocator =
                new EventDrivenNpcLocator(npcRepository, name -> rendererHolder.reanchor(name));
        HologramRenderer renderer = new HologramRenderer(
                plugin,
                manager,
                kernel.scheduler(),
                kernel.log(),
                symbols.wrap(PlaceholderApiSupport.globalBridge()),
                MiniPlaceholdersSupport::globalResolver,
                viewers,
                textOverrides,
                npcLocator,
                leaderboards,
                pageState);
        rendererHolder.set(renderer);
        // Close the cross-server loop: a remote hologram change reloads exactly that hologram into the same cache
        // the commands and renderer read, then re-renders (or despawns) the live display through the renderer
        // the same render path the local edit runs, hopped onto the right region thread by the renderer itself.
        // With the bus disabled the publisher is a no-op and this listener is never invoked, so the single-server
        // path is unchanged.
        bus.registry()
                .register(com.uxplima.uxmessentials.shared.adapter.outbound.bus.HologramSync.listener(
                        cached, renderer, kernel.scheduler()));
        InProcessDomainEventPublisher events = (InProcessDomainEventPublisher) kernel.events();
        Consumer<DomainEvent> npcSubscriber = npcLocator;
        events.subscribe(npcSubscriber);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        HologramServices services = assemble(kernel, repository, renderer, notifier, npcLocator);
        // A joining player must pick up the permission-gated holograms they qualify for at once, not after a
        // refresh tick; this listener re-evaluates only the gated holograms for that one player.
        plugin.getServer().getPluginManager().registerEvents(new HologramVisibilityListener(renderer), plugin);
        // The shared click-action engine: a hologram's chain runs through the same BukkitClickActionRunner npc uses,
        // built over the shared filtered command runner. Holograms ship no blocked-commands config key, so the
        // blocklist is empty (a transparent pass-through: an operator-owned fixture, no command filtering needed yet).
        // The b64 item resolver matches the token /hologram action … give hand stamps onto a GIVE action.
        ClickCommandRunner commandRunner = new FilteredClickCommandRunner(
                new BukkitClickCommandRunner(), BlockedCommands.of(List.of()), kernel.log());
        BukkitServerConnector connector = new BukkitServerConnector(plugin, kernel.log());
        ClickActionRunner actionRunner = new BukkitClickActionRunner(
                commandRunner,
                connector,
                kernel.scheduler(),
                kernel.permissions(),
                economy,
                kernel.messages(),
                HologramsMessageKey.HOLOGRAM_ACTION_COST_DENIED,
                SerializedItems::decode,
                kernel.log());
        HologramClickListener clickListener =
                new HologramClickListener(plugin, repository, renderer, actionRunner, kernel.scheduler());
        plugin.getServer().getPluginManager().registerEvents(clickListener, plugin);
        // The damage-indicator feature ships disabled: only when its config switches it on is the combat listener
        // registered, so a default server spawns no floating-number entities and the listener costs nothing.
        DamageIndicatorConfig damageIndicators = DamageIndicatorConfig.fromConfig(ctx.config());
        if (damageIndicators.enabled()) {
            plugin.getServer()
                    .getPluginManager()
                    .registerEvents(new DamageIndicatorListener(damageIndicators, kernel.scheduler()), plugin);
        }
        AutoCloseable refreshTask = scheduleRefresh(kernel.scheduler(), repository, renderer, kernel.log());
        // The cached repository's all() is the authoritative in-memory set after the one warm load, so reading
        // names off it per keystroke is allocation-light and never touches the database, safe on the tick thread.
        java.util.function.Supplier<java.util.List<String>> hologramNames = () -> repository.all().stream()
                .map(hologram -> hologram.name().value())
                .toList();
        // The same warm-set read for NPC names, so /hologram linknpc tab-completes against the current NPCs.
        java.util.function.Supplier<java.util.List<String>> npcNames = () ->
                npcRepository.all().stream().map(npc -> npc.name().value()).toList();
        // The management GUI: an editor that exposes every property over the use cases, and a list that opens it.
        // The list backs both /hologram (no args) and the /uxmess gui hub entry; the back button returns to it.
        HologramEditorSubLayouts subLayouts = HologramEditorSubLayouts.load(
                plugin.getDataFolder().toPath(), "holograms", "hologram-editor", kernel.log());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout colourPicker =
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout.load(
                        plugin.getDataFolder().toPath(), kernel.log());
        EntityEditorLayout editorLayout =
                guiLayouts.loadEntityEditor("holograms", "hologram-editor", editorCodeDefault());
        HologramListMenu[] listHolder = new HologramListMenu[1];
        HologramEditorView editorView = new HologramEditorView(
                menus,
                guiText,
                kernel.scheduler(),
                repository,
                services,
                textInput,
                kernel.playerLookup(),
                kernel.messages(),
                editorLayout,
                subLayouts,
                colourPicker,
                (player, viewer) -> listHolder[0].open(viewer));
        HologramListMenu listMenu =
                new HologramListMenu(menus, kernel.scheduler(), repository, services, textInput, editorView);
        listHolder[0] = listMenu;
        listMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        return new Wired(
                HologramCommands.all(services, kernel.messages(), hologramNames, npcNames, listMenu),
                renderer,
                repository,
                services,
                refreshTask,
                events,
                npcSubscriber,
                connector,
                listMenu);
    }

    /** The editor's property-button slots, the code default matching the bundled hologram-editor.conf. */
    private static final List<Integer> EDITOR_PROPERTY_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42,
            43, 46, 47, 48);

    /**
     * The 6-row editor code default used when no {@code hologram-editor.conf} is present. The shared
     * {@link EntityEditorLayout#withDelete} factory is a 3-row default that cannot hold the 22 property slots,
     * so this builds the layout directly with the bundled geometry.
     */
    private static EntityEditorLayout editorCodeDefault() {
        return new EntityEditorLayout(
                6,
                EDITOR_PROPERTY_SLOTS,
                49,
                java.util.OptionalInt.of(53),
                org.bukkit.Material.ARROW,
                org.bukkit.Material.BARRIER,
                org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * A write-once holder for the renderer so the event-driven NPC locator (built before the renderer, since the
     * renderer needs it) can route a re-anchor back into the renderer once it exists. A re-anchor that somehow
     * arrives before the renderer is set is dropped: there is nothing rendered yet to move.
     */
    private static final class RendererHolder {

        private @Nullable HologramRenderer renderer;

        void set(HologramRenderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
        }

        void reanchor(String npcName) {
            HologramRenderer current = renderer;
            if (current != null) {
                current.reanchorLinkedTo(npcName);
            }
        }
    }

    /**
     * Build the lib per-viewer text-override packet port: the channel resolver finds each viewer's Netty channel,
     * the sender writes to it, and the NMS port builds the {@code ClientboundSetEntityDataPacket} that overrides
     * one viewer's copy of a shared display's text. The same stack the nametags and tablist contexts use; it is a
     * no-op send for a viewer whose channel cannot be resolved.
     */
    private static DisplayTextPackets perViewerTextPackets() {
        return new NmsDisplayTextPackets(new PacketSender(new ChannelResolver()));
    }

    private static AutoCloseable scheduleRefresh(
            Scheduler scheduler, HologramRepository repository, HologramRenderer renderer, Logger log) {
        // A single global timer ticks every second and re-renders only the holograms whose interval is due, so
        // a server with no refreshing hologram pays just the empty iteration. The handle is closed on stop.
        HologramRefreshTask task = new HologramRefreshTask(repository::all, renderer::refresh, log, REFRESH_BASE_TICKS);
        return scheduler.repeatGlobal(task::tick, REFRESH_BASE, REFRESH_BASE);
    }

    private static HologramServices assemble(
            KernelPorts kernel,
            HologramRepository repository,
            HologramRenderer renderer,
            Notifier notifier,
            LinkedNpcLocator npcLocator) {
        Clock clock = Clock.systemUTC();
        DirectTeleporter teleporter = new BukkitDirectTeleporter(kernel.scheduler(), kernel.log());
        return new HologramServices(
                new CreateHologram(repository, renderer, notifier, kernel.events(), clock),
                new DeleteHologram(repository, renderer, notifier, kernel.events()),
                new ListHolograms(repository, notifier),
                new AddHologramLine(repository, renderer, notifier),
                new SetHologramLine(repository, renderer, notifier),
                new InsertHologramLine(repository, renderer, notifier),
                new RemoveHologramLine(repository, renderer, notifier),
                new MoveHologram(repository, renderer, notifier),
                new CenterHologram(repository, renderer, notifier),
                new TeleportToHologram(repository, teleporter, notifier),
                new CopyHologram(repository, renderer, notifier),
                new RotateHologram(repository, renderer, notifier),
                new DescribeHologram(repository, notifier),
                new NearbyHolograms(repository, notifier),
                new SetHologramAppearance(repository, renderer, notifier),
                new SetHologramRefresh(repository, renderer, notifier),
                new SetHologramVisibility(repository, renderer, notifier),
                new ManageHologramViewer(repository, renderer, notifier),
                new SetHologramModel(repository, renderer, notifier),
                new SetHologramClickCommand(repository, renderer, notifier),
                new SetHologramLeaderboard(repository, renderer, notifier),
                new LinkHologramToNpc(repository, renderer, notifier, npcLocator),
                new UnlinkHologramFromNpc(repository, renderer, notifier),
                new AddHologramPage(repository, renderer, notifier),
                new RemoveHologramPage(repository, renderer, notifier),
                new ListHologramPages(repository, notifier),
                new SetHologramGrowUp(repository, renderer, notifier),
                new ManageHologramBlacklist(repository, renderer, notifier),
                new AddHologramAction(repository, notifier),
                new InsertHologramAction(repository, notifier),
                new SetHologramAction(repository, notifier),
                new MoveHologramAction(repository, notifier),
                new RemoveHologramAction(repository, notifier),
                new ClearHologramActions(repository, notifier),
                new ListHologramActions(repository, notifier));
    }

    /**
     * Everything the holograms module contributes once wired: the single Brigadier command and the renderer
     * whose live display entities must be despawned on stop so a reload re-spawns cleanly.
     *
     * @param commands the Brigadier command registrations to publish
     * @param renderer the live-entity renderer, despawned on stop
     * @param repository the cached hologram repository the PAPI seam reads the server-wide count from
     * @param refreshTask the global refresh timer handle, cancelled on stop so no task outlives a disable
     * @param events the domain-event bus the npc-link locator subscribed to, dropped on stop
     * @param npcSubscriber the npc-link locator subscription, unsubscribed on stop so it does not outlive a reload
     * @param connector the proxy connect channel the click-action engine uses, unregistered on stop so nothing outlives a disable
     * @param listMenu the management-GUI list, opened by /hologram (no args) and the /uxmess gui hub entry
     */
    public record Wired(
            List<CommandRegistration> commands,
            HologramRenderer renderer,
            HologramRepository repository,
            HologramServices services,
            AutoCloseable refreshTask,
            InProcessDomainEventPublisher events,
            Consumer<DomainEvent> npcSubscriber,
            BukkitServerConnector connector,
            HologramListMenu listMenu) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(refreshTask, "refreshTask");
            Objects.requireNonNull(events, "events");
            Objects.requireNonNull(npcSubscriber, "npcSubscriber");
            Objects.requireNonNull(connector, "connector");
            Objects.requireNonNull(listMenu, "listMenu");
        }

        /**
         * Spawn every stored hologram.
         *
         * <p>Called once the worlds exist, not during wiring. The plugin enables at {@code load: STARTUP}, so on
         * the wiring thread {@code Bukkit.getWorld(uid)} answers null for every hologram on the server and the
         * renderer would skip each one with a "world not loaded" warning that blames the operator's data rather
         * than the startup order. Each render still hops onto the hologram's own region thread inside the renderer.
         */
        public void spawnStored() {
            for (Hologram hologram : repository.all()) {
                renderer.render(hologram);
            }
        }

        /**
         * Cancel the refresh timer, drop the npc-link subscription, unregister the proxy connect channel, and
         * despawn every spawned hologram.
         */
        public void stop() {
            events.unsubscribe(npcSubscriber);
            closeQuietly(refreshTask);
            connector.close();
            renderer.despawnAll();
        }

        private static void closeQuietly(@Nullable AutoCloseable task) {
            if (task == null) {
                return;
            }
            try {
                task.close();
            } catch (Exception cancellation) {
                // The repeating-task handle's cancel does not throw; close() only declares the checked type.
            }
        }
    }
}
