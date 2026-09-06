package com.uxplima.uxmessentials.holograms.application.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;

/**
 * Outbound port for durable, server-wide hologram storage. Every hologram fact (name, world, coordinates,
 * creation time) is a first-class column, and the ordered lines live in a separate queryable child table
 * there is no opaque JSON blob (the architecture persistence invariant), so a {@link Hologram} loaded from
 * its rows is rebuilt from queryable fields, and the list query reads them in stored creation order.
 *
 * <p>Holograms are keyed by their {@link HologramName} alone, so a {@code save} upserts on the single-column
 * {@code name} primary key (a re-anchor or a line edit overwrites the same hologram and rewrites its lines),
 * and a delete is by name. A cache decorator may sit in front of this port; the contract here is the durable
 * source of truth.
 */
public interface HologramRepository {

    /** The hologram under {@code name}, if one exists. */
    Optional<Hologram> find(HologramName name);

    /** Every hologram, in stored creation order, for the {@code /hologram list} and spawn-on-enable. */
    List<Hologram> all();

    /** True when a hologram already exists under {@code name} (the {@code /hologram create} name-taken check). */
    boolean exists(HologramName name);

    /** Insert {@code hologram} or overwrite the row (and its lines) under its {@code name} key. */
    void save(Hologram hologram);

    /** Remove the hologram under {@code name} and its lines; a no-op when no such row exists. */
    void delete(HologramName name);

    /**
     * The set of player uuids explicitly shown the {@link com.uxplima.uxmessentials.holograms.domain.Visibility.Mode#MANUAL}
     * hologram under {@code name}, in stored order; empty when none is shown or no such hologram exists. Backs the
     * renderer's MANUAL allowed-viewer set, queried per hologram on render and on join.
     */
    Set<UUID> manualViewers(HologramName name);

    /** Add {@code viewer} to the manual viewer set of the hologram under {@code name}; idempotent. */
    void showTo(HologramName name, UUID viewer);

    /** Remove {@code viewer} from the manual viewer set of the hologram under {@code name}; a no-op when absent. */
    void hideFrom(HologramName name, UUID viewer);

    /**
     * The set of player uuids the hologram under {@code name} is hidden from regardless of its visibility mode;
     * empty when none is blacklisted or no such hologram exists. The renderer hides the live entity from these
     * players on spawn and on join.
     */
    Set<UUID> blacklisted(HologramName name);

    /** Add {@code viewer} to the hologram {@code name}'s viewer blacklist; idempotent. */
    void addToBlacklist(HologramName name, UUID viewer);

    /** Remove {@code viewer} from the hologram {@code name}'s viewer blacklist; a no-op when absent. */
    void removeFromBlacklist(HologramName name, UUID viewer);
}
