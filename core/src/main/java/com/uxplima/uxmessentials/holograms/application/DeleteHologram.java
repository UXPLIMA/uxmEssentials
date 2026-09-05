package com.uxplima.uxmessentials.holograms.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.event.HologramDeleted;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram delete <name>}: remove a server-wide hologram, despawn its live entity, and free its name
 * for reuse. A name no hologram exists at is rejected with {@link HologramError#NOT_FOUND}; a successful
 * delete removes the rows, despawns the entity, and publishes {@code HologramDeleted} attributed to the staff
 * member who ran the command. The operator-only permission is enforced at the command gate.
 */
public final class DeleteHologram {

    private final HologramRepository repository;
    private final HologramView view;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public DeleteHologram(
            HologramRepository repository, HologramView view, Notifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Delete the hologram {@code name}, or reject when no such hologram exists. */
    public Result<Unit, HologramError> delete(PlayerRef actor, HologramName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(actor, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        repository.delete(name);
        view.despawn(name);
        events.publish(new HologramDeleted(name, actor));
        notifier.send(actor, HologramsMessageKey.HOLOGRAM_DELETED, Map.of("name", name.value()));
        return Result.ok();
    }
}
