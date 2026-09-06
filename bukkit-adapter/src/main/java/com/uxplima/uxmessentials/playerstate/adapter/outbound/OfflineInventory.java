package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A snapshot of an offline player's stored items, read from their {@code playerdata/<uuid>.dat} file. Two flat
 * arrays, each indexed by the slot index Minecraft writes to disk so the mapping survives any in-memory
 * reshuffling:
 *
 * <ul>
 *   <li>{@code slots}. Length {@value #SLOT_COUNT}: indices {@code 0..35} the main inventory (hotbar then
 *       storage), {@code 36..39} armour (boots, leggings, chestplate, helmet), {@code 40} the offhand. This is
 *       exactly the slot order the {@code /invsee} menu lays out, so the snapshot drops straight into it.
 *   <li>{@code ender}: length {@value #ENDER_SIZE}: the ender chest, slots {@code 0..26}.
 * </ul>
 *
 * <p>A {@code null} element is an empty slot. Not a record: the holder owns two arrays, and exposing arrays from a
 * record is the kind of mutable-leak the static analysis rejects. The snapshot is read once and handed straight
 * to the menu seed, which clones each stack as it copies it in, so the held arrays are never aliased into a live
 * container.
 */
@NullMarked
public final class OfflineInventory {

    /** Main (0..35) + armour (36..39) + offhand (40): the slot span the invsee menu mirrors. */
    public static final int SLOT_COUNT = 41;

    /** The ender chest size, slots 0..26. */
    public static final int ENDER_SIZE = 27;

    private final @Nullable ItemStack[] slots;
    private final @Nullable ItemStack[] ender;

    public OfflineInventory(@Nullable ItemStack[] slots, @Nullable ItemStack[] ender) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(ender, "ender");
        if (slots.length != SLOT_COUNT) {
            throw new IllegalArgumentException("slots must be length " + SLOT_COUNT + ", was " + slots.length);
        }
        if (ender.length != ENDER_SIZE) {
            throw new IllegalArgumentException("ender must be length " + ENDER_SIZE + ", was " + ender.length);
        }
        this.slots = slots;
        this.ender = ender;
    }

    /** The inventory slots {@code 0..40} (main, armour, offhand); a {@code null} element is an empty slot. */
    public @Nullable ItemStack[] slots() {
        return slots;
    }

    /** The ender-chest slots {@code 0..26}; a {@code null} element is an empty slot. */
    public @Nullable ItemStack[] ender() {
        return ender;
    }
}
