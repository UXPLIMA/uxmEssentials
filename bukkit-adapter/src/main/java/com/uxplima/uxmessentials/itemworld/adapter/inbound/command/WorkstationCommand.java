package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * One virtual-workstation command, {@code /anvil}, {@code /workbench} ({@code /craft}), {@code /enderchest}
 * ({@code /echest}), {@code /grindstone}, {@code /cartography}, {@code /loom}, {@code /smithingtable},
 * {@code /stonecutter}, {@code /furnace}, parameterised by its {@link Workstation}. Opening an inventory view
 * is an entity-bound operation, so the open is scheduled on the opener's region thread through the kernel
 * {@code Scheduler} and reported through {@link ItemworldMessageKey#WORKSTATION_OPENED}. The whole group is
 * gated by the {@code workstations} sub-feature flag and each command by its own per-command disable.
 *
 * <p>An optional {@code <player>} target forces the workstation open on another player, gated by the single
 * {@code uxmessentials.workstation.others} node: no argument keeps the sender-only behaviour; a present
 * argument opens the station on the resolved target's own entity thread and confirms to the sender with
 * {@link ItemworldMessageKey#WORKSTATION_OPENED_FOR}. Without the node a present argument is rejected.
 */
@NullMarked
public final class WorkstationCommand extends ItemworldCommandSupport implements CommandRegistration {

    /** The cross-cutting node that lets a workstation command target a player other than the sender. */
    private static final String OTHERS_PERMISSION = "uxmessentials.workstation.others";

    private final Workstation station;

    public WorkstationCommand(Workstation station, ItemworldServices services) {
        super(
                services,
                station.literal(),
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual " + station.displayName() + ".");
        this.station = station;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(station.permission()))
                .executes(this::run)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return station.aliases();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player sender = player(ctx);
        if (sender == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<PlayerSelectorArgumentResolver> selector = selector(ctx);
        if (selector.isEmpty()) {
            openForSelf(ctx, sender);
            return Command.SINGLE_SUCCESS;
        }
        openForOther(ctx, sender, selector.get());
        return Command.SINGLE_SUCCESS;
    }

    private void openForSelf(CommandContext<CommandSourceStack> ctx, Player sender) {
        services.kernel().scheduler().onEntity(ref(sender), () -> {
            station.open(sender);
            reply(ctx, ItemworldMessageKey.WORKSTATION_OPENED, Map.of("station", station.displayName()));
        });
    }

    private void openForOther(
            CommandContext<CommandSourceStack> ctx, Player sender, PlayerSelectorArgumentResolver selector) {
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            reply(ctx, SharedMessageKey.COMMAND_NO_PERMISSION);
            return;
        }
        Optional<Player> target = resolveTarget(ctx, selector);
        if (target.isEmpty()) {
            reply(ctx, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", typedTarget(ctx)));
            return;
        }
        Player other = target.get();
        services.kernel().scheduler().onEntity(ref(other), () -> {
            station.open(other);
            reply(
                    ctx,
                    ItemworldMessageKey.WORKSTATION_OPENED_FOR,
                    Map.of("station", station.displayName(), "player", other.getName()));
        });
    }

    private Optional<PlayerSelectorArgumentResolver> selector(CommandContext<CommandSourceStack> ctx) {
        try {
            return Optional.of(ctx.getArgument("player", PlayerSelectorArgumentResolver.class));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }

    private static Optional<Player> resolveTarget(
            CommandContext<CommandSourceStack> ctx, PlayerSelectorArgumentResolver selector) {
        try {
            List<Player> resolved = selector.resolve(ctx.getSource());
            return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
        } catch (CommandSyntaxException unmatched) {
            // A name with no online player, or a selector that matched nothing, surfaced as the unknown-player
            // rejection by the caller, never as a raw Brigadier parse error.
            return Optional.empty();
        }
    }

    /** The raw text the sender typed for the {@code player} argument, for the unknown-player rejection. */
    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }
}
