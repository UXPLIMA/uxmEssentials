package com.uxplima.uxmessentials.kits.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A kit's identity, normalised to its canonical lowercase form so lookup and the per-kit permission node are
 * case-insensitive, {@code /kit create Starter} and {@code /kit starter} address the same kit.
 *
 * <p>The id is also the segment in the per-kit permission node ({@code uxmessentials.kit.<id>}) and the
 * key under which a player's claim/cooldown stamp lives in PDC, so the normalised lowercase form is what
 * both gates are checked against. Blank ids and ids longer than the configured cap are rejected at
 * construction, so an invalid id can never reach a definition or a stamp.
 *
 * @param value the canonical lowercase id
 */
public record KitId(String value) {

    /** Upper bound on a kit id's length, matching the PDC key and permission-node segment limits. */
    public static final int MAX_LENGTH = 64;

    public KitId {
        Objects.requireNonNull(value, "value");
    }

    /** Build an id from raw input, trimming and lower-casing it; rejects blank or overlong input. */
    public static KitId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("kit id must not be blank");
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("kit id must be at most " + MAX_LENGTH + " characters");
        }
        return new KitId(normalised);
    }

    /** The per-kit permission node segment for this kit: {@code uxmessentials.kit.<id>}. */
    public String permissionNode() {
        return "uxmessentials.kit." + value;
    }

    @Override
    public String toString() {
        return value;
    }
}
