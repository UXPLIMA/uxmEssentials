package com.uxplima.uxmessentials.migration.convert.ax.parse;

/**
 * One row of an AxPlayerWarps player-list table. {@code axplayerwarps_whitelisted}, {@code _blacklisted}, or
 * {@code _favorites}, which all share the same {@code (player_id, warp_id, date)} shape. The {@code playerId} is
 * resolved to a uuid through the player map when the plan groups these rows by {@code warpId}. {@code date} is the
 * epoch-millisecond instant the entry was added ({@code 0} when the source stored none).
 *
 * @param warpId the warp the entry belongs to
 * @param playerId the listed player's AxPlayerWarps id
 * @param date when the entry was added, in epoch milliseconds
 */
public record AxAccessRow(int warpId, int playerId, long date) {}
