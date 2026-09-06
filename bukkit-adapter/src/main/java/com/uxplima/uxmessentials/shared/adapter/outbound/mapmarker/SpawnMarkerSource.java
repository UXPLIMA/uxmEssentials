package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerKind;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSource;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import org.jspecify.annotations.NullMarked;

/**
 * The spawn {@link MapMarkerSource}: the operator-set spawn of each loaded world ({@code /setspawn}) plus the
 * single global main spawn ({@code /setmainspawn}). Only operator-set spawns render, the bottom-of-chain
 * vanilla world spawn is not a configured point and is left off the map. The per-world spawn marker is named
 * {@code main} for the global spawn and the world name otherwise, so each renders under a stable id.
 *
 * <p>Read on refresh (enable / reload) off the main thread, since {@link SpawnDirectory#operatorSpawn} and
 * {@link SpawnDirectory#mainSpawn} touch the (cached) database.
 */
@NullMarked
final class SpawnMarkerSource implements MapMarkerSource {

    private static final String MAIN_SPAWN_NAME = "main";

    private final Server server;
    private final SpawnDirectory spawns;
    private final MapMarkerSettings settings;

    SpawnMarkerSource(Server server, SpawnDirectory spawns, MapMarkerSettings settings) {
        this.server = Objects.requireNonNull(server, "server");
        this.spawns = Objects.requireNonNull(spawns, "spawns");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public MapMarkerKind kind() {
        return MapMarkerKind.SPAWN;
    }

    @Override
    public List<MapMarker> currentMarkers() {
        List<MapMarker> markers = new ArrayList<>();
        spawns.mainSpawn().ifPresent(at -> markers.add(marker(MAIN_SPAWN_NAME, at)));
        for (World world : server.getWorlds()) {
            WorldRef ref = BukkitRefs.toRef(world);
            spawns.operatorSpawn(ref).ifPresent(at -> markers.add(marker(world.getName(), at)));
        }
        return markers;
    }

    private MapMarker marker(String name, Position at) {
        return MapMarker.of(
                MapMarkerKind.SPAWN, name, settings.tooltipFor(name), at.world().name(), at.x(), at.y(), at.z());
    }
}
