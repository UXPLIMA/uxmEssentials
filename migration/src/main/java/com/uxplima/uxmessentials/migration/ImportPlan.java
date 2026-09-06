package com.uxplima.uxmessentials.migration;

import java.util.stream.Stream;

import org.jspecify.annotations.NullMarked;

/**
 * A source's lazily-evaluated import plan: the mapped {@link ImportRecord} stream and the descriptor of
 * the source that produced it. The stream is deliberately lazy and {@link AutoCloseable}, a 50 000-file
 * {@code userdata/} directory is read and mapped on demand as the executor drains it, never buffered
 * whole, which is what lets the import stay bounded and backpressured (docs/12-migration §7). The
 * importer batches the stream onto the bounded executor; the plan never touches a tick thread.
 *
 * <p>Parsing happens as the stream is consumed, strictly inside the adapter that built the plan. The
 * use case sees only {@link ImportRecord} domain shapes, never a YAML node or a foreign field.
 */
@NullMarked
public interface ImportPlan extends AutoCloseable {

    /** The mapped records, evaluated lazily as the stream is consumed. */
    Stream<ImportRecord> records();

    /** Release any file handles or readers the plan opened. */
    @Override
    void close();
}
