package com.uxplima.uxmessentials.teleport.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.teleport.CachedSpawnDirectory;
import com.uxplima.uxmessentials.persistence.teleport.RtpPoolStores;
import com.uxplima.uxmessentials.persistence.teleport.SpawnDirectories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProvidersConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimServiceImpl;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.claim.ClaimPolicySettings;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.RtpCommand;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.TeleportCommands;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.TpSettingsCommand;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.RtpMenu;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.TeleportSettingsView;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.ArrivalGraceGuard;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.BiomeHotspotListener;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.RequestExpirySweep;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.RespawnListener;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.SpawnJoinListener;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.TeleportListeners;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.adapter.outbound.AsyncTeleportExecutor;
import com.uxplima.uxmessentials.teleport.adapter.outbound.BukkitBiomeCatalog;
import com.uxplima.uxmessentials.teleport.adapter.outbound.BukkitChunkAccess;
import com.uxplima.uxmessentials.teleport.adapter.outbound.BukkitRtpAreaSource;
import com.uxplima.uxmessentials.teleport.adapter.outbound.ForeignCombatGate;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryBackLocationStore;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryRequestRegistry;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PdcTeleportFlags;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PrewarmedSafeLocationQueue;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpPoolSettings;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpPoolWarmup;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpWorldSettings;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TeleportArrivalEffects;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TeleportArrivalHud;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TrackingWarmups;
import com.uxplima.uxmessentials.teleport.adapter.outbound.VanillaFallbackSpawnDirectory;
import com.uxplima.uxmessentials.teleport.adapter.outbound.WorldGuardRegions;
import com.uxplima.uxmessentials.teleport.application.AcceptTeleport;
import com.uxplima.uxmessentials.teleport.application.AsyncSafeLocationFinder;
import com.uxplima.uxmessentials.teleport.application.BiomePoolSlice;
import com.uxplima.uxmessentials.teleport.application.BiomeTargetedSearch;
import com.uxplima.uxmessentials.teleport.application.BudgetedSafeSearch;
import com.uxplima.uxmessentials.teleport.application.CappedBiomeHotspots;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.application.ClaimAwareProtectedLand;
import com.uxplima.uxmessentials.teleport.application.HotspotBiasedSampler;
import com.uxplima.uxmessentials.teleport.application.ListPendingRequests;
import com.uxplima.uxmessentials.teleport.application.RequestTeleport;
import com.uxplima.uxmessentials.teleport.application.ResolveBiomeRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveRespawn;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.RtpPoolPrewarm;
import com.uxplima.uxmessentials.teleport.application.RtpPoolSink;
import com.uxplima.uxmessentials.teleport.application.RtpPoolWriter;
import com.uxplima.uxmessentials.teleport.application.RtpRadiusTier;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.BiomeCatalog;
import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.application.port.CombatGate;
import com.uxplima.uxmessentials.teleport.application.port.ProtectedLand;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.SearchBudget;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the teleport context's adapters and use cases over the injected kernel ports, and produces
 * everything the plugin must register: the Brigadier command list, the move/death/quit listener, and the
 * TTL expiry sweep. This is the one place the teleport context is wired. Nothing else news up its
 * classes. The {@code Plugin} handle stays inside bootstrap; the adapters take only the {@code Plugin}
 * interface and the kernel ports.
 *
 * <p>The warmup port is wrapped in {@link TrackingWarmups} so every warmup the engine begins is registered
 * with the {@link WarmupTracker}, giving the move-cancels-warmup listener the live handle to cancel.
 */
@NullMarked
public final class TeleportWiring {

    // A couple of ticks between rescheduled RTP search attempts. Enough to slice a long search across ticks so
    // it never fires every candidate at once or monopolises an async worker.
    private static final Duration RTP_RETRY_INTERVAL = Duration.ofMillis(100);

    // How far (blocks) a biome-targeted sample may sit from a known hotspot chunk. Wide enough to spread candidates
    // over a biome patch, tight enough that the sample stays in the target biome most of the time.
    private static final int BIOME_HOTSPOT_RADIUS = 512;

    // The bounded window of chunk keys the hotspot listener remembers so it scans each chunk once; reset wholesale
    // when it fills so it can never grow without limit on a long-lived, heavily-explored server.
    private static final int BIOME_SEEN_CHUNK_CAP = 8_192;

    // How many persisted per-biome columns a /rtp biome tries (re-probing each) before it falls back to a live search.
    private static final int BIOME_POOL_SLICE_LIMIT = 16;

    private TeleportWiring() {}

    /** Build the teleport adapters and use cases from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings,
            TeleportFee fee,
            ClaimProvidersConfig claimProviders) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(claimProviders, "claimProviders");
        ConfigStore config = ctx.config();
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        AtomicBoolean running = new AtomicBoolean(true);

        TeleportSettings settings = new TeleportSettings(config);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        WarmupTracker warmupTracker = new WarmupTracker();
        // The jail gate forwards to NEVER until the moderation context lands and rebinds it (soft couple).
        MutableJailGate jailGate = new MutableJailGate();
        // Reads an installed combat plugin's tag so a losing fight cannot be escaped with /home or /tpa. We own
        // no combat timer: with the switch off or with no supported plugin installed this is NEVER, and every
        // teleport route behaves exactly as it did before.
        CombatGate combatGate = combatGate(config, plugin.getServer(), kernel.log());
        // The home-respawn seam resolves to empty until the homes context lands and rebinds it (soft couple),
        // so a HOME step in a configured respawn chain falls through whenever homes is disabled.
        MutableHomeRespawnLocator homeRespawnLocator = new MutableHomeRespawnLocator();
        MutableWarpRespawnLocator warpRespawnLocator = new MutableWarpRespawnLocator();
        SpawnDirectory spawns = spawns(plugin, persistence);
        RtpBundle rtp = buildRtp(plugin, kernel, config, settings, persistence, running, claimProviders);
        // The post-arrival grace shields an /rtp landing (Resistance + Slow-Falling + a no-fall-damage window);
        // it is both the engine's ArrivalGrace port and the fall-damage listener, so it is registered below.
        ArrivalGraceGuard graceGuard =
                new ArrivalGraceGuard(plugin.getServer(), kernel.scheduler(), settings::arrivalGrace, clock);
        TeleportServices services = assemble(
                plugin,
                kernel,
                settings,
                notifier,
                warmupTracker,
                jailGate,
                combatGate,
                spawns,
                clock,
                rtp,
                fee,
                graceGuard);
        RequestExpirySweep sweep = new RequestExpirySweep(
                kernel.scheduler(), services.requests(), services.acceptTeleport(), kernel.log(), running::get);
        RespawnListener respawnListener = new RespawnListener(
                new ResolveRespawn(settings),
                services.resolveSpawn()::resolveDefault,
                homeRespawnLocator,
                warpRespawnLocator,
                plugin.getServer(),
                services.rtpQueue(),
                graceGuard,
                settings::rtpOnRespawnWorlds);
        SpawnJoinListener joinListener = new SpawnJoinListener(
                settings, services.resolveSpawn(), services.resolveRtp(), services.executor(), kernel.permissions());
        // The per-player settings panel reuses the SP0 GUI framework over the shared catalog and the data-folder
        // layout loader. It reads and writes the same TeleportFlags the /tptoggle and /tpauto commands do, so
        // /tpsettings, the panel, and the commands all see one switch. The teleport entry on the /uxmess gui hub
        // opens the same panel for an admin (gated on uxmessentials.teleport.gui).
        GuiText guiText = new GuiText(kernel.messages());
        TeleportSettingsView settingsView = new TeleportSettingsView(
                guiText, kernel.scheduler(), guiLayouts, kernel.messages(), services.flags(), menus);
        guiRegistry.register(new ManagementGuiEntry(
                "teleport",
                TeleportMessageKey.GUI_SETTINGS_TITLE,
                org.bukkit.Material.ENDER_PEARL,
                "uxmessentials.teleport.gui",
                settingsView::open));
        // The /rtp menu-engine world picker (opened by /rtp gui). Registered with the shared menu bindings, its
        // list source, tile placeholders, and world-click action, so the shipped rtp.conf spec resolves cleanly.
        RtpMenu rtpMenu = new RtpMenu(menus, kernel.scheduler(), kernel.messages(), plugin.getServer(), services);
        rtpMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        List<CommandRegistration> commands =
                new java.util.ArrayList<>(TeleportCommands.all(services, kernel.messages()));
        // /rtp is built here (not in TeleportCommands.all) because it needs the RTP menu opener wired above. The bare
        // command opens that picker by default; an operator can flip it back to an in-place teleport via config.
        boolean rtpOpensGui = config.getBoolean("rtp.command-opens-gui", true);
        commands.add(new RtpCommand(services, kernel.messages(), rtpMenu, rtpOpensGui));
        commands.add(new TpSettingsCommand(services, kernel.messages(), settingsView));
        return new Wired(
                services,
                commands,
                listeners(services, config, respawnListener, joinListener, graceGuard, rtp.hotspotListener()),
                sweep,
                jailGate,
                homeRespawnLocator,
                warpRespawnLocator,
                running,
                rtp.warmup(),
                graceGuard);
    }

    private static TeleportServices assemble(
            Plugin plugin,
            KernelPorts kernel,
            TeleportSettings settings,
            Notifier notifier,
            WarmupTracker warmupTracker,
            MutableJailGate jailGate,
            CombatGate combatGate,
            SpawnDirectory spawns,
            Clock clock,
            RtpBundle rtp,
            TeleportFee fee,
            ArrivalGrace grace) {
        InMemoryBackLocationStore backStore = new InMemoryBackLocationStore();
        InMemoryRequestRegistry requests = new InMemoryRequestRegistry(settings.singleRequestDisplace());
        PdcTeleportFlags flags = new PdcTeleportFlags(plugin);
        TeleportArrivalHud arrivalHud =
                new TeleportArrivalHud(kernel.messages(), plugin.getServer(), settings, kernel.scheduler());
        TeleportArrivalEffects arrivalEffects =
                new TeleportArrivalEffects(plugin.getServer(), settings, kernel.scheduler());
        TeleportExecutor executor = new AsyncTeleportExecutor(
                kernel.scheduler(),
                backStore,
                kernel.events(),
                kernel.log(),
                clock,
                settings::teleportToCenter,
                arrivalHud,
                arrivalEffects);
        Warmups warmups = new TrackingWarmups(
                kernel.warmups(), warmupTracker, settings::cancelToggles, kernel.permissions(), clock);
        TeleportEngine engine = new TeleportEngine(
                kernel.cooldowns(),
                warmups,
                executor,
                notifier,
                kernel.events(),
                settings,
                jailGate,
                combatGate,
                fee,
                grace,
                kernel.gate());
        // The /rtp biome use case shares the engine (so it charges after success like a normal RTP), the live area
        // source (so /settpr moves both zones at once), and the biome-targeted search built in buildRtp.
        ResolveBiomeRtp resolveBiomeRtp = new ResolveBiomeRtp(
                rtp.biomeCatalog(),
                rtp.areaSource(),
                kernel.worldLookup(),
                settings,
                engine,
                rtp.biomeSearch(),
                notifier,
                kernel.scheduler(),
                new RtpRadiusTier(kernel.permissions()));
        return new TeleportServices.Builder()
                .engine(engine)
                .notifier(notifier)
                .settings(settings)
                .warmupTracker(warmupTracker)
                .requests(requests)
                .backStore(backStore)
                .flags(flags)
                .rtpQueue(rtp.queue())
                .executor(executor)
                .players(kernel.playerLookup())
                .worlds(kernel.worldLookup())
                .scheduler(kernel.scheduler())
                .requestTeleport(new RequestTeleport(
                        requests, flags, notifier, kernel.events(), settings, jailGate, combatGate, clock))
                .acceptTeleport(new AcceptTeleport(requests, engine, notifier, kernel.events(), clock))
                .listPendingRequests(new ListPendingRequests(requests, notifier))
                .captureBack(new CaptureBack(backStore, engine, notifier, kernel.events(), clock))
                .resolveRtp(new ResolveRtp(rtp.queue(), kernel.worldLookup(), engine, notifier, settings))
                .resolveBiomeRtp(resolveBiomeRtp)
                .biomeCatalog(rtp.biomeCatalog())
                .resolveSpawn(new ResolveSpawn(spawns, kernel.worldLookup(), engine, notifier))
                .build();
    }

    /**
     * Assemble the RTP engine: the async finder, the budgeted search, the in-memory pre-warmed queue, and, when the
     * persisted pool is enabled in config, the durable {@link RtpPoolStore}, the persist-on-validate {@link
     * RtpPoolWriter} the queue records through, and the enable-time {@link RtpPoolWarmup} that pre-warms each world's
     * queue from disk. With the pool disabled the sink is {@link RtpPoolSink#NONE} and there is no warmup, so the
     * queue runs purely in memory.
     */
    private static RtpBundle buildRtp(
            Plugin plugin,
            KernelPorts kernel,
            ConfigStore config,
            TeleportSettings settings,
            Persistence persistence,
            AtomicBoolean running,
            ClaimProvidersConfig claimProviders) {
        // The safe-search probe loads each candidate's chunk asynchronously through BukkitChunkAccess, so no RTP
        // probe generates a far chunk on a tick thread and every probed-but-unserved chunk is released again. The
        // budgeted search wraps the finder so a single search terminates within its budget and tick-slices its
        // retries through the scheduler: no worker blocks on a candidate any more.
        //
        // The claim + WorldGuard protection check rides along on the probe's region-thread snapshot callback (no
        // extra hop), setting each candidate's insideClaim flag so the pure policy keeps the shared pool out of
        // claimed land and regions. Both checks are folded behind the respect-claims / respect-worldguard toggles.
        ProtectedLand protectedLand = new ClaimAwareProtectedLand(
                buildClaimService(plugin, kernel, settings.respectClaims(), claimProviders),
                new WorldGuardRegions(plugin.getServer(), kernel.log()),
                settings.respectClaims(),
                settings.respectWorldguard());
        // The passive rare-biome registry (enabled only when biome-targeting is on) biases the biome-targeted
        // sampler toward learned hotspots; an ordinary /rtp area carries no target biome, so its sampling is
        // unchanged. The registry is fed by the BiomeHotspotListener below and bounded per biome.
        BiomeHotspots hotspots = settings.biomeTargeting()
                ? new CappedBiomeHotspots(settings.biomeHotspotRegistrySize())
                : BiomeHotspots.NONE;
        HotspotBiasedSampler sampler =
                new HotspotBiasedSampler(hotspots, settings.biomeHotspotWeight(), BIOME_HOTSPOT_RADIUS);
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(
                new BukkitChunkAccess(plugin.getServer(), kernel.log(), protectedLand),
                settings.safeSearchPolicy(),
                Clock.systemUTC(),
                sampler);
        BudgetedSafeSearch search =
                new BudgetedSafeSearch(finder, kernel.scheduler(), Clock.systemUTC(), RTP_RETRY_INTERVAL);
        RtpWorldSettings worldSettings = RtpWorldSettings.from(config);
        RtpPoolSettings poolSettings = RtpPoolSettings.from(config);
        BukkitRtpAreaSource areaSource = new BukkitRtpAreaSource(plugin.getServer(), worldSettings);
        BiomeCatalog biomeCatalog = new BukkitBiomeCatalog();
        Listener hotspotListener =
                settings.biomeTargeting() ? new BiomeHotspotListener(hotspots, BIOME_SEEN_CHUNK_CAP) : null;
        java.util.function.Supplier<SearchBudget> budget =
                () -> areaSource.current().searchBudget();
        if (!poolSettings.persist()) {
            PrewarmedSafeLocationQueue queue = new PrewarmedSafeLocationQueue(
                    kernel.scheduler(), search, areaSource, kernel.log(), running::get, RtpPoolSink.NONE);
            BiomeTargetedSearch biomeSearch = new BiomeTargetedSearch(
                    BiomePoolSlice.EMPTY,
                    finder,
                    search,
                    budget,
                    kernel.scheduler(),
                    kernel.log(),
                    RTP_RETRY_INTERVAL,
                    BIOME_POOL_SLICE_LIMIT);
            return new RtpBundle(queue, null, areaSource, biomeCatalog, biomeSearch, hotspotListener);
        }
        RtpPoolStore store = RtpPoolStores.cached(persistence, poolSettings.maxPerWorld(), Clock.systemUTC());
        RtpPoolWriter writer = new RtpPoolWriter(store, kernel.scheduler(), kernel.log());
        PrewarmedSafeLocationQueue queue = new PrewarmedSafeLocationQueue(
                kernel.scheduler(), search, areaSource, kernel.log(), running::get, writer);
        RtpPoolPrewarm prewarm =
                new RtpPoolPrewarm(store, finder, kernel.scheduler(), kernel.log(), RTP_RETRY_INTERVAL);
        RtpPoolWarmup warmup = new RtpPoolWarmup(
                store,
                prewarm,
                queue,
                plugin.getServer(),
                kernel.scheduler(),
                poolSettings,
                worldSettings.targetSize(),
                kernel.log(),
                running::get);
        BiomeTargetedSearch biomeSearch = new BiomeTargetedSearch(
                store::loadByBiome,
                finder,
                search,
                budget,
                kernel.scheduler(),
                kernel.log(),
                RTP_RETRY_INTERVAL,
                BIOME_POOL_SLICE_LIMIT);
        return new RtpBundle(queue, warmup, areaSource, biomeCatalog, biomeSearch, hotspotListener);
    }

    /**
     * The claim seam the RTP protection check consults, resolved just like the poses gate: the shared provider
     * detection bound to a {@link ClaimServiceImpl}, or the no-op {@link AlwaysAllowClaimService} when
     * {@code respect-claims} is off (nothing to detect, nothing to log). RTP asks only the player-agnostic
     * {@link ClaimService#isProtected}, is any claim here, so the placement knobs are left at their inert
     * defaults; only presence-of-claim matters.
     */
    private static ClaimService buildClaimService(
            Plugin plugin, KernelPorts kernel, boolean respectClaims, ClaimProvidersConfig claimProviders) {
        if (!respectClaims) {
            return new AlwaysAllowClaimService();
        }
        return new ClaimServiceImpl(
                ClaimProviders.detectAll(claimProviders, plugin, plugin.getServer(), kernel.log()),
                ClaimPolicySettings.defaults());
    }

    /**
     * The wired RTP engine: the servable queue and, when the pool is persisted, the enable-time warmup, plus the
     * biome-targeting pieces. The shared area source, the biome-key catalog, the {@code /rtp biome} search, and
     * (when biome-targeting is on) the passive rare-biome chunk-load listener.
     */
    private record RtpBundle(
            PrewarmedSafeLocationQueue queue,
            @Nullable RtpPoolWarmup warmup,
            BukkitRtpAreaSource areaSource,
            BiomeCatalog biomeCatalog,
            BiomeTargetedSearch biomeSearch,
            @Nullable Listener hotspotListener) {}

    private static SpawnDirectory spawns(Plugin plugin, Persistence persistence) {
        // The durable jOOQ store holds the per-world spawns, the main spawn, named spawns and mirror
        // redirects; the decorator adds the vanilla world spawn as the bottom-of-chain last resort so
        // /spawn answers on a fresh server before any /setspawn.
        CachedSpawnDirectory cached = SpawnDirectories.cachedConcrete(persistence);
        cached.warmAll();
        return new VanillaFallbackSpawnDirectory(cached, plugin.getServer());
    }

    /**
     * The combat gate to bind: the foreign reader when {@code combat.block-teleport} is on and a supported
     * combat plugin is actually installed, otherwise {@link CombatGate#NEVER}. Binding the reader on a server
     * with no combat plugin would only add a plugin-manager lookup to every teleport for an answer that can
     * never be anything but false, so the decision is made once here rather than on each call.
     */
    private static CombatGate combatGate(ConfigStore config, Server server, Logger log) {
        if (!config.getBoolean("combat.block-teleport", true)) {
            return CombatGate.NEVER;
        }
        if (!ForeignCombatGate.anyPresent(server)) {
            return CombatGate.NEVER;
        }
        log.info("event=combat_gate_bound providers={}", ForeignCombatGate.supportedPlugins());
        return new ForeignCombatGate(server, log);
    }

    private static List<Listener> listeners(
            TeleportServices services,
            ConfigStore config,
            RespawnListener respawnListener,
            SpawnJoinListener joinListener,
            ArrivalGraceGuard graceGuard,
            @Nullable Listener hotspotListener) {
        List<Listener> listeners = new java.util.ArrayList<>(List.of(
                new TeleportListeners(
                        services.warmupTracker(),
                        services.captureBack(),
                        config,
                        services.settings()::backCapturePolicy),
                respawnListener,
                joinListener,
                graceGuard));
        if (hotspotListener != null) {
            // Registered only when biome-targeting is on, so a disabled feature wires no ChunkLoadEvent listener.
            listeners.add(hotspotListener);
        }
        return List.copyOf(listeners);
    }

    /**
     * Everything the teleport module contributes once wired: the services (for stop-time drain), the
     * Brigadier commands, the Bukkit listeners, the expiry sweep, and the {@code running} flag the async
     * loops observe.
     *
     * @param services the constructed use cases and in-memory stores
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param expirySweep the self-rescheduling TTL sweep, armed by the caller
     * @param jailGate the rebindable jail gate moderation rebinds when it lands
     * @param homeRespawnLocator the rebindable home-respawn seam homes rebinds when it lands
     * @param warpRespawnLocator the rebindable warp-respawn seam warps rebinds when it lands
     * @param running the flag flipped false on stop so the sweep and refill loops exit
     * @param poolWarmup the enable-time RTP pool pre-warm, or {@code null} when the persisted pool is disabled
     * @param graceGuard the post-arrival grace / fall-damage guard, cleared on stop
     */
    public record Wired(
            TeleportServices services,
            List<CommandRegistration> commands,
            List<Listener> listeners,
            RequestExpirySweep expirySweep,
            MutableJailGate jailGate,
            MutableHomeRespawnLocator homeRespawnLocator,
            MutableWarpRespawnLocator warpRespawnLocator,
            AtomicBoolean running,
            @Nullable RtpPoolWarmup poolWarmup,
            ArrivalGraceGuard graceGuard) {

        public Wired {
            Objects.requireNonNull(services, "services");
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(expirySweep, "expirySweep");
            Objects.requireNonNull(jailGate, "jailGate");
            Objects.requireNonNull(homeRespawnLocator, "homeRespawnLocator");
            Objects.requireNonNull(warpRespawnLocator, "warpRespawnLocator");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(graceGuard, "graceGuard");
        }

        /**
         * Arm the expiry sweep; call after the listeners and commands are registered. Touches no world, so it is
         * safe on the enable thread even though the plugin enables before the worlds exist.
         */
        public void startBackgroundWork() {
            expirySweep.start();
        }

        /**
         * Pre-warm each world's RTP queue from the persisted pool, when the pool is enabled.
         *
         * <p>Split out of {@link #startBackgroundWork()} because it enumerates {@code getWorlds()}: at
         * {@code load: STARTUP} that list is empty during enable, so pre-warming there would quietly warm
         * nothing and leave the first {@code /rtp} of every session to search from cold. It runs through
         * {@code WorldPhase} instead, once the worlds are up.
         */
        public void warmRtpPool() {
            if (poolWarmup != null) {
                poolWarmup.start();
            }
        }

        /** Flip the running flag and drain the in-memory stores. Called on module stop. */
        public void stop() {
            running.set(false);
            services.drain();
            graceGuard.clear();
        }
    }
}
