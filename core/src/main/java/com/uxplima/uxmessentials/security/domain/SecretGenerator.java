package com.uxplima.uxmessentials.security.domain;

import java.security.SecureRandom;

/**
 * Mints a fresh {@link TwoFactorSecret} from cryptographically strong randomness. A new secret is 160 bits (20
 * bytes) of {@link SecureRandom} output. The size RFC 4226 §4 recommends for an HMAC-SHA1 key and the length
 * authenticator apps expect: Base32-encoded into the 32-character string shown once at enrolment. It lives in the
 * domain because the encoding is domain knowledge; the randomness source ({@code java.security.SecureRandom}) is a
 * platform primitive the domain is allowed to use, and it never touches Bukkit.
 */
public final class SecretGenerator {

    /** 160 bits: the RFC 4226 recommended HMAC-SHA1 key length. */
    private static final int SECRET_BYTES = 20;

    private final SecureRandom random;

    /** A generator over the platform's default strong randomness source. */
    public SecretGenerator() {
        this(new SecureRandom());
    }

    /** A generator over a supplied randomness source, so a test can drive it deterministically. */
    public SecretGenerator(SecureRandom random) {
        this.random = java.util.Objects.requireNonNull(random, "random");
    }

    /** A new 160-bit shared secret, Base32-encoded. */
    public TwoFactorSecret generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return new TwoFactorSecret(Base32.encode(bytes));
    }
}
