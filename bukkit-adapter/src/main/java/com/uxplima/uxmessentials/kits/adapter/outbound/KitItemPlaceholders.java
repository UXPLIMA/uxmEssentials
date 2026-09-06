package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves PlaceholderAPI tokens in a granted kit item's display name and lore. Extracted from
 * {@link BukkitKitGranter} so the granter stays focused on inventory placement: this is the one place the kit
 * grant touches an item's text, round-tripping each name and lore line through MiniMessage so an operator can
 * author an item named {@code <gold>{player}'s Reward} and have it resolve for the recipient.
 *
 * <p>{@link #bridgeFor} returns the per-grant transform: the recipient's PlaceholderAPI substitution when the
 * kit opts in and PlaceholderAPI is installed, the identity otherwise, so an existing kit and a server
 * without PlaceholderAPI both grant the item's text unchanged.
 */
@NullMarked
final class KitItemPlaceholders {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private KitItemPlaceholders() {}

    /** The name/lore transform for {@code recipient}: PlaceholderAPI when {@code enabled} and present, else identity. */
    static UnaryOperator<String> bridgeFor(java.util.UUID recipient, boolean enabled) {
        if (enabled && PlaceholderApiSupport.isPresent()) {
            return PlaceholderApiSupport.messageBridge(recipient);
        }
        return UnaryOperator.identity();
    }

    /**
     * Rewrite {@code stack}'s display name and lore through {@code placeholders}. A stack with no meta, no
     * name, and no lore is left untouched, and an identity bridge is a no-op.
     */
    static void apply(ItemStack stack, UnaryOperator<String> placeholders) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean changed = rewriteName(meta, placeholders);
        changed |= rewriteLore(meta, placeholders);
        if (changed) {
            stack.setItemMeta(meta);
        }
    }

    private static boolean rewriteName(ItemMeta meta, UnaryOperator<String> placeholders) {
        Component name = meta.hasDisplayName() ? meta.displayName() : null;
        if (name == null) {
            return false;
        }
        meta.displayName(resolve(name, placeholders));
        return true;
    }

    private static boolean rewriteLore(ItemMeta meta, UnaryOperator<String> placeholders) {
        List<Component> lore = meta.hasLore() ? meta.lore() : null;
        if (lore == null || lore.isEmpty()) {
            return false;
        }
        List<Component> resolved = new ArrayList<>(lore.size());
        for (Component line : lore) {
            resolved.add(resolve(line, placeholders));
        }
        meta.lore(resolved);
        return true;
    }

    /** Serialize {@code component} to MiniMessage, resolve placeholders, and re-deserialize the result. */
    private static Component resolve(Component component, UnaryOperator<String> placeholders) {
        return MINI.deserialize(placeholders.apply(MINI.serialize(component)));
    }
}
