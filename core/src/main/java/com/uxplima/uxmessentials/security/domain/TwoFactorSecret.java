package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A TOTP shared secret: the Base32 string a player scans into their authenticator app and the server keys the
 * one-time-password HMAC with. It is the one piece of two-factor material that is <b>not</b> a one-way hash, TOTP
 * verification recomputes the expected code from the shared secret every 30 seconds, so both sides must hold the
 * same value. That is exactly why the secret is stored <b>encrypted</b> at rest (never a hash, never plaintext) and
 * why this value object {@linkplain #toString redacts itself} in every string form: a shared secret in a log line is
 * a bypass of the whole factor.
 *
 * <p>The constructor normalises the input to canonical upper-case Base32 (stripping the spaces and {@code =} padding
 * an app may print) and rejects anything that is not valid Base32, so an invalid secret can never be constructed and
 * later silently verify against the wrong key. {@link #keyBytes()} decodes it to the raw HMAC key on demand.
 *
 * @param value the canonical upper-case, unpadded Base32 secret
 */
public record TwoFactorSecret(String value) {

    public TwoFactorSecret {
        Objects.requireNonNull(value, "value");
        String normalised = value.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        if (normalised.isBlank()) {
            throw new IllegalArgumentException("a two-factor secret must not be blank");
        }
        // Decode eagerly to validate the alphabet: a bad character throws here, at construction, rather than
        // surfacing later as a code that never matches.
        Base32.decode(normalised);
        value = normalised;
    }

    /** The raw HMAC key bytes this secret decodes to. */
    public byte[] keyBytes() {
        return Base32.decode(value);
    }

    /**
     * Redacted deliberately: a {@code TwoFactorSecret} travels near log statements and exception messages during
     * enrolment and verification, and printing the raw Base32 would hand anyone reading the log a working second
     * factor. The canonical value is reachable only through {@link #value()}, which is called on the two intended
     * paths, building the {@code otpauth://} URI shown to the enrolling player, and encrypting the secret for
     * storage: never on a logging path.
     */
    @Override
    public String toString() {
        return "TwoFactorSecret[***]";
    }
}
