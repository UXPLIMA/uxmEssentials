package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tags a menu display item with one persistent-data byte so a copy that ever escapes the menu into a real inventory
 * a bug, a plugin conflict, or a crash with a menu open: stays identifiable and can be stripped. This is
 * defence-in-depth behind the engine's cancel-all-clicks invariant: nothing should extract a display item in the first
 * place, but if one slips through, the mark distinguishes it from a genuine player item so a close/join sweep removes
 * only the escaped copies and never touches real inventory.
 *
 * <p>The key is created once as a {@code static final} and reused on every mark and read, never on a hot path
 * (CLAUDE.md §NamespacedKey). Only the key's presence matters, so every value is a {@link PersistentDataType#BYTE} of
 * one. A mark is only ever written onto a display copy the renderer built, never onto a player's real item, which is
 * what makes it a reliable "this is a menu tile, not something the player owns" signal for the sweep.
 */
@NullMarked
public final class MenuItemMark {

    /** The single persistent-data key every rendered menu tile carries; created once, reused on every mark and read. */
    public static final NamespacedKey KEY =
            Objects.requireNonNull(NamespacedKey.fromString("uxmessentials:menu"), "menu mark key");

    /** The stored flag byte. Its value is immaterial: the sweep tests for the key's presence, not this number. */
    private static final byte MARKED = (byte) 1;

    private MenuItemMark() {}

    /**
     * Write the menu mark onto {@code item}'s meta and return the same stack. The item is a menu display copy the
     * renderer just built, so this only ever tags a throwaway tile, never a player's real item. The mark rides on the
     * item meta, so it survives the clones and moves Bukkit makes while the tile sits in the menu window. A stack whose
     * meta cannot be read (a material with no meta) is returned unmarked rather than failing the render.
     */
    public static ItemStack mark(ItemStack item) {
        Objects.requireNonNull(item, "item");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, MARKED);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} carries the menu mark. A null, empty/AIR, or meta-less stack, and any un-marked item, is false. */
    public static boolean isMarked(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
