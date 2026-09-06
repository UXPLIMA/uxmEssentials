package com.uxplima.uxmessentials.vanish.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishContainers;
import org.jspecify.annotations.NullMarked;

/**
 * Silences the open animation and sound a vanished player would otherwise broadcast when opening a lidded container.
 * A chest, shulker box, ender chest, or barrel plays its lid animation and open sound to every nearby player, driven by
 * the block's viewer count, so even a hidden player's peek gives them away. This listener intercepts the open: for a
 * vanished opener of one of those containers ({@link VanishContainers#broadcastsOpen}) it cancels the noisy vanilla
 * open, which never touches the block's viewer count, and instead shows a detached copy of the contents that carries no
 * block and therefore no animation. On close it writes the copy's contents back to the real container.
 *
 * <p>This is the same "live inventory mirror" pattern the invsee / endersee / offline-container views use, a raw
 * Bukkit inventory built and opened outside the menu engine, and is allow-listed with them in the architecture fence.
 * It is packet-free (our packet layer carries entity data, not block actions), which is the trade-off for not shipping
 * a ProtocolLib-style interceptor: the copy is a point-in-time snapshot, so if another actor mutates the real container
 * while the vanished player has the mirror open, the write-back on close wins. That window is negligible for a hidden
 * admin peeking at a chest, and the behaviour is off entirely unless {@code silent-chests} is enabled.
 *
 * <p>The re-open is deferred to the player's entity thread through the {@link Scheduler} port (an inventory cannot be
 * opened from within {@code InventoryOpenEvent}); the {@code onEntity} hop lands next tick, off the event.
 */
@NullMarked
public final class VanishSilentContainerListener implements Listener {

    private final VanishStore store;
    private final Scheduler scheduler;
    private final Server server;
    private final boolean silentChests;
    private final ConcurrentHashMap<UUID, Mirror> mirrors = new ConcurrentHashMap<>();

    public VanishSilentContainerListener(VanishStore store, Scheduler scheduler, Server server, boolean silentChests) {
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.silentChests = silentChests;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!silentChests || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID who = player.getUniqueId();
        Inventory original = event.getInventory();
        Mirror active = mirrors.get(who);
        if (active != null && active.copy() == original) {
            return; // our own silent re-open, do not intercept it again
        }
        if (!store.isVanished(who)
                || !VanishContainers.broadcastsOpen(original.getType().name())) {
            return;
        }
        if (original.getLocation() == null) {
            // A plugin GUI (the menu engine, the PIN keypad) is a virtual inventory with no block; only a real
            // block-backed container carries a world location. Mirroring a virtual GUI would swap it for a null-holder
            // copy, so its MenuHolder is lost and clicks stop being cancelled. Never mirror one.
            return;
        }
        Inventory copy = Bukkit.createInventory(null, original.getSize());
        copy.setContents(original.getContents());
        mirrors.put(who, new Mirror(original, copy));
        event.setCancelled(true);
        scheduler.onEntity(BukkitRefs.toRef(player), () -> reopen(who, copy));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Mirror active = mirrors.get(player.getUniqueId());
        if (active != null && active.copy() == event.getInventory()) {
            active.source().setContents(active.copy().getContents());
            mirrors.remove(player.getUniqueId());
        }
    }

    /** Open the silent copy on the player's own thread, dropping the pending mirror if they went offline first. */
    private void reopen(UUID who, Inventory copy) {
        Player player = server.getPlayer(who);
        if (player != null && player.isOnline()) {
            player.openInventory(copy);
        } else {
            mirrors.remove(who);
        }
    }

    /** A vanished player's in-flight silent view: the real container to write back to and the detached copy shown. */
    private record Mirror(Inventory source, Inventory copy) {
        Mirror {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(copy, "copy");
        }
    }
}
