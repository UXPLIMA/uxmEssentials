package com.uxplima.uxmessentials.villagers.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The restock-timer rule: given the instant a villager last restocked its trades and the current instant,
 * decide whether the configured {@code interval} has elapsed and a restock is due. This is the whole of the
 * restock decision, the villager, its {@code MerchantRecipe} uses, and the persistent last-restock stamp are
 * adapter concerns; the domain only owns the elapsed-time comparison so it can be unit-tested without Bukkit.
 *
 * <p>A villager that has never restocked is modelled by a last-restock at {@link Instant#EPOCH} (the adapter's
 * default when no stamp is present), which is always due, so a freshly-seen villager restocks on the first
 * sweep and then only once per interval thereafter. The comparison is inclusive: a restock exactly one interval
 * old is due, matching the "older than the interval" wording.
 *
 * @param interval how long a villager's trades stay fresh before another restock is due; strictly positive
 */
public record RestockPolicy(Duration interval) {

    public RestockPolicy {
        Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("restock interval must be strictly positive: " + interval);
        }
    }

    /** A policy whose interval is {@code seconds} seconds; {@code seconds} must be at least one. */
    public static RestockPolicy ofSeconds(long seconds) {
        return new RestockPolicy(Duration.ofSeconds(seconds));
    }

    /**
     * Whether a villager last restocked at {@code lastRestock} is due to restock again at {@code now}, true once
     * at least {@link #interval} has elapsed (inclusive). A {@code lastRestock} at or before {@code now} minus the
     * interval is due; a more recent one is not.
     */
    public boolean restockDue(Instant lastRestock, Instant now) {
        Objects.requireNonNull(lastRestock, "lastRestock");
        Objects.requireNonNull(now, "now");
        return !now.isBefore(lastRestock.plus(interval));
    }
}
