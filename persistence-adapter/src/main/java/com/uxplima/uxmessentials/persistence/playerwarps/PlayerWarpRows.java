package com.uxplima.uxmessentials.persistence.playerwarps;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.Sponsorship;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code player_warps} row and the domain {@link PlayerWarp}. Every warp
 * fact is a first-class typed column, there is no JSON blob anywhere in this table, so the mapping is a plain
 * column-by-column translation: uuids as their canonical 36-character text, instants as {@code BIGINT} epoch
 * milliseconds, money as {@code DECIMAL(20,4)}, and the {@link WarpAccess}/{@link WarpStatus} axes as their
 * uppercase enum names. This class is the single place that translation lives.
 *
 * <p>Two subtleties are load-bearing. The owner's display name is stored on the row ({@code owner_name}) but falls
 * back to the {@code names} resolver when a legacy row left it null, so a browse always renders a name rather than a
 * raw uuid. And the password is never mapped back onto the aggregate: the domain carries only a {@code passwordSet}
 * flag (derived from whether {@code password_algorithm} is present), and {@link #apply} deliberately never writes the
 * three {@code password_*} columns: a save that flips visibility must never disturb a set password. Password writes
 * are a separate concern handled outside this mapping.
 */
final class PlayerWarpRows {

    private PlayerWarpRows() {}

    /** Rebuild a {@link PlayerWarp} from a queried row, resolving a missing owner name through {@code names}. */
    static PlayerWarp toPlayerWarp(PlayerWarpsRecord row, Function<UUID, String> names) {
        UUID ownerUuid = UUID.fromString(row.getOwner());
        String ownerName = row.getOwnerName() != null ? row.getOwnerName() : names.apply(ownerUuid);
        return new PlayerWarp(
                Optional.of(PlayerWarpId.of(row.getId())),
                new PlayerRef(ownerUuid, ownerName),
                ownerName,
                PlayerWarpName.of(row.getName()),
                optional(row.getDisplayName(), DisplayName::of),
                position(row),
                Optional.ofNullable(row.getServerId()),
                Optional.ofNullable(row.getCategoryId()),
                optional(row.getDescription(), WarpDescription::of),
                optional(row.getIcon(), IconSpec::of),
                WarpAccess.parse(row.getAccess()).orElseThrow(() -> unknown("access", row.getAccess())),
                row.getPasswordAlgorithm() != null,
                WarpStatus.parse(row.getStatus()).orElseThrow(() -> unknown("status", row.getStatus())),
                WarpCost.of(row.getPriceAmount(), row.getPriceCurrency()),
                WarpEarnings.of(row.getEarnedAmount(), row.getEarnedCurrency()),
                RatingSummary.of(
                        row.getRatingSum(), row.getRatingCount(), row.getRatingAverage(), row.getRatingScore()),
                new VisitSummary(row.getVisitCount(), row.getUniqueVisitors()),
                row.getFavouriteCount(),
                sponsorship(row),
                rent(row),
                effects(row),
                timing(row),
                Instant.ofEpochMilli(row.getCreatedAt()),
                Instant.ofEpochMilli(row.getUpdatedAt()));
    }

    /**
     * Populate {@code record} from {@code warp} for an insert or update, every column <em>except</em> the surrogate
     * {@code id} (the repository owns that: it stamps a fresh id on insert and uses it as the update key) and the
     * three {@code password_*} columns (never carried on the aggregate; see the class note).
     */
    static void apply(PlayerWarpsRecord record, PlayerWarp warp) {
        applyIdentityAndLocation(record, warp);
        applyPresentation(record, warp);
        applyMoneyAndRollups(record, warp);
        applyFacetsAndTimestamps(record, warp);
    }

    private static void applyIdentityAndLocation(PlayerWarpsRecord record, PlayerWarp warp) {
        Position location = warp.location();
        record.setName(warp.name().value())
                .setOwner(warp.owner().uuid().toString())
                .setOwnerName(warp.ownerName())
                .setServerId(warp.serverId().orElse(null))
                .setWorld(location.world().uid().toString())
                .setWorldName(location.world().name())
                .setX(location.x())
                .setY(location.y())
                .setZ(location.z())
                .setYaw(location.yaw())
                .setPitch(location.pitch());
    }

    private static void applyPresentation(PlayerWarpsRecord record, PlayerWarp warp) {
        record.setDisplayName(warp.displayName().map(DisplayName::value).orElse(null))
                .setCategoryId(warp.categoryId().orElse(null))
                .setDescription(warp.description().map(WarpDescription::value).orElse(null))
                .setIcon(warp.icon().map(IconSpec::value).orElse(null))
                .setAccess(warp.access().name())
                .setStatus(warp.status().name());
    }

    private static void applyMoneyAndRollups(PlayerWarpsRecord record, PlayerWarp warp) {
        RatingSummary ratings = warp.ratings();
        VisitSummary visits = warp.visits();
        record.setPriceAmount(warp.price().amount())
                .setPriceCurrency(warp.price().currencyId())
                .setEarnedAmount(warp.earnings().amount())
                .setEarnedCurrency(warp.earnings().currencyId())
                .setRatingSum(ratings.sum())
                .setRatingCount(ratings.count())
                .setRatingAverage(ratings.average())
                .setRatingScore(ratings.score())
                .setVisitCount(visits.count())
                .setUniqueVisitors(visits.uniqueVisitors())
                .setFavouriteCount(warp.favouriteCount());
    }

    private static void applyFacetsAndTimestamps(PlayerWarpsRecord record, PlayerWarp warp) {
        WarpEffects effects = warp.effects();
        WarpTimingOverrides timing = warp.timing();
        record.setSponsoredUntil(warp.sponsorship()
                        .map(s -> s.activeUntil().toEpochMilli())
                        .orElse(null))
                .setSponsorSlot(warp.sponsorship().map(Sponsorship::slot).orElse(null))
                .setRentPaidUntil(
                        warp.rent().map(r -> r.paidUntil().toEpochMilli()).orElse(null))
                .setRentSuspendedAt(millisOrNull(warp.rent().flatMap(RentState::suspendedAt)))
                .setRentArchiveAfter(millisOrNull(warp.rent().flatMap(RentState::archiveAfter)))
                .setWarmupSeconds(timing.warmupSeconds().orElse(null))
                .setCooldownSeconds(timing.cooldownSeconds().orElse(null))
                .setDepartureSound(effects.departureSound().orElse(null))
                .setArrivalSound(effects.arrivalSound().orElse(null))
                .setDepartureParticle(effects.departureParticle().orElse(null))
                .setArrivalParticle(effects.arrivalParticle().orElse(null))
                .setCreatedAt(warp.createdAt().toEpochMilli())
                .setUpdatedAt(warp.updatedAt().toEpochMilli());
    }

    private static Position position(PlayerWarpsRecord row) {
        WorldRef world = new WorldRef(UUID.fromString(row.getWorld()), row.getWorldName());
        return new Position(world, row.getX(), row.getY(), row.getZ(), row.getYaw(), row.getPitch());
    }

    private static Optional<Sponsorship> sponsorship(PlayerWarpsRecord row) {
        Long until = row.getSponsoredUntil();
        Integer slot = row.getSponsorSlot();
        // A sponsorship is a promotion in a numbered slot, so it needs both facets. The expiry sweep frees the slot
        // (NULL) while leaving the now-past sponsored_until behind, so a slot-freed row carries no live sponsorship
        // and a subsequent whole-aggregate save writes both back as NULL, self-healing the stale timestamp.
        if (until == null || slot == null) {
            return Optional.empty();
        }
        return Optional.of(new Sponsorship(Instant.ofEpochMilli(until), slot));
    }

    private static Optional<RentState> rent(PlayerWarpsRecord row) {
        Long paidUntil = row.getRentPaidUntil();
        if (paidUntil == null) {
            return Optional.empty();
        }
        return Optional.of(new RentState(
                Instant.ofEpochMilli(paidUntil),
                instant(row.getRentSuspendedAt()),
                instant(row.getRentArchiveAfter())));
    }

    private static WarpEffects effects(PlayerWarpsRecord row) {
        return new WarpEffects(
                Optional.ofNullable(row.getDepartureSound()),
                Optional.ofNullable(row.getArrivalSound()),
                Optional.ofNullable(row.getDepartureParticle()),
                Optional.ofNullable(row.getArrivalParticle()));
    }

    private static WarpTimingOverrides timing(PlayerWarpsRecord row) {
        return new WarpTimingOverrides(
                Optional.ofNullable(row.getWarmupSeconds()), Optional.ofNullable(row.getCooldownSeconds()));
    }

    private static <T> Optional<T> optional(@Nullable String value, Function<String, T> factory) {
        return value == null ? Optional.empty() : Optional.of(factory.apply(value));
    }

    private static Optional<Instant> instant(@Nullable Long millis) {
        return millis == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }

    private static @Nullable Long millisOrNull(Optional<Instant> instant) {
        return instant.map(Instant::toEpochMilli).orElse(null);
    }

    private static IllegalStateException unknown(String field, String value) {
        return new IllegalStateException("unrecognised player warp " + field + " token in storage: " + value);
    }
}
