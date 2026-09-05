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
 * {@code /hologram removeline <name> <index>}: drop one line of a hologram (1-based at the command boundary,
 * 0-based here), save the new snapshot, and re-render the live entity. A name no hologram exists at is
 * rejected with {@link HologramError#NOT_FOUND}; an index outside the line range with
 * {@link HologramError#LINE_INDEX_OUT_OF_RANGE}; and a removal that would empty the hologram with
 * {@link HologramError#WOULD_BE_EMPTY} — a hologram must keep at least one line, so the operator deletes the
 * whole hologram instead. The operator-only permission is enforced at the command gate.
 */
public final class RemoveHologramLine {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;

    public RemoveHologramLine(HologramRepository repository, HologramView view, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Remove line {@code index} (0-based) of the hologram {@code name}, keeping at least one line. */
    public Result<Unit, HologramError> remove(PlayerRef actor, HologramName name, int index) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
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
        if (hologram.lineCount() == 1) {
            notifier.send(actor, HologramError.WOULD_BE_EMPTY.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.WOULD_BE_EMPTY);
        }
        Hologram updated = hologram.withLineRemoved(index);
        repository.save(updated);
        view.render(updated);
        notifier.send(
                actor,
                HologramsMessageKey.HOLOGRAM_LINE_REMOVED,
                Map.of("name", name.value(), "index", Integer.toString(index + 1)));
        return Result.ok();
    }
}
