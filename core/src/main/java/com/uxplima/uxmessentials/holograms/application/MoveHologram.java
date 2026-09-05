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
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram movehere <name>}: re-anchor an existing hologram to the operator's current position, save
 * the new snapshot, and re-render the live entity at its new location. A name no hologram exists at is
 * rejected with {@link HologramError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class MoveHologram {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public MoveHologram(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Re-anchor the hologram {@code name} to {@code at}, or reject when no such hologram exists. */
    public Result<Unit, HologramError> move(PlayerRef actor, HologramName name, Position at) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram moved = existing.get().movedTo(at);
        repository.save(moved);
        view.render(moved);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_MOVED, Map.of("name", name.value()));
        return Result.ok();
    }
}
