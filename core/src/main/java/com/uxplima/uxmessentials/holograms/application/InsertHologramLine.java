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
 * {@code /hologram insertline <name> <index> <text…>}: insert a text line <em>before</em> a 1-based position
 * (0-based here), save the new snapshot, and re-render the live entity. An index at or past the current line
 * count appends, like {@code addline}; the only modelled failure is a name no hologram exists at, rejected with
 * {@link HologramError#NOT_FOUND} (a negative index cannot reach here: the command floors it at 1). The
 * operator-only permission is enforced at the command gate.
 */
public final class InsertHologramLine {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public InsertHologramLine(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Insert {@code line} before line {@code index} (0-based) of the hologram {@code name}; append on overflow. */
    public Result<Unit, HologramError> insert(PlayerRef actor, HologramName name, int index, HologramLine line) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram updated = existing.get().withLineInserted(index, line);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_LINE_INSERTED,
                Map.of("name", name.value(), "index", Integer.toString(index + 1)));
        return Result.ok();
    }
}
