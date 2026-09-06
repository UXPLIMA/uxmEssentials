package com.uxplima.uxmessentials.moderation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A target's jail, as a closed set: {@link None} (not jailed) and {@link Active} (held in a named jail). An
 * active sentence is one of three durations:
 *
 * <ul>
 *   <li><b>Permanent</b>. {@code /jail} with no duration: no {@code remaining}, no {@code until}; released
 *       only by {@code /unjail}.
 *   <li><b>Online-only timed</b>, the default {@code /jail <player> <duration>}: the {@code remaining}
 *       online time to serve is decremented only while the player is connected in the jail world (docs/02
 *       concurrency, docs/09-deployment). {@link Active#tickOnline(Duration)} burns elapsed online time off
 *       the remainder; the sentence is over when the remainder hits zero. Wall-clock time off the server
 *       never advances it.
 *   <li><b>Wall-clock timed</b>, when an operator opts a jail into wall-clock countdown via {@code
 *       jail-countdown} in {@code moderation.conf}: the sentence expires at the {@code until} instant
 *       regardless of online time, exactly like a tempban. The two timed forms are mutually exclusive.
 * </ul>
 *
 * <p>The domain never reads the wall clock; the caller passes the instant ({@link #isActiveAt}) or the
 * elapsed online duration ({@link Active#tickOnline}), so the online-only countdown is deterministic and
 * testable.
 */
public sealed interface JailState permits JailState.None, JailState.Active {

    /** The not-jailed state. */
    static JailState none() {
        return None.INSTANCE;
    }

    /** A permanent jail in {@code jail} (no expiry, {@code /unjail} only). */
    static JailState permanent(String jail, Issuer issuer, Optional<String> reason, Instant issuedAt) {
        return new Active(jail, Optional.empty(), Optional.empty(), issuer, reason, issuedAt);
    }

    /** An online-only timed jail with {@code remaining} online time still to serve. */
    static JailState onlineTimed(
            String jail, Duration remaining, Issuer issuer, Optional<String> reason, Instant issuedAt) {
        Objects.requireNonNull(remaining, "remaining");
        return new Active(jail, Optional.of(remaining), Optional.empty(), issuer, reason, issuedAt);
    }

    /** A wall-clock timed jail expiring at {@code until} regardless of online time. */
    static JailState wallClockTimed(
            String jail, Instant until, Issuer issuer, Optional<String> reason, Instant issuedAt) {
        Objects.requireNonNull(until, "until");
        return new Active(jail, Optional.empty(), Optional.of(until), issuer, reason, issuedAt);
    }

    /** True when this jail still holds the target at {@code now}. */
    boolean isActiveAt(Instant now);

    /** The not-jailed singleton. */
    record None() implements JailState {

        private static final None INSTANCE = new None();

        @Override
        public boolean isActiveAt(Instant now) {
            return false;
        }
    }

    /**
     * A live jail sentence. Exactly one of {@code remaining} (online-only) or {@code until} (wall-clock) is
     * present for a timed sentence; both empty is a permanent sentence.
     *
     * @param jail the named jail location the target is held in
     * @param remaining the online time still to serve, for an online-only sentence
     * @param until the wall-clock expiry, for a wall-clock sentence
     * @param issuer who issued the jail
     * @param reason the optional free-text reason
     * @param issuedAt when the jail was issued
     */
    record Active(
            String jail,
            Optional<Duration> remaining,
            Optional<Instant> until,
            Issuer issuer,
            Optional<String> reason,
            Instant issuedAt)
            implements JailState {

        public Active {
            Objects.requireNonNull(jail, "jail");
            Objects.requireNonNull(remaining, "remaining");
            Objects.requireNonNull(until, "until");
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(issuedAt, "issuedAt");
            if (jail.isBlank()) {
                throw new IllegalArgumentException("jail name must not be blank");
            }
            if (remaining.isPresent() && until.isPresent()) {
                throw new IllegalArgumentException("a jail is online-only or wall-clock timed, never both");
            }
            if (remaining.isPresent() && remaining.get().isNegative()) {
                throw new IllegalArgumentException("remaining online time must not be negative");
            }
        }

        /** True for a permanent sentence (no remaining, no expiry). */
        public boolean isPermanent() {
            return remaining.isEmpty() && until.isEmpty();
        }

        /** True for an online-only sentence (the countdown advances on online time, not wall-clock). */
        public boolean isOnlineTimed() {
            return remaining.isPresent();
        }

        @Override
        public boolean isActiveAt(Instant now) {
            Objects.requireNonNull(now, "now");
            if (until.isPresent()) {
                return now.isBefore(until.get());
            }
            // Permanent (empty remaining) is always active; an online-only sentence is active until the
            // remainder is exhausted: wall-clock time at the gate never advances it.
            return remaining.map(left -> !left.isZero() && !left.isNegative()).orElse(true);
        }

        /**
         * Burn {@code elapsedOnline} off an online-only sentence's remainder, never below zero. A permanent
         * or wall-clock sentence is returned unchanged: only online-only time accrues this way.
         */
        public Active tickOnline(Duration elapsedOnline) {
            Objects.requireNonNull(elapsedOnline, "elapsedOnline");
            if (remaining.isEmpty()) {
                return this;
            }
            Duration left = remaining.get().minus(elapsedOnline);
            Duration clamped = left.isNegative() ? Duration.ZERO : left;
            return new Active(jail, Optional.of(clamped), until, issuer, reason, issuedAt);
        }

        /** True once an online-only sentence has been fully served (remainder at zero). */
        public boolean isServed() {
            return remaining.map(left -> left.isZero() || left.isNegative()).orElse(false);
        }
    }
}
