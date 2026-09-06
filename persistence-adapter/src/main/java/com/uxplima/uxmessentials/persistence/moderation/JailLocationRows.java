package com.uxplima.uxmessentials.persistence.moderation;

import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJailLocations;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationJailLocationsRecord;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code moderation_jail_locations} row and the domain
 * {@link StoredJail}. The location is the warps shape: the world uid as its canonical 36-character text plus
 * the operator-facing world name, the coordinates as doubles and the look angles as reals, so a stored jail
 * round-trips through {@link Position} the same way a warp does. This class is the single place that
 * translation lives.
 *
 * <p>The {@code defined_by} actor uuid and {@code created_at} timestamp are persistence metadata, not part of
 * the {@link StoredJail} value object, so {@link #apply} stamps them at save time and {@link #toStoredJail}
 * ignores them: the jail's identity is its name and the location is what a sanction needs.
 */
final class JailLocationRows {

    private static final ModerationJailLocations JAILS = ModerationJailLocations.MODERATION_JAIL_LOCATIONS;

    private JailLocationRows() {}

    /** Rebuild a {@link StoredJail} from a queried row. */
    static StoredJail toStoredJail(Record row) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(JAILS.WORLD)), row.get(JAILS.WORLD_NAME));
        Position location = new Position(
                world, row.get(JAILS.X), row.get(JAILS.Y), row.get(JAILS.Z), row.get(JAILS.YAW), row.get(JAILS.PITCH));
        return new StoredJail(row.get(JAILS.NAME), location);
    }

    /** Populate a record from a domain {@link StoredJail} for an upsert, stamping the save metadata. */
    static void apply(ModerationJailLocationsRecord record, StoredJail jail, long savedAtEpochMillis) {
        Position location = jail.location();
        record.setName(jail.name())
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setCreatedAt(savedAtEpochMillis);
    }
}
