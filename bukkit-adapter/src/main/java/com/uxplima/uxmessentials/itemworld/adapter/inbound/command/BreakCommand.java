package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;

import org.bukkit.block.Block;
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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /break}: instantly break the block the caller is looking at, dropping it as if mined by hand. An
 * admin-fun verb (audit-logged). A quick block-removal utility that is easy to abuse, so each break is
 * recorded with actor and block type. When nothing solid is in reach the caller is told
 * {@link ItemworldMessageKey#BREAK_NO_TARGET} and nothing changes.
 *
 * <p>Breaking the block mutates the world, so it runs on the caller's region thread through the kernel
 * {@code Scheduler}; the block type is captured before scheduling for the audit line and the
 * {@link ItemworldMessageKey#BREAK_DONE} reply.
 */
@NullMarked
public final class BreakCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.break.use";
    private static final int REACH = 64;

    public BreakCommand(ItemworldServices services) {
        super(services, "break", SubFeatureGroup.ADMIN_FUN, "Break the block you are looking at.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        Block target = self.getTargetBlockExact(REACH);
        if (target == null) {
            reply(ctx, ItemworldMessageKey.BREAK_NO_TARGET);
            return Command.SINGLE_SUCCESS;
        }
        String type = target.getType().getKey().asString();
        PlayerRef actor = ref(self);
        services.kernel().scheduler().onEntity(actor, () -> {
            target.breakNaturally();
            reply(ctx, ItemworldMessageKey.BREAK_DONE, Map.of("block", type));
            services.audit().brokeBlock(actor, type);
        });
        return Command.SINGLE_SUCCESS;
    }
}
