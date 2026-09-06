package com.uxplima.uxmessentials.persistence.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption for the TOTP shared secret at rest. Unlike the PIN, a one-way hash, the TOTP secret must
 * be recoverable, because verification recomputes the expected code from the secret every 30 seconds; so it is
 * <b>encrypted, not hashed</b>, under a server-held key (a random key-file in the data folder, see
 * {@link SecurityKeyFile}). AES-GCM is authenticated encryption: a tampered ciphertext fails to decrypt rather than
 * yielding a wrong secret, so a corrupted or swapped column is caught, not silently mis-verified.
 *
 * <p>The stored form is {@code Base64(iv || ciphertext||tag)} with a fresh 12-byte random IV per encryption, so
 * encrypting the same secret twice yields different columns and no IV is ever reused under one key. Neither the
 * plaintext secret nor the key is ever logged; a crypto failure is rethrown as an {@link IllegalStateException}
 * carrying only the algorithm name, never the data.
 */
public final class TotpSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * A cipher keyed by {@code keyBytes}, which must be a valid AES key length (16, 24 or 32 bytes). The security
     * module derives 32 bytes from its key-file, but the shorter lengths are accepted so a test can key it directly.
     */
    public TotpSecretCipher(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        int length = keyBytes.length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24 or 32 bytes, was " + length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Encrypt {@code plaintext} into the {@code Base64(iv || ciphertext)} column form. */
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException failure) {
            // Rethrow without the plaintext: a broken provider must fail loud, but the secret must not reach a log.
            throw new IllegalStateException("could not encrypt with " + TRANSFORMATION, failure);
        }
    }

    /** Decrypt a {@code Base64(iv || ciphertext)} column back into the plaintext secret. */
    public String decrypt(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        byte[] combined = Base64.getDecoder().decode(encoded);
        if (combined.length <= IV_BYTES) {
            throw new IllegalStateException("stored TOTP secret is too short to contain an IV and ciphertext");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("could not decrypt with " + TRANSFORMATION, failure);
        }
    }
}
