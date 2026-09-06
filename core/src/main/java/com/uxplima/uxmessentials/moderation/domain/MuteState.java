package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A target's mute, as a closed set of three shapes: {@link None} (not muted), {@link Permanent} (a
 * {@code /mute} with no duration. Never expires), and {@link Timed} (a {@code /tempmute} that expires at a
 * wall-clock instant). The presence of a {@code moderation_mutes} row is the mute; a null {@code until}
 * column rebuilds to {@link Permanent}, a non-null one to {@link Timed}.
 *
 * <p>The mute gate the messaging context consumes is {@link #isActiveAt(Instant)}: a permanent mute is
 * always active, a timed mute is active only until its expiry, and {@link None} is never active. The domain
 * never reads the wall clock, the caller passes the instant, so the gate is deterministic.
 */
public sealed interface MuteState permits MuteState.None, MuteState.Permanent, MuteState.Timed {

    /** The not-muted state. */
    static MuteState none() {
        return None.INSTANCE;
    }

    /** A permanent mute with the issuer and reason. */
    static MuteState permanent(Issuer issuer, Optional<String> reason, Instant issuedAt) {
        return new Permanent(issuer, reason, issuedAt);
    }

    /** A timed mute expiring at {@code until}. */
    static MuteState timed(Instant until, Issuer issuer, Optional<String> reason, Instant issuedAt) {
        return new Timed(until, issuer, reason, issuedAt);
    }

    /** True when this mute gags the target at {@code now}: a permanent mute always, a timed mute until expiry. */
    boolean isActiveAt(Instant now);

    /** The expiry instant for a timed mute, else empty (permanent or none). */
    Optional<Instant> expiry();

    /** The not-muted singleton. */
    record None() implements MuteState {

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

    /** A mute with no expiry. */
    record Permanent(Issuer issuer, Optional<String> reason, Instant issuedAt) implements MuteState {

        public Permanent {
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(issuedAt, "issuedAt");
        }

        @Override
        public boolean isActiveAt(Instant now) {
            Objects.requireNonNull(now, "now");
            return true;
        }

        @Override
        public Optional<Instant> expiry() {
            return Optional.empty();
        }
    }

    /** A mute that lifts at {@code until}. */
    record Timed(Instant until, Issuer issuer, Optional<String> reason, Instant issuedAt) implements MuteState {

        public Timed {
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
