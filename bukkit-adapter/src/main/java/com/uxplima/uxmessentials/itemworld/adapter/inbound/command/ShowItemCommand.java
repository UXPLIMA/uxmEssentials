package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /showitem}: broadcast the held item to chat so everyone online can read what the sender is holding
 * the read-only chat verb that shares the held item. It mirrors the held-item read
 * commands ({@link ItemDbCommand}, {@code /recipe}) but fans the result out to the whole roster instead of the
 * sender. No persistence, no audit, and no region hop is needed: the held item is read on the sender's region
 * thread and the broadcast line is a {@link ItemworldMessageKey} resolved per viewer.
 *
 * <p>An empty hand replies {@link ItemworldMessageKey#NO_ITEM_IN_HAND} and broadcasts nothing. Otherwise the
 * item id and amount ride as placeholders of {@link ItemworldMessageKey#SHOWITEM_BROADCAST} (sent to every
 * online player) so a player can never inject MiniMessage into the line, and the sender gets a separate
 * {@link ItemworldMessageKey#SHOWITEM_SHOWN} confirmation.
 */
@NullMarked
public final class ShowItemCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.showitem.use";

    public ShowItemCommand(ItemworldServices services) {
        super(services, "showitem", SubFeatureGroup.ITEM_UTILS, "Broadcast the held item to chat.");
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
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        heldItem(ctx, player).ifPresent(hand -> broadcast(ctx, player, hand));
        return Command.SINGLE_SUCCESS;
    }

    private void broadcast(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand) {
        Map<String, String> placeholders = Map.of(
                "player",
                player.getName(),
                "item",
                hand.getType().getKey().toString(),
                "amount",
                String.valueOf(hand.getAmount()));
        // The online roster is enumerated on the global region thread (Folia forbids iterating
        // Bukkit.getOnlinePlayers() off it); the sink then resolves per viewer and hops to their region thread.
        services.kernel().scheduler().onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PlayerRef who = BukkitRefs.toRef(viewer);
                services.kernel()
                        .messageSink()
                        .deliver(
                                who,
                                services.kernel()
                                        .messages()
                                        .resolve(who, ItemworldMessageKey.SHOWITEM_BROADCAST, placeholders));
            }
        });
        reply(ctx, ItemworldMessageKey.SHOWITEM_SHOWN);
    }
}
