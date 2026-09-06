package com.uxplima.uxmessentials.regions.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Identity of a WorldGuard region, decoupled from any {@code com.sk89q} handle: the world the region lives in and
 * its region id (WorldGuard ids are lowercase and unique within a world). A reference stays valid across a reload
 * the adapter re-resolves the live {@code ProtectedRegion} from the {@link WorldRef} and {@link #id()} each time it
 * needs one, so the domain never holds a WorldGuard object.
 *
 * <p>Equality is on both components, since the same id may exist in two worlds. Pure Java: no Bukkit, Paper, Kyori,
 * or WorldGuard.
 *
 * @param world the world the region belongs to
 * @param id the WorldGuard region id, unique within {@link #world()}
 */
public record RegionRef(WorldRef world, String id) {

    public RegionRef {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("region id must not be blank");
        }
    }
}
