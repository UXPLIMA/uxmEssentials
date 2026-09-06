package com.uxplima.uxmessentials.worlds.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.PendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.PendingDeletion;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldDeleted;

/**
 * Two-phase world deletion. {@code request} validates and stages a confirmation (no destruction);
 * {@code confirm} consumes the staged deletion, re-validates from scratch (never trusting the staged
 * value), unloads the world if loaded, deletes its files, drops the metadata row, and publishes
 * {@link WorldDeleted}. The default world is protected; the recursive file delete and the metadata
 * drop run off-tick on the {@code Scheduler}'s async executor, hopping back to the requester only to
 * notify: the synchronous gate (unload + protect-default check) stays on the calling global thread.
 */
public final class DeleteWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final PendingDeletionRegistry pending;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;
    private final Clock clock;
    private final BooleanSupplier protectDefault;

    public DeleteWorld(
            WorldRepository repository,
            WorldEngine engine,
            PendingDeletionRegistry pending,
            Notifier notifier,
            DomainEventPublisher events,
            Scheduler scheduler,
            Clock clock,
            BooleanSupplier protectDefault) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.protectDefault = Objects.requireNonNull(protectDefault, "protectDefault");
    }

    public Result<Unit, WorldError> request(PlayerRef who, WorldName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Optional<WorldError> gate = validate(name);
        if (gate.isPresent()) {
            notifier.send(who, gate.get().messageKey(), Map.of("world", name.value()));
            return Result.err(gate.get());
        }
        pending.stage(new PendingDeletion(name, who.uuid(), clock.instant()));
        notifier.send(who, WorldsMessageKey.WORLD_DELETE_CONFIRM, Map.of("world", name.value()));
        return Result.ok();
    }

    public Result<Unit, WorldError> confirm(PlayerRef who, WorldName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        if (pending.take(name, who.uuid()).isEmpty()) {
            notifier.send(who, WorldsMessageKey.WORLD_DELETE_NONE, Map.of("world", name.value()));
            return Result.err(WorldError.NOT_FOUND);
        }
        Optional<WorldError> gate = validate(name);
        if (gate.isPresent()) {
            notifier.send(who, gate.get().messageKey(), Map.of("world", name.value()));
            return Result.err(gate.get());
        }
        if (engine.isLoaded(name)) {
            Result<Unit, WorldError> unloaded = engine.unload(name, false);
            if (unloaded.isErr()) {
                notifier.send(who, unloaded.errorOrThrow().messageKey(), Map.of("world", name.value()));
                return unloaded;
            }
        }
        scheduler.async(() -> deleteOffTick(who, name));
        return Result.ok();
    }

    private void deleteOffTick(PlayerRef who, WorldName name) {
        Result<Unit, WorldError> deleted = engine.deleteFiles(name);
        if (deleted.isErr()) {
            WorldError error = deleted.errorOrThrow();
            scheduler.onEntity(who, () -> notifier.send(who, error.messageKey(), Map.of("world", name.value())));
            return;
        }
        repository.delete(name);
        events.publish(new WorldDeleted(name));
        scheduler.onEntity(
                who, () -> notifier.send(who, WorldsMessageKey.WORLD_DELETED, Map.of("world", name.value())));
    }

    private Optional<WorldError> validate(WorldName name) {
        if (!repository.exists(name) && !engine.exists(name)) {
            return Optional.of(WorldError.NOT_FOUND);
        }
        if (protectDefault.getAsBoolean()
                && engine.defaultWorldName().map(name::equals).orElse(false)) {
            return Optional.of(WorldError.IS_PROTECTED);
        }
        return Optional.empty();
    }
}
