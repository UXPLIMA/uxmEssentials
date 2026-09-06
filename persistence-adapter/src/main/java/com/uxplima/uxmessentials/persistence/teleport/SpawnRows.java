package com.uxplima.uxmessentials.persistence.teleport;

import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportMainSpawnRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportNamedSpawnsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportSpawnMirrorsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.TeleportSpawnsRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;

/**
 * The anti-corruption mapping between a spawn row and a domain {@link Position} (or {@link SpawnMirror}). The
 * location is the warps shape verbatim: the world uid as canonical 36-character text plus the operator-facing
 * world name, the coordinates as doubles and the look angles as reals, so a stored spawn round-trips through
 * {@link Position} the same way a warp does. This class is the single place that translation lives for the
 * per-world, main, and named spawn tables and the mirror table.
 *
 * <p>{@code created_at} is persistence metadata stamped at save time, not part of the {@link Position} value
 * object, so the apply helpers write it and the readers ignore it.
 */
final class SpawnRows {

    private SpawnRows() {}

    /** Rebuild a {@link Position} from a per-world spawn row. */
    static Position toPosition(TeleportSpawnsRecord row) {
        WorldRef world = new WorldRef(UUID.fromString(row.getWorld()), row.getWorldName());
        return new Position(world, row.getX(), row.getY(), row.getZ(), row.getYaw(), row.getPitch());
    }

    /** Rebuild a {@link Position} from the singleton main-spawn row. */
    static Position toPosition(TeleportMainSpawnRecord row) {
        WorldRef world = new WorldRef(UUID.fromString(row.getWorld()), row.getWorldName());
        return new Position(world, row.getX(), row.getY(), row.getZ(), row.getYaw(), row.getPitch());
    }

    /** Rebuild a {@link Position} from a named-spawn row. */
    static Position toPosition(TeleportNamedSpawnsRecord row) {
        WorldRef world = new WorldRef(UUID.fromString(row.getWorld()), row.getWorldName());
        return new Position(world, row.getX(), row.getY(), row.getZ(), row.getYaw(), row.getPitch());
    }

    /** Rebuild a {@link SpawnMirror} from a mirror row. */
    static SpawnMirror toMirror(TeleportSpawnMirrorsRecord row) {
        return new SpawnMirror(UUID.fromString(row.getSourceWorld()), UUID.fromString(row.getTargetWorld()));
    }

    /** Populate a per-world spawn record for an upsert, stamping the save metadata. */
    static void apply(TeleportSpawnsRecord record, Position spawn, long savedAtEpochMillis) {
        record.setWorld(spawn.world().uid().toString())
                .setWorldName(spawn.world().name())
                .setX(spawn.x())
                .setY(spawn.y())
                .setZ(spawn.z())
                .setYaw(spawn.yaw())
                .setPitch(spawn.pitch())
                .setCreatedAt(savedAtEpochMillis);
    }

    /** Populate the singleton main-spawn record (id 0) for an upsert, stamping the save metadata. */
    static void applyMain(TeleportMainSpawnRecord record, Position spawn, long savedAtEpochMillis) {
        record.setId(0)
                .setWorld(spawn.world().uid().toString())
                .setWorldName(spawn.world().name())
                .setX(spawn.x())
                .setY(spawn.y())
                .setZ(spawn.z())
                .setYaw(spawn.yaw())
                .setPitch(spawn.pitch())
                .setCreatedAt(savedAtEpochMillis);
    }

    /** Populate a named-spawn record for an upsert, stamping the save metadata. */
    static void applyNamed(TeleportNamedSpawnsRecord record, String name, Position spawn, long savedAtEpochMillis) {
        record.setName(name)
                .setWorld(spawn.world().uid().toString())
                .setWorldName(spawn.world().name())
                .setX(spawn.x())
                .setY(spawn.y())
                .setZ(spawn.z())
                .setYaw(spawn.yaw())
                .setPitch(spawn.pitch())
                .setCreatedAt(savedAtEpochMillis);
    }

    /** Populate a mirror record for an upsert, stamping the save metadata. */
    static void applyMirror(TeleportSpawnMirrorsRecord record, SpawnMirror mirror, long savedAtEpochMillis) {
        record.setSourceWorld(mirror.sourceWorld().toString())
                .setTargetWorld(mirror.targetWorld().toString())
                .setCreatedAt(savedAtEpochMillis);
    }
}
