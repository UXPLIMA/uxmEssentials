package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * The orderings the paged browse offers. Each constant maps to one {@code ORDER BY} the read-model applies in the
 * database, always with a stable {@code id} tiebreaker appended so paging is deterministic. Two warps that tie on
 * the primary key never swap places between page reads.
 *
 * <ul>
 *   <li>{@link #ALPHABETICAL}: by name, ascending.
 *   <li>{@link #NEWEST} / {@link #OLDEST}, by creation time.
 *   <li>{@link #VISITS}: by total visit count, most-visited first.
 *   <li>{@link #UNIQUE_VISITORS}, by distinct visitor count, most first.
 *   <li>{@link #RATING}. By the stored Bayesian rating score, highest first (the rating use case computes the
 *       score; the browse only orders by it).
 *   <li>{@link #RATING_COUNT}: by how many ratings a warp has, most first.
 *   <li>{@link #FAVOURITES}, by favourite count, most first.
 *   <li>{@link #DISTANCE}. By squared planar distance from the viewer, nearest first, within the viewer's world;
 *       with no viewer position it falls back to {@link #ALPHABETICAL}.
 *   <li>{@link #RANDOM}: by the persisted {@code random_sort} column, the only way to page a stable shuffle.
 * </ul>
 */
public enum WarpSort {
    ALPHABETICAL,
    NEWEST,
    OLDEST,
    VISITS,
    UNIQUE_VISITORS,
    RATING,
    RATING_COUNT,
    FAVOURITES,
    DISTANCE,
    RANDOM
}
