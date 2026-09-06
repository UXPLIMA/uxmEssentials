package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.MenuTitles;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Opens a shulker box <em>item</em> the player is holding as a 27-slot view they edit in place, and writes the
 * edited contents back into the box's {@link BlockStateMeta} on close. The AxShulkers "open a shulker from the
 * inventory" surface. It is a sanctioned raw-inventory leaf (on the ArchUnit createInventory allow-list), built the
 * same dupe-safe way as the playerstate invsee mirrors: the view is seeded from a <em>snapshot</em> of the box's
 * contents ({@link BlockStateMeta#getBlockState()} returns a copy), so the player's edits touch only the view until
 * the single write-back on close applies them in one pass.
 *
 * <p>The source box is locked in place while the view is open. {@link ShulkerBoxListener} cancels any click, drag
 * or drop that would move it out of its hotbar slot, and refuses to open a second view for a player who already has
 * one, so the {@link ShulkerBoxHolder#sourceSlot() slot} the write-back reads always still holds that same box. A
 * player is tracked once so a single write-back claims their view, whichever of the close handler or
 * {@link #flushAll} (on module stop) reaches it first, and the write-back re-checks the slot still carries a
 * shulker box before touching it, so a box that somehow left the slot discards the edits rather than corrupting a
 * different item.
 *
 * <p>A disconnect is covered too. {@link #onQuit} claims a view its owner is still holding as they leave and writes
 * it back off the quitting player, because a view abandoned unwritten would duplicate anything they had dragged out
 * of the box: the item would be saved with the player while the box kept its own copy.
 *
 * <p>The open runs from a {@code PlayerInteractEvent} on the player's own region thread and the close write-back
 * from a {@code InventoryCloseEvent} on that same thread, so both mutate the player's inventory directly; only the
 * disable-time {@link #flushAll} hops onto each owner's entity thread through the kernel {@link Scheduler}.
 */
@NullMarked
public final class ShulkerBoxView {

    /** A shulker box holds a single row-of-three grid: 27 slots, always. */
    static final int SIZE = 27;

    private final Messages messages;
    private final Scheduler scheduler;
    private final Map<UUID, ShulkerBoxHolder> open = new ConcurrentHashMap<>();

    public ShulkerBoxView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Open the shulker box in {@code player}'s main hand as an editable view. A no-op when the player already has a
     * view open (the already-open guard) or the held item is not a shulker box carrying a block state.
     */
    public void open(Player player, PlayerRef owner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(owner, "owner");
        if (open.containsKey(owner.uuid())) {
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack source = player.getInventory().getItem(slot);
        ShulkerBox box = shulkerStateOf(source);
        if (box == null) {
            return;
        }
        ShulkerBoxHolder holder = new ShulkerBoxHolder(owner, slot);
        Inventory view = Bukkit.createInventory(holder, SIZE, title(player));
        holder.attach(view);
        view.setContents(box.getInventory().getContents());
        open.put(owner.uuid(), holder);
        player.openInventory(view);
    }

    /** Claim {@code holder}'s view on close and write its edits back into the source box. Called by the listener. */
    void onClose(ShulkerBoxHolder holder, Player closer) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(closer, "closer");
        if (open.remove(holder.owner().uuid(), holder)) {
            writeBack(closer, holder);
        }
    }

    /**
     * Write a quitting player's open view back before they leave. A disconnect is not guaranteed to reach the close
     * handler while the player still counts as online, and a view abandoned unwritten is not merely a lost edit: an
     * item dragged out of the box and into the inventory would be saved with the player while the box kept its copy,
     * which duplicates it. The quitting player's inventory is still live here and is written to disk after this, so
     * the write-back lands.
     */
    public void onQuit(Player player) {
        Objects.requireNonNull(player, "player");
        ShulkerBoxHolder holder = open.remove(player.getUniqueId());
        if (holder != null) {
            writeBack(player, holder);
        }
    }

    /** Write back and forget every still-open view; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (ShulkerBoxHolder holder : Map.copyOf(open).values()) {
            if (open.remove(holder.owner().uuid(), holder)) {
                scheduler.onEntity(holder.owner(), () -> {
                    Player live = Bukkit.getPlayer(holder.owner().uuid());
                    if (live != null) {
                        writeBack(live, holder);
                    }
                });
            }
        }
    }

    /** The number of shulker views currently open (a doctor line could report it). */
    public int openCount() {
        return open.size();
    }

    /**
     * Reconcile the edited view back into the source box, re-reading the locked hotbar slot. The slot is guarded
     * against movement while the view is open, so it still holds the same box; the guard here is defensive, if the
     * slot no longer carries a shulker box the edits are discarded rather than written onto a different item.
     */
    private void writeBack(Player player, ShulkerBoxHolder holder) {
        ItemStack source = player.getInventory().getItem(holder.sourceSlot());
        if (source == null
                || !(source.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }
        box.getInventory().setContents(holder.getInventory().getContents());
        meta.setBlockState(box);
        source.setItemMeta(meta);
        player.getInventory().setItem(holder.sourceSlot(), source);
    }

    private Component title(Player player) {
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        return MenuTitles.centre(StyledText.render(messages.resolve(ref, ItemworldMessageKey.SHULKER_TITLE, Map.of())));
    }

    /** The {@link ShulkerBox} block state a shulker-box item carries, or {@code null} for any other item. */
    private static @Nullable ShulkerBox shulkerStateOf(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return null;
        }
        return meta.getBlockState() instanceof ShulkerBox box ? box : null;
    }
}
