package com.uxplima.uxmessentials.villagers.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.VillagersPlaceholders;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.villagers.adapter.inbound.command.VillagerCommand;
import com.uxplima.uxmessentials.villagers.adapter.inbound.command.VillagerProtectToggle;
import com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerView;
import com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerWindow;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.ClickToTradeListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.DisableTradesListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerBucketListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerLeashListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerProtectionListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerRecipeReapplyListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerTradeListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.FollowVillagersPlaceholders;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PathfinderVillagerMover;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerBucketItem;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerFollowService;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRestockSweep;
import com.uxplima.uxmessentials.villagers.application.VillagersConfig;
import com.uxplima.uxmessentials.villagers.domain.RestockPolicy;
import com.uxplima.uxmessentials.villagers.domain.VillagerProtectionPolicy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the villagers context's adapters over the injected kernel ports and produces the listeners, commands, and
 * restock sweep the plugin registers. Each feature wires only when its config switch is on: the
 * {@link VillagerTradeListener} lands only when infinite trading or instant restock is enabled; the
 * {@link VillagerRestockSweep} schedules a task only when the restock timer is enabled; the trade manager (the
 * {@code /villager manager} command, its window, and the load-time recipe reapply) wires only when
 * {@code trade-manager} is on; and the {@link ClickToTradeListener} lands only when {@code click-to-trade} is on. The
 * {@link DisableTradesListener} always registers when the module is on, because it also honours the per-villager
 * disable flag the manager sets, with the global switch off and no flag it is an inert no-op. The
 * {@link VillagerProtectionListener} and the {@code /villager protect} toggle wire only under {@code protect}, while the
 * {@link VillagerBucketListener}, like the disable listener, always registers and honours its own {@code bucket}
 * switch. The {@link VillagerFollowService} and the {@code /villager follow} toggle wire only under {@code follow}, and
 * the {@link VillagerLeashListener} lands only under {@code leash}. The one {@code /villager} command registers whenever
 * the module is on (gated on {@code uxmessentials.villagers.use}) and carries whichever of the {@code manager} /
 * {@code protect} / {@code follow} subcommands their features enabled; with every sub-feature off it is just the root,
 * whose bare executor reports that no villager tools are available.
 *
 * <p>The context persists nothing relational, the last-restock stamp, the disable flag, the follow-owner mark, and the
 * manager's custom recipe set are all PDC state on the villager entity, so there is no repository or migration. The
 * restock sweep's and the follow runtime's repeating tasks are started here and any still-open manager window is drained
 * on {@link Wired#stop()}, so a disable or reload leaves no scheduled work and no unsaved edit behind.
 */
@NullMarked
public final class VillagersWiring {

    /** The permission gating the {@code /villager manager} staff tool. */
    private static final String MANAGER_PERMISSION = "uxmessentials.villagers.manager";
    /** The permission gating click-to-trade access. */
    private static final String TRADE_PERMISSION = "uxmessentials.villagers.trade";
    /** The permission gating the {@code /villager protect} toggle. */
    private static final String PROTECT_PERMISSION = "uxmessentials.villagers.protect";
    /** The permission gating villager pickup with the villager-in-a-bucket item. */
    private static final String BUCKET_PERMISSION = "uxmessentials.villagers.bucket";
    /** The permission gating the {@code /villager follow} toggle. */
    private static final String FOLLOW_PERMISSION = "uxmessentials.villagers.follow";
    /** The permission gating villager leashing. */
    private static final String LEASH_PERMISSION = "uxmessentials.villagers.leash";

    private VillagersWiring() {}

    /**
     * Build the villagers listeners and commands and start the restock sweep from {@code ctx}, ready to register.
     *
     * @param ctx the module context carrying the scoped config and kernel ports
     * @param server the server the restock sweep enumerates villagers across on the global region thread
     * @param menus the menu engine the trade-manager window is registered with and opened through
     * @param menuBindings the binding registry the trade-manager spec's buttons and item region resolve against
     * @param dataFolder the plugin data folder an operator's own copy of the trade-manager spec is read from
     */
    public static Wired wire(
            ModuleContext ctx, Server server, Menus menus, MenuBindings menuBindings, Path dataFolder) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        KernelPorts kernel = ctx.kernel();
        VillagersConfig config = VillagersConfig.from(ctx.config());
        PdcVillagerFlags flags = new PdcVillagerFlags();
        VillagerRecipeStore recipeStore = new VillagerRecipeStore();
        GuiText guiText = new GuiText(kernel.messages());

        List<Listener> listeners = new ArrayList<>();
        List<CommandRegistration> commands = new ArrayList<>();
        // Infinite trading and instant restock both react to a completed trade, so they share one listener; it lands
        // only when at least one of the two is on, and each flag selects its own reset shape inside the handler.
        if (config.infiniteTrading().enabled() || config.instantRestock().enabled()) {
            listeners.add(new VillagerTradeListener(
                    config.infiniteTrading().enabled(), config.instantRestock().enabled()));
        }
        // The disable listener honours both the global switch and the per-villager PDC flag, so it registers for the
        // whole enabled module; with the switch off and no flag set it cancels nothing.
        listeners.add(new DisableTradesListener(config.disableTrades().enabled(), flags, kernel.messages()));

        VillagerManagerView managerView = null;
        if (config.tradeManager().enabled()) {
            VillagerManagerWindow window =
                    new VillagerManagerWindow(kernel.messages(), menus, dataFolder, kernel.log());
            managerView = new VillagerManagerView(kernel.scheduler(), flags, recipeStore, window);
            window.register(menuBindings, managerView);
            listeners.add(new VillagerRecipeReapplyListener(recipeStore));
        }
        if (config.clickToTrade().enabled()) {
            listeners.add(new ClickToTradeListener(
                    config.disableTrades().enabled(), TRADE_PERMISSION, flags, VillagersWiring::openTrade));
        }
        // Protection: the saver listener cancels the deaths a protected villager would suffer and keeps it loaded, and
        // the /villager protect toggle sets the per-villager mark it reads. Both wire only under the protect switch.
        VillagerProtectToggle protectToggle = null;
        if (config.protect().enabled()) {
            VillagerProtectionPolicy protectPolicy = config.protect().policy();
            listeners.add(new VillagerProtectionListener(protectPolicy, flags));
            protectToggle = new VillagerProtectToggle(kernel.scheduler(), flags, protectPolicy, kernel.messages());
        }
        // Follow: /villager follow makes the looked-at villager pathfind after the player, and a repeating sweep walks
        // each following villager toward its owner. Wires only under the follow switch.
        VillagerFollowService followService = null;
        if (config.follow().enabled()) {
            followService = new VillagerFollowService(
                    server,
                    kernel.scheduler(),
                    flags,
                    new PathfinderVillagerMover(),
                    config.follow().followRange(),
                    config.follow().speed(),
                    kernel.messages(),
                    true);
        }
        // The one /villager command registers whenever the module is on: its subcommands each self-gate on their own
        // permission and are present only when their feature wired, so with every sub-feature off the root still
        // exists (gated on uxmessentials.villagers.use) and its bare executor reports that no tools are enabled,
        // rather than the command silently not existing while the module reads as running.
        commands.add(new VillagerCommand(
                managerView != null ? MANAGER_PERMISSION : null,
                managerView,
                protectToggle != null ? PROTECT_PERMISSION : null,
                protectToggle,
                followService != null ? FOLLOW_PERMISSION : null,
                followService,
                kernel.messages()));
        // Leashing: intercept a lead right-click on a villager and attach the lead. Wires only under the leash switch.
        if (config.leash().enabled()) {
            listeners.add(new VillagerLeashListener(true, LEASH_PERMISSION));
        }
        // Villager-in-a-bucket: sneak-pickup into a captured-villager item and place it back. The listener honours its
        // own bucket switch, so it registers for the whole enabled module and is an inert early return when off.
        listeners.add(new VillagerBucketListener(
                config.bucket().enabled(), BUCKET_PERMISSION, new VillagerBucketItem(), guiText));

        RestockPolicy policy = new RestockPolicy(config.restock().interval());
        VillagerRestockSweep sweep = new VillagerRestockSweep(
                server,
                kernel.scheduler(),
                flags,
                policy,
                config.restock().interval(),
                Clock.systemUTC(),
                config.restock().enabled());
        AutoCloseable sweepTask = sweep.start();
        AutoCloseable followTask = followService != null ? followService.start() : () -> {};
        VillagerManagerView viewToDrain = managerView;
        Runnable stop = () -> stop(sweepTask, followTask, viewToDrain, kernel.log());
        // The follow sessions are the module's one readable per-player fact, so the seam exists only when follow
        // does: with the feature off there is nothing to count and the placeholder degrades to the dash.
        VillagersPlaceholders placeholders =
                followService == null ? null : new FollowVillagersPlaceholders(followService);
        return new Wired(listeners, commands, stop, placeholders);
    }

    // The click-to-trade opener: force-open the villager's trade window. Both openMerchant overloads carry a
    // deprecation for their InventoryView return type, but it is the only API that opens a merchant window, and the
    // return value is unused here.
    @SuppressWarnings("deprecation") // openMerchant is the only API to force-open a villager's trade window
    private static void openTrade(org.bukkit.entity.Player player, org.bukkit.entity.Villager villager) {
        player.openMerchant((org.bukkit.inventory.Merchant) villager, true);
    }

    // Cancel the repeating tasks and drain any still-open manager window on module stop, logging any failure with
    // context rather than swallowing it, so a disable or reload strands no scheduled task and loses no edit.
    private static void stop(
            AutoCloseable sweepTask, AutoCloseable followTask, @Nullable VillagerManagerView managerView, Logger log) {
        if (managerView != null) {
            managerView.flushAll();
        }
        close(sweepTask, "failed to cancel the villager restock sweep", log);
        close(followTask, "failed to cancel the villager follow sweep", log);
    }

    private static void close(AutoCloseable task, String failureMessage, Logger log) {
        try {
            task.close();
        } catch (Exception failure) {
            log.error(failureMessage, failure);
        }
    }

    /**
     * Everything the villagers module contributes once wired: the listeners and commands to register and the teardown
     * hook that cancels the restock sweep and saves any open manager window.
     *
     * @param listeners the Bukkit listeners to register
     * @param commands the Brigadier commands to register
     * @param stop the teardown hook run on module stop
     */
    public record Wired(
            List<Listener> listeners,
            List<CommandRegistration> commands,
            Runnable stop,
            @Nullable VillagersPlaceholders placeholders) {

        public Wired {
            listeners = List.copyOf(listeners);
            commands = List.copyOf(commands);
            Objects.requireNonNull(stop, "stop");
        }
    }
}
