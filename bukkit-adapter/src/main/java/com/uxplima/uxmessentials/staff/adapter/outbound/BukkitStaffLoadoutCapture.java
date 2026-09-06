package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffGadgetItems;
import com.uxplima.uxmessentials.staff.adapter.StaffSettings;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-facing {@link StaffLoadoutCapture}: it is the only staff class that touches a live {@code Player},
 * an {@code ItemStack}, or a {@code PotionEffect}. It snapshots the real loadout into the pure
 * {@link SavedLoadout} the use case persists, restores a stored loadout back onto a player, and lays out the
 * gadget hotbar. The item regions ride the same {@link VaultItemCodec} the vaults context uses; the potion
 * effects ride {@link StaffEffectCodec}. The domain never sees a Bukkit type.
 *
 * <p><b>Thread ownership.</b> Every method here reads or writes the live player entity, so each must run on
 * that player's region thread. The caller (the command on enter, the use case path on exit, the listener on
 * quit/disable) is responsible for hopping onto the entity thread through the {@code Scheduler} port before
 * invoking these; this class assumes it already owns the entity.
 *
 * <p>An offline player cannot be snapshotted or swapped, so {@link #capture} of an absent player yields an
 * empty-but-valid loadout and {@link #applyGadgetHotbar} is a silent no-op. {@link #restore} of an absent
 * player returns {@code false} (nothing written back) so the exit use case keeps the durable DB row, the
 * item-loss-safe net: for the join-recovery path rather than deleting it over a non-restore.
 */
@NullMarked
public final class BukkitStaffLoadoutCapture implements StaffLoadoutCapture {

    private static final int MAIN_INVENTORY_SLOTS = 36;
    private static final int INFINITE_TICKS = -1;

    private final StaffSettings settings;
    private final StaffGadgetItems gadgetItems;
    private final StaffVanish vanish;

    public BukkitStaffLoadoutCapture(StaffSettings settings, StaffGadgetItems gadgetItems, StaffVanish vanish) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gadgetItems = Objects.requireNonNull(gadgetItems, "gadgetItems");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    @Override
    public SavedLoadout capture(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            // Offline at capture: hand back a valid empty loadout so the use case still has something durable.
            return emptyLoadout();
        }
        // Return any cursor item (a held-on-cursor stack from an open inventory) to the inventory before the
        // snapshot, so it is captured rather than dropped when the gadget hotbar clears the inventory (FIX 5).
        player.closeInventory();
        PlayerInventory inventory = player.getInventory();
        return new SavedLoadout(
                blob(inventory.getStorageContents()),
                blob(inventory.getArmorContents()),
                blob(new ItemStack[] {inventory.getItemInOffHand()}),
                inventory.getHeldItemSlot(),
                player.getLevel(),
                player.getExp(),
                player.getGameMode().name(),
                player.isFlying(),
                player.getAllowFlight(),
                StaffEffectCodec.encode(player.getActivePotionEffects()),
                vanish.isVanished(who));
    }

    @Override
    public boolean restore(PlayerRef who, SavedLoadout loadout) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(loadout, "loadout");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            // Offline at restore (a disconnect race): nothing was written back, so report failure, the use case
            // keeps the durable row for the join-recovery path rather than deleting it over a non-restore.
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(
                VaultItemCodec.decode(VaultContents.of(loadout.inventory().bytes()), 36));
        inventory.setArmorContents(
                VaultItemCodec.decode(VaultContents.of(loadout.armor().bytes()), 4));
        inventory.setItemInOffHand(offhand(loadout));
        inventory.setHeldItemSlot(loadout.heldSlot());
        restoreExperience(player, loadout);
        restoreEffects(player, loadout);
        restoreGameMode(player, loadout);
        player.updateInventory();
        return true;
    }

    @Override
    public void applyGadgetHotbar(PlayerRef who, String modeName) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(modeName, "modeName");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (StaffSettings.GadgetSpec spec : settings.gadgets()) {
            inventory.setItem(spec.slot(), gadgetItems.build(spec));
        }
        applyStaffModePerks(player);
        player.updateInventory();
    }

    /**
     * Grant the configured in-mode perks after the gadget hotbar is laid out. This runs after {@code capture}
     * (the enter sequence captures the real loadout first), so the granted night vision is never part of the
     * saved set and the captured flight allowance still reflects the player's real pre-mode value, both revert
     * cleanly on exit (the captured {@code allowFlight} on restore, the granted effect cleared by
     * {@link #restoreEffects}).
     */
    private void applyStaffModePerks(Player player) {
        if (settings.flightOnEnter()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        if (settings.nightVisionOnEnter()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, INFINITE_TICKS, 0, false, false));
        }
    }

    private static SavedLoadout emptyLoadout() {
        return new SavedLoadout(
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0.0f,
                GameMode.SURVIVAL.name(),
                false,
                false,
                LoadoutBlob.empty(),
                false);
    }

    private static LoadoutBlob blob(ItemStack[] contents) {
        return LoadoutBlob.of(VaultItemCodec.encode(contents).payload().orElseGet(() -> new byte[0]));
    }

    private static ItemStack offhand(SavedLoadout loadout) {
        ItemStack[] decoded =
                VaultItemCodec.decode(VaultContents.of(loadout.offhand().bytes()), 1);
        ItemStack stored = decoded.length == 0 ? null : decoded[0];
        return stored == null ? new ItemStack(org.bukkit.Material.AIR) : stored;
    }

    private static void restoreExperience(Player player, SavedLoadout loadout) {
        player.setLevel(loadout.expLevel());
        player.setExp(loadout.expProgress());
    }

    private static void restoreEffects(Player player, SavedLoadout loadout) {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        StaffEffectCodec.decode(loadout.potionEffects()).forEach(player::addPotionEffect);
    }

    private static void restoreGameMode(Player player, SavedLoadout loadout) {
        GameMode mode = parseGameMode(loadout.gameMode());
        player.setGameMode(mode);
        // Honour the captured allowance so a real pre-mode fly permission is preserved while a staff-granted one
        // (captured allowFlight=false for a survival player) is removed on exit. Creative/spectator always fly.
        boolean flightCapable = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        boolean allowed = flightCapable || loadout.allowFlight();
        player.setAllowFlight(allowed);
        // A legacy row can carry flying=true with allowFlight=false (a survival player who used /fly mid-air before
        // STAFF-C added the allowance column); setFlying(true) without the matching allowance throws, so gate it.
        player.setFlying(allowed && loadout.flying());
    }

    private static GameMode parseGameMode(String name) {
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            // A renamed/removed game mode falls back to survival rather than failing the whole restore.
            return GameMode.SURVIVAL;
        }
    }

    /** {@code MAIN_INVENTORY_SLOTS} documents the 36-slot storage region the codec round-trips. */
    static int mainInventorySlots() {
        return MAIN_INVENTORY_SLOTS;
    }
}
