package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.PendingRestoreRegistry;
import com.uxplima.uxmessentials.worlds.application.port.WorldArchive;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.PendingRestore;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Two-phase world restore. {@code request} validates (managed, not the default world, and the named
 * backup exists) and stages a confirmation without touching the world; {@code confirm} consumes the
 * staged restore and hands off to {@link WorldArchive#restore}, which evacuates players, replaces the
 * folder, and reloads off-tick before firing the {@code WORLD_RESTORED} / {@code WORLD_RESTORE_FAILED}
 * completion notification. The default world is protected: it cannot be unloaded to swap its folder.
 */
public final class RestoreWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldArchive archive;
    private final PendingRestoreRegistry pending;
    private final Notifier notifier;
    private final Scheduler scheduler;

    public RestoreWorld(
            WorldRepository repository,
            WorldEngine engine,
            WorldArchive archive,
            PendingRestoreRegistry pending,
            Notifier notifier,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.archive = Objects.requireNonNull(archive, "archive");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Result<Unit, WorldError> request(PlayerRef who, WorldName world, BackupId id) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        if (!repository.exists(world)) {
            return fail(who, WorldsMessageKey.WORLD_NOT_FOUND, Map.of("world", world.value()), WorldError.NOT_FOUND);
        }
        if (engine.defaultWorldName().filter(world::equals).isPresent()) {
            return fail(who, WorldsMessageKey.WORLD_PROTECTED, Map.of("world", world.value()), WorldError.IS_PROTECTED);
        }
        if (archive.list(world).stream().noneMatch(b -> b.id().equals(id))) {
            return fail(
                    who,
                    WorldsMessageKey.WORLD_BACKUP_NOT_FOUND,
                    Map.of("backup", id.value()),
                    WorldError.BACKUP_NOT_FOUND);
        }
        pending.stage(new PendingRestore(world, id, who.uuid()));
        notify(who, WorldsMessageKey.WORLD_RESTORE_CONFIRM, Map.of("world", world.value(), "backup", id.value()));
        return Result.ok();
    }

    public Result<Unit, WorldError> confirm(PlayerRef who, WorldName world) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        Optional<PendingRestore> p = pending.take(world, who.uuid());
        if (p.isEmpty()) {
            return fail(
                    who,
                    WorldsMessageKey.WORLD_RESTORE_NONE_PENDING,
                    Map.of("world", world.value()),
                    WorldError.RESTORE_NONE_PENDING);
        }
        return archive.restore(who, world, p.get().id());
    }

    private Result<Unit, WorldError> fail(
            PlayerRef who, MessageKey key, Map<String, String> placeholders, WorldError error) {
        notify(who, key, placeholders);
        return Result.err(error);
    }

    /** Folia-safe notify: bounce the delivery back onto the recipient's region thread. */
    private void notify(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(who, () -> notifier.send(who, key, placeholders));
    }
}
