package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Slices a flat list of list-source entries into the slots a single rendered page can show. The content slots are
 * the cells a menu reserves for its scrollable list (everything else is fixed decoration), so the page size is
 * simply how many of those slots exist. Entries are laid into those slots in order, and a request for a page past
 * the end is clamped back to the last real page rather than rendering an empty screen. With no entries, or no
 * content slots at all. There is still exactly one page, just an empty one, which keeps the renderer's paging
 * controls consistent.
 */
public final class Pagination {

    private Pagination() {}

    /**
     * A single rendered page: the entry placed in each content slot, the clamped page index, and how many pages
     * the full entry list spans. A short final page leaves its trailing content slots unmapped.
     */
    public record Page<T>(List<Map.Entry<Integer, T>> placements, int page, int pageCount) {}

    /**
     * Computes the placements for one page of {@code entries} across {@code contentSlots}. An entry that implements
     * {@link PinnedEntry} and names a content slot is fixed to that slot on every page, outside the scrolling flow
     * (first entry wins a contested slot; an out-of-range slot flows like a normal entry). Everything else pages
     * across the remaining content slots: the page size is how many slots are left, and {@code page} is clamped into
     * {@code [0, pageCount - 1]}. An empty slot list yields a single empty page so callers never divide by zero.
     */
    public static <T> Page<T> paginate(List<T> entries, List<Integer> contentSlots, int page) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(contentSlots, "contentSlots");
        if (contentSlots.isEmpty()) {
            return new Page<>(List.of(), 0, 1);
        }
        Map<Integer, T> pinned = pin(entries, contentSlots);
        List<T> flowing = flowing(entries, contentSlots, pinned);
        List<Integer> flowSlots = remaining(contentSlots, pinned.keySet());
        int size = flowSlots.size();
        int pageCount = size == 0 ? 1 : Math.max(1, (flowing.size() + size - 1) / size);
        int clamped = Math.max(0, Math.min(page, pageCount - 1));
        List<Map.Entry<Integer, T>> placements = new ArrayList<>(pinned.entrySet());
        placements.addAll(place(flowing, flowSlots, size, clamped));
        return new Page<>(placements, clamped, pageCount);
    }

    /** Maps each pinned slot to the first entry that claims it; a contested or out-of-range slot is skipped. */
    private static <T> Map<Integer, T> pin(List<T> entries, List<Integer> contentSlots) {
        Map<Integer, T> pinned = new LinkedHashMap<>();
        for (T entry : entries) {
            if (claims(entry, contentSlots, pinned)) {
                pinned.put(((PinnedEntry) entry).pinnedSlot(), entry);
            }
        }
        return pinned;
    }

    /** The entries that page normally: everything that didn't win a pinned slot, order preserved. */
    private static <T> List<T> flowing(List<T> entries, List<Integer> contentSlots, Map<Integer, T> pinned) {
        Map<Integer, T> claimed = new LinkedHashMap<>();
        List<T> flowing = new ArrayList<>(entries.size() - pinned.size());
        for (T entry : entries) {
            if (claims(entry, contentSlots, claimed)) {
                claimed.put(((PinnedEntry) entry).pinnedSlot(), entry);
            } else {
                flowing.add(entry);
            }
        }
        return flowing;
    }

    /** Whether {@code entry} would win a free, in-range content slot given what is already {@code claimed}. */
    private static <T> boolean claims(T entry, List<Integer> contentSlots, Map<Integer, T> claimed) {
        return entry instanceof PinnedEntry pe
                && contentSlots.contains(pe.pinnedSlot())
                && !claimed.containsKey(pe.pinnedSlot());
    }

    /** The content slots left for the flow: {@code contentSlots} minus the claimed pinned slots, order preserved. */
    private static List<Integer> remaining(List<Integer> contentSlots, Set<Integer> claimed) {
        List<Integer> remaining = new ArrayList<>(contentSlots.size() - claimed.size());
        for (int slot : contentSlots) {
            if (!claimed.contains(slot)) {
                remaining.add(slot);
            }
        }
        return remaining;
    }

    private static <T> List<Map.Entry<Integer, T>> place(
            List<T> entries, List<Integer> contentSlots, int size, int page) {
        if (size == 0) {
            return List.of();
        }
        int from = page * size;
        int to = Math.min(from + size, entries.size());
        List<Map.Entry<Integer, T>> placements = new ArrayList<>(Math.max(0, to - from));
        for (int i = 0; from + i < to; i++) {
            placements.add(Map.entry(contentSlots.get(i), entries.get(from + i)));
        }
        return placements;
    }
}
