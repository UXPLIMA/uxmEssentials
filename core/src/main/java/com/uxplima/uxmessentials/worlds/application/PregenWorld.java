package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldPregen;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ChunkRegion;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The thin, pure front for {@code /worlds pregen}. It resolves and validates the request: the world must
 * exist, be loaded, and carry a positive radius, and no pre-generation may already be running for it, then
 * clamps the radius to {@link WorldsSettings#pregenMaxRadius()} and delegates to the {@link WorldPregen} port.
 *
 * <p>The use case notifies only the immediate command outcome (started, already-running, not-loaded,
 * not-found, cancelled, not-running). The asynchronous generation loop, its progress bar, and the
 * completion ("done") notification all belong to the engine behind the port, so this class never notifies
 * completion: doing so would double-notify the operator.
 */
@NullMarked
public final class PregenWorld {

    private final WorldRepository repository;
    private final WorldEngine engine;
    private final WorldPregen pregen;
    private final WorldsSettings settings;
    private final Notifier notifier;
    private final Scheduler scheduler;

    public PregenWorld(
            WorldRepository repository,
            WorldEngine engine,
            WorldPregen pregen,
            WorldsSettings settings,
            Notifier notifier,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.pregen = Objects.requireNonNull(pregen, "pregen");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Validate the request, clamp the radius, and hand the generation off to the engine behind the port. */
    public Result<Unit, WorldError> start(PlayerRef who, WorldName world, int radius) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        if (repository.find(world).isEmpty()) {
            return fail(who, WorldsMessageKey.WORLD_NOT_FOUND, world, WorldError.NOT_FOUND);
        }
        if (!engine.isLoaded(world)) {
            return fail(who, WorldsMessageKey.WORLD_NOT_LOADED, world, WorldError.NOT_LOADED);
        }
        if (radius <= 0) {
            notify(who, WorldsMessageKey.WORLD_SETTING_INVALID_VALUE, Map.of());
            return Result.err(WorldError.SETTING_INVALID_VALUE);
        }
        if (pregen.isRunning(world)) {
            return fail(who, WorldsMessageKey.WORLD_PREGEN_ALREADY_RUNNING, world, WorldError.PREGEN_ALREADY_RUNNING);
        }
        int clamped = Math.min(radius, settings.pregenMaxRadius());
        Result<Unit, WorldError> result = pregen.start(who, world, clamped);
        if (result.isOk()) {
            long chunks = new ChunkRegion(0, 0, clamped).totalChunks();
            notify(
                    who,
                    WorldsMessageKey.WORLD_PREGEN_STARTED,
                    Map.of("world", world.value(), "chunks", String.valueOf(chunks)));
        }
        return result;
    }

    /** Cancel the active pre-generation of {@code world}, reporting whether one was running to cancel. */
    public Result<Unit, WorldError> cancel(PlayerRef who, WorldName world) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        if (pregen.cancel(world)) {
            notify(who, WorldsMessageKey.WORLD_PREGEN_CANCELLED, Map.of("world", world.value()));
            return Result.ok();
        }
        return fail(who, WorldsMessageKey.WORLD_PREGEN_NOT_RUNNING, world, WorldError.PREGEN_NOT_RUNNING);
    }

    private Result<Unit, WorldError> fail(PlayerRef who, MessageKey key, WorldName world, WorldError error) {
        notify(who, key, Map.of("world", world.value()));
        return Result.err(error);
    }

    /** Folia-safe notify: bounce the delivery back onto the recipient's region thread. */
    private void notify(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(who, () -> notifier.send(who, key, placeholders));
    }
}
