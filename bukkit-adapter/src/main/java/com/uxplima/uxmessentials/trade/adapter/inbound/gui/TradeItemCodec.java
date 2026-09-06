package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.trade.domain.OfferedItem;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The boundary between the Bukkit {@link ItemStack}s that physically sit in a trade window and the domain's opaque
 * {@link TradeOffer}. A positional offer array, one nullable stack per editable slot, maps to a list of
 * {@link OfferedItem}s by turning every non-empty stack into one whose handle is the stack's material name (a stable,
 * opaque token the domain never decodes) and whose amount is the stack size. The {@link TradeExchange} combines that
 * item list with the side's staked money into the whole {@link TradeOffer} it re-stakes into the session.
 *
 * <p>Delivery reads the real stacks straight off the same array through {@link #stacks}, so a commit or a return moves
 * the concrete items: the domain handle is bookkeeping only and is never used to reconstruct an item here.
 */
@NullMarked
final class TradeItemCodec {

    private TradeItemCodec() {}

    /** The domain item list for a positional editable-slot array, one {@link OfferedItem} per non-empty stack. */
    static List<OfferedItem> items(@Nullable ItemStack @Nullable [] offer) {
        List<OfferedItem> items = new ArrayList<>();
        if (offer == null) {
            return items;
        }
        for (@Nullable ItemStack stack : offer) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(new OfferedItem(stack.getType().name(), stack.getAmount()));
            }
        }
        return items;
    }

    /** The concrete, cloned stacks of a positional offer array, empties dropped, ready to hand to a recipient. */
    static List<ItemStack> stacks(@Nullable ItemStack @Nullable [] offer) {
        List<ItemStack> stacks = new ArrayList<>();
        if (offer == null) {
            return stacks;
        }
        for (@Nullable ItemStack stack : offer) {
            if (stack != null && !stack.getType().isAir()) {
                stacks.add(stack.clone());
            }
        }
        return stacks;
    }
}
