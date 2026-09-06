package com.uxplima.uxmessentials.migration.convert.multiverse;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The Multiverse source's {@code SupportedMappings} rows: its claim of what it migrates (docs/12-migration §5). The
 * whole registry is one file and one mapper, so every row names {@code MultiverseWorldMapper}; they are split by the
 * facet of the world each key lands on, because that is what an operator reading the table wants to check.
 *
 * <p>What Multiverse stores and we deliberately do not take is documented as not-migrated rather than half-mapped:
 * its per-world weather, hunger, auto-heal, bed-respawn, anchor-respawn, adjust-spawn, allow-flight, portal-form,
 * scale, respawn-world, world-blacklist, biome and hidden flags have no counterpart in our worlds module, and an
 * entry fee charged in items is dropped because ours is charged in money. {@code MigrationSourceRegistryDriftTest}
 * holds this table in lock-step with the registered source.
 */
@NullMarked
public final class MultiverseMappings {

    private static final List<MappingRow> ROWS = List.of(
            new MappingRow("worlds.yml <world>", "ManagedWorld", "worlds", "MultiverseWorldMapper", "name"),
            new MappingRow("<world>.alias + auto-load", "ManagedWorld", "worlds", "MultiverseWorldMapper", "name"),
            new MappingRow(
                    "<world>.read-only.environment + read-only.seed + generator",
                    "WorldSpec",
                    "worlds",
                    "MultiverseWorldMapper",
                    "name"),
            new MappingRow(
                    "<world>.difficulty + pvp + gamemode + player-limit + entry-fee.amount",
                    "WorldSettings",
                    "worlds",
                    "MultiverseWorldMapper",
                    "name"),
            new MappingRow("<world>.spawn-location", "WorldSettings", "worlds", "MultiverseWorldMapper", "name"));

    private MultiverseMappings() {}

    /** The Multiverse mapping rows, in doc order. */
    public static List<MappingRow> rows() {
        return ROWS;
    }
}
