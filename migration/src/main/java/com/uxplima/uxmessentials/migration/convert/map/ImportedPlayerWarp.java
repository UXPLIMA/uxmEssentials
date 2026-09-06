package com.uxplima.uxmessentials.migration.convert.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped player-owned warp, expressed in a competitor-neutral shape the shared
 * {@code PlayerWarpRecordWriter} lands on the new {@code player_warps} schema. It is the player-warps analogue of
 * {@link ImportedWarp}, but deliberately <em>not</em> the domain aggregate: a source records raw facets (a name the
 * new charset may reject, a plaintext password, an ordinal access flag, per-vote ratings) that the writer resolves
 * sanitise + globally dedupe the name, hash the password, map the access flag: before an aggregate can be built.
 * Every importer (AxPlayerWarps here; Athelion and Olzie later) maps its own storage into this one record, so the
 * write path (collision rename, PBKDF2 hashing, side-list stores, idempotency, dry-run) is written once.
 *
 * <p>Fields a given source lacks are empty: AxPlayerWarps carries no password (empty), no co-owner members (empty
 * list), and no per-ban reason, so those arrive as absent rather than as a sentinel. The {@link #location} is already
 * resolved to a {@link Position} by the source's mapper. A warp whose world the live server does not know is dropped
 * there, exactly as the EssentialsX warp mapper drops an unknown world, so the writer never touches a Bukkit world.
 *
 * @param owner the warp owner's uuid
 * @param ownerName the owner's name captured by the source, for the browse author line
 * @param name the source's raw warp name, sanitised and globally de-collided at write time
 * @param displayName the original-cased label to keep, if the source has one distinct from the id
 * @param location the resolved teleport destination
 * @param categoryId the browse category id, if filed under one
 * @param description the owner's blurb, if any
 * @param icon the raw icon token (a material name or a richer scheme), if set
 * @param access the mapped access axis (public / password / whitelist / private)
 * @param plaintextPassword the source's plaintext password, hashed at write and never stored or logged
 * @param price the entry price
 * @param currencyId the currency the price is denominated in
 * @param earnedMoney the owner earnings accrued but not yet withdrawn
 * @param createdAt when the source first created the warp
 * @param visits the total teleports to the warp
 * @param uniqueVisitors the distinct players behind those teleports
 * @param ratings the per-vote star ratings, written to the rating store and rolled up onto the warp
 * @param members the co-owner / manager delegates, where the source has them
 * @param whitelist the players allowed to a whitelist-access warp
 * @param bans the per-player bans on the warp
 * @param favourites the players who have starred the warp
 */
@NullMarked
public record ImportedPlayerWarp(
        UUID owner,
        String ownerName,
        String name,
        Optional<String> displayName,
        Position location,
        Optional<String> categoryId,
        Optional<String> description,
        Optional<String> icon,
        WarpAccess access,
        Optional<String> plaintextPassword,
        BigDecimal price,
        String currencyId,
        BigDecimal earnedMoney,
        Instant createdAt,
        long visits,
        int uniqueVisitors,
        List<Rating> ratings,
        List<Member> members,
        List<UUID> whitelist,
        List<Ban> bans,
        List<UUID> favourites) {

    public ImportedPlayerWarp {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(plaintextPassword, "plaintextPassword");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(earnedMoney, "earnedMoney");
        Objects.requireNonNull(createdAt, "createdAt");
        if (visits < 0) {
            throw new IllegalArgumentException("visits must not be negative: " + visits);
        }
        if (uniqueVisitors < 0) {
            throw new IllegalArgumentException("unique visitors must not be negative: " + uniqueVisitors);
        }
        ratings = List.copyOf(Objects.requireNonNull(ratings, "ratings"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        whitelist = List.copyOf(Objects.requireNonNull(whitelist, "whitelist"));
        bans = List.copyOf(Objects.requireNonNull(bans, "bans"));
        favourites = List.copyOf(Objects.requireNonNull(favourites, "favourites"));
    }

    /** One player's star rating on the warp, with the instant it was cast. */
    public record Rating(UUID player, int stars, Instant at) {
        public Rating {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(at, "at");
        }
    }

    /** One delegate granted a management {@link WarpRole} on the warp, with the instant they were added. */
    public record Member(UUID player, WarpRole role, Instant at) {
        public Member {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(at, "at");
        }
    }

    /** One player's ban from the warp: who, an optional expiry and reason and issuer, and when it was imposed. */
    public record Ban(
            UUID player, Optional<Instant> until, Optional<String> reason, Optional<UUID> bannedBy, Instant at) {
        public Ban {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(until, "until");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(bannedBy, "bannedBy");
            Objects.requireNonNull(at, "at");
        }
    }
}
