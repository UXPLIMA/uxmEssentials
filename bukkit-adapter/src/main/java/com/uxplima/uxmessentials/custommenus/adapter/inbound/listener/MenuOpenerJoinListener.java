package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import org.jspecify.annotations.Nullable;

/**
 * Hands joining players their opener items per each opener's {@link GiveOnJoin} rule. {@link GiveOnJoin#ALWAYS}
 * gives the item on every join; {@link GiveOnJoin#FIRST} gives it once, guarded by the per-opener PDC flag
 * {@link OpenerItems} owns, so a relog never duplicates it; {@link GiveOnJoin#NEVER} gives nothing (the opener is
 * only reachable from an item a player already holds).
 *
 * <p>The item is placed in the opener's configured slot when that slot is a valid, empty player-inventory slot;
 * otherwise it is added to the first free slot so a full or occupied target never drops the item on the floor. The
 * join event fires on the joining player's own entity thread, where touching their live inventory is legal, so the
 * give runs inline: it only ever touches the one player who joined, never the wider roster.
 */
public final class MenuOpenerJoinListener implements Listener {

    /** The player inventory addresses slots {@code 0..40}: 0 to 35 storage, 36 to 39 armour, 40 off-hand. */
    private static final int MAX_SLOT = 40;

    private final Supplier<List<OpenerSpec>> openers;
    private final OpenerItems openerItems;

    public MenuOpenerJoinListener(Supplier<List<OpenerSpec>> openers, OpenerItems openerItems) {
        this.openers = Objects.requireNonNull(openers, "openers");
        this.openerItems = Objects.requireNonNull(openerItems, "openerItems");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (OpenerSpec opener : openers.get()) {
            giveIfDue(player, opener);
        }
    }

    private void giveIfDue(Player player, OpenerSpec opener) {
        GiveOnJoin mode = opener.giveOnJoin();
        if (mode == GiveOnJoin.NEVER) {
            return;
        }
        if (mode == GiveOnJoin.FIRST && openerItems.hasGiven(player, opener.menu())) {
            return;
        }
        give(player, opener);
        if (mode == GiveOnJoin.FIRST) {
            openerItems.markGiven(player, opener.menu());
        }
    }

    private void give(Player player, OpenerSpec opener) {
        ItemStack item = openerItems.build(opener);
        PlayerInventory inventory = player.getInventory();
        int slot = opener.slot();
        if (slot >= 0 && slot <= MAX_SLOT && isEmpty(inventory.getItem(slot))) {
            inventory.setItem(slot, item);
        } else {
            inventory.addItem(item);
        }
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
