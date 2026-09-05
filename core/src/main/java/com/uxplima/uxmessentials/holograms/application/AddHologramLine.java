package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram addline <name> <text…>}: append a text line to an existing hologram, save the new
 * snapshot, and re-render the live entity. A name no hologram exists at is rejected with
 * {@link HologramError#NOT_FOUND}. The operator-only permission is enforced at the command gate.
 */
public final class AddHologramLine {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public AddHologramLine(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Append {@code line} to the hologram {@code name}, or reject when no such hologram exists. */
    public Result<Unit, HologramError> add(PlayerRef actor, HologramName name, HologramLine line) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withLineAppended(line);
        repository.save(updated);
        view.render(updated);
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_LINE_ADDED, Map.of("name", name.value()));
        return Result.ok();
    }
}
