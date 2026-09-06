package com.uxplima.uxmessentials.holograms.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /hologram list}: list every server-wide hologram in stored creation order. Holograms are an
 * operator surface (the command is gated as a whole), so the list is unfiltered: every hologram is shown.
 * The holograms are returned for the adapter to render, and the header / per-entry / empty feedback is
 * pushed through the notifier so all text resolves from {@link HologramsMessageKey}.
 */
public final class ListHolograms {

    private final HologramRepository repository;
    private final Notifier notifier;

    public ListHolograms(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** The holograms in stored creation order, also pushing the header/entries (or the empty notice). */
    public List<Hologram> list(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<Hologram> all = repository.all();
        if (all.isEmpty()) {
            notifier.send(viewer, HologramsMessageKey.HOLOGRAM_LIST_EMPTY);
            return all;
        }
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_LIST_HEADER, Map.of("count", Integer.toString(all.size())));
        for (Hologram hologram : all) {
            notifier.send(
                    viewer,
                    HologramsMessageKey.HOLOGRAM_LIST_ENTRY,
                    Map.of("name", hologram.name().value(), "lines", Integer.toString(hologram.lineCount())));
        }
        return all;
    }
}
