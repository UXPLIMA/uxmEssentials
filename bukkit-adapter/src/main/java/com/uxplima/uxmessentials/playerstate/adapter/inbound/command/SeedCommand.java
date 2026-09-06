package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Map;

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
 * {@code /seed} ({@code uxmessentials.seed.use}): tell the player the world generation seed of the world they
 * are standing in, read straight from {@code World#getSeed()}. A pure read in the adapter, no use case and no
 * state mutation. Self-only, so there is no {@code [player]} target form; the seed is always that of the
 * invoking player's current world.
 *
 * <p>Unlike {@code /biome}, the seed needs no block position, it is a world-level value, so there is no
 * {@code getLocation()} dereference here. The numeric seed is data, not a user-facing literal, so it feeds the
 * {@code seed} placeholder of the localized show line rather than being a message of its own.
 */
@NullMarked
public final class SeedCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.seed.use";

    public SeedCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("seed")
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
        return "Show the seed of the world you are standing in.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        long seed = player.getWorld().getSeed();
        Map<String, String> placeholders = Map.of("seed", Long.toString(seed));
        feedback.send(player, PlayerstateMessageKey.SEED_SHOW, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
