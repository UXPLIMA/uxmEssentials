package com.uxplima.uxmessentials.ranks.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.playerstate.PlaytimeRepositories;
import com.uxplima.uxmessentials.persistence.ranks.PlayerRankRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.ranks.adapter.inbound.command.PrestigeCommand;
import com.uxplima.uxmessentials.ranks.adapter.inbound.command.RanksCommand;
import com.uxplima.uxmessentials.ranks.adapter.inbound.command.RankupCommand;
import com.uxplima.uxmessentials.ranks.adapter.inbound.command.SetRankCommand;
import com.uxplima.uxmessentials.ranks.adapter.inbound.gui.RanksPanelMenu;
import com.uxplima.uxmessentials.ranks.adapter.outbound.AutorankScan;
import com.uxplima.uxmessentials.ranks.adapter.outbound.BukkitRankActionRunner;
import com.uxplima.uxmessentials.ranks.adapter.outbound.BukkitRankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.adapter.outbound.api.RanksApiWrites;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.Prestige;
import com.uxplima.uxmessentials.ranks.application.RankLadders;
import com.uxplima.uxmessentials.ranks.application.RanksConfig;
import com.uxplima.uxmessentials.ranks.application.RanksMessageKey;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
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
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the ranks context's use cases over the injected kernel ports and the shared persistence DSL. Phase 2
 * builds the rankup pipeline: the parsed ladder, the DB-backed {@link PlayerRankRepository}, the
 * {@link CurrentRank} read, the {@link RankRequirementEvaluator} that resolves a rank's typed requirements against
 * the economy / playtime / permission / stored-rank / placeholder / inventory seams, and the
 * {@link RankActionRunner} that runs a rank's configured actions through the shared click-action engine. Those
 * assemble the {@link Rankup} and {@link SetRank} use cases, published as the {@code /rankup} command and the
 * {@code /ranks setrank} subcommand (which the top-level {@code /setrank} alias also publishes).
 *
 * <p>The rank cost is charged through the {@link RankEconomy} seam bridged in the economy context and handed in
 * here as an {@link Optional}: empty when economy is disabled, in which case a priced rank is free. The rank
 * action runner is wired with no click-action economy of its own: a rank's paywall is its {@code cost}, so its
 * actions are effects and commands, never a second charge.
 */
@NullMarked
public final class RanksWiring {

    /** The node that gates the ladder panel, shared by {@code /ranks} and the hub entry. */
    private static final String RANKS_GUI_PERMISSION = "uxmessentials.ranks.gui";

    private RanksWiring() {}

    /** Build the ranks use cases and commands from {@code ctx}, the shared {@code persistence} handle and economy. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Optional<RankEconomy> economy,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        RanksConfig config = RanksConfig.from(ctx.config());
        RankLadder ladder = RankLadders.from(ctx.config());
        PlayerRankRepository repository = PlayerRankRepositories.jooq(persistence);
        CurrentRank currentRank = new CurrentRank(repository, ladder);
        RankRequirementEvaluator requirements = requirementEvaluator(plugin, kernel, persistence, currentRank, economy);
        RankActionRunner actions = actionRunner(plugin, kernel);
        Rankup rankup = new Rankup(currentRank, repository, ladder, requirements, actions, economy, kernel.events());
        SetRank setRank = new SetRank(repository, ladder, kernel.events());
        // The ladder GUI is config-gated: when gui.enabled is off, no spec is registered, no binding wired, and the
        // /ranks command publishes only its admin setrank surface (the panel Optional stays empty).
        Optional<RanksPanelMenu> panel =
                ladderPanel(plugin, kernel, config, menus, menuBindings, rankup, currentRank, ladder);
        // The hub entry only exists when the panel does: with gui.enabled off there is nothing to open, so the
        // /uxmess gui hub shows no ranks icon rather than one that leads nowhere.
        panel.ifPresent(menu -> guiRegistry.register(new ManagementGuiEntry(
                "ranks", RanksMessageKey.RANKS_GUI_TITLE, Material.DIAMOND, RANKS_GUI_PERMISSION, menu::open)));
        List<CommandRegistration> commands = new ArrayList<>(List.of(
                new RankupCommand(rankup, kernel.messages()),
                new RanksCommand(setRank, ladder, panel, kernel.messages()),
                // The standalone /setrank publishes the same admin direct-set as /ranks setrank, under its own command
                // id so it renames/disables independently through commands.conf.
                new SetRankCommand(setRank, ladder, kernel.messages())));
        Optional<Prestige> prestige = Optional.empty();
        if (config.prestige().enabled()) {
            // Prestige is config-gated: when off, the /prestige verb is not published at all. It reuses the same
            // requirement evaluator, action runner and economy seam a rankup goes through.
            prestige = Optional.of(new Prestige(
                    currentRank,
                    repository,
                    ladder,
                    requirements,
                    actions,
                    economy,
                    config.prestige(),
                    kernel.events()));
            commands.add(new PrestigeCommand(prestige.orElseThrow(), kernel.messages()));
        }
        // Autorank reuses the same Rankup pipeline per online player on a schedule. start() no-ops when the scan is
        // disabled, so the module always builds it and hands back the stop hook that cancels the repeating task.
        AutorankScan autorank =
                new AutorankScan(plugin.getServer(), kernel.scheduler(), rankup, config.autorank(), kernel.log());
        AutoCloseable autorankTask = autorank.start();
        Runnable stop = () -> cancelAutorank(autorankTask, kernel.log());
        return new Wired(
                List.copyOf(commands),
                config,
                currentRank,
                ladder,
                requirements,
                new RanksApiWrites(rankup, setRank, prestige),
                stop);
    }

    /**
     * The {@code /ranks} ladder panel when {@code gui.enabled} is on: built over the same {@link Rankup} pipeline
     * the {@code /rankup} command runs and registered with the shared menu engine (its spec and per-display
     * placeholders bound into the shared {@link MenuBindings}), or {@link Optional#empty()} when the GUI is off, in
     * which case nothing is registered and the {@code /ranks} command stays admin-setrank-only.
     */
    private static Optional<RanksPanelMenu> ladderPanel(
            Plugin plugin,
            KernelPorts kernel,
            RanksConfig config,
            Menus menus,
            MenuBindings menuBindings,
            Rankup rankup,
            CurrentRank currentRank,
            RankLadder ladder) {
        if (!config.gui().enabled()) {
            return Optional.empty();
        }
        RanksPanelMenu panel =
                new RanksPanelMenu(menus, rankup, currentRank, ladder, kernel.scheduler(), kernel.messages());
        panel.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        return Optional.of(panel);
    }

    /** Cancel the autorank repeating task on module stop; a cancel failure is logged, never allowed to strand stop. */
    private static void cancelAutorank(AutoCloseable task, Logger log) {
        try {
            task.close();
        } catch (Exception failure) {
            log.error("failed to cancel the autorank scan task", failure);
        }
    }

    private static RankRequirementEvaluator requirementEvaluator(
            Plugin plugin,
            KernelPorts kernel,
            Persistence persistence,
            CurrentRank currentRank,
            Optional<RankEconomy> economy) {
        PlaytimeRepository playtime = PlaytimeRepositories.jooq(persistence);
        return new BukkitRankRequirementEvaluator(
                plugin.getServer(), kernel.permissions(), playtime, currentRank, economy, kernel.log());
    }

    /** The rank action runner over the same shared click-action engine the npc and hologram contexts use. */
    private static RankActionRunner actionRunner(Plugin plugin, KernelPorts kernel) {
        ClickCommandRunner commandRunner = new FilteredClickCommandRunner(
                new BukkitClickCommandRunner(), BlockedCommands.of(List.of()), kernel.log());
        ClickActionRunner clickRunner = new BukkitClickActionRunner(
                commandRunner,
                new BukkitServerConnector(plugin, kernel.log()),
                kernel.scheduler(),
                kernel.permissions(),
                Optional.empty(),
                kernel.messages(),
                RanksMessageKey.RANKS_RANKUP_COST,
                SerializedItems::decode,
                kernel.log());
        return new BukkitRankActionRunner(plugin.getServer(), clickRunner, kernel.log());
    }

    /**
     * Everything the ranks module contributes once wired: the Brigadier command registrations to publish, the
     * typed {@link RanksConfig} snapshot, the {@link CurrentRank} read use case and the parsed {@link RankLadder}
     * the PAPI placeholder seam reads a player's standing and next rank from, and the {@link #stop} hook that
     * cancels the autorank scan on module disable.
     *
     * @param commands the Brigadier command registrations to publish
     * @param config the typed ranks config snapshot
     * @param currentRank the read use case resolving a player's rank standing against the ladder
     * @param ladder the parsed ladder, exposed for the {@code %uxmessentials_rank_next%} placeholder seam
     * @param requirements the evaluator the published query checks a pending rankup's conditions with
     * @param apiWrites the same use cases the published rank actions run
     * @param stop the teardown hook the module registers, cancelling the autorank scan's repeating task
     */
    public record Wired(
            List<CommandRegistration> commands,
            RanksConfig config,
            CurrentRank currentRank,
            RankLadder ladder,
            RankRequirementEvaluator requirements,
            RanksApiWrites apiWrites,
            Runnable stop) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(currentRank, "currentRank");
            Objects.requireNonNull(ladder, "ladder");
            Objects.requireNonNull(requirements, "requirements");
            Objects.requireNonNull(apiWrites, "apiWrites");
            Objects.requireNonNull(stop, "stop");
        }
    }
}
