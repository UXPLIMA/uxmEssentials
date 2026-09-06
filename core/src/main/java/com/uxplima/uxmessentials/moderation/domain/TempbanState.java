package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A target's tempban, as a closed set: {@link None} (not banned) and {@link Active} (banned until a
 * wall-clock instant). A tempban always has an expiry. A permanent ban is an external ban-list or an
 * {@code /banip}, not a tempban. The ban-on-login listener reads {@link #isActiveAt(Instant)} at
 * {@code PlayerLoginEvent} and kicks before player data loads while the ban is still in the future.
 *
 * <p>The domain never reads the wall clock; the caller passes the login instant, so the expiry check is
 * deterministic.
 */
public sealed interface TempbanState permits TempbanState.None, TempbanState.Active {

    /** The not-banned state. */
    static TempbanState none() {
        return None.INSTANCE;
    }

    /** A tempban expiring at {@code until}. */
    static TempbanState active(Instant until, Issuer issuer, Optional<String> reason, Instant issuedAt) {
        return new Active(until, issuer, reason, issuedAt);
    }

    /** True when this ban still bars login at {@code now}. */
    boolean isActiveAt(Instant now);

    /** The expiry instant if banned, else empty. */
    Optional<Instant> expiry();

    /** The not-banned singleton. */
    record None() implements TempbanState {

        private static final None INSTANCE = new None();

        @Override
        public boolean isActiveAt(Instant now) {
            return false;
        }

        @Override
        public Optional<Instant> expiry() {
            return Optional.empty();
        }
    }

    /** A live tempban with a wall-clock expiry. */
    record Active(Instant until, Issuer issuer, Optional<String> reason, Instant issuedAt) implements TempbanState {

        public Active {
            Objects.requireNonNull(until, "until");
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(issuedAt, "issuedAt");
        }

        @Override
        public boolean isActiveAt(Instant now) {
            Objects.requireNonNull(now, "now");
            return now.isBefore(until);
        }

        @Override
        public Optional<Instant> expiry() {
            return Optional.of(until);
        }
    }
}
