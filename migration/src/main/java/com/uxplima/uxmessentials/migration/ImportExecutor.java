package com.uxplima.uxmessentials.migration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NullMarked;

/**
 * Bounded, batched, backpressured import pool (docs/12-migration §7, docs/02-concurrency §6.10). It owns
 * a fixed-size <em>platform</em>-thread pool. The deliberate exception to the project's prefer-virtual-
 * threads default, because the goal is to cap DB write pressure, not maximise parallelism. The bounded
 * queue plus {@link ThreadPoolExecutor.CallerRunsPolicy} makes the producer stall when the writers lag,
 * so a 50 000-file source cannot out-run the writers and OOM the server. The executor is created on
 * import start and closed on finish and on module stop.
 *
 * <p>This pool never touches a region or tick thread, and it is the only place the importer spawns work:
 * no {@code new Thread(...)}, no {@code CompletableFuture.supplyAsync(...)}, no {@code BukkitRunnable}
 * ({@code ForbiddenConcurrencyApiDriftTest}).
 */
@NullMarked
public final class ImportExecutor implements AutoCloseable {

    /** Fixed writer count, caps concurrent DB write pressure (SQLite single-writer interplay, §7.1). */
    public static final int WRITERS = 4;

    /** Bounded queue depth; full queue triggers caller-runs backpressure. */
    public static final int QUEUE_CAPACITY = 1_000;

    /** Rows per committed transaction, bounds lock duration and replication lag. */
    public static final int BATCH = 500;

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            WRITERS,
            WRITERS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** Submit one batch to be written off-tick; backpressure runs it on the caller when the queue is full. */
    public void submitBatch(List<ImportRecord> batch, BatchSink sink) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(sink, "sink");
        List<ImportRecord> snapshot = List.copyOf(batch);
        pool.execute(() -> sink.write(snapshot));
    }

    /** Block until every queued and in-flight batch has finished, within {@code timeout}. */
    public boolean awaitQuiescence(long timeout, TimeUnit unit) throws InterruptedException {
        pool.shutdown();
        return pool.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        pool.shutdown();
    }

    /** Writes one batch in a single transaction. Implemented adapter-side over the persistence DSL. */
    @FunctionalInterface
    public interface BatchSink {
        void write(List<ImportRecord> batch);
    }
}
