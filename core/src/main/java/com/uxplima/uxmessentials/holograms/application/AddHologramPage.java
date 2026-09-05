package com.uxplima.uxmessentials.holograms.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramPage;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram page <name> add <text…>}: append a new page (one or more lines) to a text hologram, save the
 * new snapshot, and re-render. The first {@code add} promotes an ordinary hologram to multi-page — its current
 * lines become page 0 and the new lines become page 1. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}; a non-text hologram (item/block/head/entity) with
 * {@link HologramError#NOT_TEXT_HOLOGRAM}, since only text holograms render paged lines. The operator-only
 * permission is enforced at the command gate.
 */
public final class AddHologramPage {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public AddHologramPage(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Append a page of {@code lines} (at least one) to the hologram {@code name}. */
    public Result<Unit, HologramError> add(PlayerRef actor, HologramName name, List<HologramLine> lines) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lines, "lines");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        if (hologram.type() != HologramType.TEXT) {
            notifier.send(actor, HologramError.NOT_TEXT_HOLOGRAM.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_TEXT_HOLOGRAM);
        }
        Hologram updated = hologram.withPageAppended(HologramPage.of(lines));
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_PAGE_ADDED,
                Map.of("name", name.value(), "page", Integer.toString(updated.pageCount())));
        return Result.ok();
    }
}
