package com.uxplima.uxmessentials.homes.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for durable, per-owner home storage, keyed by {@code (owner, slot)}. Every home fact
 * (owner, slot, world, coordinates, label, icon, timestamps) is a first-class column. There is no opaque
 * JSON blob (the architecture persistence invariant), so the {@link HomeSet} an owner loads is rebuilt
 * from queryable rows and the count is read with a {@code COUNT(*)} rather than by deserialising a
 * document.
 *
 * <p>The jOOQ adapter behind this port loads an owner's set, upserts a single home on a {@code /sethome} or
 * relocate (overwriting the {@code (owner, slot)} primary key in place), and deletes by key. A cache
 * decorator may sit in front of it; the contract here is the durable source of truth.
 */
public interface HomeRepository {

    /** The owner's full set of homes, ordered by slot. Empty set when the owner has none. */
    HomeSet load(PlayerRef owner);

    /** How many homes the owner holds, read without materialising the whole set (for the quota check). */
    int count(PlayerRef owner);

    /** The owner's home in {@code slot}, or empty when the slot is free. */
    Optional<Home> findSlot(PlayerRef owner, HomeSlot slot);

    /** Insert {@code home} or overwrite the row under its {@code (owner, slot)} key (a set or a relocate). */
    void save(Home home);

    /** Remove the owner's home in {@code slot}; a no-op when no such row exists. */
    void deleteSlot(PlayerRef owner, HomeSlot slot);

    /**
     * Remove every home the owner holds. Used by the admin bulk-clear operation; a no-op when the
     * owner has no homes.
     */
    void deleteAll(PlayerRef owner);

    /**
     * The owner's homes if they are already in memory, without touching the database. A cache decorator
     * returns its cached set on a hit and an empty {@link Optional} on a miss; an undecorated store has
     * nothing in memory and so returns empty. This exists for tick-thread callers that must never block on
     * I/O. Chiefly the slot suggesters, which complete only the slots a join-warmed cache already holds and
     * suggest nothing on a cold miss rather than reaching the disk while the player types. Never loads;
     * never blocks.
     */
    default Optional<List<Home>> peek(PlayerRef owner) {
        return Optional.empty();
    }
}
