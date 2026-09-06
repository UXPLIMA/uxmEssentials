package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The rent lifecycle of a warp that occupies a paid slot. A warp is rented through {@link #paidUntil}; once that
 * instant passes with no renewal the warp is suspended. {@link #suspendedAt}, when present, marks the moment it
 * was pulled from listings, and is slated to be archived at {@link #archiveAfter}, giving the owner a grace
 * window to renew before the warp is retired for good.
 *
 * <p>The two later stages are {@link Optional} because a healthy, paid-up warp has reached neither: an empty
 * {@link #suspendedAt} means "not suspended", an empty {@link #archiveAfter} means "no archival scheduled". The
 * Optionals themselves are required to be non-null so the two absent states stay a single, explicit value rather
 * than a null.
 *
 * @param paidUntil the instant paid rent currently covers the warp through
 * @param suspendedAt when the warp was suspended for non-payment, if it has been
 * @param archiveAfter when the warp is due to be archived, if archival is scheduled
 */
public record RentState(Instant paidUntil, Optional<Instant> suspendedAt, Optional<Instant> archiveAfter) {

    public RentState {
        Objects.requireNonNull(paidUntil, "paidUntil");
        Objects.requireNonNull(suspendedAt, "suspendedAt");
        Objects.requireNonNull(archiveAfter, "archiveAfter");
    }

    /** True while paid rent still covers the warp at {@code now}; the moment {@link #paidUntil} itself still counts. */
    public boolean isPaidThrough(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isAfter(paidUntil);
    }

    /** True once the warp has been pulled from listings for non-payment. */
    public boolean isSuspended() {
        return suspendedAt.isPresent();
    }
}
