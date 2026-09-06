package com.uxplima.uxmessentials.staff.domain;

import java.util.Objects;

/**
 * The captured pre-staff-mode state of a player, held so the exact loadout can be restored when the
 * player leaves staff mode. The four item/effect regions are opaque {@link LoadoutBlob}s, the adapter
 * encodes and decodes them through the same {@code VaultItemCodec} the vaults context uses, so this record
 * stays a pure value with no Bukkit, Paper, or NBT type leaking into the domain. The scalars carry the rest
 * of the visible player state staff mode overwrites: the held hotbar slot, the experience bar, the game
 * mode, and the flight flag.
 *
 * <p>This is the item-loss-safe heart of staff mode: it is committed to the loadout repository <i>before</i>
 * the player's inventory is swapped for the gadget hotbar, so a crash mid-swap leaves the real loadout
 * recoverable from the database rather than lost. The record never lives in PDC. It is DB-backed (it must
 * survive a world rollback), which is why it carries the full snapshot rather than a reference. The regions
 * are wrapped in {@link LoadoutBlob} value objects rather than raw {@code byte[]} so the record stays a clean
 * value type.
 *
 * <p>{@code vanishedBefore} captures the player's vanish state as it was <i>before</i> they entered staff mode,
 * so leaving (or being recovered) restores that exact state rather than hard-revealing. A player who was already
 * vanished via {@code /vanish} before entering stays vanished on exit; one who was visible is revealed.
 *
 * @param inventory encoded main inventory contents (slots 0..35)
 * @param armor encoded armour contents (boots/leggings/chestplate/helmet)
 * @param offhand encoded off-hand item
 * @param heldSlot the selected hotbar slot, {@code 0..8}
 * @param expLevel the player's experience level
 * @param expProgress the fractional progress towards the next level, {@code 0.0..1.0}
 * @param gameMode the game-mode name (e.g. {@code SURVIVAL}); never blank
 * @param flying whether the player was flying
 * @param allowFlight whether the player was allowed to fly before entering staff mode, kept so granting
 *     flight during staff mode reverts to the player's real allowance rather than always switching it off
 * @param potionEffects encoded active potion effects
 * @param vanishedBefore whether the player was vanished before entering staff mode
 */
public record SavedLoadout(
        LoadoutBlob inventory,
        LoadoutBlob armor,
        LoadoutBlob offhand,
        int heldSlot,
        int expLevel,
        float expProgress,
        String gameMode,
        boolean flying,
        boolean allowFlight,
        LoadoutBlob potionEffects,
        boolean vanishedBefore) {

    public SavedLoadout {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(armor, "armor");
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(gameMode, "gameMode");
        Objects.requireNonNull(potionEffects, "potionEffects");
        if (heldSlot < 0 || heldSlot > 8) {
            throw new IllegalArgumentException("heldSlot must be 0..8: " + heldSlot);
        }
        if (gameMode.isBlank()) {
            throw new IllegalArgumentException("gameMode must not be blank");
        }
    }
}
