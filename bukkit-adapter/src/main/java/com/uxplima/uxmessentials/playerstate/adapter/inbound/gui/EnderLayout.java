package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflineInventory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The slot map of the {@code /endersee} menu, a flat 27-slot ender chest, plus the reconciliation directions over
 * it. The ender chest is a single fixed-size container with no filler region, so every slot is editable. This is
 * the ender-chest counterpart of {@link InvseeLayout}: {@link #fromPlayer} / {@link #seedSlots} clone the items
 * <em>into</em> the menu and {@link #writeBack} / {@link #readSlots} clone the edited region back <em>onto</em> the
 * target's live ender chest, each direction cloning so the menu never aliases the live container.
 */
@NullMarked
final class EnderLayout {

    static final int SIZE = OfflineInventory.ENDER_SIZE;

    private EnderLayout() {}

    /** Snapshot a live player's ender chest into a flat {@value #SIZE}-slot array (cloned). */
    static @Nullable ItemStack[] fromPlayer(Player target) {
        Objects.requireNonNull(target, "target");
        Inventory chest = target.getEnderChest();
        @Nullable ItemStack[] slots = new ItemStack[SIZE];
        for (int slot = 0; slot < SIZE && slot < chest.getSize(); slot++) {
            slots[slot] = clone(chest.getItem(slot));
        }
        return slots;
    }

    /** Copy a flat {@value #SIZE}-slot array into {@code menu} (clones each stack). */
    static void seedSlots(Inventory menu, @Nullable ItemStack[] slots) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(slots, "slots");
        for (int slot = 0; slot < SIZE; slot++) {
            menu.setItem(slot, clone(slot < slots.length ? slots[slot] : null));
        }
    }

    /** Read {@code menu}'s slots into a flat {@value #SIZE}-slot array (cloned). */
    static @Nullable ItemStack[] readSlots(Inventory menu) {
        Objects.requireNonNull(menu, "menu");
        @Nullable ItemStack[] slots = new ItemStack[SIZE];
        for (int slot = 0; slot < SIZE; slot++) {
            slots[slot] = clone(menu.getItem(slot));
        }
        return slots;
    }

    /** Reconcile {@code menu}'s slots back onto {@code target}'s live ender chest. */
    static void writeBack(Inventory menu, Player target) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(target, "target");
        applySlots(readSlots(menu), target);
    }

    /** Reconcile a flat {@value #SIZE}-slot array onto {@code target}'s live ender chest. */
    static void applySlots(@Nullable ItemStack[] slots, Player target) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(target, "target");
        Inventory chest = target.getEnderChest();
        for (int slot = 0; slot < SIZE && slot < chest.getSize(); slot++) {
            chest.setItem(slot, clone(slot < slots.length ? slots[slot] : null));
        }
    }

    /** Whether {@code slot} is one the viewer may edit; the whole ender chest is editable. */
    static boolean isEditable(int slot) {
        return slot >= 0 && slot < SIZE;
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
