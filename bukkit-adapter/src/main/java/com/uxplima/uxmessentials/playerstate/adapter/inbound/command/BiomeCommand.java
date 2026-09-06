package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Location;
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
 * {@code /biome} ({@code uxmessentials.biome.use}): tell the player which biome they are standing in, read from
 * the world at their block position. A pure read in the adapter, no use case and no state mutation. Self-only,
 * so there is no {@code [player]} target form; the biome is always the invoking player's own.
 *
 * <p>The biome name comes from its registry key path segment (e.g. {@code plains}). That is data, not a
 * user-facing literal, so it feeds the {@code biome} placeholder of the localized show line rather than being
 * a message of its own. Underscores are swapped for spaces so {@code old_growth_pine_taiga} reads naturally.
 */
@NullMarked
public final class BiomeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.biome.use";

    public BiomeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("biome")
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
        return "Show the biome you are standing in.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player never is): assert it so NullAway is satisfied at the dereference.
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        String name = player.getWorld()
                .getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                .getKey()
                .getKey()
                .replace('_', ' ');
        Map<String, String> placeholders = Map.of("biome", name);
        feedback.send(player, PlayerstateMessageKey.BIOME_SHOW, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
