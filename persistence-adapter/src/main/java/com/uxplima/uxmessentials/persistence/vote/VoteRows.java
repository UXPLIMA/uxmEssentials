package com.uxplima.uxmessentials.persistence.vote;

import java.time.Instant;

import com.uxplima.uxmessentials.persistence.jooq.tables.VoteQueue;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import org.jooq.Record;

/**
 * The anti-corruption mapping between {@code vote_queue} rows and the domain {@link QueuedReward}. A
 * single queued batch is stored as one row per command (keyed {@code (player, idx)}), so a batch loaded
 * for a player is the player's command rows in {@code idx} order rebuilt into one {@link QueuedReward};
 * UUIDs are stored as canonical 36-character text and the queue time as epoch milliseconds, identical on
 * every backend. This class is the single place that translation lives.
 *
 * <p>The voter name is not persisted (only the uuid is), so a {@link QueuedReward} rebuilt from rows
 * carries the player uuid with the uuid string as a placeholder name. The join handler passes the live
 * name through when it dispatches the drained commands.
 */
final class VoteRows {

    private static final VoteQueue VOTE_QUEUE = VoteQueue.VOTE_QUEUE;

    private VoteRows() {}

    /** The {@code idx}-ordered command text of a queried {@code vote_queue} row. */
    static String toCommand(Record row) {
        return row.get(VOTE_QUEUE.COMMAND);
    }

    /** The queue time of a queried {@code vote_queue} row. */
    static Instant toQueuedAt(Record row) {
        return Instant.ofEpochMilli(row.get(VOTE_QUEUE.QUEUED_AT));
    }
}
