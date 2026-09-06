package com.uxplima.uxmessentials.npc.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcCommand;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcSkinByName;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcEditorSubLayouts;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcEditorView;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcListMenu;
import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcInteractionListener;
import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcLifecycleListener;
import com.uxplima.uxmessentials.npc.adapter.outbound.CompositeSkinService;
import com.uxplima.uxmessentials.npc.adapter.outbound.EquipmentPayloads;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcRenderer;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcViewSpawner;
import com.uxplima.uxmessentials.npc.adapter.outbound.RepositoryNpcPlaceholders;
import com.uxplima.uxmessentials.npc.application.AddNpcAction;
import com.uxplima.uxmessentials.npc.application.CenterNpc;
import com.uxplima.uxmessentials.npc.application.ClearNpcActions;
import com.uxplima.uxmessentials.npc.application.CopyNpc;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.DescribeNpc;
import com.uxplima.uxmessentials.npc.application.FixNpc;
import com.uxplima.uxmessentials.npc.application.InsertNpcAction;
import com.uxplima.uxmessentials.npc.application.ListNpcActions;
import com.uxplima.uxmessentials.npc.application.ListNpcEquipment;
import com.uxplima.uxmessentials.npc.application.ListNpcTypeData;
import com.uxplima.uxmessentials.npc.application.ListNpcs;
import com.uxplima.uxmessentials.npc.application.MoveNpc;
import com.uxplima.uxmessentials.npc.application.MoveNpcAction;
import com.uxplima.uxmessentials.npc.application.MoveNpcTo;
import com.uxplima.uxmessentials.npc.application.NearbyNpcs;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.NpcQuota;
import com.uxplima.uxmessentials.npc.application.NpcSettings;
import com.uxplima.uxmessentials.npc.application.RemoveNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcCollidable;
import com.uxplima.uxmessentials.npc.application.SetNpcDisplayName;
import com.uxplima.uxmessentials.npc.application.SetNpcEntityType;
import com.uxplima.uxmessentials.npc.application.SetNpcEquipment;
import com.uxplima.uxmessentials.npc.application.SetNpcGlowing;
import com.uxplima.uxmessentials.npc.application.SetNpcInteractionCooldown;
import com.uxplima.uxmessentials.npc.application.SetNpcLookAtPlayer;
import com.uxplima.uxmessentials.npc.application.SetNpcMirrorSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcPose;
import com.uxplima.uxmessentials.npc.application.SetNpcRange;
import com.uxplima.uxmessentials.npc.application.SetNpcScale;
import com.uxplima.uxmessentials.npc.application.SetNpcShowInTab;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcSkinSlim;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.npc.application.SetNpcTypeData;
import com.uxplima.uxmessentials.npc.application.TeleportToNpc;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.persistence.npc.NpcRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
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
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.NpcPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpClientFetcher;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MineSkinService;
import com.uxplima.uxmessentials.shared.adapter.outbound.teleport.BukkitDirectTeleporter;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.internal.NmsNpcPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the npc context's adapters and use cases over the injected kernel ports, the persistence DSL, and
 * the uxmLib NPC packet stack, and produces the Brigadier command and lifecycle listener the plugin registers.
 * This is the one place the npc context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator. The renderer holds the packet
 * stack — a {@link ChannelResolver} → {@link PacketSender} → {@link NmsNpcPackets} — and sends each viewer a
 * fake-player spawn (its tab entry kept unlisted) with no real entity. On wire every stored NPC is spawned to the
 * online viewers in range; a global refresh timer re-evaluates range each second so an NPC appears/disappears as
 * players move, and a faster look timer turns each looking NPC toward its nearby viewers. The interaction
 * listener runs an NPC's bound click command when a player clicks its fake entity. A {@code COST} click action
 * charges through the optional {@link ClickActionEconomy} bridge captured during economy wiring — empty on a
 * server without economy, in which case the cost gate is simply skipped. On stop the {@code Wired} bundle cancels
 * both timers and removes every shown NPC from every viewer so nothing is orphaned across a reload.
 */
@NullMarked
public final class NpcWiring {

    private static final Duration REFRESH_PERIOD = Duration.ofSeconds(1);
    /** The radius within which a viewer is shown an NPC — the vanilla player tracking range. */
    private static final double RENDER_RANGE = 48.0;

    private NpcWiring() {}

    /** Build the npc adapters and use cases, and spawn the stored NPCs to the online viewers in range. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus bus,
            Optional<ClickActionEconomy> economy,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        NpcSettings settings = new NpcSettings(ctx.config());
        // The concrete cache is what the cross-server listener reloads per name; the broadcasting decorator wraps
        // that same cache so a local NPC write announces it to peers, and the listener reloads + re-renders the
        // named NPC there. The renderer is built below, so the listener is registered after it exists.
        com.uxplima.uxmessentials.persistence.npc.CachedNpcRepository cached =
                NpcRepositories.cachedConcrete(persistence);
        NpcRepository repository =
                com.uxplima.uxmessentials.shared.adapter.outbound.bus.NpcSync.repository(cached, bus.publisher());
        NpcPackets packets = new NmsNpcPackets(new PacketSender(new ChannelResolver()));
        NpcViewSpawner spawner = new NpcViewSpawner(packets, kernel.log());
        NpcRenderer renderer =
                new NpcRenderer(packets, spawner, kernel.scheduler(), RENDER_RANGE, settings.lookRange());
        // Close the cross-server loop: a remote NPC change reloads exactly that NPC into the same cache the
        // commands and renderer read, then re-renders (or despawns) the live fake player through the renderer —
        // the same render path the local edit runs, hopped onto the global region thread by the renderer itself.
        // With the bus disabled the publisher is a no-op and this listener is never invoked, so the single-server
        // path is unchanged.
        bus.registry()
                .register(com.uxplima.uxmessentials.shared.adapter.outbound.bus.NpcSync.listener(
                        cached, renderer, kernel.scheduler()));
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        NpcQuota quota = new NpcQuota(kernel.permissions(), settings.defaultLimit());
        NpcServices services = assemble(kernel, repository, renderer, notifier, quota);
        spawnStored(repository, renderer);
        MineSkinService mineSkins = new MineSkinService(
                kernel.scheduler(),
                kernel.log(),
                new HttpClientFetcher(kernel.log(), MineSkinService.GENERATE_TIMEOUT),
                settings.mineSkinApiKey());
        SkinService skinService = new CompositeSkinService(kernel.skins(), mineSkins);
        NpcSkinByName skinByName =
                new NpcSkinByName(skinService, services.skin(), repository, notifier, kernel.scheduler());
        // The management GUI: an editor exposing every NPC property over the use cases, and a list — drawn through
        // the menu engine — that opens it. The list backs both /npc (no args) and the /uxmess gui hub entry; the
        // editor's back button returns to it. The list registers its bindings and spec with the engine here.
        NpcListMenu listMenu =
                buildGui(plugin, kernel, repository, services, skinByName, guiText, guiLayouts, textInput, menus);
        listMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        guiRegistry.register(new ManagementGuiEntry(
                "npc",
                NpcMessageKey.NPC_GUI_LIST_TITLE,
                org.bukkit.Material.PLAYER_HEAD,
                "uxmessentials.npc.gui",
                listMenu::open));
        List<CommandRegistration> commands =
                List.of(new NpcCommand(services, renderer::npcNames, skinByName, kernel.messages(), listMenu));
        ClickCommandRunner commandRunner = new FilteredClickCommandRunner(
                new BukkitClickCommandRunner(), BlockedCommands.of(settings.blockedCommands()), kernel.log());
        BukkitServerConnector connector = new BukkitServerConnector(plugin, kernel.log());
        ClickActionRunner actionRunner = new BukkitClickActionRunner(
                commandRunner,
                connector,
                kernel.scheduler(),
                kernel.permissions(),
                economy,
                kernel.messages(),
                NpcMessageKey.NPC_ACTION_COST_DENIED,
                EquipmentPayloads::resolve,
                kernel.log());
        NpcInteractionListener interaction = new NpcInteractionListener(
                renderer, repository, commandRunner, actionRunner, kernel.scheduler(), settings.clickCooldown());
        List<Listener> listeners = List.of(new NpcLifecycleListener(renderer), interaction);
        AutoCloseable refreshTask = kernel.scheduler().repeatGlobal(renderer::refresh, REFRESH_PERIOD, REFRESH_PERIOD);
        Duration lookPeriod = settings.lookPeriod();
        AutoCloseable lookTask = kernel.scheduler().repeatGlobal(renderer::lookTick, lookPeriod, lookPeriod);
        return new Wired(
                commands,
                listeners,
                renderer,
                repository,
                services,
                connector,
                refreshTask,
                lookTask,
                new RepositoryNpcPlaceholders(repository, quota));
    }

    /** The editor's property-button slots, the code default matching the bundled npc-editor.conf. */
    private static final List<Integer> EDITOR_PROPERTY_SLOTS =
            List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32);

    /**
     * Build the NPC management list (drawn through the menu engine) and editor over the shared GUI framework. The
     * editor's back button reopens the list, so a one-slot holder breaks the list↔editor construction cycle (the
     * editor is built first, the list second, and the holder is filled before either is shown). The list still
     * needs the bundled npc-editor layout loaded for the editor it opens; only the list geometry now lives in the
     * engine spec rather than the npc-list.conf layout.
     */
    private static NpcListMenu buildGui(
            Plugin plugin,
            KernelPorts kernel,
            NpcRepository repository,
            NpcServices services,
            NpcSkinByName skinByName,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus) {
        NpcEditorSubLayouts subLayouts =
                NpcEditorSubLayouts.load(plugin.getDataFolder().toPath(), "npc", "npc-editor", kernel.log());
        EntityEditorLayout editorLayout = guiLayouts.loadEntityEditor("npc", "npc-editor", editorCodeDefault());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout colourPicker =
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout.load(
                        plugin.getDataFolder().toPath(), kernel.log());
        NpcListMenu[] listHolder = new NpcListMenu[1];
        NpcEditorView editor = new NpcEditorView(
                menus,
                guiText,
                kernel.scheduler(),
                repository,
                services,
                skinByName,
                textInput,
                kernel.messages(),
                editorLayout,
                subLayouts,
                colourPicker,
                (player, viewer) -> listHolder[0].open(player, viewer));
        NpcListMenu listMenu = new NpcListMenu(menus, kernel.scheduler(), repository, services, textInput, editor);
        listHolder[0] = listMenu;
        return listMenu;
    }

    /**
     * The 6-row editor code default used when no {@code npc-editor.conf} is present. The shared
     * {@link EntityEditorLayout#withDelete} factory is a 3-row default that cannot hold the property slots, so
     * this builds the layout directly with the bundled geometry.
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

    private static NpcServices assemble(
            KernelPorts kernel, NpcRepository repository, NpcRenderer renderer, Notifier notifier, NpcQuota quota) {
        Clock clock = Clock.systemUTC();
        return new NpcServices(
                new CreateNpc(repository, renderer, notifier, kernel.events(), clock, quota),
                new DeleteNpc(repository, renderer, notifier, kernel.events()),
                new ListNpcs(repository, notifier),
                new NearbyNpcs(repository, notifier),
                new DescribeNpc(repository, notifier),
                new TeleportToNpc(repository, new BukkitDirectTeleporter(kernel.scheduler(), kernel.log()), notifier),
                new MoveNpc(repository, renderer, notifier, kernel.events()),
                new CopyNpc(repository, renderer, notifier, kernel.events(), clock),
                new CenterNpc(repository, renderer, notifier, kernel.events()),
                new FixNpc(repository, renderer, notifier),
                new SetNpcSkin(repository, renderer, notifier),
                new SetNpcEntityType(repository, renderer, notifier),
                new SetNpcClickCommand(repository, notifier),
                new SetNpcLookAtPlayer(repository, renderer, notifier),
                new SetNpcEquipment(repository, renderer, notifier),
                new ListNpcEquipment(repository, notifier),
                new SetNpcGlowing(repository, renderer, notifier),
                new SetNpcPose(repository, renderer, notifier),
                new SetNpcScale(repository, renderer, notifier),
                new AddNpcAction(repository, notifier),
                new ListNpcActions(repository, notifier),
                new RemoveNpcAction(repository, notifier),
                new ClearNpcActions(repository, notifier),
                new InsertNpcAction(repository, notifier),
                new SetNpcAction(repository, notifier),
                new MoveNpcAction(repository, notifier),
                new SetNpcTypeData(repository, renderer, notifier),
                new ListNpcTypeData(repository, notifier),
                new MoveNpcTo(repository, renderer, notifier, kernel.events()),
                new SetNpcDisplayName(repository, renderer, notifier),
                new SetNpcInteractionCooldown(repository, notifier),
                new SetNpcMirrorSkin(repository, renderer, notifier),
                new SetNpcCollidable(repository, renderer, notifier),
                new SetNpcShowInTab(repository, renderer, notifier),
                new SetNpcRange(repository, renderer, notifier),
                new SetNpcState(repository, renderer, notifier),
                new SetNpcSkinSlim(repository, renderer, notifier));
    }

    private static void spawnStored(NpcRepository repository, NpcRenderer renderer) {
        // Each render hops onto each viewer's region thread inside the renderer, so this is safe to call straight
        // from the enable path; a viewer out of range or in another world is simply not shown.
        for (Npc npc : repository.all()) {
            renderer.render(npc);
        }
    }

    /**
     * Everything the npc module contributes once wired: the single Brigadier command, the join/quit/world-change
     * listener, the renderer whose fake players must be removed on stop, and the refresh timer handle.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the lifecycle listener to register
     * @param renderer the packet renderer, drained on stop
     * @param connector the proxy connect channel, unregistered on stop so nothing outlives a disable
     * @param refreshTask the global refresh timer handle, cancelled on stop so no task outlives a disable
     * @param lookTask the look-at-player timer handle, cancelled on stop alongside the refresh timer
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            NpcRenderer renderer,
            NpcRepository repository,
            NpcServices services,
            BukkitServerConnector connector,
            AutoCloseable refreshTask,
            AutoCloseable lookTask,
            NpcPlaceholders placeholders) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(connector, "connector");
            Objects.requireNonNull(refreshTask, "refreshTask");
            Objects.requireNonNull(lookTask, "lookTask");
            Objects.requireNonNull(placeholders, "placeholders");
        }

        /** Cancel the timers, unregister the proxy channel, and remove every shown NPC so nothing is orphaned. */
        public void stop() {
            closeQuietly(refreshTask);
            closeQuietly(lookTask);
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
