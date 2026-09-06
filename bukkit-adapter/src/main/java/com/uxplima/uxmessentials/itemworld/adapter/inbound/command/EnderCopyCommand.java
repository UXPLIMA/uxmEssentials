package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Arrays;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /endercopy <player>}: copy an online target's ender-chest contents into your own, the ender-chest
 * sibling of {@code /copyinv}. The named target must resolve online (else
 * {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>The target's ender chest is snapshotted on the <em>target's</em> own region thread, then the cloned
 * contents are written to the actor's ender chest on the <em>actor's</em> region thread through the kernel
 * {@code Scheduler}, so neither player's container is touched off its owning region thread (Folia). The
 * result is reported through {@link ItemworldMessageKey#ENDERCOPY_DONE}.
 */
@NullMarked
public final class EnderCopyCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.endercopy.use";

    public EnderCopyCommand(ItemworldServices services) {
        super(services, "endercopy", SubFeatureGroup.ITEM_UTILS, "Copy a player's ender chest into yours.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
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
        String name = StringArgumentType.getString(ctx, "player");
        Player target = self.getServer().getPlayerExact(name);
        if (target == null) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        services.kernel().scheduler().onEntity(ref(target), () -> {
            // Read the target's ender chest only on the target's own region thread; clone so the array that
            // crosses to the actor's thread shares no mutable ItemStack with the target's live ender chest.
            ItemStack[] contents = Arrays.stream(target.getEnderChest().getContents())
                    .map(stack -> stack == null ? null : stack.clone())
                    .toArray(ItemStack[]::new);
            services.kernel().scheduler().onEntity(ref(self), () -> {
                self.getEnderChest().setContents(contents);
                reply(ctx, ItemworldMessageKey.ENDERCOPY_DONE, Map.of("player", target.getName()));
            });
        });
        return Command.SINGLE_SUCCESS;
    }
}
