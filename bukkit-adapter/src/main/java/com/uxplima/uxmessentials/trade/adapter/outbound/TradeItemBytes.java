package com.uxplima.uxmessentials.trade.adapter.outbound;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one codec between the real item stacks a cross-server trade escrows and the opaque {@code itemData} string the
 * escrow row holds. It encodes a list of stacks (with every component and NBT) to base64 of Paper's
 * {@link ItemStack#serializeItemsAsBytes(ItemStack[])} and decodes it back with
 * {@link ItemStack#deserializeItemsFromBytes(byte[])}, so both backends, running the same Paper build, round-trip the
 * exact items. Blank data means an empty stake, and a decode of corrupt data fails soft to an empty list rather than
 * aborting a delivery (the money leg still settles).
 */
@NullMarked
public final class TradeItemBytes {

    private TradeItemBytes() {}

    /** Encode the non-empty stacks in {@code items} to the opaque escrow string; empty in, blank out. */
    public static String encode(List<ItemStack> items) {
        if (items.isEmpty()) {
            return "";
        }
        byte[] bytes = ItemStack.serializeItemsAsBytes(items.toArray(ItemStack[]::new));
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Decode escrow {@code itemData} back into concrete stacks; blank or corrupt data yields no items. */
    public static List<ItemStack> decode(@Nullable String itemData) {
        List<ItemStack> stacks = new ArrayList<>();
        if (itemData == null || itemData.isBlank()) {
            return stacks;
        }
        try {
            for (@Nullable ItemStack stack :
                    ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(itemData))) {
                if (stack != null && !stack.getType().isAir()) {
                    stacks.add(stack);
                }
            }
        } catch (RuntimeException corrupt) {
            return new ArrayList<>();
        }
        return stacks;
    }

    /** The total quantity across {@code items}: the count the escrow row records for the audit receipt. */
    public static int totalCount(List<ItemStack> items) {
        int total = 0;
        for (ItemStack stack : items) {
            total += stack.getAmount();
        }
        return total;
    }
}
