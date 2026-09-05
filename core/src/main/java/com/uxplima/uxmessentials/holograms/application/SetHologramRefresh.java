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
 * {@code /hologram refreshinterval <name> <ticks>}: set how often a hologram re-renders so its lines pick up
 * fresh placeholder values, save the new snapshot, and re-render once now. A value of 0 makes the hologram
 * static (rendered once, never again). A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}; the operator-only permission is enforced at the command gate, and a
 * negative tick count is clamped to 0 at the command boundary.
 */
public final class SetHologramRefresh {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public SetHologramRefresh(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set the hologram {@code name}'s refresh interval to {@code ticks}, or reject an unknown name. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, int ticks) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withRefreshIntervalTicks(Math.max(0, ticks));
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_REFRESH_SET,
                Map.of("name", name.value(), "ticks", Integer.toString(updated.refreshIntervalTicks())));
        return Result.ok();
    }
}
