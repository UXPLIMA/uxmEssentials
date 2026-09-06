package com.uxplima.uxmessentials.staff.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.staff.StaffStores;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.NetworkConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffChatCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffListCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.command.StaffModeCommand;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineMenu;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffGadgetActions;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffJoinListener;
import com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffModeListener;
import com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffChannel;
import com.uxplima.uxmessentials.staff.adapter.outbound.ModerationStaffFreeze;
import com.uxplima.uxmessentials.staff.adapter.outbound.PlayerstateStaffInspector;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import com.uxplima.uxmessentials.staff.adapter.outbound.TeleportStaffTeleport;
import com.uxplima.uxmessentials.staff.adapter.outbound.VanishStaffVanish;
import com.uxplima.uxmessentials.staff.application.EnterStaffMode;
import com.uxplima.uxmessentials.staff.application.ExitStaffMode;
import com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the staff context's adapters and use cases over the injected kernel ports, the persistence DSL, and
 * the soft-coupled seams handed in from the vanish, playerstate, moderation, messaging, and teleport modules
 * (captured during their wiring, since staff wires last). This is the one place the staff context is wired.
 *
 * <p>The five soft couplings ride rebindable holders ({@link MutableStaffVanish}, {@link MutableStaffInspector},
 * {@link MutableStaffChannel}, {@link MutableStaffFreeze}, {@link MutableStaffTeleport}), each starting on its
 * port's {@code NONE} and bound to the real vanish/playerstate/messaging/moderation/teleport impl only when
 * that module is enabled, so a disabled source module degrades the matching gadget or staff chat to a no-op
 * rather than failing (mirroring messaging's {@code MutableMutePolicy}). Staff-mode vanish routes through the
 * dedicated vanish context's {@code ToggleVanish}, the single vanish authority.
 *
 * <p>The loadout is DB-backed through the jOOQ {@code StaffLoadoutRepository} (built via {@link StaffStores})
 * so it survives a restart (the item-loss-safe net). On stop the wiring exits every staff member still in staff
 * mode, restoring their real loadout, so a disable or reload never strands anyone in the gadget hotbar.
 */
@NullMarked
public final class StaffWiring {

    /** The node {@code /stafflist} requires, reused for the hub entry that opens the same roster. */
    private static final String STAFF_LIST_PERMISSION = "uxmessentials.staff.list";

    private StaffWiring() {}

    /**
     * Build the staff adapters from {@code plugin}, {@code ctx}, the {@code persistence} DSL, the {@code seams},
     * and the in-process event bus. The bus is the concrete {@link InProcessDomainEventPublisher} so the
     * enter/exit roster-alert subscriber can be registered here and unsubscribed on stop, the kernel port
     * exposes only {@code publish}.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            StaffSeams seams,
            InProcessDomainEventPublisher events,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(seams, "seams");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        StaffSettings settings = new StaffSettings(ctx.config(), kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        StaffGadgetItems gadgetItems = new StaffGadgetItems(plugin);
        StaffModeStoreImpl store = new StaffModeStoreImpl();
        // The loadout is per-server state, so it is keyed per (player, server_id): the repository scopes every
        // save/load/delete to THIS backend's network.server-id, so two backends sharing one DB never clobber
        // each other's captured pre-mode inventory. A single-server install runs on the default id unchanged.
        String serverId = NetworkConfig.from(ctx.config()).serverId();
        StaffLoadoutRepository repository = StaffStores.loadouts(persistence, Clock.systemUTC(), serverId);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());

        // vanish is built (and the seams bound) before the capture, because the capture reads the pre-mode
        // vanish state through it so the player's vanish flag is part of the captured loadout.
        MutableStaffVanish vanish = new MutableStaffVanish();
        MutableStaffInspector inspector = new MutableStaffInspector();
        MutableStaffChannel channel = new MutableStaffChannel();
        MutableStaffFreeze freeze = new MutableStaffFreeze();
        MutableStaffTeleport teleport = new MutableStaffTeleport();
        bindSeams(seams, plugin, kernel, settings, vanish, inspector, channel, freeze, teleport);

        BukkitStaffLoadoutCapture capture = new BukkitStaffLoadoutCapture(settings, gadgetItems, vanish);
        RecoverStaffLoadout recover = new RecoverStaffLoadout(store, repository, capture, vanish, notifier);
        EnterStaffMode enter = new EnterStaffMode(
                store,
                repository,
                capture,
                vanish,
                notifier,
                kernel.events(),
                recover,
                StaffSettings.DEFAULT_MODE,
                settings.vanishOnEnter());
        ExitStaffMode exit = new ExitStaffMode(store, repository, capture, vanish, notifier, kernel.events());
        SendStaffChat staffChat = new SendStaffChat(channel, kernel.events());
        StaffServices services = new StaffServices(enter, exit, recover, staffChat, inspector, store);

        StaffFollowService followService = new StaffFollowService(
                plugin.getServer(),
                kernel.scheduler(),
                notifier,
                kernel.log(),
                staffId -> store.activePlayers().contains(staffId),
                settings.followIntervalTicks());
        // The COMPASS navigator, /stafflist, and the EXAMINE picker all render through the shared menu engine. The
        // StaffPlayerMenu owns the two teleport-picker specs plus the shared staff:players list source (reads the
        // pre-computed roster subject), the staff_player_name head label, and the staff:teleport-to click; the
        // StaffExamineMenu reuses that source and label, adding only its own spec and the staff:examine click. Every
        // open site snapshots the roster on the global region thread and hands it in, so no Bukkit API runs off the
        // list-source thread.
        java.nio.file.Path dataFolder = plugin.getDataFolder().toPath();
        StaffPlayerMenu playerMenu =
                new StaffPlayerMenu(menus, plugin.getServer(), kernel.messages(), kernel.messageSink(), teleport);
        playerMenu.register(menuBindings, dataFolder, kernel.log());
        StaffExamineMenu examineMenu =
                new StaffExamineMenu(menus, plugin.getServer(), kernel.messages(), kernel.messageSink(), inspector);
        examineMenu.register(menuBindings, dataFolder, kernel.log());
        StaffGadgetActions actions = new StaffGadgetActions(
                vanish,
                freeze,
                teleport,
                followService,
                examineMenu,
                playerMenu,
                kernel.scheduler(),
                plugin.getServer(),
                notifier);

        StaffListCommand staffList = new StaffListCommand(
                services, kernel.messages(), kernel.scheduler(), plugin.getServer(), kernel.messageSink(), playerMenu);
        // The hub entry opens the same online-staff roster /stafflist opens, under the same node.
        guiRegistry.register(new ManagementGuiEntry(
                "staff", StaffMessageKey.STAFF_LIST_TITLE, Material.SHIELD, STAFF_LIST_PERMISSION, staffList::open));
        List<CommandRegistration> commands = List.of(
                new StaffModeCommand(
                        services, kernel.messages(), kernel.scheduler(), kernel.playerLookup(), running::get),
                new StaffChatCommand(services, kernel.messages()),
                staffList);
        List<Listener> listeners = List.of(
                new StaffModeListener(services, gadgetItems, followService, actions),
                new StaffJoinListener(services, repository, kernel.scheduler()));

        // Roster alerts are wired (and unsubscribed on stop) by StaffAlertWiring; they exist only when messaging is
        // enabled, and RecoverStaffLoadout publishes no toggle event, so a crash recovery on join never alerts.
        @Nullable Consumer<DomainEvent> alertSubscriber = StaffAlertWiring.subscribe(seams, settings, kernel, events);
        return new Wired(
                commands, listeners, services, followService, kernel.scheduler(), running, events, alertSubscriber);
    }

    private static void bindSeams(
            StaffSeams seams,
            Plugin plugin,
            KernelPorts kernel,
            StaffSettings settings,
            MutableStaffVanish vanish,
            MutableStaffInspector inspector,
            MutableStaffChannel channel,
            MutableStaffFreeze freeze,
            MutableStaffTeleport teleport) {
        seams.vanish().ifPresent(v -> vanish.bind(new VanishStaffVanish(v.toggleVanish())));
        seams.openContainer().ifPresent(open -> inspector.bind(new PlayerstateStaffInspector(open)));
        seams.staffAudience()
                .ifPresent(audience -> channel.bind(new MessagingStaffChannel(
                        audience, kernel.messages(), kernel.messageSink(), settings.staffChatNode())));
        seams.moderationFreeze().ifPresent(m -> freeze.bind(new ModerationStaffFreeze(m.freeze(), m.sanctions())));
        seams.teleport().ifPresent(t -> teleport.bind(new TeleportStaffTeleport(t.engine(), plugin.getServer())));
    }

    /**
     * The soft-coupled seams staff binds when their source modules are enabled. Each is optional: an absent seam
     * leaves the matching holder on {@code NONE}, so the gadget or staff chat degrades to a no-op.
     *
     * @param vanish the vanish toggle backing the VANISH gadget and vanish-on-enter
     * @param openContainer the playerstate inventory-open use case backing the EXAMINE gadget
     * @param staffAudience the messaging staff-audience resolver backing staff chat
     * @param moderationFreeze the moderation freeze use case + sanction read backing the FREEZE gadget
     * @param teleport the teleport engine backing the COMPASS gadget and {@code /stafflist}
     */
    public record StaffSeams(
            Optional<VanishSeam> vanish,
            Optional<OpenContainer> openContainer,
            Optional<StaffAudience> staffAudience,
            Optional<ModerationFreezeSeam> moderationFreeze,
            Optional<TeleportSeam> teleport) {

        public StaffSeams {
            Objects.requireNonNull(vanish, "vanish");
            Objects.requireNonNull(openContainer, "openContainer");
            Objects.requireNonNull(staffAudience, "staffAudience");
            Objects.requireNonNull(moderationFreeze, "moderationFreeze");
            Objects.requireNonNull(teleport, "teleport");
        }

        /** The seam set with nothing bound: every soft couple degrades to a no-op. */
        public static StaffSeams none() {
            return new StaffSeams(
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /** The vanish handle staff needs to set a vanish state absolutely through the one vanish authority. */
    public record VanishSeam(ToggleVanish toggleVanish) {
        public VanishSeam {
            Objects.requireNonNull(toggleVanish, "toggleVanish");
        }
    }

    /** The moderation handles the FREEZE gadget needs: the audited freeze use case and the live freeze-state read. */
    public record ModerationFreezeSeam(Freeze freeze, Sanctions sanctions) {
        public ModerationFreezeSeam {
            Objects.requireNonNull(freeze, "freeze");
            Objects.requireNonNull(sanctions, "sanctions");
        }
    }

    /** The teleport handle the COMPASS gadget and {@code /stafflist} need: the admin-teleport engine. */
    public record TeleportSeam(TeleportEngine engine) {
        public TeleportSeam {
            Objects.requireNonNull(engine, "engine");
        }
    }

    /**
     * Everything the staff module contributes once wired: the Brigadier commands, the gadget/connection
     * listener, and the FOLLOW gadget's repeating task. {@link #stop()} exits every staff member still in staff
     * mode, restoring their real loadout, and then cancels the follow task, in that order so a follow-shutdown
     * failure never aborts the loadout restore and strands anyone in the gadget hotbar or leaves a follow running.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the gadget interaction and quit listener to register
     * @param services the constructed use cases and the active-staff store, used to exit everyone on stop
     * @param followService the FOLLOW gadget runtime, shut down on stop to cancel its repeating task
     * @param scheduler the Scheduler port, used to run each exit on the player's entity thread on stop
     * @param running the flag flipped false on stop so the toggle command stops accepting new entries
     * @param eventBus the in-process event bus the roster-alert subscriber is registered on
     * @param alertSubscriber the enter/exit alert consumer to unsubscribe on stop (null when messaging is off)
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            StaffServices services,
            StaffFollowService followService,
            com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler,
            AtomicBoolean running,
            InProcessDomainEventPublisher eventBus,
            @Nullable Consumer<DomainEvent> alertSubscriber) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(followService, "followService");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(eventBus, "eventBus");
        }

        /** Exit every online staff member still in staff mode, restoring their real loadout on their entity thread. */
        public void stop() {
            running.set(false);
            if (alertSubscriber != null) {
                eventBus.unsubscribe(alertSubscriber);
            }
            // Restore every staff member's real loadout first, then stop the follow runtime: a follow-shutdown
            // failure must never abort the loadout restore and strand staff in the gadget hotbar on disable.
            for (UUID id : services.store().activePlayers()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());
                scheduler.onEntity(who, () -> services.exit().exit(who));
            }
            followService.shutdown();
        }
    }
}
