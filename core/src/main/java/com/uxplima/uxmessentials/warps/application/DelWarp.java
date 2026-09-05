package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleted;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleting;

/**
 * {@code /delwarp <name>}: remove a server-wide warp, freeing its name for reuse. A name no warp exists at
 * is rejected with {@link WarpError#NOT_FOUND}; a successful delete removes the row and publishes
 * {@code WarpDeleted} attributed to the staff member who ran the command. The operator-only permission is
 * enforced at the command gate.
 */
public final class DelWarp {

    private final WarpRepository repository;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;

    /** The use case with nothing outside the plugin able to refuse a delete. The form the pure tests use. */
    public DelWarp(WarpRepository repository, Notifier notifier, DomainEventPublisher events) {
        this(repository, notifier, events, DomainGate.allowAll());
    }

    public DelWarp(WarpRepository repository, Notifier notifier, DomainEventPublisher events, DomainGate gate) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    /** Delete the warp {@code name}, or reject when no such warp exists. */
    public Result<Unit, WarpError> delete(PlayerRef actor, WarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        if (!gate.allows(new WarpDeleting(name, actor))) {
            notifier.send(actor, WarpError.VETOED.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.VETOED);
        }
        repository.delete(name);
        events.publish(new WarpDeleted(name, actor));
        notifier.send(actor, WarpsMessageKey.WARP_DELETED, Map.of("warp", name.value()));
        return Result.ok();
    }
}
