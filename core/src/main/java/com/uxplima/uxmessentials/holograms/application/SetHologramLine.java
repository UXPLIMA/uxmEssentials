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
 * {@code /hologram setline <name> <index> <text…>}: replace one existing line of a hologram (1-based at the
 * command boundary, 0-based here), save the new snapshot, and re-render the live entity. A name no hologram
 * exists at is rejected with {@link HologramError#NOT_FOUND}; an index outside the hologram's line range with
 * {@link HologramError#LINE_INDEX_OUT_OF_RANGE}. The operator-only permission is enforced at the command gate.
 */
public final class SetHologramLine {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public SetHologramLine(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Replace line {@code index} (0-based) of the hologram {@code name} with {@code line}. */
    public Result<Unit, HologramError> set(PlayerRef actor, HologramName name, int index, HologramLine line) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(line, "line");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        Hologram hologram = existing.get();
        if (index < 0 || index >= hologram.lineCount()) {
            notifier.send(
                    actor,
                    HologramError.LINE_INDEX_OUT_OF_RANGE.messageKey(),
                    Map.of("name", name.value(), "index", Integer.toString(index + 1)));
            return Result.err(HologramError.LINE_INDEX_OUT_OF_RANGE);
        }
        Hologram updated = hologram.withLineReplaced(index, line);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_LINE_SET,
                Map.of("name", name.value(), "index", Integer.toString(index + 1)));
        return Result.ok();
    }
}
