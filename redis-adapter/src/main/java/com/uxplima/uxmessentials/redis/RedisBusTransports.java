package com.uxplima.uxmessentials.redis;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmlib.redis.LettuceRedisBus;
import com.uxplima.uxmlib.redis.RedisBus;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StaticCredentialsProvider;

/**
 * Factory for the cross-server bus's Redis transport, so the consuming {@code bukkit-adapter} wires a
 * {@link BusTransport} from configuration without ever naming a Lettuce type, Lettuce ({@link RedisClient},
 * {@link RedisURI}) is bundled in this companion jar alone, kept off the host's compile classpath. The host
 * references this factory {@code compileOnly} and guards the call against the jar being absent at runtime.
 */
public final class RedisBusTransports {

    private RedisBusTransports() {}

    /**
     * Build the Redis pub/sub transport that carries the full bus frame bytes over a Redis channel with no proxy.
     *
     * <p>{@code password} is empty to skip auth; {@code db} is the logical database index. Redis pub/sub is
     * global across logical databases, so it has no functional effect on this transport, but it is appended to
     * the connection URI for back-compat with the existing {@code network.redis.db} config so an operator's
     * settings still parse. {@code channel} is the Redis pub/sub channel both sides publish to and subscribe on.
     * The {@code scheduler} is accepted to keep the call site identical to the other transports; Lettuce owns the
     * transport's own threading on its event loop, so it is not used here.
     */
    public static BusTransport redis(
            String host, int port, String password, int db, String channel, Scheduler scheduler, Logger log) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");

        RedisURI.Builder uri = RedisURI.builder().withHost(host).withPort(port);
        if (db != 0) {
            uri.withDatabase(db);
        }
        if (!password.isEmpty()) {
            // Lettuce 7 dropped RedisURI.setPassword; auth flows through a credentials provider (username null =
            // default ACL user).
            uri.withAuthentication(new StaticCredentialsProvider(null, password.toCharArray()));
        }
        RedisClient client = RedisClient.create(uri.build());
        RedisBus bus = new LettuceRedisBus(client, log::warn);
        return new RedisBusTransportAdapter(bus, channel, log);
    }
}
