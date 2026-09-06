package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;

/**
 * Check a single submitted value against a player's second factor during join verification, the read-only twin of
 * {@link DisableTwoFactor}'s proof step, without the removal. One submission is checked against whichever factors the
 * player holds: a 6-digit TOTP code from an authenticator app (or typed on the keypad) verifies against their shared
 * secret, and a numeric PIN verifies against the stored hash. Either unlocks, so a player who enrolled both may use
 * whichever is to hand.
 *
 * <p>The use case never learns which factor matched or how close a wrong value was ({@link TotpCode#matchingStep}
 * and {@link TwoFactorRepository#verifyPin} are both constant-time) and it holds the candidate for exactly as long
 * as the two checks take. It returns a typed {@link VerifyResult} so the adapter can unfreeze, count a failure, or
 * (for an unenrolled player it should never have prompted) do nothing.
 *
 * <p>An authenticator code is single-use here. The time-step it matched is written back through
 * {@link TwoFactorRepository#recordTotpStep} and any later submission at or below that step is refused as though the
 * digits were wrong, so a code someone read over a shoulder or lifted from a screen share is spent the moment its
 * owner uses it (RFC 6238 §5.2). A replay is an ordinary failure: it counts toward the lockout like any other.
 */
public final class VerifyTwoFactor {

    private final TwoFactorRepository repository;
    private final int codeWindow;

    public VerifyTwoFactor(TwoFactorRepository repository, int codeWindow) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (codeWindow < 0) {
            throw new IllegalArgumentException("codeWindow must not be negative: " + codeWindow);
        }
        this.codeWindow = codeWindow;
    }

    /** Verify {@code candidate} against the player's factors at {@code now}, returning the typed outcome. */
    public VerifyResult verify(UUID playerId, String candidate, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(now, "now");
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            return VerifyResult.NOT_ENROLLED;
        }
        return proves(registration, playerId, candidate, now) ? VerifyResult.SUCCESS : VerifyResult.INVALID;
    }

    /** True when {@code candidate} verifies against the player's TOTP secret or their stored PIN. */
    private boolean proves(TwoFactorRegistration registration, UUID playerId, String candidate, Instant now) {
        TwoFactorSecret secret = registration.secret();
        if (secret != null) {
            long step = TotpCode.matchingStep(secret, candidate, now, codeWindow);
            // A step already spent is a replay, not a proof. It falls through to the PIN check rather than failing
            // outright, so a player whose PIN happens to read like a spent code is still let in by it.
            if (step != TotpCode.NO_STEP && step > registration.lastTotpStep()) {
                repository.recordTotpStep(playerId, step);
                return true;
            }
        }
        return registration.pinSet() && repository.verifyPin(playerId, candidate);
    }
}
