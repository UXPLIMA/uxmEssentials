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
 * {@code /dimension} ({@code uxmessentials.dimension.use}): tell the player the canonical dimension key of the
 * world they are standing in, read straight from {@code World#getKey()} (for example {@code minecraft:the_nether}),
 * alongside the friendly environment name. A pure read in the adapter, no use case and no state mutation.
 * Self-only, so there is no {@code [player]} target form; it is always the invoking player's current world.
 *
 * <p>This is deliberately distinct from {@code /world}: {@code /world} surfaces the world's display name,
 * environment, and per-world player count, whereas {@code /dimension} surfaces the namespaced dimension
 * identifier that {@code /world} never shows: the value plugins and datapacks key worlds by. The dimension key
 * is data, not a user-facing literal, so it feeds the {@code dimension} placeholder; the environment enum name is
 * lower-cased and its underscores spaced for display, which is data shaping rather than a literal too.
 */
@NullMarked
public final class DimensionCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.dimension.use";

    public DimensionCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("dimension")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::show)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("dim");
    }

    @Override
    public String description() {
        return "Show the dimension you are standing in.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        World world = player.getWorld();
        Map<String, String> placeholders = Map.of(
                "dimension", world.getKey().asString(),
                "environment",
                        world.getEnvironment().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        feedback.send(player, PlayerstateMessageKey.DIMENSION_SHOW, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
