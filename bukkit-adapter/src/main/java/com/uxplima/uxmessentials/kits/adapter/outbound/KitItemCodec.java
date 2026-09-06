package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.kits.domain.KitItem;
import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption layer between a Bukkit {@link ItemStack} and the kits domain's opaque
 * {@link KitItem} payload. A kit's item is serialized with Paper's own item codec
 * ({@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}), so the full item
 * components, enchantments, custom name. Round-trips exactly, then Base64-encoded into the platform-neutral
 * string the domain carries. The codec is the only place the kits context touches an {@code ItemStack}; the
 * {@code :core} layer never sees one.
 *
 * <p>An empty or air stack is dropped at encode time so a kit defined from a partially-filled inventory does
 * not store blank slots. A payload that fails to decode (a corrupt or version-incompatible kit file) is
 * surfaced as an {@link IllegalArgumentException} the caller logs and skips, rather than silently granting
 * nothing.
 */
@NullMarked
public final class KitItemCodec {

    private KitItemCodec() {}

    /** The {@link KitItem}s for the non-empty stacks in {@code contents}, preserving slot order. */
    public static List<KitItem> encodeAll(ItemStack[] contents) {
        Objects.requireNonNull(contents, "contents");
        List<KitItem> items = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && !stack.getType().isAir()) {
                if (i >= 0 && i <= 40) {
                    items.add(encode(stack, i));
                } else {
                    items.add(encode(stack));
                }
            }
        }
        return List.copyOf(items);
    }

    /** Encode a single non-air {@code stack} into a {@link KitItem} carrying its amount and serialized form. */
    public static KitItem encode(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        String data = Base64.getEncoder().encodeToString(stack.serializeAsBytes());
        return KitItem.of(data, stack.getAmount());
    }

    /** Encode a single non-air {@code stack} into a {@link KitItem} carrying its amount, serialized form, and slot. */
    public static KitItem encode(ItemStack stack, int slot) {
        Objects.requireNonNull(stack, "stack");
        String data = Base64.getEncoder().encodeToString(stack.serializeAsBytes());
        return KitItem.of(data, stack.getAmount(), java.util.Optional.of(slot));
    }

    /**
     * Decode {@code item} back into a Bukkit {@link ItemStack}. The serialized form carries the stack's own
     * amount, which Paper restores; the {@link KitItem#amount()} mirrors it for display and is not re-applied.
     *
     * @throws IllegalArgumentException when the payload is not valid Base64 or not a serializable item
     */
    public static ItemStack decode(KitItem item) {
        Objects.requireNonNull(item, "item");
        try {
            byte[] bytes = Base64.getDecoder().decode(item.data());
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("kit item payload could not be decoded", malformed);
        }
    }
}
