package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnregistered;

/**
 * Removes a world from the registry while leaving its files on disk and any live world loaded. The
 * gate (NOT_FOUND) is synchronous; the metadata delete and {@link WorldUnregistered} publish run on
 * the {@code Scheduler}'s async executor, hopping back to the requester only to notify. With no Bukkit
 * handle op, this is the use case for which the off-tick split matters most: the whole tail is I/O.
 */
public final class UnregisterWorld {

    private final WorldRepository repository;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;

    public UnregisterWorld(
            WorldRepository repository, Notifier notifier, DomainEventPublisher events, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> unregister(PlayerRef who, WorldName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(who, WorldError.NOT_FOUND.messageKey(), Map.of("world", name.value()));
            return Result.err(WorldError.NOT_FOUND);
        }
        scheduler.async(() -> deleteOffTick(who, name));
        return Result.ok();
    }

    private void deleteOffTick(PlayerRef who, WorldName name) {
        repository.delete(name);
        events.publish(new WorldUnregistered(name));
        scheduler.onEntity(
                who, () -> notifier.send(who, WorldsMessageKey.WORLD_UNREGISTERED, Map.of("world", name.value())));
    }
}
