package com.uxplima.uxmessentials.migration.convert.olzie.parse;

/**
 * The distinct-visitor tally for one warp, aggregated by the reader's {@code COUNT(DISTINCT player_uuid)} over
 * {@code playerwarps_warps_visits}. Olzie already keeps the <em>total</em> visit count on the warp row itself, so this
 * side table is read only for the distinct-visitor figure the total does not carry; reading it in one grouped query
 * keeps the visit log, the largest of the source's side tables, off the per-warp path.
 *
 * @param warpId the warp the tally belongs to
 * @param uniqueVisitors the number of distinct players who have visited
 */
public record OlzieVisitRow(long warpId, int uniqueVisitors) {}
