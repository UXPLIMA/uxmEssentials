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
 * {@code /hologram center <name>}: re-anchor a hologram to the centre of its current block on the horizontal
 * plane ({@code x → floor + 0.5}, {@code z → floor + 0.5}), keeping its Y and look direction, the
 * {@code center} convenience. Save the new snapshot and re-render the live entity. A name no
 * hologram exists at is rejected with {@link HologramError#NOT_FOUND}. The operator-only permission is enforced
 * at the adapter gate.
 */
public final class CenterHologram {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public CenterHologram(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Snap the hologram {@code name} to the centre of its block, or reject when no such hologram exists. */
    public Result<Unit, HologramError> center(PlayerRef actor, HologramName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        Hologram centred = hologram.movedTo(hologram.location().atBlockCenter());
        repository.save(centred);
        view.render(centred);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_CENTERED, Map.of("name", name.value()));
        return Result.ok();
    }
}
