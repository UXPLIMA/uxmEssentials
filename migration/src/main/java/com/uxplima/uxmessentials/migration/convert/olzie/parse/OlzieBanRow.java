package com.uxplima.uxmessentials.migration.convert.olzie.parse;

import org.jspecify.annotations.Nullable;

/**
 * One row of {@code playerwarps_warps_banned}: a single player's ban from a warp. Olzie is the only player-warp source
 * that records a ban's {@code reason} and the {@code time} it was imposed, so. Unlike the AxPlayerWarps blacklist and
 * the Athelion blocked-players, which carry only the player, an Olzie ban maps to a warp ban with its reason and
 * imposed-at instant preserved. Olzie stores no expiry, so the imported ban is permanent.
 *
 * @param warpId the banned-from warp's id
 * @param playerUuid the banned player's uuid text, or null when the source stored none
 * @param time when the ban was imposed, in epoch milliseconds ({@code 0} when the source stored none)
 * @param reason the stated ban reason, or null when the source stored none
 */
public record OlzieBanRow(
        long warpId,
        @Nullable String playerUuid,
        long time,
        @Nullable String reason) {}
