package com.uxplima.uxmessentials.security.application;

import java.util.Objects;

import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;

/**
 * What {@code /2fa setup} hands back to the command layer: the freshly generated shared secret and the
 * {@code otpauth://} URI that encodes it, so the adapter can show both to the enrolling player, the URI for an app
 * that reads a link, the raw secret for one where the code is typed in by hand. Nothing here is persisted yet; the
 * secret becomes an enabled factor only once the player confirms a code from it.
 *
 * @param secret the generated shared secret (redacts itself in logs. Only shown to the enrolling player)
 * @param otpauthUri the {@code otpauth://totp/...} enrolment URI carrying the same secret and the server's parameters
 */
public record EnrollmentChallenge(TwoFactorSecret secret, String otpauthUri) {

    public EnrollmentChallenge {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(otpauthUri, "otpauthUri");
    }
}
