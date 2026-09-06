package com.uxplima.uxmessentials.migration.convert.olzie.parse;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * One Olzie PlayerWarps warp row from {@code playerwarps_warps}. Unlike the normalised AxPlayerWarps schema, Olzie
 * denormalises everything onto the warp: the owner is stored as a uuid string, and the world / icon / category are
 * stored by name rather than by a lookup id, so no join is needed to read a warp. Only the side tables key on the
 * warp's id. Olzie's warps table carries no explicit id column, so {@code id} is the SQLite {@code rowid} the reader's
 * {@code SELECT rowid AS id} surfaces; every side row references that same value.
 *
 * <p>Access is not a single flag but three inputs the mapper resolves in order: a non-blank {@code password} means the
 * warp is password-gated, else a truthy {@code whitelistEnabled} means whitelist-only, else a truthy {@code locked}
 * means private, else public. A nullable text column (world, description, category, icon, password) is absent when the
 * source left it unset.
 *
 * @param id the warp's SQLite {@code rowid}, the key its rate / whitelist / manager / ban / favourite rows reference
 * @param ownerUuid the owner's uuid text, or null when the source stored none
 * @param world the world name, or null when the source stored none
 * @param x the warp x coordinate
 * @param y the warp y coordinate
 * @param z the warp z coordinate
 * @param yaw the warp yaw
 * @param pitch the warp pitch
 * @param name the source warp name, sanitised and de-collided at write time
 * @param description the owner's blurb, or null
 * @param category the browse category name, or null
 * @param icon the icon token (a material name), or null
 * @param visits the total teleport count Olzie tracks on the warp row
 * @param date the creation instant in epoch milliseconds
 * @param cost the entry price
 * @param password the plaintext password gate, or null when the warp is not password-gated
 * @param whitelistEnabled whether the warp restricts entry to its whitelist
 * @param locked whether the warp is locked to its owner
 */
public record OlzieWarpRow(
        long id,
        @Nullable String ownerUuid,
        @Nullable String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String name,
        @Nullable String description,
        @Nullable String category,
        @Nullable String icon,
        long visits,
        long date,
        double cost,
        @Nullable String password,
        boolean whitelistEnabled,
        boolean locked) {

    public OlzieWarpRow {
        Objects.requireNonNull(name, "name");
    }
}
