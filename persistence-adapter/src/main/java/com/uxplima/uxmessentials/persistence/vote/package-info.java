/**
 * The vote context's outbound persistence adapter: the jOOQ {@code VoteRepository} over the generated DSL
 * (the single-row {@code vote_party} counter and the per-player {@code vote_queue} offline reward batches)
 * and its thin counter-cache decorator. The party count is a first-class queryable column and each queued
 * reward command is a first-class row, there is no opaque JSON blob, and a drain selects then deletes a
 * player's rows in one transaction so a reward pays out exactly once. SQL is issued only through the typed
 * jOOQ DSL, never string concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.vote;
