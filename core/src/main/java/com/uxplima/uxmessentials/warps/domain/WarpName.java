package com.uxplima.uxmessentials.warps.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A warp's name, normalised to its canonical lowercase form so uniqueness and lookup are
 * case-insensitive: {@code /setwarp Shop} and {@code /warp shop} address the same warp.
 *
 * <p>The name is the warp's identity: warps are server-wide, so a name is unique across the whole
 * {@code warps} table rather than per owner. It is also the segment in the per-warp permission node
 * ({@code uxmessentials.warp.use.<warp>}), so the normalised lowercase form is what the gate is checked
 * against. Blank names and names longer than the column width are rejected at construction, so an invalid
 * name can never reach the aggregate or the repository.
 *
 * @param value the canonical lowercase name
 */
public record WarpName(String value) {

    /** Mirrors the {@code warps.name} column width in the V1 migration. */
    public static final int MAX_LENGTH = 64;

    public WarpName {
        Objects.requireNonNull(value, "value");
    }

    /** Build a name from raw player input, trimming and lower-casing it; rejects blank or overlong input. */
    public static WarpName of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("warp name must not be blank");
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("warp name must be at most " + MAX_LENGTH + " characters");
        }
        return new WarpName(normalised);
    }

    /** The per-warp permission node segment for this warp: {@code uxmessentials.warp.use.<warp>}. */
    public String useNode() {
        return "uxmessentials.warp.use." + value;
    }

    @Override
    public String toString() {
        return value;
    }
}
