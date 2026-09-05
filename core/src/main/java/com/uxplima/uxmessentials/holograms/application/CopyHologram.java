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
 * {@code /hologram copy <src> <dest>}: duplicate an existing hologram under a new name at the same location,
 * carrying every property (lines, model, appearance, visibility, rotation, refresh interval, type). A {@code
 * src} no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; a {@code dest} a hologram already
 * exists under with {@link HologramError#NAME_TAKEN} (so a copy never overwrites). The new hologram is stored
 * and its live entity spawned. The operator-only permission is enforced at the command gate.
 */
public final class CopyHologram {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public CopyHologram(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Duplicate {@code source} into a new hologram {@code destination}, or reject a missing src / taken dest. */
    public Result<Unit, HologramError> copy(PlayerRef actor, HologramName source, HologramName destination) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Optional<Hologram> existing = repository.find(source);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", source.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        if (repository.exists(destination)) {
            notifier.send(actor, HologramError.NAME_TAKEN.messageKey(), Map.of("name", destination.value()));
            return Result.err(HologramError.NAME_TAKEN);
        }
        Hologram clone = existing.get().renamedTo(destination);
        repository.save(clone);
        view.render(clone);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_COPIED,
                Map.of("name", source.value(), "copy", destination.value()));
        return Result.ok();
    }
}
