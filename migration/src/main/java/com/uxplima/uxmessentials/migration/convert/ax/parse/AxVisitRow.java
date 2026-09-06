package com.uxplima.uxmessentials.migration.convert.ax.parse;

/**
 * The visit tally for one warp, aggregated by the reader's {@code GROUP BY warp_id} over
 * {@code axplayerwarps_visits}: {@code total} teleports and {@code unique} distinct visitors. Reading the counts in
 * one grouped query keeps the visit log, the largest of the source's side tables, off the per-warp path.
 *
 * @param warpId the warp the tally belongs to
 * @param total the total number of visit rows
 * @param unique the number of distinct visitors
 */
public record AxVisitRow(int warpId, long total, long unique) {}
