package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.NoFlyWorldListener;
import com.uxplima.uxmessentials.playerstate.application.NoFlyWorldPolicy;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /fly [player]} ({@code uxmessentials.fly.use}): toggle flight for yourself, or another player with
 * the {@code uxmessentials.fly.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node. v1 ships the plain on/off toggle; timed fly is deferred
 * post-v1, so there is no duration argument. The {@code ToggleFly} use case owns the snapshot mutation,
 * reconciliation, event, and feedback.
 *
 * <p>When the target stands in a no-fly world ({@code no-fly-worlds}) and the toggle would <em>enable</em>
 * flight, the command refuses with {@code FLY_WORLD_DISABLED} rather than toggling, unless the player holds
 * the {@code uxmessentials.playerstate.fly.allworlds} bypass node. Disabling flight is always allowed, and a
 * player already permitted to fly by their gamemode (creative/spectator) is left to the gamemode: the refusal
 * only fires when the toggle would grant plugin flight the world forbids.
 */
@NullMarked
public final class FlyCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.fly.use";

    private final NoFlyWorldPolicy noFlyWorlds;

    public FlyCommand(PlayerStateServices services, Messages messages, NoFlyWorldPolicy noFlyWorlds) {
        super(services, messages);
        this.noFlyWorlds = Objects.requireNonNull(noFlyWorlds, "noFlyWorlds");
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.fly.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("fly")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(PlayerTargets.players("player").executes(this::toggle))
                .build();
    }

    @Override
    public String description() {
        return "Toggle flight.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            // The no-fly-world refusal is per target: a player standing in a forbidden world is skipped (and
            // told why) while the rest of an @a fan-out still toggle.
            Player live = Bukkit.getPlayer(target.uuid());
            if (live != null && refusedByNoFlyWorld(sender, live)) {
                continue;
            }
            services.toggleFly().toggleFor(actor(ctx), target);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** True when the toggle would grant plugin flight in a no-fly world for a player without the bypass. */
    private boolean refusedByNoFlyWorld(CommandSender sender, Player target) {
        if (noFlyWorlds.isEmpty()
                || target.getAllowFlight()
                || target.hasPermission(NoFlyWorldListener.BYPASS_NODE)
                || !noFlyWorlds.isNoFly(target.getWorld().getName())) {
            return false;
        }
        feedback.send(sender, PlayerstateMessageKey.FLY_WORLD_DISABLED, Map.of());
        return true;
    }
}
