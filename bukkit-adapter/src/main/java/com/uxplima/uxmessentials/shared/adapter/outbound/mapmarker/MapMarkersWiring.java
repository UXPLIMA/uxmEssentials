package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Server;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.teleport.SpawnDirectories;
import com.uxplima.uxmessentials.persistence.warps.WarpRepositories;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerService;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSource;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Wires the map-plugin marker integration: a cross-cutting feature owned by no single context (it spans the
 * server's warps and spawns), so it is wired here in the bootstrap surface rather than as a {@code
 * FeatureModule}. It does nothing when {@code map-markers.enabled} is false or when neither Dynmap nor
 * squaremap is installed. In either case the discoverer hands back the no-op publisher and no source is
 * read, so the integration holds no runtime state.
 *
 * <p>When a map plugin is present the wiring builds the {@link MapMarkerService} over read-side warp/spawn
 * sources, subscribes it to the in-process domain-event bus so a {@code /setwarp}//{@code /delwarp} (and an
 * opt-in {@code /sethome}//{@code /delhome}) updates exactly that marker, and refreshes the whole layer off
 * the tick on enable. On disable it unsubscribes and clears the layer so a reload re-renders cleanly.
 */
@NullMarked
public final class MapMarkersWiring {

    private MapMarkersWiring() {}

    /**
     * Build and start the integration, returning the disable hook (unsubscribe + clear) or an inert hook when
     * nothing was wired.
     */
    public static Stopped wire(
            Server server,
            ConfigStore config,
            Persistence persistence,
            Scheduler scheduler,
            InProcessDomainEventPublisher events,
            Logger log) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(log, "log");

        MapMarkerSettings settings = MapMarkerSettings.from(config);
        if (!settings.enabled() || !MapMarkerPublishers.anyPresent(server)) {
            return Stopped.inert();
        }
        return start(server, persistence, scheduler, events, log, settings);
    }

    private static Stopped start(
            Server server,
            Persistence persistence,
            Scheduler scheduler,
            InProcessDomainEventPublisher events,
            Logger log,
            MapMarkerSettings settings) {
        MapMarkerPublisher publisher = MapMarkerPublishers.discover(server, settings, log);
        MapMarkerService service = new MapMarkerService(publisher, settings, sources(server, persistence, settings));
        Consumer<DomainEvent> subscriber = event -> scheduler.async(() -> service.onEvent(event));
        events.subscribe(subscriber);
        scheduler.async(service::refreshAll);
        log.info("event=map_markers_wired layer={}", settings.layerName());
        return new Stopped(() -> {
            events.unsubscribe(subscriber);
            publisher.clear();
        });
    }

    private static List<MapMarkerSource> sources(Server server, Persistence persistence, MapMarkerSettings settings) {
        // Only warps and spawns have an enumerable source for the on-enable refresh. Homes have no
        // all-owners list (and are private), so they populate only via the opt-in HomeCreated/HomeDeleted
        // events the service handles directly: never bulk-rendered.
        return List.of(
                new WarpMarkerSource(WarpRepositories.cached(persistence), settings),
                new SpawnMarkerSource(server, SpawnDirectories.jooq(persistence), settings));
    }

    /** The integration's disable hook: unsubscribe from the bus and clear the rendered layer. */
    public record Stopped(Runnable stop) {
        public Stopped {
            Objects.requireNonNull(stop, "stop");
        }

        static Stopped inert() {
            return new Stopped(() -> {});
        }
    }
}
