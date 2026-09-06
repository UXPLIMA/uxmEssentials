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
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /depth} ({@code uxmessentials.depth.use}): tell the player how many blocks they stand above or below
 * the world's sea level. A pure read in the adapter. Block Y minus {@code World#getSeaLevel()}, no use case and
 * no state mutation. Self-only, so there is no {@code [player]} target form; the count is always the invoking
 * player's own. The sign of the difference picks the above / at / below message.
 */
@NullMarked
public final class DepthCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.depth.use";

    public DepthCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("depth")
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
        return "Show your height relative to sea level.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player never is): assert it so NullAway is satisfied at the dereference.
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        int delta = location.getBlockY() - player.getWorld().getSeaLevel();
        MessageKey key = delta > 0
                ? PlayerstateMessageKey.DEPTH_ABOVE
                : delta < 0 ? PlayerstateMessageKey.DEPTH_BELOW : PlayerstateMessageKey.DEPTH_AT;
        Map<String, String> placeholders = Map.of("blocks", String.valueOf(Math.abs(delta)));
        feedback.send(player, key, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
