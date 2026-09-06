package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram page <name> list}: show a hologram's page set, a header with the page count, then one entry
 * per page with its line count (1-based page numbers at the boundary). An ordinary single-page hologram lists
 * its one page. A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; all feedback
 * resolves through {@link HologramsMessageKey}.
 */
public final class ListHologramPages {

    private final HologramRepository repository;
    private final Notifier notifier;

    public ListHologramPages(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** List the pages of the hologram {@code name}, pushing the header and per-page entries. */
    public Result<Unit, HologramError> list(PlayerRef viewer, HologramName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_PAGE_LIST_HEADER,
                Map.of("name", name.value(), "count", Integer.toString(hologram.pageCount())));
        for (int page = 0; page < hologram.pageCount(); page++) {
            notifier.send(
                    viewer,
                    HologramsMessageKey.HOLOGRAM_PAGE_LIST_ENTRY,
                    Map.of(
                            "page",
                            Integer.toString(page + 1),
                            "lines",
                            Integer.toString(hologram.pageLines(page).size())));
        }
        return Result.ok();
    }
}
