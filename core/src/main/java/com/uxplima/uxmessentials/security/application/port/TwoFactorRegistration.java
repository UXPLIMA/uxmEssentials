package com.uxplima.uxmessentials.security.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import org.jspecify.annotations.Nullable;

/**
 * A player's stored two-factor registration as the application reads it back: which factors they hold and when they
 * first enrolled. A player may hold a TOTP secret, a PIN, or both, either is a valid second factor, so both are
 * optional here and {@link #hasAnyFactor()} tells the application whether the player is enrolled at all.
 *
 * <p>The TOTP secret is present in its decrypted {@link TwoFactorSecret} form because verification recomputes the
 * expected code from the shared secret; the PIN is deliberately <b>absent</b>. It is a one-way hash the store
 * verifies against internally through {@link TwoFactorRepository#verifyPin}, and is never reconstructed here, so a
 * PIN plaintext never reaches the application. {@code pinSet} records only that a hash exists.
 *
 * <p>{@code lastTotpStep} is the RFC 6238 time-step of the last authenticator code this player got in with. A code
 * is only good for the 30 seconds its step covers, so accepting the same step twice would let anyone who saw the
 * six digits (over a shoulder, in a screen share, in a log) walk in behind the player while they are still valid.
 * The verifier refuses any step at or below this one, which makes each code single-use. Zero means no code has been
 * accepted yet, the state every player starts in.
 *
 * @param playerId the player this registration belongs to
 * @param secret the decrypted TOTP shared secret, or {@code null} when the player has no authenticator factor
 * @param pinSet whether a PIN hash is on file (its plaintext is never exposed)
 * @param enrolledAt the instant the player first enrolled a factor
 * @param lastTotpStep the time-step of the last accepted code, or 0 when none has been accepted
 */
public record TwoFactorRegistration(
        UUID playerId, @Nullable TwoFactorSecret secret, boolean pinSet, Instant enrolledAt, long lastTotpStep) {

    public TwoFactorRegistration {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(enrolledAt, "enrolledAt");
    }

    /** The decrypted TOTP secret, if the player enrolled an authenticator factor. */
    public Optional<TwoFactorSecret> totpSecret() {
        return Optional.ofNullable(secret);
    }

    /** Whether the player holds an authenticator (TOTP) factor. */
    public boolean totpEnabled() {
        return secret != null;
    }

    /** Whether the player holds at least one factor: the signal that they are enrolled and must verify. */
    public boolean hasAnyFactor() {
        return secret != null || pinSet;
    }
}
