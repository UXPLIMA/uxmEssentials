package com.uxplima.uxmessentials.homes.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.homes.domain.event.HomeEvent;
import com.uxplima.uxmessentials.homes.domain.event.HomeIconChanged;
import com.uxplima.uxmessentials.homes.domain.event.HomeLimitReached;
import com.uxplima.uxmessentials.homes.domain.event.HomeRelocated;
import com.uxplima.uxmessentials.homes.domain.event.HomeRenamed;
import com.uxplima.uxmessentials.homes.domain.event.HomeVisibilityChanged;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The aggregate over one owner's homes, keyed by {@link HomeSlot}. It owns the invariants the rest of the
 * context relies on: a slot index stays within the owner's resolved maximum-slot count, a slot holds at
 * most one home, and the count never exceeds the owner's resolved {@link HomeLimit}. Every mutation is a
 * pure transition. It returns a new {@code HomeSet} plus the {@link HomeEvent} it raised, or a
 * {@link HomeError} when an invariant would be broken, so the aggregate never reaches for a repository,
 * a clock, or a teleport. The application layer loads the set, applies a transition, and persists the
 * result.
 *
 * <p>Homes are held in a {@link TreeMap} ordered on slot index, so {@link #all()} lists them
 * slot-ascending and {@link #firstFreeSlot(int)} finds the lowest unused slot deterministically.
 */
public final class HomeSet {

    private final PlayerRef owner;
    private final Map<HomeSlot, Home> homes;

    private HomeSet(PlayerRef owner, Map<HomeSlot, Home> homes) {
        this.owner = owner;
        this.homes = homes;
    }

    /** An owner's set rebuilt from its persisted homes, ordered by slot index. */
    public static HomeSet of(PlayerRef owner, List<Home> homes) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(homes, "homes");
        Map<HomeSlot, Home> bySlot = newMap();
        for (Home home : homes) {
            bySlot.put(home.slot(), home);
        }
        return new HomeSet(owner, bySlot);
    }

    /** An owner with no homes yet. */
    public static HomeSet empty(PlayerRef owner) {
        return new HomeSet(Objects.requireNonNull(owner, "owner"), newMap());
    }

    /** The owner these homes belong to. */
    public PlayerRef owner() {
        return owner;
    }

    /** How many homes the owner currently holds. */
    public int count() {
        return homes.size();
    }

    /** The homes in slot-ascending order. */
    public List<Home> all() {
        return List.copyOf(homes.values());
    }

    /** The home in {@code slot}, if the owner has one there. */
    public Optional<Home> find(HomeSlot slot) {
        return Optional.ofNullable(homes.get(Objects.requireNonNull(slot, "slot")));
    }

    /** The lowest unused slot index in {@code [0, maxSlots)}, or empty when every slot is taken. */
    public Optional<HomeSlot> firstFreeSlot(int maxSlots) {
        for (int index = 0; index < maxSlots; index++) {
            HomeSlot candidate = HomeSlot.of(index);
            if (!homes.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * {@code /sethome}: create a new home in {@code slot}. The slot must be within range ({@code slot.index()
     * < maxSlots}) and free, and the owner must be under their resolved {@code limit}; otherwise the matching
     * {@link HomeError} is returned. A successful create raises {@link HomeCreated}.
     */
    public Result<Change, HomeError> createAt(
            HomeSlot slot, Position location, HomeLimit limit, int maxSlots, Instant now) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(now, "now");
        if (slot.index() >= maxSlots) {
            return Result.err(HomeError.SLOT_OUT_OF_RANGE);
        }
        if (homes.containsKey(slot)) {
            return Result.err(HomeError.SLOT_TAKEN);
        }
        if (limit.isReachedAt(count())) {
            return Result.err(HomeError.LIMIT_REACHED);
        }
        Home created = Home.create(owner, slot, location, now);
        return Result.ok(Change.raising(put(created), created, new HomeCreated(owner, slot, location)));
    }

    /** Re-anchor the home in {@code slot} to {@code location}, keeping its label, icon, and creation time. */
    public Result<Change, HomeError> relocate(HomeSlot slot, Position location, Instant now) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(now, "now");
        Home existing = homes.get(slot);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        Home moved = existing.relocatedTo(location, now);
        return Result.ok(Change.raising(put(moved), moved, new HomeRelocated(owner, slot)));
    }

    /** Set or clear the display label of the home in {@code slot}, keeping everything else. */
    public Result<Change, HomeError> renameLabel(HomeSlot slot, Optional<HomeLabel> label, Instant now) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(now, "now");
        Home existing = homes.get(slot);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        Home relabelled = existing.withLabel(label, now);
        return Result.ok(Change.raising(put(relabelled), relabelled, new HomeRenamed(owner, slot)));
    }

    /** Set or clear the GUI icon of the home in {@code slot}, keeping everything else. */
    public Result<Change, HomeError> setIcon(HomeSlot slot, Optional<HomeIcon> icon, Instant now) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(now, "now");
        Home existing = homes.get(slot);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        Home reIconed = existing.withIcon(icon, now);
        return Result.ok(Change.raising(put(reIconed), reIconed, new HomeIconChanged(owner, slot)));
    }

    /** Flip the visibility of the home in {@code slot} to {@code makePublic}, keeping everything else. */
    public Result<Change, HomeError> setVisibility(HomeSlot slot, boolean makePublic, Instant now) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(now, "now");
        Home existing = homes.get(slot);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        Home updated = existing.withVisibility(makePublic, now);
        return Result.ok(Change.raising(put(updated), updated, new HomeVisibilityChanged(owner, slot)));
    }

    /** {@code /delhome}: remove the home in {@code slot}, rejecting an empty slot. */
    public Result<Change, HomeError> delete(HomeSlot slot) {
        Objects.requireNonNull(slot, "slot");
        Home existing = homes.get(slot);
        if (existing == null) {
            return Result.err(HomeError.NOT_FOUND);
        }
        Map<HomeSlot, Home> next = newMap();
        next.putAll(homes);
        next.remove(slot);
        return Result.ok(Change.raising(new HomeSet(owner, next), existing, new HomeDeleted(owner, slot)));
    }

    /** The {@link HomeLimitReached} event for an attempt the caller has already rejected with the limit. */
    public HomeLimitReached limitReached(HomeLimit limit) {
        Objects.requireNonNull(limit, "limit");
        return new HomeLimitReached(owner, count(), limit.cap());
    }

    private HomeSet put(Home home) {
        Map<HomeSlot, Home> next = newMap();
        next.putAll(homes);
        next.put(home.slot(), home);
        return new HomeSet(owner, next);
    }

    private static Map<HomeSlot, Home> newMap() {
        return new TreeMap<>(Comparator.comparingInt(HomeSlot::index));
    }

    /**
     * The outcome of a mutation: the resulting set, the home the operation produced (created, relocated,
     * relabelled, re-iconed, or deleted), and the domain event to publish when one was raised. The
     * application layer persists the {@link #home} and publishes {@link #event} when present.
     *
     * @param result the set after the mutation
     * @param home the home the operation acted on
     * @param event the event to publish, or empty when the transition raised none
     */
    public record Change(HomeSet result, Home home, Optional<HomeEvent> event) {

        public Change {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(home, "home");
            Objects.requireNonNull(event, "event");
        }

        /** A change that publishes {@code event}. */
        static Change raising(HomeSet result, Home home, HomeEvent event) {
            return new Change(result, home, Optional.of(event));
        }

        /** A change with no event to publish. */
        static Change silent(HomeSet result, Home home) {
            return new Change(result, home, Optional.empty());
        }
    }
}
