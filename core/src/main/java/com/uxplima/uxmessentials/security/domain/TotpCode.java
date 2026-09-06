package com.uxplima.uxmessentials.security.domain;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The RFC 6238 Time-based One-Time Password maths, as a pure function of a {@link TwoFactorSecret} and an instant.
 * It is HOTP (RFC 4226) counted over 30-second time-steps: HMAC-SHA1 of the step counter keyed by the shared
 * secret, dynamically truncated to a 6-digit code. This is the exact algorithm Google Authenticator, Aegis, Authy
 * and every other standard authenticator app implement, so a code generated here matches the code on the player's
 * phone for the same 30-second window.
 *
 * <p>Everything here is deterministic in {@code (secret, instant)} (no {@code SecureRandom}, no wall clock read)
 * so it is unit-tested directly against the RFC 6238 Appendix B reference vectors at fixed timestamps. {@link
 * #verify} accepts a small ± window of steps to tolerate clock skew and the player typing a code as its window
 * rolls, comparing candidate against expected in constant time so verification leaks nothing about which digits
 * were right.
 *
 * <p>{@link #matchingStep} is the same check reported as the time-step that matched instead of a plain yes/no. A
 * caller that must not accept the same code twice (RFC 6238 §5.2: a verifier should reject a code whose step it
 * has already accepted) remembers that number and refuses anything at or below it, which is what turns a shoulder-
 * surfed or logged code into a single-use one.
 */
public final class TotpCode {

    /** RFC 6238's default 30-second time-step. */
    private static final long TIME_STEP_SECONDS = 30L;

    /** The number of decimal digits in a code, the near-universal authenticator default. */
    private static final int DIGITS = 6;

    /** {@code 10^DIGITS}, the modulus dynamic truncation reduces the binary code by. */
    private static final int DIGITS_MODULUS = 1_000_000;

    private static final String HMAC_ALGORITHM = "HmacSHA1";

    /** What {@link #matchingStep} returns when no step in the window matched: no real step is ever this small. */
    public static final long NO_STEP = Long.MIN_VALUE;

    private TotpCode() {}

    /** The 6-digit code for {@code secret} at {@code at}, zero-padded to {@link #DIGITS} characters. */
    public static String generate(TwoFactorSecret secret, Instant at) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(at, "at");
        return codeForCounter(secret, counter(at));
    }

    /**
     * True when {@code candidate} matches the code for {@code secret} at {@code at} within ± {@code window}
     * time-steps. A {@code window} of 0 accepts only the current step; 1 also accepts the immediately preceding and
     * following step (±30s), which absorbs modest clock skew and the roll of a code the player is mid-way through
     * typing. The comparison is constant-time and a candidate that is not a well-formed 6-digit code simply fails.
     */
    public static boolean verify(TwoFactorSecret secret, String candidate, Instant at, int window) {
        return matchingStep(secret, candidate, at, window) != NO_STEP;
    }

    /**
     * The time-step {@code candidate} matched within ± {@code window} of {@code at}, or {@link #NO_STEP} when it
     * matched none. The whole window is always scanned, so the time taken does not depend on which step matched;
     * the step itself is returned because a caller that retires used codes needs to know which one to retire. When
     * more than one step in the window renders the same code the latest matching step is reported, which is the
     * conservative choice: retiring it also retires the earlier one.
     */
    public static long matchingStep(TwoFactorSecret secret, String candidate, Instant at, int window) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(at, "at");
        if (window < 0) {
            throw new IllegalArgumentException("window must not be negative: " + window);
        }
        String normalised = candidate.replace(" ", "");
        long base = counter(at);
        long matched = NO_STEP;
        for (long step = base - window; step <= base + window; step++) {
            // No early return inside the loop: check every step in the window so verification time does not depend
            // on which step (if any) matched, keeping the ± tolerance from leaking through timing.
            if (constantTimeEquals(codeForCounter(secret, step), normalised)) {
                matched = step;
            }
        }
        return matched;
    }

    private static long counter(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), TIME_STEP_SECONDS);
    }

    private static String codeForCounter(TwoFactorSecret secret, long counter) {
        byte[] hmac = hmacSha1(
                secret.keyBytes(),
                ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
        // RFC 4226 dynamic truncation: the low nibble of the last byte selects a 4-byte window, whose high bit is
        // masked off to a 31-bit integer that is then reduced to the decimal code.
        int offset = hmac[hmac.length - 1] & 0x0F;
        int binary = ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);
        return zeroPad(binary % DIGITS_MODULUS);
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA1 is mandated on every JDK, so this is unreachable in practice; rethrow rather than swallow so
            // a broken crypto provider fails loud instead of silently rejecting every code.
            throw new IllegalStateException("JDK is missing the mandatory " + HMAC_ALGORITHM + " provider", e);
        }
    }

    /** Render {@code value} as a decimal string zero-padded on the left to {@link #DIGITS} characters. */
    private static String zeroPad(int value) {
        String raw = Integer.toString(value);
        if (raw.length() >= DIGITS) {
            return raw;
        }
        StringBuilder padded = new StringBuilder(DIGITS);
        for (int i = raw.length(); i < DIGITS; i++) {
            padded.append('0');
        }
        return padded.append(raw).toString();
    }

    /** Constant-time string equality over UTF-8 bytes: {@link MessageDigest#isEqual} never short-circuits. */
    private static boolean constantTimeEquals(String expected, String candidate) {
        return MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
