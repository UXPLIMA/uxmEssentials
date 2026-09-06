package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoUpdates;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.pipeline.PacketAction;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The opt-in "suppress real players" synthetic-tab mechanism (TAB-C). A {@link TablistRenderer} delegates here when a
 * viewer's selected format carries {@code suppress-real-players = true}: every real player is hidden from that viewer's
 * tab list so only this renderer's filler rows remain. It is split out of the renderer so each class stays a cohesive
 * size — the renderer owns the header/footer/name/order/skin/filler painting, this owns the packet-rewrite interception.
 *
 * <p><strong>How a player is hidden.</strong> A single shared {@link PacketListener} is registered once on the injected
 * {@link PacketListenerRegistry}. For every outbound {@code ClientboundPlayerInfoUpdatePacket} bound for a viewer who is
 * currently in suppress mode, it rewrites the packet (via {@link PlayerInfoUpdates#forceUnlisted}) so every entry whose
 * uuid is <em>not</em> one of this renderer's filler ids for that viewer is forced {@code listed=false} — present as a
 * known client entity (entity name/skin still work) but not drawn as a tab row. Any other packet, or a packet for a
 * viewer not in suppress mode, passes through unchanged. Because the rewrite forces every non-filler entry unlisted, a
 * TAB-A custom-skin {@code ADD_PLAYER} re-add for a real player is force-unlisted too, so it can never un-suppress them.
 *
 * <p><strong>Netty-thread safety.</strong> The listener runs on a Netty I/O thread. It touches no Bukkit API and never
 * blocks: it reads only the thread-safe {@link #fillerIds} snapshot (a {@link ConcurrentHashMap} of immutable id sets
 * the main thread swaps whole) and the {@link #active} membership set. Any error building the rewrite is logged and the
 * original packet is passed unchanged — the listener never throws on the I/O thread and never drops a viewer's tab.
 *
 * <p><strong>Lifecycle.</strong> {@link #apply} runs on the viewer's region/entity thread each render: entering suppress
 * mode injects the interceptor into that viewer's connection and immediately relists the real players unlisted (so rows
 * the join bundle already sent are hidden at once); leaving it (a switch-away, {@link #disable}) ejects the interceptor
 * and relists the real players back so their rows return. {@link #forget} drops a quitting viewer's tracking without a
 * relist packet to a closing channel. Per-viewer suppress state is mirrored exactly like the renderer's applied-state
 * maps, so no listener leaks and no viewer is ever left stuck-suppressed.
 */
@NullMarked
public final class TablistSuppression {

    /** Ejects/injects the per-viewer interceptor; abstracted so the lifecycle is unit-testable without a live channel. */
    @FunctionalInterface
    public interface ConnectionGate {
        /** Inject the interceptor into {@code viewer}'s pipeline (idempotent); {@code true} on success. */
        boolean inject(Player viewer);

        /** Eject the interceptor from {@code viewer}'s pipeline (idempotent); {@code true} if it was present. */
        default boolean eject(Player viewer) {
            return false;
        }
    }

    /**
     * The packet-rewrite seam: {@link PlayerInfoUpdates#forceUnlisted} in production, swappable in a unit test so the
     * listener's REWRITE/PASS branching and its fail-soft are exercised without the NMS dev bundle. Returns the
     * rewritten packet, or {@code null} when the original should be forwarded unchanged.
     */
    @FunctionalInterface
    public interface Rewriter {
        @Nullable Object forceUnlisted(Object packet, Predicate<UUID> suppress);
    }

    private final ConnectionGate gate;
    private final TabListPackets packets;
    private final Supplier<? extends Collection<? extends Player>> viewers;
    private final Logger log;
    private final Rewriter rewriter;

    /**
     * The filler-entry ids this renderer owns for each viewer currently in suppress mode, keyed by viewer uuid. The
     * value is an immutable snapshot swapped whole by {@link #apply} on the viewer's region thread; the Netty listener
     * only ever reads it, so no entry is mutated in place and the read needs no lock. An absent viewer means no fillers
     * are protected (everything would be suppressed) — but a viewer is only ever a key while {@link #active}, so the
     * listener reads this only for active viewers.
     */
    private final Map<UUID, Set<UUID>> fillerIds = new ConcurrentHashMap<>();

    /** The viewers currently in suppress mode. Read on the Netty thread, mutated on the region thread; concurrent set. */
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public TablistSuppression(
            ConnectionGate gate,
            PacketListenerRegistry registry,
            TabListPackets packets,
            Supplier<? extends Collection<? extends Player>> viewers,
            Logger log) {
        this(gate, registry, packets, viewers, log, PlayerInfoUpdates::forceUnlisted);
    }

    /** Test seam: the same wiring with the packet {@link Rewriter} supplied so the listener can be driven NMS-free. */
    TablistSuppression(
            ConnectionGate gate,
            PacketListenerRegistry registry,
            TabListPackets packets,
            Supplier<? extends Collection<? extends Player>> viewers,
            Logger log,
            Rewriter rewriter) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.log = Objects.requireNonNull(log, "log");
        this.rewriter = Objects.requireNonNull(rewriter, "rewriter");
        Objects.requireNonNull(registry, "registry").register(new SuppressListener());
    }

    /**
     * Reconcile {@code viewer}'s suppress state for the selected format. When {@code suppress} is true the viewer's
     * filler-id snapshot is refreshed and, on the entering edge, the interceptor is injected and the real players
     * relisted unlisted at once. When false the viewer is taken out of suppress mode (eject + relist real players back)
     * if they were in it. Runs on the viewer's region/entity thread, like the rest of the render path.
     *
     * <p>The renderer calls this <em>before</em> it paints the fillers, passing the ids the layout is about to paint, so
     * for an already-suppressed viewer the snapshot already protects a filler when its {@code ADD_PLAYER} crosses the
     * live interceptor — otherwise that first paint would be force-unlisted and the per-cell flicker guard would never
     * repaint it.
     *
     * @param plannedFillerIds the filler entry ids the layout will paint this tick (the entries kept listed)
     */
    public void apply(Player viewer, boolean suppress, Set<UUID> plannedFillerIds) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(plannedFillerIds, "plannedFillerIds");
        UUID viewerId = viewer.getUniqueId();
        if (!suppress) {
            disable(viewer);
            return;
        }
        // Refresh the protected-id snapshot before the fillers are painted, so the listener never suppresses a filler
        // this layout is about to add for an already-suppressed viewer.
        Set<UUID> kept = Set.copyOf(plannedFillerIds);
        Set<UUID> previous = fillerIds.put(viewerId, kept);
        if (active.add(viewerId)) {
            gate.inject(viewer);
            hideReal(viewer, kept);
            return;
        }
        if (previous != null && !previous.equals(kept)) {
            // The protected set changed under a viewer who is already suppressed: a roster group seated a player (their
            // own entry must come back) or released one (it must go). The interceptor only rewrites packets the server
            // sends, so a set that changes with no packet in flight needs this relist to reach the client at all.
            relistChanged(viewer, previous, kept);
        }
    }

    /**
     * Take {@code viewer} out of suppress mode if they were in it: eject the interceptor, drop the tracking, and relist
     * the real players back so their tab rows return. A no-op for a viewer who was never suppressed, so it is safe to
     * call from every teardown path (a switch-away, {@code clear}, world-blacklist, module stop).
     */
    public void disable(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        UUID viewerId = viewer.getUniqueId();
        if (!active.remove(viewerId)) {
            fillerIds.remove(viewerId);
            return;
        }
        fillerIds.remove(viewerId);
        gate.eject(viewer);
        relistReal(viewer, true);
    }

    /**
     * Drop a quitting {@code viewer}'s suppress tracking without sending a relist packet to a closing channel — the
     * connection is gone, so the relist would hit a dead channel. The interceptor is ejected anyway (idempotent and
     * harmless on a closed pipeline), and the tracking is forgotten so a relog re-evaluates from scratch.
     */
    public void forget(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        UUID viewerId = viewer.getUniqueId();
        boolean wasActive = active.remove(viewerId);
        fillerIds.remove(viewerId);
        if (wasActive) {
            gate.eject(viewer);
        }
    }

    /** Relist the currently-online real players for {@code viewer}: {@code false} hides them, {@code true} restores. */
    private void relistReal(Player viewer, boolean listed) {
        List<UUID> realIds = realPlayerIds();
        if (!realIds.isEmpty()) {
            packets.send(viewer, packets.relist(realIds, listed));
        }
    }

    /**
     * Hide the real players from {@code viewer} on the entering edge, except the ones {@code kept} protects: a player a
     * roster group has seated is drawn by their own entry, so hiding it would empty the cell they sit in.
     */
    private void hideReal(Player viewer, Set<UUID> kept) {
        List<UUID> hidden = new ArrayList<>();
        for (UUID id : realPlayerIds()) {
            if (!kept.contains(id)) {
                hidden.add(id);
            }
        }
        if (!hidden.isEmpty()) {
            packets.send(viewer, packets.relist(hidden, false));
        }
    }

    /**
     * Carry a changed protected set to {@code viewer}: a real player who entered it is listed again (they now hold a
     * cell), and one who left it is hidden again (their cell is gone). Only real players are relisted; a filler entry
     * is added listed and removed outright, so it never needs one.
     */
    private void relistChanged(Player viewer, Set<UUID> previous, Set<UUID> kept) {
        List<UUID> shown = new ArrayList<>();
        List<UUID> hidden = new ArrayList<>();
        for (UUID id : realPlayerIds()) {
            boolean wasKept = previous.contains(id);
            boolean isKept = kept.contains(id);
            if (isKept && !wasKept) {
                shown.add(id);
            } else if (!isKept && wasKept) {
                hidden.add(id);
            }
        }
        if (!shown.isEmpty()) {
            packets.send(viewer, packets.relist(shown, true));
        }
        if (!hidden.isEmpty()) {
            packets.send(viewer, packets.relist(hidden, false));
        }
    }

    /** The uuids of the currently-online real players — the entries a suppress mode hides and a restore re-lists. */
    private List<UUID> realPlayerIds() {
        Collection<? extends Player> online = viewers.get();
        List<UUID> ids = new ArrayList<>(online.size());
        for (Player player : online) {
            ids.add(player.getUniqueId());
        }
        return ids;
    }

    /** True when {@code id} is one of {@code viewer}'s protected filler ids — i.e. an entry that must stay listed. */
    private boolean isFiller(UUID viewerId, UUID id) {
        Set<UUID> ids = fillerIds.get(viewerId);
        return ids != null && ids.contains(id);
    }

    /**
     * The "hide this uuid" predicate for {@code viewerId}: a uuid is suppressed exactly when it is <em>not</em> one of
     * this renderer's filler ids for the viewer, so the synthetic tab keeps only the filler rows and hides every real
     * player (and any other entry). Package-private so a test can assert a filler id is kept and a real id is hidden.
     */
    Predicate<UUID> suppressPredicate(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        return id -> !isFiller(viewerId, id);
    }

    /**
     * The single shared interceptor: for an active viewer it force-unlists every non-filler entry of an outbound
     * player-info update; otherwise (or for any other packet) it passes the packet through. Fail-soft on the I/O thread.
     */
    private final class SuppressListener implements PacketListener {

        @Override
        public PacketAction onSend(@Nullable UUID viewerId, Object packet) {
            // The rewrite path is in onSendVerdict; a plain pass keeps a non-rewriting caller correct.
            return PacketAction.PASS;
        }

        @Override
        public PacketVerdict onSendVerdict(@Nullable UUID viewerId, Object packet) {
            if (viewerId == null || !active.contains(viewerId)) {
                return PacketVerdict.pass();
            }
            try {
                Object rewritten = rewriter.forceUnlisted(packet, suppressPredicate(viewerId));
                return rewritten == null ? PacketVerdict.pass() : PacketVerdict.rewrite(rewritten);
            } catch (RuntimeException failure) {
                // Never throw on the Netty thread and never drop a viewer's tab: log and forward the original.
                log.error("tablist suppress rewrite failed for " + viewerId + "; forwarding original", failure);
                return PacketVerdict.pass();
            }
        }
    }
}
