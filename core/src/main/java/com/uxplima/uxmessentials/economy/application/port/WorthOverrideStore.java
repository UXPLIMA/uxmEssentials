package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.Worth;

/**
 * Outbound port for the DB-backed per-material worth overrides an operator sets in-game with {@code /setworth}.
 * This is the writable complement to the read-only config {@code WorthTable}: where the table reflects the
 * {@code worth.items} list in {@code economy.conf}, this store holds prices set at runtime and survives a world
 * rollback like an economy balance does (it is relational, never PDC, the economy hard invariant). The two are
 * merged behind a combined worth source consulted store-first then config-fallback, so a freshly set price is
 * what {@code /worth} reports and {@code /sell} credits against without a rewire.
 *
 * <p>A row is keyed by its canonical lowercase material id, so {@link #set} is an upsert, a {@code /setworth}
 * onto an existing material overwrites that price (and currency) in place. The override carries the currency it
 * pays out in, so an operator can price an item in any configured currency from in-game, matching what the
 * config {@code worth.items} list already supports. The implementation lands in the persistence adapter; the
 * use cases depend only on this contract.
 */
public interface WorthOverrideStore {

    /**
     * Set (or overwrite) the unit price and pay-out currency for {@code material}, an upsert on the material
     * key.
     */
    void set(String material, BigDecimal price, String currencyId);

    /** The override worth (price and currency) for {@code material}, or empty when none is set. */
    Optional<Worth> find(String material);

    /** True when an override is set for {@code material}. */
    boolean exists(String material);

    /** Remove the override for {@code material}; true when a row was removed, false when none existed. */
    boolean remove(String material);
}
