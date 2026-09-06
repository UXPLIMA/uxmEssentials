package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.HomesRecord;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.Record;

/**
 * The anti-corruption mapping between a {@code homes} row and the domain {@link Home}. UUIDs are stored as
 * their canonical 36-character text and timestamps as epoch milliseconds, so the column shape is identical
 * on every backend. Identity is the slot, not a name; the optional label and icon are nullable columns
 * that map to {@link Optional} in the domain.
 *
 * <p>The owner name is not persisted (only the uuid is), so a {@link Home} rebuilt from a row carries the
 * owner uuid with the name the caller already holds. The repository passes the queried {@link PlayerRef}
 * through rather than inventing a display name from the row.
 */
final class HomeRows {

    private HomeRows() {}

    /** Rebuild a {@link Home} from a queried row, attributing it to the already-resolved {@code owner}. */
    static Home toHome(Record row, PlayerRef owner) {
        WorldRef world = new WorldRef(UUID.fromString(row.get(HOMES.WORLD)), row.get(HOMES.WORLD_NAME));
        Position position = new Position(
                world, row.get(HOMES.X), row.get(HOMES.Y), row.get(HOMES.Z), row.get(HOMES.YAW), row.get(HOMES.PITCH));

        String labelRaw = row.get(HOMES.LABEL);
        Optional<HomeLabel> label = labelRaw == null ? Optional.empty() : Optional.of(HomeLabel.of(labelRaw));

        String iconRaw = row.get(HOMES.ICON);
        Optional<HomeIcon> icon = iconRaw == null ? Optional.empty() : Optional.of(HomeIcon.of(iconRaw));

        return new Home(
                owner,
                HomeSlot.of(row.get(HOMES.SLOT)),
                position,
                label,
                icon,
                row.get(HOMES.PUBLIC) != 0,
                Instant.ofEpochMilli(row.get(HOMES.CREATED_AT)),
                Instant.ofEpochMilli(row.get(HOMES.UPDATED_AT)));
    }

    /** Populate a {@link HomesRecord} from a domain {@link Home} for an upsert. */
    static void apply(HomesRecord record, Home home) {
        Position location = home.location();
        record.setOwner(home.owner().uuid().toString())
                .setSlot(home.slot().index())
                .setLabel(home.label().map(HomeLabel::value).orElse(null))
                .setIcon(home.icon().map(HomeIcon::materialName).orElse(null))
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch())
                .setPublic(home.isPublic() ? 1 : 0)
                .setCreatedAt(home.createdAt().toEpochMilli())
                .setUpdatedAt(home.updatedAt().toEpochMilli());
    }
}
