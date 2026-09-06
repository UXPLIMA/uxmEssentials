package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Sweeps entities for the purge family ({@code /butcher}, {@code /killall}, {@code /remove}) according to a
 * validated {@link PurgeSelection}, and reports the count removed. The domain owns <em>what</em> may be swept
 * (the scope, the type filter, the never-players category and the radius clamp); this adapter performs the live
 * world scan and removal, the one side-effecting step.
 *
 * <p>Invariants the sweep enforces regardless of selection: a {@link Player} is never removed, and a
 * {@link Tameable} that is tamed is left alone (a player's pet survives a {@code /killall}).
 *
 * <p>The two scopes thread very differently on Folia. A {@link PurgeSelection.Scope#RADIUS} sweep reads only the
 * actor's nearby entities (a single region the caller already owns (it schedules via {@code onEntity(actor)}))
 * so {@link #purge} stays a synchronous read-and-remove returning the count. A {@link PurgeSelection.Scope#WORLD}
 * sweep is a different shape entirely: {@code world.getEntities()} spans every region of the world and each
 * matching entity is owned by the region thread its location falls in, not the actor's. Enumerating and removing
 * those from the actor's thread is the exact cross-region mutation Folia forbids, so {@link #purgeWorld} snapshots
 * the roster on the global region thread and then hops each removal onto the region that owns that entity's
 * location, aggregating the count and calling back when every hop has finished. On Paper every region is the main
 * thread, so both paths behave exactly as a straight inline sweep did.
 */
@NullMarked
public final class BukkitEntityPurger {

    private BukkitEntityPurger() {}

    /**
     * Remove the radius-bounded entities {@code selection} targets around {@code actor}, returning how many were
     * removed. The caller must already be on {@code actor}'s region thread (the {@code Scheduler.onEntity} hop the
     * purge commands make), since this reads the actor's nearby entities, a single region. Only valid for a
     * {@link PurgeSelection.Scope#RADIUS} selection; a world sweep goes through {@link #purgeWorld}.
     */
    public static int purge(Player actor, PurgeSelection selection) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(selection, "selection");
        if (selection.scope() != PurgeSelection.Scope.RADIUS) {
            throw new IllegalArgumentException("purge handles only RADIUS sweeps; use purgeWorld for WORLD scope");
        }
        int removed = 0;
        for (Entity entity : nearby(actor, selection.radius())) {
            if (matches(entity, selection)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Remove the world-wide entities {@code selection} targets in {@code actor}'s world, reporting the count to
     * {@code onComplete} once every removal has run. The enumeration of {@code world.getEntities()} happens on the
     * global region thread (the one thread that can read the whole world's roster coherently on Folia); each
     * candidate is then re-checked and removed on the region that owns its location via
     * {@link Scheduler#onRegion(Position, Runnable)}. {@code onComplete} fires on whichever region thread runs the
     * final removal (or inline on the global thread when the world holds no matching entity), carrying the total
     * removed, so the caller must route any reply it makes back to the recipient's own thread.
     */
    public static void purgeWorld(Scheduler scheduler, Player actor, PurgeSelection selection, IntConsumer onComplete) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(onComplete, "onComplete");
        if (selection.scope() != PurgeSelection.Scope.WORLD) {
            throw new IllegalArgumentException("purgeWorld handles only WORLD sweeps; use purge for RADIUS scope");
        }
        World world = actor.getWorld();
        scheduler.onGlobal(() -> fanOutRemovals(scheduler, world, selection, onComplete));
    }

    /**
     * Snapshot the world's entities on the global thread, then hop each candidate's removal onto its owning region.
     * The match is re-checked inside the region hop (an entity can change category, be tamed, or vanish between the
     * snapshot and the hop), so the count reflects what was actually removed. {@code pending} counts the global
     * snapshot itself as one unit so a world with no candidates still completes through the same drain path.
     */
    private static void fanOutRemovals(
            Scheduler scheduler, World world, PurgeSelection selection, IntConsumer onComplete) {
        List<Candidate> candidates = snapshot(world, selection);
        AtomicInteger removed = new AtomicInteger();
        AtomicInteger pending = new AtomicInteger(candidates.size() + 1);
        for (Candidate candidate : candidates) {
            scheduler.onRegion(candidate.position(), () -> {
                Entity live = candidate.entity();
                if (live.isValid() && matches(live, selection)) {
                    live.remove();
                    removed.incrementAndGet();
                }
                drain(pending, removed, onComplete);
            });
        }
        drain(pending, removed, onComplete);
    }

    /** Decrement the outstanding-hop counter and fire the completion callback once the last hop has drained. */
    private static void drain(AtomicInteger pending, AtomicInteger removed, IntConsumer onComplete) {
        if (pending.decrementAndGet() == 0) {
            onComplete.accept(removed.get());
        }
    }

    /**
     * The matching entities and their owning-region positions, read once on the global thread. Filtering here keeps
     * the per-region fan-out to the entities that could be removed; the match is re-checked in the hop because the
     * snapshot is a moment-in-time read.
     */
    private static List<Candidate> snapshot(World world, PurgeSelection selection) {
        List<Candidate> candidates = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (matches(entity, selection)) {
                Location location = entity.getLocation();
                candidates.add(new Candidate(entity, BukkitRefs.toPosition(location)));
            }
        }
        return candidates;
    }

    private static Iterable<Entity> nearby(Player actor, int radius) {
        return actor.getNearbyEntities(radius, radius, radius);
    }

    private static boolean matches(Entity entity, PurgeSelection selection) {
        if (entity instanceof Player) {
            return false; // never sweep players
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false; // a tamed pet is protected
        }
        return switch (selection.category()) {
            case MONSTERS -> entity instanceof Monster;
            case NAMED_TYPE ->
                selection.typeId().map(id -> typeMatches(entity.getType(), id)).orElse(false);
            case ALL_ENTITIES -> true;
        };
    }

    private static boolean typeMatches(EntityType type, String id) {
        Optional<EntityType> resolved = BukkitEntityResolver.spawnable("minecraft:" + id);
        if (resolved.isPresent()) {
            return type == resolved.get();
        }
        return type.name().toLowerCase(Locale.ROOT).equals(id);
    }

    /** A world entity captured on the global thread with the region position that owns its removal. */
    private record Candidate(Entity entity, Position position) {}
}
