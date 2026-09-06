package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.AutoUnloadPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnloaded;
import org.jspecify.annotations.NullMarked;

/**
 * The idle world auto-unload sweep. When enabled, a repeating global-region task wakes every configured
 * interval, looks at every loaded world, and unloads the ones that have sat empty at-or-past the configured
 * idle threshold: skipping the protected default world and any explicitly excluded world. The pure
 * {@link AutoUnloadPolicy#shouldUnload(int, Duration, Duration)} owns the decision; this adapter only feeds
 * it live counts and acts on the verdict.
 *
 * <p>The sweep is opt-in: when {@link WorldsSettings#autoUnloadEnabled()} is false {@link #start()} schedules
 * nothing and hands back a no-op closeable, so a disabled sweep holds zero runtime state.
 *
 * <p><b>Threading.</b> {@link #tick} runs on the global region thread (where unloading a world handle is
 * legal), so it is the only place that reads the idle map and calls into the engine. The map is a
 * {@link ConcurrentHashMap} purely as belt-and-suspenders. The sweep is single-threaded on that one thread,
 * so no entry is ever touched concurrently.
 */
@NullMarked
public final class WorldAutoUnloadSweep {

    private final Scheduler scheduler;
    private final WorldEngine engine;
    private final DomainEventPublisher events;
    private final WorldsSettings settings;
    private final Logger log;
    private final Clock clock;

    private final Map<WorldName, Instant> lastNonEmpty = new ConcurrentHashMap<>();

    public WorldAutoUnloadSweep(
            Scheduler scheduler,
            WorldEngine engine,
            DomainEventPublisher events,
            WorldsSettings settings,
            Logger log,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Start the sweep, returning the handle the module closes on stop to cancel the repeating task. When the
     * sweep is disabled nothing is scheduled and a no-op closeable is returned, so disabling it leaves no task
     * running. Otherwise the first sweep fires one interval in and then every interval.
     */
    public AutoCloseable start() {
        if (!settings.autoUnloadEnabled()) {
            return () -> {};
        }
        Duration interval = settings.autoUnloadSweepInterval();
        return scheduler.repeatGlobal(this::tick, interval, interval);
    }

    /** One sweep pass on the global region thread: refresh occupied worlds, unload those idle past the threshold. */
    void tick() {
        Instant now = clock.instant();
        Set<WorldName> loaded = engine.loadedWorldNames();
        Duration threshold = settings.autoUnloadIdle();
        Set<String> excluded = settings.autoUnloadExcluded();
        Optional<WorldName> def = engine.defaultWorldName();
        for (WorldName w : loaded) {
            if (skip(w, excluded, def)) {
                continue;
            }
            int players = engine.playerCount(w);
            if (players > 0) {
                lastNonEmpty.put(w, now);
                continue;
            }
            Instant since = lastNonEmpty.computeIfAbsent(w, k -> now);
            if (AutoUnloadPolicy.shouldUnload(players, Duration.between(since, now), threshold)) {
                unloadIdle(w);
            }
        }
        lastNonEmpty.keySet().retainAll(loaded); // drop worlds no longer loaded so the map stays bounded
    }

    /** Whether {@code w} is exempt from auto-unload: explicitly excluded, or the protected default world. */
    private boolean skip(WorldName w, Set<String> excluded, Optional<WorldName> def) {
        return excluded.contains(w.value())
                || (settings.protectDefaultWorld() && def.filter(w::equals).isPresent());
    }

    /** Unload an idle world (saving it first); on success drop its tracking and publish {@link WorldUnloaded}. */
    private void unloadIdle(WorldName w) {
        Result<Unit, WorldError> r = engine.unload(w, true);
        if (r.isOk()) {
            lastNonEmpty.remove(w);
            events.publish(new WorldUnloaded(w));
            log.info("auto-unloaded idle world {}", w.value());
        } else {
            log.warn("auto-unload of {} skipped: {}", w.value(), r.errorOrThrow());
        }
    }
}
