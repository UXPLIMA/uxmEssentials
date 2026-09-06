package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import org.jspecify.annotations.NullMarked;

/**
 * A read-only view of the cross-server bus's runtime state for {@code /uxmess doctor}: whether this backend opts
 * into network sync, which transport carries the bus, and, the point of the view, whether the running transport
 * can currently deliver frames. It is the diagnostic seam the bus exposes <em>without</em> handing out the
 * {@link BusCore} or the transport itself, mirroring how {@link Bus} exposes only the publish/register seams.
 *
 * <p>{@link #healthy()} is a live read of the transport's own flag ({@code BusTransport#healthy()}), a cheap
 * volatile-field read, never a blocking connect, so the doctor command may call it on the run thread. For a
 * Redis transport it reflects whether the subscribe connection is actually up; for the plugin-messaging transport
 * it reflects whether the channel is registered. A disabled backend reports {@code enabled() == false} and the
 * doctor never reads {@link #healthy()}.
 */
@NullMarked
public interface BusHealth {

    /** True when this backend participates in network sync ({@code network.enabled = true}). */
    boolean enabled();

    /** The configured transport carrying the bus, lower-cased: {@code velocity}, {@code redis}, or {@code both}. */
    String transport();

    /** A live read of whether the running transport can currently deliver frames. Cheap; never blocks. */
    boolean healthy();
}
