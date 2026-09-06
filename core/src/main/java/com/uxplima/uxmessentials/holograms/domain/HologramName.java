package com.uxplima.uxmessentials.holograms.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A hologram's name, normalised to its canonical lowercase form so uniqueness and lookup are
 * case-insensitive, {@code /hologram create Spawn} and {@code /hologram delete spawn} address the same
 * hologram.
 *
 * <p>The name is the hologram's identity: holograms are server-wide, so a name is unique across the whole
 * {@code holograms} table. Blank names and names longer than the column width are rejected at construction,
 * so an invalid name can never reach the aggregate or the repository.
 *
 * @param value the canonical lowercase name
 */
public record HologramName(String value) {

    /** Mirrors the {@code holograms.name} column width in the V13 migration. */
    public static final int MAX_LENGTH = 64;

    public HologramName {
        Objects.requireNonNull(value, "value");
    }

    /** Build a name from raw player input, trimming and lower-casing it; rejects blank or overlong input. */
    public static HologramName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("hologram name must not be blank");
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("hologram name must be at most " + MAX_LENGTH + " characters");
        }
        return new HologramName(normalised);
    }

    @Override
    public String toString() {
        return value;
    }
}
