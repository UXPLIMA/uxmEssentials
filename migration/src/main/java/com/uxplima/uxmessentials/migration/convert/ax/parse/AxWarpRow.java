package com.uxplima.uxmessentials.migration.convert.ax.parse;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * One AxPlayerWarps warp row with its foreign ids already resolved to names by the reader's join
 * ({@code axplayerwarps_warps} joined to {@code _worlds} / {@code _categories} / {@code _materials} /
 * {@code _currencies}). The owner is still an id. It is resolved through the pre-loaded player map, not a join, since
 * both the warp owner and every side-table row key on that same id. {@code access} is the ordinal of AxPlayerWarps'
 * {@code Access} enum ({@code 0=PUBLIC, 1=WHITELISTED, 2=PRIVATE}); AxPlayerWarps has no password gate. A nullable
 * column (world, description, category, material, currency) is absent when the source left it unset.
 *
 * @param id the warp's surrogate id, the key its rating / whitelist / blacklist / favourite / visit rows reference
 * @param ownerId the owner's AxPlayerWarps player id, resolved through the player map
 * @param world the world name, or null when the join found none
 * @param x the warp x coordinate
 * @param y the warp y coordinate
 * @param z the warp z coordinate
 * @param yaw the warp yaw
 * @param pitch the warp pitch
 * @param name the source warp name, sanitised and de-collided at write time
 * @param description the owner's blurb, or null
 * @param category the browse category name, or null
 * @param material the icon material name, or null
 * @param created the creation instant in epoch milliseconds
 * @param currency the price currency's AxPlayerWarps hook name, or null
 * @param price the entry price
 * @param earnedMoney the accrued owner earnings
 * @param access the ordinal of the source's {@code Access} enum
 */
public record AxWarpRow(
        int id,
        int ownerId,
        @Nullable String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String name,
        @Nullable String description,
        @Nullable String category,
        @Nullable String material,
        long created,
        @Nullable String currency,
        double price,
        double earnedMoney,
        int access) {

    public AxWarpRow {
        Objects.requireNonNull(name, "name");
    }
}
