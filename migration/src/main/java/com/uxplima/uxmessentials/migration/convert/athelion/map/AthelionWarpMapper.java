package com.uxplima.uxmessentials.migration.convert.athelion.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.athelion.parse.AthelionLocation;
import com.uxplima.uxmessentials.migration.convert.athelion.parse.AthelionWarp;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Translates an Athelion warp into the competitor-neutral {@link ImportedPlayerWarp} the shared writer lands
 * (docs/12-migration §5). The scalar work lives here:
 *
 * <ul>
 *   <li><b>Access.</b> Athelion's gate is a plaintext {@code password} plus a {@code WarpStatus}. A warp with a password
 *       maps to {@link WarpAccess#PASSWORD}, a {@code CLOSED} warp to {@link WarpAccess#PRIVATE}, and everything else
 *       (an {@code OPENED} or status-less warp) to {@link WarpAccess#PUBLIC}. Athelion has no whitelist gate.
 *   <li><b>Ratings.</b> Athelion stores only the accumulated star total and the set of reviewers, not per-vote stars, so
 *       the total is distributed as evenly as possible across the reviewers, the count and the average survive exactly,
 *       which is what the browse rollup shows; the per-vote timestamp Athelion never kept is left at the epoch.
 *   <li><b>The rest.</b> {@code admission} becomes the entry price in the server default currency, {@code blocked-players}
 *       become warp bans, {@code category} carries across (Athelion's {@code all} bucket meaning "uncategorised" is
 *       dropped to empty), and the owner <em>name</em>, which Athelion never serialised, falls back to a placeholder.
 * </ul>
 *
 * <p>A warp whose world the live server does not know is dropped ({@link Optional#empty()}, counted as skipped), exactly
 * as the EssentialsX and AxPlayerWarps mappers drop an unknown world, so a migrated warp always has a resolvable
 * destination and this mapper never touches a Bukkit world.
 */
@NullMarked
public final class AthelionWarpMapper {

    /** The currency imported prices are recorded in; Athelion's admission is a bare economy amount, not a currency id. */
    private static final String DEFAULT_CURRENCY = "default";

    /** Athelion does not serialise the owner's name; the browse author line falls back to this until the owner logs in. */
    private static final String UNKNOWN_OWNER = "Unknown";

    /** Athelion's default category bucket; it means "uncategorised", so it maps to no category rather than a real id. */
    private static final String ALL_CATEGORY = "all";

    /** Athelion's closed-warp status token; a closed warp maps to the private access axis. */
    private static final String CLOSED_STATUS = "CLOSED";

    private static final int MIN_STARS = 1;
    private static final int MAX_STARS = 5;

    private final WorldNameResolver worlds;

    public AthelionWarpMapper(WorldNameResolver worlds) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** Map one warp, or empty when its world does not resolve on the live server. */
    public Optional<ImportedPlayerWarp> map(AthelionWarp warp) {
        Objects.requireNonNull(warp, "warp");
        return worlds.resolve(warp.location().world()).map(world -> build(warp, world));
    }

    private ImportedPlayerWarp build(AthelionWarp warp, WorldRef world) {
        Optional<String> password = password(warp.password());
        return new ImportedPlayerWarp(
                warp.owner(),
                UNKNOWN_OWNER,
                warp.name(),
                Optional.of(warp.displayName()),
                position(world, warp.location()),
                category(warp.category()),
                blankToEmpty(warp.description()),
                Optional.empty(),
                access(password, warp.status()),
                password,
                BigDecimal.valueOf(warp.admission()),
                DEFAULT_CURRENCY,
                BigDecimal.ZERO,
                instant(warp.dateCreated()),
                warp.visits(),
                0,
                ratings(warp),
                List.of(),
                List.of(),
                bans(warp),
                List.of());
    }

    private static Position position(WorldRef world, AthelionLocation loc) {
        return new Position(world, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
    }

    /**
     * A warp with a password is password-gated; a {@code CLOSED} warp is private; everything else is public. The password
     * presence wins so a {@code PASSWORD_PROTECTED} warp whose password somehow went missing is never claimed gated
     * without a hash: it degrades to public rather than to an un-enterable warp.
     */
    private static WarpAccess access(Optional<String> password, @Nullable String status) {
        if (password.isPresent()) {
            return WarpAccess.PASSWORD;
        }
        if (status != null && status.strip().equalsIgnoreCase(CLOSED_STATUS)) {
            return WarpAccess.PRIVATE;
        }
        return WarpAccess.PUBLIC;
    }

    /** The plaintext password to hash, or empty when Athelion stored none (blank, or the literal {@code null} token). */
    private static Optional<String> password(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")) {
            return Optional.empty();
        }
        // Keep the password exactly as stored (Athelion validated it by equals) so the hash matches the original.
        return Optional.of(raw);
    }

    private static Optional<String> category(@Nullable String raw) {
        Optional<String> trimmed = blankToEmpty(raw);
        return trimmed.filter(value -> !value.equalsIgnoreCase(ALL_CATEGORY));
    }

    /**
     * Reconstruct the per-vote ratings from Athelion's aggregate: one vote per reviewer, the star total spread as evenly
     * as the count allows, each clamped to a legal 1..5. For real Athelion data (every vote is 1..5) the spread is exact,
     * so the writer's rollup lands the same count and average Athelion showed; the timestamp it never stored is the epoch.
     */
    private static List<ImportedPlayerWarp.Rating> ratings(AthelionWarp warp) {
        List<UUID> reviewers = warp.reviewers();
        int count = reviewers.size();
        if (count == 0 || warp.ratingSum() <= 0) {
            return List.of();
        }
        int base = warp.ratingSum() / count;
        int remainder = warp.ratingSum() % count;
        List<ImportedPlayerWarp.Rating> ratings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int stars = clampStars(base + (index < remainder ? 1 : 0));
            ratings.add(new ImportedPlayerWarp.Rating(reviewers.get(index), stars, Instant.EPOCH));
        }
        return ratings;
    }

    private static List<ImportedPlayerWarp.Ban> bans(AthelionWarp warp) {
        List<ImportedPlayerWarp.Ban> bans =
                new ArrayList<>(warp.blockedPlayers().size());
        for (UUID blocked : warp.blockedPlayers()) {
            // Athelion records no reason, expiry or issuer for a block, and no timestamp. The ban carries only the
            // player.
            bans.add(new ImportedPlayerWarp.Ban(
                    blocked, Optional.empty(), Optional.empty(), Optional.empty(), Instant.EPOCH));
        }
        return bans;
    }

    private static int clampStars(int stars) {
        return Math.max(MIN_STARS, Math.min(MAX_STARS, stars));
    }

    private static Optional<String> blankToEmpty(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private static Instant instant(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : Instant.EPOCH;
    }
}
