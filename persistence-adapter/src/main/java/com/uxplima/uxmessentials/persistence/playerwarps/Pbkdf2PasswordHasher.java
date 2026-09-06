package com.uxplima.uxmessentials.persistence.playerwarps;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.uxplima.uxmessentials.playerwarps.application.port.PasswordHasher;
import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;

/**
 * PBKDF2-HMAC-SHA256 implementation of {@link PasswordHasher}, using only the JDK crypto provider so no third
 * party dependency guards a warp password. It lives in the persistence adapter on purpose: the digest is
 * password-at-rest material, and the repository and the P3 data migration that read and write it sit right
 * here, so this is where the crypto naturally belongs: {@code :core} stays free of a concrete cipher.
 *
 * <p>The work factor is deliberately high: {@value #ITERATIONS} iterations is an OWASP-aligned floor for
 * PBKDF2-HMAC-SHA256, which makes an offline dictionary attack against a stolen digest expensive while a single
 * legitimate verify stays well under a tick's worth of work. The algorithm string is carried on every
 * {@link PasswordHash}, so if that floor rises later, existing records can be detected and re-hashed on the
 * next successful verify rather than invalidated en masse.
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    @Override
    public PasswordHash hash(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        byte[] digest = deriveDigest(plaintext, salt);
        Base64.Encoder encoder = Base64.getEncoder();
        return new PasswordHash(ALGORITHM, encoder.encodeToString(salt), encoder.encodeToString(digest));
    }

    @Override
    public boolean verify(String plaintext, PasswordHash against) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(against, "against");
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt = decoder.decode(against.salt());
        byte[] expected = decoder.decode(against.hash());
        byte[] candidate = deriveDigest(plaintext, salt);
        // MessageDigest.isEqual is constant-time and length-safe: it never short-circuits on the first
        // differing byte, so verify timing leaks nothing about how much of the password was correct.
        return MessageDigest.isEqual(expected, candidate);
    }

    private static byte[] deriveDigest(String plaintext, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(plaintext.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // PBKDF2WithHmacSHA256 is mandated on every JDK 25, so this branch is unreachable in practice;
            // rethrow rather than swallow so a broken crypto provider fails loud instead of silently.
            throw new IllegalStateException("JDK is missing the mandatory " + ALGORITHM + " provider", e);
        } finally {
            spec.clearPassword();
        }
    }
}
