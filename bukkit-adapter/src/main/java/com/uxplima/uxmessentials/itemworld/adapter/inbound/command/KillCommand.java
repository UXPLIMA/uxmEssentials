package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kill [player]}: kill a target. The named player, the invoking player when no argument is given, or
 * every player a selector matches ({@code /kill @a}). An abusable verb (audit-logged): killing another player
 * is a moderation-adjacent action, so each kill is recorded with actor and target. A name or selector that
 * matches no online player answers {@link ItemworldMessageKey#UNKNOWN_TARGET}.
 *
 * <p>Setting health is entity-bound, so each kill runs on that victim's region thread through the kernel
 * {@code Scheduler}; the kill is reported through {@link ItemworldMessageKey#KILL_DONE} and audited.
 */
@NullMarked
public final class KillCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kill.use";

    public KillCommand(ItemworldServices services) {
        super(services, "kill", SubFeatureGroup.MOB_ENTITY, "Kill a target.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runSelf)
                .then(PlayerTargets.players("player").executes(this::runTargets))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int runSelf(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        kill(ctx, self, self);
        return Command.SINGLE_SUCCESS;
    }

    private int runTargets(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = actor(ctx);
        List<Player> victims = PlayerTargets.resolveAll(ctx, "player");
        if (victims.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", typedTarget(ctx)));
            return Command.SINGLE_SUCCESS;
        }
        for (Player victim : victims) {
            kill(ctx, actor, victim);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void kill(CommandContext<CommandSourceStack> ctx, Player actor, Player victim) {
        kill(ctx, ref(actor), victim);
    }

    private void kill(CommandContext<CommandSourceStack> ctx, PlayerRef actorRef, Player victim) {
        String targetName = victim.getName();
        services.kernel().scheduler().onEntity(ref(victim), () -> {
            victim.setHealth(0.0);
            reply(ctx, ItemworldMessageKey.KILL_DONE, Map.of("target", targetName));
            services.audit().killed(actorRef, targetName);
        });
    }

    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }
}
