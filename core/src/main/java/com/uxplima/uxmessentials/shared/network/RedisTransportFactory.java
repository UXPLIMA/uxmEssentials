package com.uxplima.uxmessentials.shared.network;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * The seam the main plugin uses to obtain a Redis {@link BusTransport} without ever naming a Lettuce type or
 * bundling a Redis client. The main jar owns this interface; the optional {@code uxmEssentials-redis} companion
 * plugin implements it (over Lettuce) and publishes the implementation through Bukkit's {@code ServicesManager}.
 *
 * <p>This indirection is what makes the companion loadable at all. A Redis transport implements the main jar's
 * {@link BusTransport}, and that instance crosses the classloader boundary into the main's {@code BusCore}. If
 * the companion shaded its own copy of {@code core}, the two {@code BusTransport} class objects would differ and
 * the JVM would reject the hand-off with a loader-constraint {@link LinkageError}. So the companion is a real
 * Paper plugin that joins the main's classpath ({@code paper-plugin.yml > join-classpath}) and references the
 * <em>same</em> {@link BusTransport} and this factory the main loaded. No duplicate class, no constraint
 * violation.
 *
 * <p>Pure Java by design (no Bukkit, no Lettuce) so {@code core} can name it. The main looks it up through the
 * {@code ServicesManager} after enable; a {@code null} lookup means the companion is not deployed, in which case
 * the bus degrades to local-only rather than failing.
 */
public interface RedisTransportFactory {

    /**
     * Build the Redis pub/sub transport carrying the whole bus frame over a Redis channel with no proxy.
     *
     * <p>{@code password} is empty to skip auth; {@code db} is the logical database index appended to the
     * connection URI for config back-compat (Redis pub/sub is global across logical databases, so it has no
     * functional effect). {@code channel} is the pub/sub channel both sides publish to and subscribe on. The
     * {@code scheduler} is accepted to keep the call shape identical to the other transports; the implementation
     * owns its own threading on the Redis client's event loop.
     */
    BusTransport redis(String host, int port, String password, int db, String channel, Scheduler scheduler, Logger log);
}
