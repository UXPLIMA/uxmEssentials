package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoEntry;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoGameMode;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoPackets;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Paints the fixed-slot {@link TablistLayout filler grid} a {@link TablistRenderer}'s selected format may carry. Split out
 * of the renderer so each class stays a cohesive size: the renderer owns the header/footer/name/order/skin row of the real
 * players, this owns the synthetic filler cells.
 *
 * <p>A selected layout's {@link TablistFiller} rows fill the tab cells the real players do not. Each filler is painted at
 * its {@link TablistLayout#slotToListOrder slot list-order} through a packet sent to the one viewer, with its text resolved
 * through the renderer's pipeline (the injected {@link TextRenderer}) and its skin through the {@link TablistSkinResolver}.
 * Each filler entry carries a <em>deterministic</em> UUID derived from {@code (viewer, slot)} ({@link #fillerId}) so a
 * re-paint updates the same entry and never leaks a fresh fake row per tick. The painted set is tracked per viewer
 * ({@link #appliedFillers}) and diffed: a steady tick whose filler text and skin are unchanged re-sends nothing, while a
 * switch away (a format with no or fewer fillers), a {@link #clear}/{@link #forget}, or a disable removes the filler
 * entries by their tracked UUIDs so no fake row is ever orphaned.
 *
 * <p>Every method touches the live viewer, so the renderer must call this on the viewer's region/entity thread, the same
 * contract as {@link TablistRenderer#renderFor(Player)}.
 */
@NullMarked
final class FillerPainter {

    /**
     * Resolves a filler's raw MiniMessage source to a rendered {@link Component} for a viewer at a render tick, the
     * renderer's own pipeline (built-in tokens → PlaceholderAPI → MiniMessage), handed in so the filler cells render
     * identically to the header/footer/name without this class re-implementing it.
     */
    @FunctionalInterface
    interface TextRenderer {
        Component render(Player viewer, String source, long tick);
    }

    private final TabListPackets packets;
    private final @Nullable PlayerInfoPackets batchPackets;
    private final TablistSkinResolver skinResolver;
    private final TextRenderer textRenderer;

    /**
     * The filler grid currently painted for each viewer, keyed by viewer UUID then by 1-based slot. The value remembers
     * what was last sent for that (viewer, slot) cell: the rendered text, list order, resolved skin, and whether the skin
     * was still resolving, so a steady-state tick whose cell is unchanged re-sends nothing (no flicker), while a switch
     * to a format with different/fewer fillers removes the cells that fell away by their deterministic {@link #fillerId}
     * UUIDs. An absent viewer key means no fillers are painted for that viewer. The inner map is a {@link LinkedHashMap}
     * guarded by the outer {@link ConcurrentHashMap}; every mutation runs on the viewer's region/entity thread, so the
     * inner map needs no concurrency of its own.
     */
    private final Map<UUID, Map<Integer, AppliedFiller>> appliedFillers = new ConcurrentHashMap<>();

    FillerPainter(TabListPackets packets, TablistSkinResolver skinResolver, TextRenderer textRenderer) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.batchPackets = packets instanceof PlayerInfoPackets supported ? supported : null;
        this.skinResolver = Objects.requireNonNull(skinResolver, "skinResolver");
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
    }

    /**
     * Reconcile the filler grid painted for {@code viewer} with the selected format's {@link TablistLayout}. Each filler
     * is painted at its slot's list-order through a packet sent to the one viewer, with its text and skin resolved per
     * viewer. The painted set is diffed against {@link #appliedFillers}: an unchanged cell re-sends nothing, a changed
     * cell is re-sent, and a cell that fell away (the new layout omits it) is removed by its deterministic
     * {@link #fillerId} UUID so no fake row is orphaned. An empty layout removes every filler this viewer carried.
     */
    void applyFillers(Player viewer, TablistLayout layout, long tick) {
        applyFillers(viewer, layout, Set.of(), tick);
    }

    /**
     * The same reconcile with the cells a roster group has seated a player in. A seated cell is drawn by that player's
     * own tab entry (the renderer gives them the cell's list order and the group's text), so this painter leaves the
     * slot alone: painting a synthetic cell there too would put two entries in one cell and push the grid out of shape.
     */
    void applyFillers(Player viewer, TablistLayout layout, Set<Integer> seatedSlots, long tick) {
        UUID viewerId = viewer.getUniqueId();
        Map<Integer, AppliedFiller> previous = appliedFillers.get(viewerId);
        if (layout.isEmpty()) {
            if (previous != null) {
                removeFillers(viewer, previous);
                appliedFillers.remove(viewerId);
            }
            return;
        }
        Map<Integer, AppliedFiller> next = new LinkedHashMap<>();
        List<PlayerInfoEntry> changed = new ArrayList<>();
        List<UUID> replacedProfiles = new ArrayList<>();
        for (PlannedCell cell : plannedCells(layout, seatedSlots)) {
            paintCell(viewer, layout, cell, tick, previous, next, changed, replacedProfiles);
        }
        removeIds(viewer, replacedProfiles);
        sendChanged(viewer, changed);
        removeStaleFillers(viewer, previous, next.keySet());
        appliedFillers.put(viewerId, next);
    }

    /**
     * Paint one filler into {@code viewer}'s grid, re-sending only when its <em>resolved</em> text, order, or skin
     * changed since the last tick. The text is keyed on the rendered component, not the raw source, so an animated or
     * relational-placeholder filler re-sends as it changes (the animation steps) while a static filler re-sends nothing
     * on a steady tick, the per-cell flicker guard. The entry id is the deterministic {@link #fillerId} so the same
     * cell is always the same tab entry: an update targets it and a later removal removes it exactly.
     *
     * <p>An offline filler skin that is still resolving (the {@link TablistSkinResolver} returns empty while its async
     * fetch is in flight) is painted skinless this tick and recorded as a <em>pending</em> cell. The cell is still
     * tracked (so a later removal targets its entry and never orphans the skinless row painted now) but the
     * {@link AppliedFiller#pending() pending flag} keeps it from ever equalling the resolved cell, so the next tick
     * re-evaluates and repaints the texture once the fetch lands rather than early-returning on an unchanged tuple.
     */
    private void paintCell(
            Player viewer,
            TablistLayout layout,
            PlannedCell cell,
            long tick,
            @Nullable Map<Integer, AppliedFiller> previous,
            Map<Integer, AppliedFiller> next,
            List<PlayerInfoEntry> changed,
            List<UUID> replacedProfiles) {
        int slot = cell.slot();
        int order = TablistLayout.slotToListOrder(slot, layout.direction(), layout.gridRows());
        Optional<TablistSkinSource> skinSource = cell.skin();
        Optional<TabSkin> skin = skinSource.flatMap(skinResolver::resolve);
        boolean pending = skinSource.isPresent() && skin.isEmpty();
        // A roster cell is rendered for its occupant, not for the viewer: {player} is who the row is about, and a
        // placeholder in it reads that player's state. A filler has no occupant, so it renders for the viewer.
        Component text = cell.text().isEmpty() ? Component.empty() : textRenderer.render(viewer, cell.text(), tick);
        AppliedFiller desired = new AppliedFiller(text, order, skin.orElse(null), pending);
        AppliedFiller current = previous == null ? null : previous.get(slot);
        if (current != null && desired.equals(current)) {
            // Unchanged, fully-resolved cell: keep the entry as-is, send nothing this tick (the flicker guard). A
            // pending desired never equals a current (the flag differs once resolved), so it always repaints below.
            next.put(slot, current);
            return;
        }
        UUID id = fillerId(viewer.getUniqueId(), slot);
        if (current != null && !Objects.equals(current.skin(), desired.skin())) {
            // A profile texture is part of ADD_PLAYER's GameProfile, not a mutable player-info field. Remove the old
            // profile before re-adding it or the client is allowed to retain the stale skin.
            replacedProfiles.add(id);
        }
        String profileName = layout.exact() ? "uxm_slot_" + slot : "";
        changed.add(new PlayerInfoEntry(
                id, text, order, skin.orElse(null), profileName, true, 0, PlayerInfoGameMode.SURVIVAL, true));
        next.put(slot, desired);
    }

    private void removeIds(Player viewer, List<UUID> ids) {
        if (!ids.isEmpty()) {
            packets.send(viewer, packets.remove(ids));
        }
    }

    /** Send all changed cells in one packet when the complete uxmLib port is available. */
    private void sendChanged(Player viewer, List<PlayerInfoEntry> changed) {
        if (changed.isEmpty()) {
            return;
        }
        if (batchPackets != null) {
            batchPackets.sendPacket(viewer, batchPackets.addOrUpdate(changed));
            return;
        }
        // Source-compatible fallback for third-party/test implementations of the historical single-entry port.
        for (PlayerInfoEntry entry : changed) {
            packets.send(
                    viewer,
                    packets.addOrUpdate(new TabEntry(
                            entry.id(), entry.displayName(), entry.listOrder(), entry.skin(), entry.name())));
        }
    }

    /** One cell this painter is about to paint: where it sits, what it says, and whose skin it wears. */
    private record PlannedCell(int slot, String text, Optional<TablistSkinSource> skin) {

        static PlannedCell of(TablistFiller filler, Optional<TablistSkinSource> fallbackSkin) {
            return new PlannedCell(filler.slot(), filler.text(), filler.skin().or(() -> fallbackSkin));
        }
    }

    /**
     * The cells of {@code layout} for this paint: the authored (or, in an exact grid, the materialized) filler list,
     * minus every cell a roster group has seated a player in. A seated cell belongs to that player's own entry.
     */
    private List<PlannedCell> plannedCells(TablistLayout layout, Set<Integer> seatedSlots) {
        List<PlannedCell> cells = new ArrayList<>();
        for (TablistFiller filler : materializedFillers(layout)) {
            if (seatedSlots.contains(filler.slot())) {
                continue;
            }
            cells.add(PlannedCell.of(filler, layout.skin()));
        }
        return cells;
    }

    /** Expand an exact layout to every declared client cell; sparse layouts keep only authored fillers. */
    private static List<TablistFiller> materializedFillers(TablistLayout layout) {
        if (!layout.exact()) {
            return layout.fillers();
        }
        Map<Integer, TablistFiller> fixed = new java.util.HashMap<>();
        for (TablistFiller filler : layout.fillers()) {
            fixed.put(filler.slot(), filler);
        }
        List<TablistFiller> cells = new ArrayList<>(layout.capacity());
        for (int slot = 1; slot <= layout.capacity(); slot++) {
            TablistFiller authored = fixed.get(slot);
            cells.add(authored != null ? authored : new TablistFiller(slot, "", Optional.empty()));
        }
        return cells;
    }

    /** Remove the filler entries whose slots are no longer in {@code keptSlots}: the cells the new layout dropped. */
    private void removeStaleFillers(
            Player viewer, @Nullable Map<Integer, AppliedFiller> previous, Set<Integer> keptSlots) {
        if (previous == null) {
            return;
        }
        List<UUID> stale = new ArrayList<>();
        for (Integer slot : previous.keySet()) {
            if (!keptSlots.contains(slot)) {
                stale.add(fillerId(viewer.getUniqueId(), slot));
            }
        }
        if (!stale.isEmpty()) {
            packets.send(viewer, packets.remove(stale));
        }
    }

    /** Remove every filler entry the viewer carried, used on a switch to an empty layout, clear, forget, or stop. */
    private void removeFillers(Player viewer, Map<Integer, AppliedFiller> painted) {
        List<UUID> ids = new ArrayList<>(painted.size());
        for (Integer slot : painted.keySet()) {
            ids.add(fillerId(viewer.getUniqueId(), slot));
        }
        if (!ids.isEmpty()) {
            packets.send(viewer, packets.remove(ids));
        }
    }

    /**
     * The deterministic ids of the filler cells currently painted for {@code viewer}. The entries a "suppress real
     * players" mode must keep listed while it hides everything else. An empty set when no fillers are painted. Read on
     * the viewer's region thread, so the snapshot is taken from the per-viewer tracking without extra synchronisation.
     */
    Set<UUID> fillerIdsFor(Player viewer) {
        Map<Integer, AppliedFiller> painted = appliedFillers.get(viewer.getUniqueId());
        if (painted == null || painted.isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = new java.util.HashSet<>(painted.size());
        for (Integer slot : painted.keySet()) {
            ids.add(fillerId(viewer.getUniqueId(), slot));
        }
        return ids;
    }

    /**
     * The deterministic ids the filler cells of {@code layout} <em>will</em> carry for {@code viewer}, computed from the
     * layout's slots without painting anything. The suppress mechanism primes its protected-id snapshot with these
     * <em>before</em> {@link #applyFillers} sends a single packet, so a filler painted into a viewer who is already
     * suppressed is protected the instant its {@code ADD_PLAYER} crosses the live interceptor, otherwise that first
     * paint would be force-unlisted, and the per-cell flicker guard would never repaint it, leaving the new filler
     * permanently hidden. An empty layout yields an empty set. Pure: same {@link #fillerId} derivation as the painted ids.
     */
    Set<UUID> plannedFillerIds(Player viewer, TablistLayout layout, Set<Integer> seatedSlots) {
        if (layout.isEmpty()) {
            return Set.of();
        }
        List<TablistFiller> planned = materializedFillers(layout);
        Set<UUID> ids = new java.util.HashSet<>(planned.size());
        for (TablistFiller filler : planned) {
            if (seatedSlots.contains(filler.slot())) {
                // A seated cell is the player's own entry; the renderer protects it by their uuid, not by a filler id.
                continue;
            }
            ids.add(fillerId(viewer.getUniqueId(), filler.slot()));
        }
        return ids;
    }

    /** Remove every filler entry this painter sent for {@code viewer}, and drop the viewer's filler tracking. */
    void clear(Player viewer) {
        Map<Integer, AppliedFiller> painted = appliedFillers.remove(viewer.getUniqueId());
        if (painted != null) {
            removeFillers(viewer, painted);
        }
    }

    /**
     * Drop the viewer's filler tracking on quit <em>without</em> sending a remove packet. The connection is closing, so
     * a remove packet to a dead channel is pointless; just forgetting the tracking lets a relog re-paint from scratch.
     */
    void forget(Player viewer) {
        appliedFillers.remove(viewer.getUniqueId());
    }

    /**
     * The deterministic, stable UUID for a (viewer, slot) filler cell, so a re-paint updates the same tab entry and a
     * removal targets it exactly, never a fresh random id per tick (which would leak a new fake row each refresh). The
     * id is derived from the viewer's id and the slot, so the same cell is always the same entry across ticks and relogs.
     */
    private static UUID fillerId(UUID viewer, int slot) {
        return UUID.nameUUIDFromBytes(("uxmf:" + viewer + ":" + slot).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The filler cell last painted for a (viewer, slot): its <em>rendered</em> text component, list order, resolved skin
     * (or {@code null} for no skin), and whether the skin was still resolving when the cell was painted. Two paints with
     * an equal tuple are the same cell, so the entry is not re-sent, the per-cell flicker guard. Keying on the rendered
     * component (not the raw source) means an animated or relational filler re-sends as its frame/placeholder changes
     * acceptable, mirroring the scoreboard/header animation surface, while a static filler on a steady-state tick
     * re-sends nothing. The {@code pending} flag distinguishes a cell painted skinless while its offline texture is still
     * being fetched from the same cell once resolved, so the next tick repaints rather than treating the skinless paint
     * as the steady state. {@link Component} has a value-based {@code equals}.
     */
    private record AppliedFiller(
            Component text, int order, @Nullable TabSkin skin, boolean pending) {}
}
