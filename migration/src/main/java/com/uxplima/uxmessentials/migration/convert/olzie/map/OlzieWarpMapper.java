package com.uxplima.uxmessentials.migration.convert.olzie.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieWarpRow;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Translates an Olzie PlayerWarps warp row into the competitor-neutral {@link ImportedPlayerWarp} the shared writer
 * lands (docs/12-migration §5). Olzie is the richest of the three player-warp sources, so this mapper carries the most
 * side data:
 *
 * <ul>
 *   <li><b>Access.</b> Olzie has no single access flag; the mapper resolves the axis in order, a non-blank
 *       {@code password} means {@link WarpAccess#PASSWORD} (the writer hashes the plaintext, which never leaves this
 *       import), else a truthy {@code whitelist_enabled} means {@link WarpAccess#WHITELIST}, else a truthy
 *       {@code locked} means {@link WarpAccess#PRIVATE}, else {@link WarpAccess#PUBLIC}. The password presence wins so
 *       a warp is never claimed password-gated without a hash to check.
 *   <li><b>Owner.</b> Olzie stores the owner as a uuid text and does not serialise the owner's name, so the browse
 *       author line falls back to a placeholder until the owner next logs in, exactly as the Athelion mapper does.
 *   <li><b>The rest.</b> {@code cost} becomes the entry price in the server default currency, {@code visits} and the
 *       distinct-visitor count carry across, and the side rows (ratings, bans with their reason, whitelist, managers
 *       as members, favourites) ride along on the {@link Sidecar}. Olzie credits owner earnings instantly rather than
 *       escrowing them, so there are no accrued earnings to import.
 * </ul>
 *
 * <p>A warp whose owner uuid does not parse, or whose world the live server does not know, is dropped, the mapper
 * returns {@link Optional#empty()} and the plan counts it as skipped, so a migrated warp always has a resolvable owner
 * and destination and this mapper never touches a Bukkit world.
 */
@NullMarked
public final class OlzieWarpMapper {

    /** The currency imported prices are recorded in; Olzie's {@code cost} is a bare Vault amount, not a currency id. */
    private static final String DEFAULT_CURRENCY = "default";

    /** Olzie does not serialise the owner's name; the browse author line falls back to this until the owner logs in. */
    private static final String UNKNOWN_OWNER = "Unknown";

    private final WorldNameResolver worlds;

    public OlzieWarpMapper(WorldNameResolver worlds) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    /** Map one warp, or empty when its owner uuid does not parse or its world does not resolve on the live server. */
    public Optional<ImportedPlayerWarp> map(OlzieWarpRow row, Sidecar side) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(side, "side");
        Optional<UUID> owner = parseUuid(row.ownerUuid());
        String worldName = row.world();
        if (owner.isEmpty() || worldName == null) {
            return Optional.empty();
        }
        return worlds.resolve(worldName).map(world -> build(row, owner.orElseThrow(), world, side));
    }

    private ImportedPlayerWarp build(OlzieWarpRow row, UUID owner, WorldRef world, Sidecar side) {
        Optional<String> password = password(row.password());
        Position location = new Position(world, row.x(), row.y(), row.z(), row.yaw(), row.pitch());
        return new ImportedPlayerWarp(
                owner,
                UNKNOWN_OWNER,
                row.name(),
                Optional.of(row.name()),
                location,
                blankToEmpty(row.category()),
                blankToEmpty(row.description()),
                blankToEmpty(row.icon()),
                access(row, password),
                password,
                BigDecimal.valueOf(row.cost()),
                DEFAULT_CURRENCY,
                BigDecimal.ZERO,
                instant(row.date()),
                Math.max(0L, row.visits()),
                side.uniqueVisitors(),
                side.ratings(),
                side.members(),
                side.whitelist(),
                side.bans(),
                side.favourites());
    }

    /**
     * Resolve the access axis in priority order: a password gate wins, then the whitelist, then the private lock,
     * then public. The order matters. A warp that is both locked and password-gated is password-gated, since the
     * password is the more specific, still-enterable gate.
     */
    private static WarpAccess access(OlzieWarpRow row, Optional<String> password) {
        if (password.isPresent()) {
            return WarpAccess.PASSWORD;
        }
        if (row.whitelistEnabled()) {
            return WarpAccess.WHITELIST;
        }
        if (row.locked()) {
            return WarpAccess.PRIVATE;
        }
        return WarpAccess.PUBLIC;
    }

    /** The plaintext password to hash, or empty when Olzie stored none (null, blank, or the literal {@code null}). */
    private static Optional<String> password(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")) {
            return Optional.empty();
        }
        // Keep the password exactly as stored (Olzie compared it by equals) so the hash matches the original.
        return Optional.of(raw);
    }

    private static Optional<UUID> parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.strip()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
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

    /**
     * The resolved side-table data for one warp. Olzie's full social state, gathered and keyed to uuids by the plan
     * before mapping.
     *
     * @param whitelist the whitelisted players
     * @param bans the warp bans, carrying Olzie's reason and imposed-at instant
     * @param favourites the players who starred the warp
     * @param members the managers, mapped to the manager role
     * @param ratings the per-vote ratings
     * @param uniqueVisitors the distinct-visitor count
     */
    public record Sidecar(
            List<UUID> whitelist,
            List<ImportedPlayerWarp.Ban> bans,
            List<UUID> favourites,
            List<ImportedPlayerWarp.Member> members,
            List<ImportedPlayerWarp.Rating> ratings,
            int uniqueVisitors) {

        public Sidecar {
            whitelist = List.copyOf(Objects.requireNonNull(whitelist, "whitelist"));
            bans = List.copyOf(Objects.requireNonNull(bans, "bans"));
            favourites = List.copyOf(Objects.requireNonNull(favourites, "favourites"));
            members = List.copyOf(Objects.requireNonNull(members, "members"));
            ratings = List.copyOf(Objects.requireNonNull(ratings, "ratings"));
        }

        /** An empty sidecar: a warp with no whitelist, bans, favourites, managers or ratings. */
        public static Sidecar empty() {
            return new Sidecar(List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        }
    }
}
