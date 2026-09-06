package com.uxplima.uxmessentials.poses.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Bukkit-free policy for which block a player may sit on and where the seat sits on it. The material is named
 * by its {@code Material} name only (e.g. {@code OAK_STAIRS}). The adapter reads the live {@code Block}/{@code
 * BlockData} and passes the name in, so the sit rules stay in the domain rather than smeared across the listener.
 *
 * <p>Sittability is a configured wildcard set ({@code *_STAIRS}, {@code *_SLAB}, {@code *_CARPET}, or an exact
 * name), matched case-insensitively; a leading or trailing {@code *} is the one supported wildcard, which is all
 * the bundled palette needs. The seat offset is the nominal height above the block's base where a player's seat
 * rests (half a block on a stair or slab, a carpet's thin lip, a full block's top otherwise) so the adapter
 * places the invisible seat entity at {@code blockY + }{@link #seatOffset(String)}. {@link #facingApplies(String)}
 * reports whether the block carries a direction the seat should face (a stair does; a slab or carpet does not),
 * so the adapter only reads {@code BlockData} facing when it matters.
 */
public final class SittableBlocks {

    /** Half a block: the sit surface of a stair or a bottom slab. */
    private static final double HALF_BLOCK = 0.5;

    /** A carpet's thin lip above the block below it (1/16). */
    private static final double CARPET_LIP = 0.0625;

    /** A full block's top: the fallback when a sittable material is neither a stair, slab, nor carpet. */
    private static final double FULL_BLOCK = 1.0;

    private final List<String> patterns;

    /**
     * @param patterns the configured sittable-material patterns ({@code *_STAIRS}, {@code OAK_SLAB}, …); stored
     *     upper-cased so matching is case-insensitive against a {@code Material} name
     */
    public SittableBlocks(List<String> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        this.patterns = patterns.stream()
                .map(pattern -> pattern.toUpperCase(Locale.ROOT))
                .toList();
    }

    /** Whether {@code materialName} matches any configured sittable pattern. */
    public boolean isSittable(String materialName) {
        Objects.requireNonNull(materialName, "materialName");
        String upper = materialName.toUpperCase(Locale.ROOT);
        return patterns.stream().anyMatch(pattern -> matches(pattern, upper));
    }

    /** The nominal height above the block's base where the seat rests, by block kind. */
    public double seatOffset(String materialName) {
        Objects.requireNonNull(materialName, "materialName");
        String upper = materialName.toUpperCase(Locale.ROOT);
        if (upper.endsWith("_STAIRS") || upper.endsWith("_SLAB")) {
            return HALF_BLOCK;
        }
        if (upper.endsWith("_CARPET")) {
            return CARPET_LIP;
        }
        return FULL_BLOCK;
    }

    /** Whether the seat should adopt the block's facing (true for a stair, false otherwise). */
    public boolean facingApplies(String materialName) {
        Objects.requireNonNull(materialName, "materialName");
        return materialName.toUpperCase(Locale.ROOT).endsWith("_STAIRS");
    }

    /** Glob match supporting one leading or trailing {@code *}; otherwise an exact (case-folded) equality. */
    private static boolean matches(String pattern, String candidate) {
        if (pattern.startsWith("*")) {
            return candidate.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*")) {
            return candidate.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(candidate);
    }
}
