package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tags an in-inventory shulker-box view so {@link ShulkerBoxListener} recognises it and {@link ShulkerBoxView} can
 * write it back on close. Mirrors the sanctioned raw-inventory leaves (the playerstate {@code InvseeHolder} /
 * {@code OfflineHolder}), but the source is an <em>item the opener is holding</em>, not another player's inventory:
 * the holder carries the opener and the hotbar slot the source box occupies, so the write-back reads exactly that
 * slot and the listener can lock it against being moved while the view is open (no dupe, no loss).
 */
@NullMarked
final class ShulkerBoxHolder implements InventoryHolder {

    private final PlayerRef owner;
    private final int sourceSlot;
    private @Nullable Inventory inventory;

    ShulkerBoxHolder(PlayerRef owner, int sourceSlot) {
        this.owner = Objects.requireNonNull(owner, "owner");
        if (sourceSlot < 0 || sourceSlot > 8) {
            throw new IllegalArgumentException("source shulker must sit in a hotbar slot (0-8): " + sourceSlot);
        }
        this.sourceSlot = sourceSlot;
    }

    /** The player who opened the view; the source box lives in their inventory and the edits write back to it. */
    PlayerRef owner() {
        return owner;
    }

    /** The hotbar slot (0-8) holding the source box: locked while the view is open and re-read on write-back. */
    int sourceSlot() {
        return sourceSlot;
    }

    /** Store the built view so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("shulker view inventory not attached yet");
        }
        return built;
    }
}
