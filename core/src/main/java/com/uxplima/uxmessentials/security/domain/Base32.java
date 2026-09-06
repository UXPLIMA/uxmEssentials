package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A minimal RFC 4648 Base32 codec. The alphabet an authenticator app (Google Authenticator, Aegis, …) expects a
 * TOTP shared secret to be written in. The JDK ships Base64 but not Base32, and shading a dependency for one small,
 * well-specified transform is not worth it, so the codec is a hand-rolled part of the domain: it decodes the secret
 * an app scanned back into the raw key bytes the HMAC is keyed with, and encodes freshly generated random bytes into
 * the secret shown at enrolment.
 *
 * <p>Decoding is case-insensitive and tolerates {@code =} padding and embedded spaces (an app often prints the secret
 * in space-separated groups); encoding emits the canonical unpadded upper-case form. An input carrying a character
 * outside the alphabet is rejected loudly rather than silently skipped, so a mistyped secret fails at construction
 * time instead of quietly verifying against the wrong key.
 */
final class Base32 {

    /** The RFC 4648 Base32 alphabet: A-Z then 2-7, index = 5-bit group value. */
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final int BITS_PER_CHAR = 5;
    private static final int BITS_PER_BYTE = 8;
    private static final int BYTE_MASK = 0xFF;

    private Base32() {}

    /** Encode {@code data} as canonical unpadded upper-case Base32. */
    static String encode(byte[] data) {
        Objects.requireNonNull(data, "data");
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << BITS_PER_BYTE) | (b & BYTE_MASK);
            bitsLeft += BITS_PER_BYTE;
            while (bitsLeft >= BITS_PER_CHAR) {
                bitsLeft -= BITS_PER_CHAR;
                out.append(ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            out.append(ALPHABET.charAt((buffer << (BITS_PER_CHAR - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    /** Decode a Base32 string (case-insensitive, {@code =}-padding and spaces tolerated) into its key bytes. */
    static byte[] decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        String cleaned = encoded.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[cleaned.length() * BITS_PER_CHAR / BITS_PER_BYTE];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int value = ALPHABET.indexOf(cleaned.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("not a Base32 character at position " + i);
            }
            buffer = (buffer << BITS_PER_CHAR) | value;
            bitsLeft += BITS_PER_CHAR;
            if (bitsLeft >= BITS_PER_BYTE) {
                bitsLeft -= BITS_PER_BYTE;
                out[index++] = (byte) ((buffer >> bitsLeft) & BYTE_MASK);
            }
        }
        return out;
    }
}
