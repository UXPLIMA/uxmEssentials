package com.uxplima.uxmessentials.poses.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.poses.adapter.inbound.command.CrawlCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.PoseCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.PoseCooldownNotice;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.PosesCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.SitCommand;
import com.uxplima.uxmessentials.poses.adapter.inbound.gui.PosesSettingsView;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.CrawlMoveListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PlayerSitInteractListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCancelListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCleanupListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.SeatInteractListener;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitCrawlView;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPacketPosePort;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPoseReturn;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSnores;
import com.uxplima.uxmessentials.poses.adapter.outbound.PdcPlayerSitPreferences;
import com.uxplima.uxmessentials.poses.adapter.outbound.WorldGuardPoseFlags;
import com.uxplima.uxmessentials.poses.application.ClaimAwareRegionGate;
import com.uxplima.uxmessentials.poses.application.PoseCooldown;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesConfig;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartCrawl;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit;
import com.uxplima.uxmessentials.poses.application.StartPose;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionFlags;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProvidersConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimServiceImpl;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.claim.ClaimPolicySettings;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.internal.NmsNpcPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the poses context's adapters and use cases over the injected kernel ports, and produces the {@code
 * /sit} and {@code /poses} commands plus the listeners the plugin registers. The seat port is a real, tagged,
 * non-persistent seat entity ({@link BukkitSeatPort}); the region gate is the real {@link ClaimAwareRegionGate},
 * composing the shared {@link ClaimService} (land claims) with the {@link WorldGuardPoseFlags} WorldGuard seam behind
 * the {@code respect-claims} / {@code respect-worldguard} config toggles; the player-sit opt-out is PDC-backed
 * ({@link PdcPlayerSitPreferences}). The context persists nothing — a pose is
 * transient state in {@link PoseSessions} — so there is no repository or migration.
 *
 * <p>On enable the caller runs {@link BukkitSeatPort#sweepOrphans()} to reap any seat a previous run's crash left
 * behind, and on stop {@link Wired#stop()} removes every live seat and clears the registry, so a disable or reload
 * leaves zero residual state and no ghost entity.
 */
@NullMarked
public final class PosesWiring {

    /**
     * How often the pose render re-checks who can see a posing player and re-asserts what the server undoes. Half a
     * second is quick enough that someone walking up sees the pose without a visible gap, and rare enough to stay
     * free on a server where almost nobody is posing.
     */
    private static final int POSE_REFRESH_INTERVAL_TICKS = 10;

    /** The resource-pack-free snore: a soft fox-sleep sound at a low volume, replayed every few seconds. */
    private static final String SNORE_SOUND = "minecraft:entity.fox.sleep";

    private static final float SNORE_VOLUME = 0.6f;

    private static final float SNORE_PITCH = 1.0f;

    private static final int SNORE_INTERVAL_TICKS = 60;

    private PosesWiring() {}

    /** Build the poses adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            ClaimProvidersConfig claimProviders) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(claimProviders, "claimProviders");
        KernelPorts kernel = ctx.kernel();
        PosesConfig config = PosesConfig.from(ctx.config());
        PoseSessions sessions = new PoseSessions();
        SittableBlocks sittableBlocks = new SittableBlocks(config.sittableMaterials());
        PoseCooldown poseCooldown = PoseCooldown.backedBy(kernel.cooldowns());
        PoseCooldownNotice cooldownNotice = new PoseCooldownNotice(poseCooldown, kernel.messages());
        BukkitSeatPort seats = new BukkitSeatPort(plugin, kernel.scheduler(), kernel.log());
        ClaimService claims = buildClaimService(plugin, kernel, config.respectClaims(), claimProviders);
        PoseRegionFlags regionFlags = new WorldGuardPoseFlags(plugin.getServer(), kernel.log());
        PoseRegionGate regionGate =
                new ClaimAwareRegionGate(claims, regionFlags, config.respectClaims(), config.respectWorldguard());
        BukkitPoseReturn poseReturn = new BukkitPoseReturn(plugin, kernel.scheduler());
        PlayerSitPreferences playerSitPreferences = new PdcPlayerSitPreferences();
        // The free-pose render rides the same uxmLib packet stack the npc module uses for a fake player's body pose;
        // here it overrides a real player's DATA_POSE to the swimming lie-down for /lay and /bellyflop, and builds
        // the client-only shulker a crawl uses as its ceiling.
        NpcPackets packets = new NmsNpcPackets(new PacketSender(new ChannelResolver()));
        BukkitCrawlView crawlView = new BukkitCrawlView(plugin.getServer(), packets);
        BukkitPacketPosePort posePort = new BukkitPacketPosePort(
                plugin.getServer(), kernel.scheduler(), packets, kernel.log(), POSE_REFRESH_INTERVAL_TICKS);
        BukkitSnores snores = new BukkitSnores(
                plugin.getServer(),
                kernel.scheduler(),
                kernel.log(),
                SNORE_SOUND,
                SNORE_VOLUME,
                SNORE_PITCH,
                SNORE_INTERVAL_TICKS);

        StartSit startSit = new StartSit(
                sessions,
                seats,
                regionGate,
                kernel.playerLocator(),
                kernel.events(),
                Clock.systemUTC(),
                config.features().sit(),
                config.returnToStart(),
                poseCooldown);
        StartPlayerSit startPlayerSit = new StartPlayerSit(
                sessions,
                seats,
                playerSitPreferences,
                regionGate,
                kernel.playerLocator(),
                kernel.events(),
                Clock.systemUTC(),
                config.features().playerSit(),
                poseCooldown);
        StartPose startPose = new StartPose(
                sessions,
                seats,
                regionGate,
                posePort,
                snores,
                kernel.events(),
                Clock.systemUTC(),
                config.features().lay(),
                config.features().bellyflop(),
                config.features().spin(),
                config.snore(),
                poseCooldown);
        StartCrawl startCrawl = new StartCrawl(
                sessions,
                crawlView,
                regionGate,
                kernel.events(),
                Clock.systemUTC(),
                config.features().crawl(),
                poseCooldown);
        TogglePlayerSit togglePlayerSit = new TogglePlayerSit(playerSitPreferences);
        StopPose stopPose = new StopPose(
                sessions, seats, posePort, snores, crawlView, poseReturn, kernel.events(), config.returnToStart());

        // The personal settings/status panel rides the shared SP0 GUI framework, reading the live session registry
        // and flipping the player-sit opt-out through the same TogglePlayerSit the /poses toggle command uses. The
        // poses entry on the /uxmess gui hub opens the same panel (gated uxmessentials.poses.gui).
        GuiText guiText = new GuiText(kernel.messages());
        PosesSettingsView settingsView = new PosesSettingsView(
                guiText,
                kernel.scheduler(),
                guiLayouts,
                kernel.messages(),
                menus,
                sessions,
                playerSitPreferences,
                togglePlayerSit);
        guiRegistry.register(new ManagementGuiEntry(
                "poses",
                PosesMessageKey.POSES_GUI_TITLE,
                Material.ARMOR_STAND,
                "uxmessentials.poses.gui",
                settingsView::open));

        List<CommandRegistration> commands = List.of(
                new SitCommand(
                        startSit,
                        stopPose,
                        sessions,
                        sittableBlocks,
                        kernel.messages(),
                        cooldownNotice,
                        config.sitOnBlocks(),
                        config.maxDistance()),
                new PoseCommand(
                        "lay",
                        "uxmessentials.lay.use",
                        PoseType.LAY,
                        PosesMessageKey.POSES_NOW_LAYING,
                        "Lie down where you stand; run again to stand up.",
                        startPose,
                        stopPose,
                        sessions,
                        kernel.messages(),
                        cooldownNotice),
                new PoseCommand(
                        "bellyflop",
                        "uxmessentials.bellyflop.use",
                        PoseType.BELLYFLOP,
                        PosesMessageKey.POSES_NOW_BELLYFLOPPING,
                        "Flop onto your front; run again to stand up.",
                        startPose,
                        stopPose,
                        sessions,
                        kernel.messages(),
                        cooldownNotice),
                new PoseCommand(
                        "spin",
                        "uxmessentials.spin.use",
                        PoseType.SPIN,
                        PosesMessageKey.POSES_NOW_SPINNING,
                        "Spin in place; run again to stop.",
                        startPose,
                        stopPose,
                        sessions,
                        kernel.messages(),
                        cooldownNotice),
                new CrawlCommand(startCrawl, stopPose, sessions, kernel.messages(), cooldownNotice),
                new PosesCommand(togglePlayerSit, settingsView, kernel.messages()));
        List<Listener> listeners = List.of(
                new SeatInteractListener(
                        startSit,
                        seats,
                        sittableBlocks,
                        kernel.messages(),
                        cooldownNotice,
                        config.sitOnBlocks(),
                        config.maxDistance()),
                new PlayerSitInteractListener(startPlayerSit, kernel.messages(), cooldownNotice),
                new PoseCancelListener(stopPose, sessions),
                new CrawlMoveListener(sessions, crawlView, stopPose),
                new PoseCleanupListener(seats));
        return new Wired(commands, listeners, seats, posePort, snores, sessions, playerSitPreferences);
    }

    /**
     * The claim seam the region gate consults: the shared provider detection bound to a {@link ClaimServiceImpl}, or
     * the no-op {@link AlwaysAllowClaimService} when {@code respect-claims} is off (nothing to detect, nothing to log).
     * Poses gate on {@link ClaimService#canAccess canAccess}, which reads only the trust of the claim covering the
     * spot, so the placement knobs (require-a-claim, foreign-block, proximity) are left at their inert defaults and
     * only teleport-access checking is enabled.
     */
    private static ClaimService buildClaimService(
            Plugin plugin, KernelPorts kernel, boolean respectClaims, ClaimProvidersConfig claimProviders) {
        if (!respectClaims) {
            return new AlwaysAllowClaimService();
        }
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, true);
        return new ClaimServiceImpl(
                ClaimProviders.detectAll(claimProviders, plugin, plugin.getServer(), kernel.log()), settings);
    }

    /**
     * Everything the poses module contributes once wired: the {@code /sit}, {@code /lay}, {@code /bellyflop},
     * {@code /spin}, {@code /crawl}, and {@code /poses} commands, the interact / cancel / crawl-move / cleanup
     * listeners, the seat port (swept on enable, drained on stop), the pose and snore ports (their repeating loops
     * cancelled on stop) and the session registry (the placeholder seam's read
     * source, cleared on stop).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param seats the seat port, so the caller can sweep orphans on enable and drain on stop
     * @param posePort the free-pose render port, whose spin loop is cancelled on stop
     * @param snores the snore port, whose loop is cancelled on stop
     * @param sessions the session registry, exposed for the {@code poses_sitting}/{@code poses_pose} placeholder seam
     * @param playerSitPreferences the player-sit opt-out store, exposed for the {@code poses_toggle} placeholder seam
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            BukkitSeatPort seats,
            BukkitPacketPosePort posePort,
            BukkitSnores snores,
            PoseSessions sessions,
            PlayerSitPreferences playerSitPreferences) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(seats, "seats");
            Objects.requireNonNull(posePort, "posePort");
            Objects.requireNonNull(snores, "snores");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(playerSitPreferences, "playerSitPreferences");
        }

        /**
         * Remove every live seat, cancel the spin and snore loops, and drop every session, so a disable or reload
         * leaves no ghost, no running task, and no residual state. A crawl's ceiling is client-only and per-player,
         * so it needs no draining here: a player still online has their pose ended by the module's own stop path,
         * which removes it, and an offline player's client dropped it with the connection.
         */
        public void stop() {
            seats.removeAll();
            posePort.shutdown();
            snores.shutdown();
            sessions.clear();
        }
    }
}
