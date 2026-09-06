package com.uxplima.uxmessentials.villagers.adapter.outbound;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

import org.jspecify.annotations.NullMarked;

/**
 * The seam the follow runtime drives to walk a villager: it either sets the villager pathfinding toward a target or
 * stops it. It exists so the {@link VillagerFollowService}'s tick/threading logic can be exercised in tests with a
 * recording stand-in. The live pathfinder API ({@code Mob#getPathfinder}) is not implemented by MockBukkit, so the
 * one real call is isolated behind this interface in {@link PathfinderVillagerMover}.
 *
 * <p>Both methods run on the villager's own region thread (the follow service hops there first), so an implementation
 * may touch the live entity directly.
 */
@NullMarked
public interface VillagerMover {

    /** Set {@code villager} pathfinding toward {@code target} at the given {@code speed} multiplier. */
    void moveTo(Villager villager, Location target, double speed);

    /** Stop {@code villager} pathfinding, so it holds its ground. */
    void stop(Villager villager);
}
