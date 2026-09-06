package com.uxplima.uxmessentials.invrollback.adapter.inbound.listener;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Freezes a player's inventory into a snapshot at the two capture triggers. On {@link PlayerDeathEvent} (when
 * {@code capture.on-death} is set) it reads the dying player's inventory before the items drop; on
 * {@link PlayerQuitEvent} (when {@code capture.on-logout} is set) it reads the leaving player's inventory. Each
 * trigger reads the live inventory on the region tick thread, serializes it there through the
 * {@link InventorySnapshotCodec}, then hops the DB write off the tick thread through the injected
 * {@link Scheduler#async} port, so a capture never blocks a region thread and stays Folia-safe.
 *
 * <p>The death handler runs at {@link EventPriority#MONITOR}: it only observes, it never alters the death, and by
 * that point the drop policy and death message are settled. The ender chest rides along when
 * {@code include-enderchest} is set.
 */
@NullMarked
public final class SnapshotCaptureListener implements Listener {

    /**
     * When this enable last captured each player, and why, keyed by player UUID. Written after the DB write
     * returns, so a capture that failed is not reported as one. It is deliberately session-scoped rather than a
     * count read back from the table: the snapshots are staff-read history, and a HUD refresh must never become a
     * query.
     */
    private final Map<UUID, Capture> lastCapture = new ConcurrentHashMap<>();

    private final CaptureSnapshot captureSnapshot;
    private final Scheduler scheduler;
    private final Clock clock;
    private final boolean captureOnDeath;
    private final boolean captureOnLogout;
    private final boolean includeEnderchest;

    public SnapshotCaptureListener(
            CaptureSnapshot captureSnapshot,
            Scheduler scheduler,
            Clock clock,
            boolean captureOnDeath,
            boolean captureOnLogout,
            boolean includeEnderchest) {
        this.captureSnapshot = Objects.requireNonNull(captureSnapshot, "captureSnapshot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.captureOnDeath = captureOnDeath;
        this.captureOnLogout = captureOnLogout;
        this.includeEnderchest = includeEnderchest;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (captureOnDeath) {
            capture(event.getEntity(), SnapshotCause.DEATH);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (captureOnLogout) {
            capture(event.getPlayer(), SnapshotCause.LOGOUT);
        }
    }

    /**
     * Read {@code player}'s inventory on the current tick thread, serialize it, and schedule the DB write off the
     * tick thread. Package-visible so the capture path is exercised directly without constructing a
     * {@link PlayerDeathEvent}.
     */
    void capture(Player player, SnapshotCause cause) {
        ItemStack[] contents = player.getInventory().getContents();
        @Nullable ItemStack @Nullable [] enderChest =
                includeEnderchest ? player.getEnderChest().getContents() : null;
        Position location = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
        byte[] payload = InventorySnapshotCodec.encode(contents, enderChest, location);
        UUID owner = player.getUniqueId();
        Instant now = clock.instant();
        scheduler.async(() -> {
            captureSnapshot.capture(owner, cause, payload, now);
            lastCapture.put(owner, new Capture(now, cause));
        });
    }

    /** The last capture taken for {@code owner} since this enable, or empty when none was. */
    public Optional<Capture> lastCapture(UUID owner) {
        return Optional.ofNullable(lastCapture.get(Objects.requireNonNull(owner, "owner")));
    }

    /** One recorded capture: when it was written, and what caused it. */
    public record Capture(Instant at, SnapshotCause cause) {

        public Capture {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(cause, "cause");
        }
    }
}
