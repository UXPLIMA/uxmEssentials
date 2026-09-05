package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram page <name> remove <index>}: drop one page of a multi-page hologram (1-based at the command
 * boundary, 0-based here), save, and re-render. Removing down to a single page collapses the hologram back to an
 * ordinary one showing that page's lines. Rejected with {@link HologramError#NOT_FOUND} (no such hologram),
 * {@link HologramError#NOT_MULTI_PAGE} (the hologram has no pages to remove), or
 * {@link HologramError#PAGE_INDEX_OUT_OF_RANGE} (index outside the page range). The operator-only permission is
 * enforced at the command gate.
 */
public final class RemoveHologramPage {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public RemoveHologramPage(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Remove page {@code index} (0-based) of the hologram {@code name}. */
    public Result<Unit, HologramError> remove(PlayerRef actor, HologramName name, int index) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        if (!hologram.isMultiPage()) {
            notifier.send(actor, HologramError.NOT_MULTI_PAGE.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_MULTI_PAGE);
        }
        if (index < 0 || index >= hologram.pageCount()) {
            notifier.send(
                    actor,
                    HologramError.PAGE_INDEX_OUT_OF_RANGE.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index + 1)));
            return Result.err(HologramError.PAGE_INDEX_OUT_OF_RANGE);
        }
        Hologram updated = hologram.withPageRemoved(index);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_PAGE_REMOVED,
                Map.of("name", name.value(), "index", Integer.toString(index + 1)));
        return Result.ok();
    }
}
