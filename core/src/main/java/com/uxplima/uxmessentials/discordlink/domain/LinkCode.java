package com.uxplima.uxmessentials.discordlink.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * A one-time code a player presents in Discord to bind their account. The code is a short, uppercase
 * alphanumeric token drawn from an unambiguous alphabet. The letters {@code O} and {@code I} and the digits
 * {@code 0} and {@code 1} are omitted so a player never mistypes a code that looks like another character.
 *
 * <p>The value is normalised to its canonical uppercase, whitespace-stripped form so {@code /link abc234} and
 * {@code /link ABC234} address the same pending row; construction rejects anything outside the unambiguous
 * {@code [A-HJ-NP-Z2-9]{6,8}} shape (the same alphabet generation draws from, so {@code I}/{@code O}/{@code 0}/
 * {@code 1} are never valid), and an invalid code can never reach the store or a use case.
 *
 * @param value the canonical uppercase code
 */
public record LinkCode(String value) {

    /** The unambiguous alphabet new codes are drawn from: no {@code O}/{@code 0}/{@code I}/{@code 1}. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** The length of a freshly generated code; within the accepted 6-8 range. */
    private static final int GENERATED_LENGTH = 7;

    private static final Pattern SHAPE = Pattern.compile("^[A-HJ-NP-Z2-9]{6,8}$");

    public LinkCode {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("link code must be 6-8 chars of [A-HJ-NP-Z2-9]: " + value);
        }
    }

    /** Build a code from raw input, stripping and upper-casing it; rejects anything off the accepted shape. */
    public static LinkCode of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new LinkCode(normalise(raw));
    }

    /** The canonical uppercase, whitespace-stripped form of raw input. */
    public static String normalise(String raw) {
        Objects.requireNonNull(raw, "raw");
        return raw.strip().toUpperCase(Locale.ROOT);
    }

    /** Draw a fresh random code from the unambiguous alphabet using the supplied generator. */
    public static LinkCode generate(RandomGenerator rng) {
        Objects.requireNonNull(rng, "rng");
        StringBuilder out = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            out.append(ALPHABET[rng.nextInt(ALPHABET.length)]);
        }
        return new LinkCode(out.toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
