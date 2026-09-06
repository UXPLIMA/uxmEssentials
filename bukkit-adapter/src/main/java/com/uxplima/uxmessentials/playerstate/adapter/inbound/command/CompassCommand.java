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
 * {@code /compass} ({@code uxmessentials.compass.use}): tell the player which of the eight compass points they
 * face, read from their look yaw. A pure read in the adapter, no use case and no state mutation. Self-only,
 * so there is no {@code [player]} target form. The direction word is itself a catalog entry so it stays
 * localized: the yaw maps to one of the eight direction keys, that key resolves to a localized word, and the
 * word feeds the {@code direction} placeholder of the framing line (alongside the rounded {@code degrees}).
 *
 * <p>Minecraft yaw runs clockwise from south: 0 = south, 90 = west, 180 = north, 270 = east. Rounding the yaw
 * to the nearest 45° eighth and masking to {@code 0..7} indexes {@link #DIRECTIONS}, which is laid out in that
 * same yaw order so index 0 is south.
 */
@NullMarked
public final class CompassCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.compass.use";

    /** Direction keys in yaw order, eighths clockwise from south, so {@code DIRECTIONS[0]} is south. */
    private static final MessageKey[] DIRECTIONS = {
        PlayerstateMessageKey.COMPASS_SOUTH,
        PlayerstateMessageKey.COMPASS_SOUTH_WEST,
        PlayerstateMessageKey.COMPASS_WEST,
        PlayerstateMessageKey.COMPASS_NORTH_WEST,
        PlayerstateMessageKey.COMPASS_NORTH,
        PlayerstateMessageKey.COMPASS_NORTH_EAST,
        PlayerstateMessageKey.COMPASS_EAST,
        PlayerstateMessageKey.COMPASS_SOUTH_EAST,
    };

    public CompassCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("compass")
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
        return "Show the direction you are facing.";
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player never is): assert it so NullAway is satisfied at the dereference.
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        float yaw = location.getYaw();
        int eighth = Math.round(yaw / 45f) & 7;
        String direction = messages.resolve(ref(player), DIRECTIONS[eighth], Map.of());
        Map<String, String> placeholders = Map.of("direction", direction, "degrees", String.valueOf(Math.round(yaw)));
        feedback.send(player, PlayerstateMessageKey.COMPASS_SHOW, placeholders);
        return Command.SINGLE_SUCCESS;
    }
}
