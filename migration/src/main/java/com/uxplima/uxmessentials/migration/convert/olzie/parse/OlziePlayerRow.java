package com.uxplima.uxmessentials.migration.convert.olzie.parse;

import org.jspecify.annotations.Nullable;

/**
 * One row of an Olzie player-list side table. {@code playerwarps_warps_whitelisted},
 * {@code playerwarps_warps_managers}, or {@code playerwarps_players_favourite_warps}, which all reduce to the same
 * {@code (warpId, playerUuid)} pair. Olzie stores the player as a uuid text directly (there is no player-id lookup
 * table to resolve through, unlike AxPlayerWarps), so the plan parses {@code playerUuid} to a uuid when it groups
 * these rows by {@code warpId}; a row whose uuid text does not parse is skipped.
 *
 * @param warpId the warp the entry belongs to
 * @param playerUuid the listed player's uuid text, or null when the source stored none
 */
public record OlziePlayerRow(long warpId, @Nullable String playerUuid) {}
