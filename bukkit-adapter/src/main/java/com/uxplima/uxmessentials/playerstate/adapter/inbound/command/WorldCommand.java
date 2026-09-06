package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /world} ({@code uxmessentials.world.use}): tell the player the name, environment, and online player
 * count of the world they are standing in, read straight from {@code Player#getWorld()}. A pure read in the
 * adapter, no use case and no state mutation. Self-only, so there is no {@code [player]} target form; it is
 * always the invoking player's current world.
 *
 * <p>The player count is the per-world {@code World#getPlayers()} size rather than the server total, since a
 * "what world am I in" report is most useful scoped to that world. The environment enum name is lower-cased and
 * its underscores replaced with spaces so {@code THE_END} reads "the end". That is data shaping for display,
 * not a user-facing literal, so it feeds the {@code environment} placeholder of the localized show line.
 */
@NullMarked
public final class WorldCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.world.use";

    public WorldCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("world")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String description() {
        return "Show the world you are standing in.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        World world = player.getWorld();
        Map<String, String> placeholders = Map.of(
                "world", world.getName(),
                "environment",
                        world.getEnvironment().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                "players", Integer.toString(world.getPlayers().size()));
        feedback.send(player, PlayerstateMessageKey.WORLD_SHOW, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
