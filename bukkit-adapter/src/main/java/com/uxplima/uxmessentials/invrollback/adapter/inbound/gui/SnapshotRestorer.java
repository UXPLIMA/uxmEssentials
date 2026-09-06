package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.InvrollbackMessageKey;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.invrollback.domain.event.SnapshotRestored;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Applies a chosen snapshot to a target's live inventory. The action behind the {@code /invrestore} preview's
 * restore button. The flow hops three times so every touch happens on the thread that owns it (Folia-safe): on the
 * target's own entity thread it reads and serializes the target's <em>current</em> inventory (main + armor +
 * offhand + ender chest) as the pre-restore state; off the tick thread the {@link RestoreSnapshot} use case freezes
 * that state as a {@link SnapshotCause#RESTORE} safety snapshot and resolves the chosen snapshot; back on the
 * target's entity thread the chosen snapshot is decoded and set onto the live inventory.
 *
 * <p>Restore requires the target <b>online</b>. The snapshot is applied to their live inventory, never written to
 * disk, so a target who has logged off (between the GUI open and the click, or ever) yields a "not online" line to
 * the staff member and no change; their snapshots persist, so the restore succeeds once they rejoin. A stale
 * snapshot id (pruned or already restored) resolves to nothing and applies no change. The staff confirmation and
 * the offline refusal are delivered to the staff member through the region-hopping {@link MessageSink}.
 */
@NullMarked
public final class SnapshotRestorer {

    private final RestoreSnapshot restoreSnapshot;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final Clock clock;
    private final DomainEventPublisher events;

    public SnapshotRestorer(
            RestoreSnapshot restoreSnapshot,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            Clock clock,
            DomainEventPublisher events) {
        this.restoreSnapshot = Objects.requireNonNull(restoreSnapshot, "restoreSnapshot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Restore {@code target}'s inventory from snapshot {@code id}, safety-snapshotting first; notify {@code staff}. */
    public void restore(PlayerRef staff, PlayerRef target, SnapshotId id) {
        Objects.requireNonNull(staff, "staff");
        restore(target, id, outcome -> report(staff, target, outcome));
    }

    /**
     * The same restore, reporting what happened rather than telling a staff member about it.
     *
     * <p>The published action needs the three answers this flow can produce, and the flow itself is three thread
     * hops long, so it is shared rather than written a second time: a second copy would be the one that forgets
     * the safety snapshot.
     */
    public void restore(PlayerRef target, SnapshotId id, Consumer<Outcome> whenDone) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(whenDone, "whenDone");
        scheduler.onEntity(
                target,
                () -> readCurrentThenRestore(target, id, whenDone),
                () -> whenDone.accept(Outcome.TARGET_OFFLINE));
    }

    /** On the target's entity thread: serialize the current inventory, then run the restore off the tick thread. */
    private void readCurrentThenRestore(PlayerRef target, SnapshotId id, Consumer<Outcome> whenDone) {
        Player live = Bukkit.getPlayer(target.uuid());
        if (live == null || !live.isOnline()) {
            whenDone.accept(Outcome.TARGET_OFFLINE);
            return;
        }
        byte[] current = InventorySnapshotCodec.encode(
                live.getInventory().getContents(),
                live.getEnderChest().getContents(),
                BukkitRefs.toPosition(Objects.requireNonNull(live.getLocation(), "target location")));
        Instant now = clock.instant();
        scheduler.async(() -> {
            Optional<Snapshot> chosen = restoreSnapshot.restore(target.uuid(), id, current, now);
            if (chosen.isEmpty()) {
                whenDone.accept(Outcome.SNAPSHOT_GONE);
                return;
            }
            Snapshot snapshot = chosen.orElseThrow();
            scheduler.onEntity(
                    target, () -> apply(target, snapshot, whenDone), () -> whenDone.accept(Outcome.TARGET_OFFLINE));
        });
    }

    /** On the target's entity thread: decode the chosen snapshot and set it onto the live inventory. */
    private void apply(PlayerRef target, Snapshot snapshot, Consumer<Outcome> whenDone) {
        Player live = Bukkit.getPlayer(target.uuid());
        if (live == null || !live.isOnline()) {
            whenDone.accept(Outcome.TARGET_OFFLINE);
            return;
        }
        InventorySnapshotCodec.Decoded decoded = InventorySnapshotCodec.decode(snapshot.contents());
        live.getInventory().setContents(decoded.contents());
        if (decoded.enderChest().length > 0) {
            live.getEnderChest().setContents(decoded.enderChest());
        }
        // Published after the items are set, so a listener that reads the inventory sees the restored one.
        events.publish(new SnapshotRestored(target, snapshot.id(), snapshot.cause(), snapshot.createdAt()));
        whenDone.accept(new Outcome.Restored(snapshot.cause()));
    }

    /** Turn an outcome into the line the staff member who asked for it reads. */
    private void report(PlayerRef staff, PlayerRef target, Outcome outcome) {
        if (outcome instanceof Outcome.Restored restored) {
            notify(
                    staff,
                    InvrollbackMessageKey.INVROLLBACK_RESTORED,
                    Map.of("player", target.name(), "cause", restored.cause().name()));
            return;
        }
        // A stale snapshot and an offline target read the same to staff: nothing was restored, and the window they
        // clicked in was drawn before whichever of the two happened.
        notify(staff, InvrollbackMessageKey.INVROLLBACK_PLAYER_NOT_FOUND, Map.of("player", target.name()));
    }

    private void notify(PlayerRef staff, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(staff, messages.resolve(staff, key, placeholders));
    }

    /** What a restore did: it happened, the target was not there, or the snapshot no longer exists. */
    public sealed interface Outcome {

        /** The inventory was overwritten from a snapshot taken for {@code cause}. */
        record Restored(SnapshotCause cause) implements Outcome {
            public Restored {
                Objects.requireNonNull(cause, "cause");
            }
        }

        /** The target is not online, so there was no live inventory to write to. */
        Outcome TARGET_OFFLINE = new Missing();

        /** The snapshot no longer resolves: pruned, or already restored. */
        Outcome SNAPSHOT_GONE = new Gone();

        /** The target-offline outcome, as a type so the sealed set stays closed. */
        record Missing() implements Outcome {}

        /** The stale-snapshot outcome, as a type so the sealed set stays closed. */
        record Gone() implements Outcome {}
    }
}
