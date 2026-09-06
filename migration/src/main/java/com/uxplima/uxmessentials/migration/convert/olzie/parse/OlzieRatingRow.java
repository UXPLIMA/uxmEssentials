package com.uxplima.uxmessentials.migration.convert.olzie.parse;

import org.jspecify.annotations.Nullable;

/**
 * One row of {@code playerwarps_rates}: a single reviewer's star vote on a warp. The reviewer is stored as a uuid text
 * ({@code uuid}); the plan parses it and drops the vote when it does not parse. Olzie keeps the vote's free-text
 * {@code description} review, which our rating model does not hold, so that column is read past, only the star
 * {@code rate} and the reviewer survive. Olzie records no per-vote timestamp, so the imported rating is stamped at the
 * epoch; the writer rolls the per-vote stars up into the warp's average and count.
 *
 * @param warpId the rated warp's id
 * @param reviewerUuid the reviewer's uuid text, or null when the source stored none
 * @param rate the star value the reviewer gave
 */
public record OlzieRatingRow(long warpId, @Nullable String reviewerUuid, int rate) {}
