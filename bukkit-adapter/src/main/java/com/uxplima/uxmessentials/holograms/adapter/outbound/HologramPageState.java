package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The per-viewer current page of each multi-page hologram. Transient runtime state, never persisted, so a
 * viewer starts on page 0 each session. Keyed by hologram name then viewer uuid and mutated through
 * {@link ConcurrentHashMap}'s atomic compute, so a click advancing one viewer's page never races the render
 * loop reading it off another thread. A stored page is always read back clamped into the hologram's live page
 * range, so a page that shrank (a page was removed) never resolves out of range. Cleared per hologram when it is
 * despawned and wholesale on module stop, so a removed hologram leaves no stale page entries.
 */
@NullMarked
public final class HologramPageState {

    private final Map<String, Map<UUID, Integer>> pages = new ConcurrentHashMap<>();

    /** The viewer's current page of hologram {@code name}, wrapped into {@code [0, pageCount)}; 0 by default. */
    int currentPage(String name, UUID viewer, int pageCount) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        if (pageCount <= 1) {
            return 0;
        }
        Integer page = pages.getOrDefault(name, Map.of()).get(viewer);
        return page == null ? 0 : Math.floorMod(page, pageCount);
    }

    /** Advance the viewer to the next page of hologram {@code name} (wrapping), returning the new page. */
    int advance(String name, UUID viewer, int pageCount) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        if (pageCount <= 1) {
            return 0;
        }
        Map<UUID, Integer> byViewer = pages.computeIfAbsent(name, key -> new ConcurrentHashMap<>());
        return byViewer.compute(
                viewer, (key, current) -> Math.floorMod((current == null ? 0 : current) + 1, pageCount));
    }

    /** Forget hologram {@code name}'s per-viewer pages: called when it is despawned. */
    void clear(String name) {
        pages.remove(Objects.requireNonNull(name, "name"));
    }

    /** Forget every hologram's per-viewer pages, called on module stop. */
    void clearAll() {
        pages.clear();
    }
}
