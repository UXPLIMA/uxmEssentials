package com.uxplima.uxmessentials.playerwarps.domain;

import java.math.BigDecimal;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A lightweight, read-only projection of one warp for a browse listing. The flattened, display-ready columns a
 * card in the browse GUI needs, and nothing more. It is deliberately <em>not</em> the {@code PlayerWarp} aggregate:
 * the read-model builds a card straight from a projected row, so opening a browse over a hundred thousand warps
 * loads one page of cards rather than a hundred thousand aggregates. A card carries no password, no whitelist, no
 * rent ledger, only what a listing renders, and it never grants access: the teleport gate re-checks the real
 * warp when a player picks a card.
 *
 * @param id the warp's surrogate key, for the follow-up teleport or detail open
 * @param name the globally-unique warp name
 * @param displayName the operator-set display name, or {@code null} to fall back to {@link #name}
 * @param ownerName the owner's resolved display name (never blank, falls back to the owner uuid at read time)
 * @param world the human-facing world name the warp sits in
 * @param server the server id the warp lives on in a network, or {@code null} on a single server
 * @param category the warp's category id, or {@code null} when uncategorised
 * @param icon the icon token (material or head), or {@code null} to use the browse default
 * @param visits the total visit count
 * @param uniqueVisitors how many distinct players have visited
 * @param ratingAvg the mean star rating (0 when unrated)
 * @param ratingCount how many ratings the warp has
 * @param favourites how many players have favourited the warp
 * @param price the teleport price
 * @param currency the price currency id
 * @param access how the warp gates its visitors
 * @param sponsored whether the warp is in a live sponsored slot as of the read
 * @param viewerFavourited whether the player viewing the browse has favourited this warp
 */
public record WarpCard(
        PlayerWarpId id,
        String name,
        @Nullable String displayName,
        String ownerName,
        String world,
        @Nullable String server,
        @Nullable String category,
        @Nullable String icon,
        long visits,
        int uniqueVisitors,
        double ratingAvg,
        int ratingCount,
        int favourites,
        BigDecimal price,
        String currency,
        WarpAccess access,
        boolean sponsored,
        boolean viewerFavourited) {

    public WarpCard {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(access, "access");
    }
}
