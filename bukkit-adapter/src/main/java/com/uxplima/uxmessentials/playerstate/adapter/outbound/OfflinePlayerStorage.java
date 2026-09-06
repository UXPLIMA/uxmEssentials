package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for reading and writing an <em>offline</em> player's stored inventory and ender chest directly
 * on their {@code playerdata/<uuid>.dat} file, the data {@code /invsee} and {@code /endersee} reach for when the
 * target is not online and so has no live {@link org.bukkit.inventory.PlayerInventory}. The single implementation
 * goes through Mojang-mapped server internals (the only deliberate NMS coupling in the plugin), kept behind this
 * Bukkit-typed port so the rest of the playerstate context never sees it.
 *
 * <p>All four methods touch the disk and must run off the main/region thread (the caller schedules them through
 * the kernel {@code Scheduler}'s async lane). A read failure surfaces as an empty {@link Optional} (logged); a
 * write failure is logged and dropped, never thrown into the scheduler.
 */
@NullMarked
public interface OfflinePlayerStorage {

    /** Whether a {@code playerdata} file exists for {@code uuid} (the player has logged in at least once). */
    boolean hasData(UUID uuid);

    /** Read {@code uuid}'s stored inventory and ender chest, or empty when there is no file or the read fails. */
    Optional<OfflineInventory> load(UUID uuid);

    /**
     * Replace the inventory block of {@code uuid}'s stored data with {@code slots} (length
     * {@link OfflineInventory#SLOT_COUNT}; a {@code null} element is an empty slot). Everything else in the file
     * is preserved. A no-op if the player has no stored data.
     */
    void saveInventory(UUID uuid, @Nullable ItemStack[] slots);

    /**
     * Replace the ender-chest block of {@code uuid}'s stored data with {@code ender} (length
     * {@link OfflineInventory#ENDER_SIZE}; a {@code null} element is an empty slot). Everything else in the file
     * is preserved. A no-op if the player has no stored data.
     */
    void saveEnderChest(UUID uuid, @Nullable ItemStack[] ender);
}
