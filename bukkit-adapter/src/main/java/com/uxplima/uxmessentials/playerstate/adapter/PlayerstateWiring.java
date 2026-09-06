package com.uxplima.uxmessentials.playerstate.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.PlayerStateCommands;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.EnderseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.MirrorWindow;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.OfflineContainerView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.PlaytimeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.NoFlyWorldListener;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.PlayerStateListener;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.WorldCommandListener;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitInventoryViewer;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitNearbyPlayers;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitOnlineRoster;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitPlayerEffects;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitPlayerInfo;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitStateReconciler;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.InMemoryPlayerStateStore;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.MutablePlaytimeAfkStatus;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.PdcClearInventoryPreferences;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.PlaytimeSampler;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.nms.NmsOfflinePlayerStorage;
import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Freeze;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.NoFlyWorldPolicy;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.playerstate.application.ResetPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ResetRest;
import com.uxplima.uxmessentials.playerstate.application.SetAir;
import com.uxplima.uxmessentials.playerstate.application.SetExperience;
import com.uxplima.uxmessentials.playerstate.application.SetFoodLevel;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetHealth;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalTime;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalWeather;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ShowPing;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ShowPosition;
import com.uxplima.uxmessentials.playerstate.application.Suicide;
import com.uxplima.uxmessentials.playerstate.application.ToggleClearInventoryConfirm;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGlow;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.ToggleNightVision;
import com.uxplima.uxmessentials.playerstate.application.port.ClearInventoryPreferences;
import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the playerstate context's adapters and use cases over the injected kernel ports, and produces
 * everything the plugin must register: the Brigadier command list and the join/quit/respawn listener. This is
 * the one place the playerstate context is wired: nothing else news up its classes. The context needs no
 * database and no {@code Plugin} handle: its only outbound adapters are the in-memory snapshot store, the
 * reconciler, the effects bridge, and the nearby scan, all of which sit on the kernel {@code Scheduler} port.
 *
 * <p>The {@code heal-remove-effects} toggle (§15.6) is read once from {@code playerstate.conf} here and fixed
 * into the {@link Heal} use case; an operator changes it via a module reload, which re-wires the context.
 */
@NullMarked
public final class PlayerstateWiring {

    private PlayerstateWiring() {}

    /** Build the playerstate adapters and use cases from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            PlaytimeRepository playtimeRepository,
            GuiLayouts guiLayouts,
            Menus menus,
            MirrorWindow mirrorWindow,
            PlayerTeamCoordinator teams) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(playtimeRepository, "playtimeRepository");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(mirrorWindow, "mirrorWindow");
        Objects.requireNonNull(teams, "teams");
        KernelPorts kernel = ctx.kernel();
        ConfigStore config = ctx.config();
        Clock clock = Clock.systemUTC();

        PlayerStateStore store = new InMemoryPlayerStateStore();
        StateReconciler reconciler = new BukkitStateReconciler(kernel.scheduler());
        PlayerEffects effects = new BukkitPlayerEffects(kernel.scheduler(), teams);
        InvseeView invseeView = new InvseeView(kernel.scheduler(), mirrorWindow);
        EnderseeView enderseeView = new EnderseeView(kernel.scheduler(), mirrorWindow);
        OfflinePlayerStorage offlineStorage = new NmsOfflinePlayerStorage(kernel.log());
        OfflineContainerView offlineView = new OfflineContainerView(kernel.scheduler(), offlineStorage, mirrorWindow);
        InventoryViewer inventoryViewer = new BukkitInventoryViewer(invseeView, enderseeView, offlineView);
        NearbyPlayers nearby = new BukkitNearbyPlayers(kernel.scheduler());
        PlayerInfo info = new BukkitPlayerInfo();
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        ClearInventoryPreferences clearPrefs = new PdcClearInventoryPreferences();

        Ports ports = new Ports(
                store, reconciler, effects, inventoryViewer, nearby, info, notifier, clearPrefs, playtimeRepository);
        PlayerStateServices services = assemble(kernel, config, clock, ports);
        PlayerstateSettings settings = new PlayerstateSettings(config);
        NoFlyWorldPolicy noFlyWorlds = new NoFlyWorldPolicy(settings.noFlyWorlds());
        // The AFK source is rebound when presence wires (presence lands after playerstate); until then, or when
        // presence is disabled: the holder reports no one AFK, so every sample counts as active time.
        MutablePlaytimeAfkStatus afkStatus = new MutablePlaytimeAfkStatus();
        PlaytimeSampler sampler = new PlaytimeSampler(
                kernel.scheduler(),
                playtimeRepository,
                afkStatus,
                new BukkitOnlineRoster(plugin),
                clock,
                settings.playtimeTracking(),
                Duration.ofSeconds(settings.playtimeSampleSeconds()));
        PlaytimeView playtimeView = new PlaytimeView(
                new GuiText(kernel.messages()),
                kernel.scheduler(),
                guiLayouts,
                kernel.messages(),
                services.showPlaytime(),
                menus);
        List<CommandRegistration> commands =
                PlayerStateCommands.all(services, kernel.messages(), noFlyWorlds, playtimeView);
        List<Listener> listeners = List.of(
                new PlayerStateListener(store, reconciler, teams),
                new WorldCommandListener(settings.worldCommandPolicy(), kernel.messages(), kernel.messageSink()),
                new NoFlyWorldListener(noFlyWorlds, kernel.scheduler(), kernel.messages(), kernel.messageSink()));
        return new Wired(
                commands, listeners, invseeView, enderseeView, offlineView, services, store, info, sampler, afkStatus);
    }

    private static PlayerStateServices assemble(KernelPorts kernel, ConfigStore config, Clock clock, Ports ports) {
        boolean healRemovesEffects = config.getBoolean("heal-remove-effects", false);
        boolean restEnabled = config.getBoolean("rest-enabled", true);
        var events = kernel.events();
        PlayerStateStore store = ports.store();
        StateReconciler reconciler = ports.reconciler();
        PlayerEffects effects = ports.effects();
        InventoryViewer inventoryViewer = ports.inventoryViewer();
        Notifier notifier = ports.notifier();
        return new PlayerStateServices(
                new ToggleGod(store, reconciler, notifier, events, clock),
                new ToggleFly(store, reconciler, notifier, events, clock),
                new Heal(effects, notifier, events, clock, healRemovesEffects),
                new Feed(effects, notifier, events, clock),
                new SetFoodLevel(effects, notifier),
                new SetHealth(effects, notifier),
                new SetGamemode(store, reconciler, notifier, events, clock),
                new SetSpeed(store, reconciler, notifier, events, clock),
                new Extinguish(effects, notifier),
                new ClearInventory(effects, notifier, ports.clearPreferences(), clock),
                new ToggleClearInventoryConfirm(ports.clearPreferences(), notifier),
                new OpenContainer(inventoryViewer, notifier),
                new Suicide(effects, notifier),
                new ListNearby(ports.nearby(), notifier),
                new ToggleNightVision(effects, notifier),
                new ToggleGlow(effects, notifier),
                new SetPersonalTime(effects, notifier),
                new SetPersonalWeather(effects, notifier),
                new SetExperience(effects, notifier),
                new SetAir(effects, notifier),
                new Burn(effects, notifier),
                new Freeze(effects, notifier),
                new ShowPosition(ports.info(), notifier),
                new ShowPing(ports.info(), notifier),
                new ShowPlaytime(ports.playtimeRepository(), ports.info(), notifier, clock),
                new ResetPlaytime(ports.playtimeRepository(), notifier),
                new ResetRest(effects, notifier, restEnabled),
                kernel.playerLookup());
    }

    /** The context's constructed outbound ports, bundled so {@link #assemble} stays within its argument budget. */
    private record Ports(
            PlayerStateStore store,
            StateReconciler reconciler,
            PlayerEffects effects,
            InventoryViewer inventoryViewer,
            NearbyPlayers nearby,
            PlayerInfo info,
            Notifier notifier,
            ClearInventoryPreferences clearPreferences,
            PlaytimeRepository playtimeRepository) {}

    /**
     * Everything the playerstate module contributes once wired: the Brigadier commands and the listeners (the
     * join/quit/respawn re-apply/reset listener and the {@code /invsee} menu's click/close listener), plus the
     * self-rescheduling playtime sampler. The only durable-while-open state is the set of open invsee menus, which
     * {@link #stop()} reconciles back onto their targets, and the sampler loop, which {@code stop()} flips off so
     * no edit is lost and no orphaned tick survives disable.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param invseeView the online invsee menu, held so {@code stop()} flushes every still-open view
     * @param enderseeView the online endersee menu, held so {@code stop()} flushes every still-open view
     * @param offlineView the offline invsee/endersee menus, held so {@code stop()} flushes every still-open view
     * @param services the constructed use cases, exposing {@code openContainer} cross-context for the staff EXAMINE gadget
     * @param store the in-memory snapshot map, read by the placeholder seam for the god flag
     * @param info the live-player read port, read by the placeholder seam for total play time
     * @param sampler the AFK-aware playtime sampler, armed by {@code startBackgroundWork} and stopped by {@code stop}
     * @param afkStatus the rebindable AFK seam presence rebinds to its store when it wires
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            InvseeView invseeView,
            EnderseeView enderseeView,
            OfflineContainerView offlineView,
            PlayerStateServices services,
            PlayerStateStore store,
            PlayerInfo info,
            PlaytimeSampler sampler,
            MutablePlaytimeAfkStatus afkStatus) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(invseeView, "invseeView");
            Objects.requireNonNull(enderseeView, "enderseeView");
            Objects.requireNonNull(offlineView, "offlineView");
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(info, "info");
            Objects.requireNonNull(sampler, "sampler");
            Objects.requireNonNull(afkStatus, "afkStatus");
        }

        /** Arm the playtime sampler. Called once after registration, like the presence sweep. */
        public void startBackgroundWork() {
            sampler.start();
        }

        /** Stop the sampler and reconcile every still-open invsee/endersee menu back onto its target. */
        public void stop() {
            sampler.stop();
            invseeView.flushAll();
            enderseeView.flushAll();
            offlineView.flushAll();
        }
    }
}
