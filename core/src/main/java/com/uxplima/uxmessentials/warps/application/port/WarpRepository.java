package com.uxplima.uxmessentials.warps.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * Outbound port for durable, server-wide warp storage. Every warp fact (name, world, coordinates, owner,
 * creation time, cost, required permission) is a first-class column. There is no opaque JSON blob (the
 * architecture persistence invariant), so a {@link Warp} loaded from a row is rebuilt from queryable
 * fields, and the list query reads them in stored creation order.
 *
 * <p>Warps are keyed by their {@link WarpName} alone (they are not scoped to an owner), so a {@code save}
 * upserts on the single-column {@code name} primary key, a re-anchor overwrites the same row, and a
 * delete is by name. A cache decorator may sit in front of this port; the contract here is the durable
 * source of truth.
 */
public interface WarpRepository {

    /** The warp under {@code name}, if one exists. */
    Optional<Warp> find(WarpName name);

    /** Every warp, in stored creation order, for the {@code /warps} list. */
    List<Warp> all();

    /** True when a warp already exists under {@code name} (the {@code /setwarp} name-taken check). */
    boolean exists(WarpName name);

    /** Insert {@code warp} or overwrite the row under its {@code name} key (a set or a move). */
    void save(Warp warp);

    /** Remove the warp under {@code name}; a no-op when no such row exists. */
    void delete(WarpName name);

    /** Save/update a player's rating for a warp. */
    void rate(WarpName name, java.util.UUID player, double rating);

    /** Get the average rating for a warp, or 0.0 if not rated. */
    double averageRating(WarpName name);
}
