package com.uxplima.uxmessentials.security.application;

import java.util.Objects;

import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.security.domain.TotpUri;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The first step of TOTP enrolment ({@code /2fa setup}): mint a fresh shared secret, hold it as the player's
 * pending (un-confirmed) enrolment, and hand back the challenge, the secret and its {@code otpauth://} URI, for
 * the adapter to show the player. Crucially it persists <b>nothing</b>: the secret becomes a real factor only after
 * {@link ConfirmTotpEnrollment} proves the player can produce a code from it, so a setup the player abandons never
 * leaves a factor that could lock them out later.
 */
public final class BeginTotpEnrollment {

    private final SecretGenerator generator;
    private final PendingTotpEnrollments pending;
    private final String issuer;

    public BeginTotpEnrollment(SecretGenerator generator, PendingTotpEnrollments pending, String issuer) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
    }

    /** Generate a secret for {@code player}, stash it as their pending enrolment, and return the challenge to show. */
    public EnrollmentChallenge begin(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        TwoFactorSecret secret = generator.generate();
        pending.store(player.uuid(), secret);
        return new EnrollmentChallenge(secret, TotpUri.build(issuer, player.name(), secret));
    }
}
