package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * One player-owned warp, identified by a durable surrogate {@link PlayerWarpId} that the database assigns on the
 * first save. Its {@link PlayerWarpName} is server-wide unique, two players can never hold the same name, so the
 * name alone addresses a warp, and access is governed by an ordered {@link WarpAccess} axis
 * (public / password / whitelist / private) crossed with a {@link WarpStatus} lifecycle
 * (active / suspended / archived), not a single public flag.
 *
 * <p>Around that identity the aggregate composes the presentation, economy, and social facets a browsable warp
 * network needs: an optional {@link DisplayName}, a category id, {@link WarpDescription} and {@link IconSpec}
 * for listing; a {@link WarpCost} entry price and accrued {@link WarpEarnings}; denormalised {@link RatingSummary}
 * and {@link VisitSummary} rollups plus a favourite count for sorting without scanning child tables; optional
 * {@link Sponsorship} and {@link RentState} for paid placement; and the {@link WarpEffects} / {@link WarpTimingOverrides}
 * a warp customises its teleport with. The password itself is never held here, only a {@link #passwordSet} flag;
 * the hash lives in persistence and is verified through a hashing port, so the domain never touches a secret.
 */
public record PlayerWarp(
        Optional<PlayerWarpId> id,
        PlayerRef owner,
        String ownerName,
        PlayerWarpName name,
        Optional<DisplayName> displayName,
        Position location,
        Optional<String> serverId,
        Optional<String> categoryId,
        Optional<WarpDescription> description,
        Optional<IconSpec> icon,
        WarpAccess access,
        boolean passwordSet,
        WarpStatus status,
        WarpCost price,
        WarpEarnings earnings,
        RatingSummary ratings,
        VisitSummary visits,
        int favouriteCount,
        Optional<Sponsorship> sponsorship,
        Optional<RentState> rent,
        WarpEffects effects,
        WarpTimingOverrides timing,
        Instant createdAt,
        Instant updatedAt) {

    public PlayerWarp {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(earnings, "earnings");
        Objects.requireNonNull(ratings, "ratings");
        Objects.requireNonNull(visits, "visits");
        Objects.requireNonNull(sponsorship, "sponsorship");
        Objects.requireNonNull(rent, "rent");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (favouriteCount < 0) {
            throw new IllegalArgumentException("favourite count must not be negative: " + favouriteCount);
        }
    }

    /**
     * A brand-new warp owned by {@code owner}, at {@code location}, created now. It has no id until its first
     * save assigns one, starts {@link WarpAccess#PRIVATE} and {@link WarpStatus#ACTIVE}, carries no password, is
     * free, and has empty economy / rating / visit rollups and no optional facets. {@code ownerName} is the
     * owner's display name captured at creation so a browse can render the author without a lookup.
     */
    public static PlayerWarp create(
            PlayerRef owner, String ownerName, PlayerWarpName name, Position location, Instant now) {
        return new PlayerWarp(
                Optional.empty(),
                owner,
                ownerName,
                name,
                Optional.empty(),
                location,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                WarpAccess.PRIVATE,
                false,
                WarpStatus.ACTIVE,
                WarpCost.free(),
                WarpEarnings.zero("default"),
                RatingSummary.empty(),
                VisitSummary.empty(),
                0,
                Optional.empty(),
                Optional.empty(),
                WarpEffects.none(),
                WarpTimingOverrides.none(),
                now,
                now);
    }

    /** A pre-filled builder for the internal transitions; the public surface stays the {@code with*} methods. */
    PlayerWarpBuilder toBuilder() {
        return new PlayerWarpBuilder(this);
    }

    /** A copy re-anchored to {@code newLocation}, keeping every other field and stamping {@code now} as the edit time. */
    public PlayerWarp movedTo(Position newLocation, Instant now) {
        Objects.requireNonNull(newLocation, "newLocation");
        Objects.requireNonNull(now, "now");
        return toBuilder().location(newLocation).updatedAt(now).build();
    }

    /** A copy with the access axis set to {@code newAccess}, stamping {@code now} as the edit time. */
    public PlayerWarp withAccess(WarpAccess newAccess, Instant now) {
        Objects.requireNonNull(newAccess, "newAccess");
        Objects.requireNonNull(now, "now");
        return toBuilder().access(newAccess).updatedAt(now).build();
    }

    /** A copy with the display name swapped, stamping {@code now} as the edit time. */
    public PlayerWarp withDisplayName(Optional<DisplayName> newDisplayName, Instant now) {
        Objects.requireNonNull(newDisplayName, "newDisplayName");
        Objects.requireNonNull(now, "now");
        return toBuilder().displayName(newDisplayName).updatedAt(now).build();
    }

    /** A copy with the browse icon swapped, stamping {@code now} as the edit time. */
    public PlayerWarp withIcon(Optional<IconSpec> newIcon, Instant now) {
        Objects.requireNonNull(newIcon, "newIcon");
        Objects.requireNonNull(now, "now");
        return toBuilder().icon(newIcon).updatedAt(now).build();
    }

    /** A copy with the description swapped, stamping {@code now} as the edit time. */
    public PlayerWarp withDescription(Optional<WarpDescription> newDescription, Instant now) {
        Objects.requireNonNull(newDescription, "newDescription");
        Objects.requireNonNull(now, "now");
        return toBuilder().description(newDescription).updatedAt(now).build();
    }

    /** A copy with the teleport effects replaced, stamping {@code now} as the edit time. */
    public PlayerWarp withEffects(WarpEffects newEffects, Instant now) {
        Objects.requireNonNull(newEffects, "newEffects");
        Objects.requireNonNull(now, "now");
        return toBuilder().effects(newEffects).updatedAt(now).build();
    }

    /** A copy with the per-warp warmup/cooldown overrides replaced, stamping {@code now} as the edit time. */
    public PlayerWarp withTiming(WarpTimingOverrides newTiming, Instant now) {
        Objects.requireNonNull(newTiming, "newTiming");
        Objects.requireNonNull(now, "now");
        return toBuilder().timing(newTiming).updatedAt(now).build();
    }

    /** A copy filed under a different browse category (or none), stamping {@code now} as the edit time. */
    public PlayerWarp withCategoryId(Optional<String> newCategoryId, Instant now) {
        Objects.requireNonNull(newCategoryId, "newCategoryId");
        Objects.requireNonNull(now, "now");
        return toBuilder().categoryId(newCategoryId).updatedAt(now).build();
    }

    /** A copy with the entry price (and its currency) swapped, stamping {@code now} as the edit time. */
    public PlayerWarp withPrice(WarpCost newPrice, Instant now) {
        Objects.requireNonNull(newPrice, "newPrice");
        Objects.requireNonNull(now, "now");
        return toBuilder().price(newPrice).updatedAt(now).build();
    }

    /** A copy moved to a different lifecycle {@link WarpStatus}, stamping {@code now} as the edit time. */
    public PlayerWarp withStatus(WarpStatus newStatus, Instant now) {
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(now, "now");
        return toBuilder().status(newStatus).updatedAt(now).build();
    }

    /**
     * A copy carrying a new {@link RentState}, stamping {@code now} as the edit time. This is the rent lifecycle's
     * transition. A renewal advances {@link RentState#paidUntil} and clears the suspend/archive marks, a suspension
     * stamps them, and is kept orthogonal to {@link #withStatus}: the sweep pairs the two (suspend flips the status
     * <em>and</em> writes the marks) so each stays a single-responsibility edit.
     */
    public PlayerWarp withRent(RentState newRent, Instant now) {
        Objects.requireNonNull(newRent, "newRent");
        Objects.requireNonNull(now, "now");
        return toBuilder().rent(Optional.of(newRent)).updatedAt(now).build();
    }

    /**
     * A copy carrying a new (or cleared) {@link Sponsorship}, stamping {@code now} as the edit time. Buying a
     * sponsorship sets the promoted slot and its expiry; the sponsor expiry sweep frees the slot through the
     * persistence-only {@code sponsor_slot}/{@code sponsor_cooldown_until} writer rather than this whole-aggregate
     * edit, so the two stay orthogonal (as the rent transition does).
     */
    public PlayerWarp withSponsorship(Optional<Sponsorship> newSponsorship, Instant now) {
        Objects.requireNonNull(newSponsorship, "newSponsorship");
        Objects.requireNonNull(now, "now");
        return toBuilder().sponsorship(newSponsorship).updatedAt(now).build();
    }

    /**
     * A copy addressed by a different globally-unique {@link PlayerWarpName}, stamping {@code now} as the edit
     * time. The surrogate id is unchanged, so this renames the same row in place rather than creating a new warp.
     */
    public PlayerWarp renamed(PlayerWarpName newName, Instant now) {
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(now, "now");
        return toBuilder().name(newName).updatedAt(now).build();
    }

    /**
     * A copy handed to a new owner, stamping {@code now} as the edit time. Only the owner identity and the cached
     * {@code ownerName} change: the members, whitelist, bans, accrued earnings, ratings, and visits all stay with
     * the warp, so a transfer moves stewardship without resetting the warp's history.
     */
    public PlayerWarp transferredTo(PlayerRef newOwner, String newOwnerName, Instant now) {
        Objects.requireNonNull(newOwner, "newOwner");
        Objects.requireNonNull(newOwnerName, "newOwnerName");
        Objects.requireNonNull(now, "now");
        return toBuilder()
                .owner(newOwner)
                .ownerName(newOwnerName)
                .updatedAt(now)
                .build();
    }

    /**
     * A copy carrying the surrogate id the repository assigned on insert. This is an identity assignment, not a
     * field edit, so it deliberately does not bump {@link #updatedAt}: the warp is the same warp, now addressable
     * by its key.
     */
    public PlayerWarp withId(PlayerWarpId assignedId) {
        Objects.requireNonNull(assignedId, "assignedId");
        return toBuilder().id(Optional.of(assignedId)).build();
    }
}
