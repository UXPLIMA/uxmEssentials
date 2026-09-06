package com.uxplima.uxmessentials.migration;

import java.util.Locale;
import java.util.Objects;

/**
 * What the importer does when an imported record collides with an existing uxmEssentials record
 * (docs/12-migration §6). The chosen branch is recorded per record in the audit {@code conflict} field.
 *
 * <ul>
 *   <li>{@link #SKIP} (default): keep the existing record; the safe, re-runnable choice.
 *   <li>{@link #OVERWRITE}. The source value replaces the existing one (balance still honours the
 *       separate {@code balance-policy}).
 *   <li>{@link #MERGE}: union by identity: the source contributes only records the target lacks.
 * </ul>
 */
public enum ConflictPolicy {
    SKIP,
    OVERWRITE,
    MERGE;

    /** The conservative default: an existing record always wins, so a re-run can never clobber data. */
    public static ConflictPolicy defaultPolicy() {
        return SKIP;
    }

    /** Parse a {@code migration.conf > on-conflict} value, defaulting to {@link #SKIP} when unrecognised. */
    public static ConflictPolicy parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "overwrite" -> OVERWRITE;
            case "merge" -> MERGE;
            default -> SKIP;
        };
    }
}
