package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /book}: unlock the written book in the player's main hand back into an editable writable book.
 * A signed {@link Material#WRITTEN_BOOK} becomes a {@link Material#WRITABLE_BOOK} that
 * keeps the original pages, so the text can be edited again; any other item. A writable book that is already
 * editable, a non-book item, or an empty hand, replies {@link ItemworldMessageKey#NOT_A_WRITTEN_BOOK} and
 * mutates nothing. The new item is set back on the player's region thread through the {@code Scheduler} port.
 */
@NullMarked
public final class BookCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.book.use";

    public BookCommand(ItemworldServices services) {
        super(services, "book", SubFeatureGroup.ITEM_UTILS, "Unlock a written book for editing.");
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
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.WRITTEN_BOOK || !(hand.getItemMeta() instanceof BookMeta written)) {
            reply(ctx, ItemworldMessageKey.NOT_A_WRITTEN_BOOK);
            return Command.SINGLE_SUCCESS;
        }
        unlock(ctx, player, written);
        return Command.SINGLE_SUCCESS;
    }

    // A writable book carries only the raw page text. The author, title and signed formatting are
    // intentionally dropped when a signed book is reopened for editing. The plain-text getPages/setPages
    // pair is the only page API the test harness (MockBukkit) implements; the Adventure pages() overloads
    // throw there, so the transfer rides the String form, which is exactly what a writable book stores.
    @SuppressWarnings("deprecation") // raw page text only; no Adventure formatting is involved
    private void unlock(CommandContext<CommandSourceStack> ctx, Player player, BookMeta written) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack writable = new ItemStack(Material.WRITABLE_BOOK);
            BookMeta editable = (BookMeta) writable.getItemMeta();
            editable.setPages(written.getPages());
            writable.setItemMeta(editable);
            player.getInventory().setItemInMainHand(writable);
            reply(ctx, ItemworldMessageKey.BOOK_UNLOCKED);
        });
    }
}
